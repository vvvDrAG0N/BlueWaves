package com.epubreader.data.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.epubreader.core.debug.AppLog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

internal interface PdfToEpubConverter {
    suspend fun convert(
        pdfFile: File,
        outputFile: File,
        title: String,
        author: String,
    ): Boolean
}

internal class MlKitPdfToEpubConverter : PdfToEpubConverter {

    override suspend fun convert(
        pdfFile: File,
        outputFile: File,
        title: String,
        author: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) {
            return@withContext false
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val pages = extractPages(pdfFile, recognizer)
            if (!pages.qualifiesForGeneratedEpub()) {
                return@withContext false
            }

            writeGeneratedEpub(
                outputFile = outputFile,
                title = title,
                author = author,
                pages = pages,
            )
            true
        } catch (error: Exception) {
            AppLog.w(AppLog.PARSER, error) { "Failed to convert ${pdfFile.name} into EPUB" }
            false
        } finally {
            recognizer.close()
        }
    }
}

private data class PdfPageContent(
    val number: Int,
    val paragraphs: List<String>,
) {
    val characterCount: Int
        get() = paragraphs.sumOf { paragraph -> paragraph.count { !it.isWhitespace() } }
}

private suspend fun extractPages(
    pdfFile: File,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
): List<PdfPageContent> {
    return ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            buildList(renderer.pageCount) {
                for (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val renderWidth = PDF_CONVERSION_RENDER_WIDTH_PX
                        val renderHeight = max(
                            1,
                            (renderWidth.toFloat() * page.height.toFloat() / page.width.toFloat()).toInt(),
                        )
                        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            val matrix = Matrix().apply {
                                setScale(
                                    renderWidth.toFloat() / page.width.toFloat(),
                                    renderHeight.toFloat() / page.height.toFloat(),
                                )
                            }
                            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            add(
                                PdfPageContent(
                                    number = pageIndex + 1,
                                    paragraphs = recognizePageParagraphs(recognizer, bitmap),
                                ),
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }
}

private suspend fun recognizePageParagraphs(
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    bitmap: Bitmap,
): List<String> {
    val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
        recognizer
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
    }

    val paragraphs = result.textBlocks
        .mapNotNull { block ->
            block.lines
                .joinToString(separator = "\n") { line -> line.text.trim() }
                .normalizePdfParagraph()
        }
        .filter(String::isNotBlank)

    if (paragraphs.isNotEmpty()) {
        return paragraphs
    }

    return listOfNotNull(result.text.normalizePdfParagraph())
}

private fun List<PdfPageContent>.qualifiesForGeneratedEpub(): Boolean {
    if (isEmpty()) {
        return false
    }

    val nonEmptyPages = count { page -> page.paragraphs.isNotEmpty() }
    val totalCharacters = sumOf { it.characterCount }
    val requiredPages = when {
        size <= 2 -> 1
        else -> max(1, size / 2)
    }
    val requiredCharacters = max(60, size * 25)

    return nonEmptyPages >= requiredPages && totalCharacters >= requiredCharacters
}

private fun writeGeneratedEpub(
    outputFile: File,
    title: String,
    author: String,
    pages: List<PdfPageContent>,
) {
    outputFile.parentFile?.mkdirs()
    ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
        addStoredEntry(zip, "mimetype", EPUB_MIME_TYPE)
        addEntry(
            zip,
            "META-INF/container.xml",
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """.trimIndent().trimStart(),
        )
        addEntry(
            zip,
            "OEBPS/styles/book.css",
            """
            body { margin: 0; padding: 0; }
            section.page { margin: 0; }
            h1.page-title { margin: 0 0 1rem; font-size: 1.15rem; text-align: center; }
            p { margin: 0 0 1rem; text-indent: 0; }
            """.trimIndent(),
        )
        pages.forEach { page ->
            addEntry(
                zip,
                "OEBPS/pages/${page.fileName()}",
                page.toXhtmlDocument(title),
            )
        }
        addEntry(zip, "OEBPS/content.opf", buildOpfDocument(title, author, pages))
        addEntry(zip, "OEBPS/toc.ncx", buildNcxDocument(title, pages))
    }
}

private fun PdfPageContent.fileName(): String = "page-${number.toString().padStart(4, '0')}.xhtml"

private fun PdfPageContent.toXhtmlDocument(bookTitle: String): String {
    val body = if (paragraphs.isEmpty()) {
        "<p>This page could not be converted into reflowable text.</p>"
    } else {
        paragraphs.joinToString(separator = "\n") { paragraph ->
            "<p>${paragraph.escapeForXhtml().replace("\n", "<br />")}</p>"
        }
    }

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head>
            <title>${"$bookTitle - Page $number".escapeForXhtml()}</title>
            <link rel="stylesheet" type="text/css" href="../styles/book.css"/>
          </head>
          <body>
            <section class="page">
              <h1 class="page-title">Page $number</h1>
              $body
            </section>
          </body>
        </html>
    """.trimIndent().trimStart()
}

private fun buildOpfDocument(
    title: String,
    author: String,
    pages: List<PdfPageContent>,
): String {
    val manifest = buildString {
        appendLine("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        appendLine("""    <item id="css" href="styles/book.css" media-type="text/css"/>""")
        pages.forEach { page ->
            appendLine("""    <item id="page-${page.number}" href="pages/${page.fileName()}" media-type="application/xhtml+xml"/>""")
        }
    }.trimEnd()

    val spine = buildString {
        pages.forEach { page ->
            appendLine("""    <itemref idref="page-${page.number}"/>""")
        }
    }.trimEnd()

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>${title.escapeForXhtml()}</dc:title>
            <dc:creator>${author.escapeForXhtml()}</dc:creator>
            <dc:identifier id="BookId">urn:uuid:${UUID.randomUUID()}</dc:identifier>
            <dc:language>en</dc:language>
          </metadata>
          <manifest>
        $manifest
          </manifest>
          <spine toc="ncx">
        $spine
          </spine>
        </package>
    """.trimIndent().trimStart()
}

private fun buildNcxDocument(title: String, pages: List<PdfPageContent>): String {
    val navPoints = buildString {
        pages.forEach { page ->
            appendLine(
                """
                <navPoint id="page-${page.number}" playOrder="${page.number}">
                  <navLabel><text>Page ${page.number}</text></navLabel>
                  <content src="pages/${page.fileName()}"/>
                </navPoint>
                """.trimIndent(),
            )
        }
    }.trimEnd()

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <head/>
          <docTitle><text>${title.escapeForXhtml()}</text></docTitle>
          <navMap>
        $navPoints
          </navMap>
        </ncx>
    """.trimIndent().trimStart()
}

private fun addStoredEntry(zip: ZipOutputStream, name: String, content: String) {
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    val crc = CRC32().apply { update(bytes) }
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        this.crc = crc.value
    }
    zip.putNextEntry(entry)
    zip.write(bytes)
    zip.closeEntry()
}

private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
    zip.putNextEntry(ZipEntry(name))
    zip.write(content.toByteArray(StandardCharsets.UTF_8))
    zip.closeEntry()
}

private fun String.normalizePdfParagraph(): String? {
    return lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(separator = "\n")
        .takeIf(String::isNotBlank)
}

private fun String.escapeForXhtml(): String {
    return buildString(length) {
        for (char in this@escapeForXhtml) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}

private const val PDF_CONVERSION_RENDER_WIDTH_PX = 1440
