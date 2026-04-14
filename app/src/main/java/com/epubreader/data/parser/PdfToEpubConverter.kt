package com.epubreader.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.epubreader.core.debug.AppLog
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
import kotlinx.coroutines.CancellationException

internal const val PDF_CONVERSION_WORKSPACE_DIR_NAME = "pdf_conversion_workspace"

internal data class PdfConversionProgress(
    val completedPages: Int,
    val totalPages: Int,
)

internal data class PdfConversionResult(
    val succeeded: Boolean,
    val completedPages: Int,
    val totalPages: Int,
    val directTextPages: Int,
    val ocrPages: Int,
)

internal fun interface PdfConversionProgressListener {
    suspend fun onProgress(progress: PdfConversionProgress)
}

internal interface PdfToEpubConverter {
    suspend fun convert(
        pdfFile: File,
        workspaceDir: File,
        outputFile: File,
        title: String,
        author: String,
        onProgress: PdfConversionProgressListener = PdfConversionProgressListener {},
    ): PdfConversionResult
}

internal class MlKitPdfToEpubConverter(
    private val context: Context,
) : PdfToEpubConverter {

    override suspend fun convert(
        pdfFile: File,
        workspaceDir: File,
        outputFile: File,
        title: String,
        author: String,
        onProgress: PdfConversionProgressListener,
    ): PdfConversionResult = withContext(Dispatchers.IO) {
        if (!pdfFile.exists()) {
            return@withContext PdfConversionResult(
                succeeded = false,
                completedPages = 0,
                totalPages = 0,
                directTextPages = 0,
                ocrPages = 0,
            )
        }

        PDFBoxResourceLoader.init(context.applicationContext)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var pdfDocument: PDDocument? = null
        var rendererDescriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            workspaceDir.mkdirs()
            writeWorkspaceStylesheet(workspaceDir)

            val workspaceState = loadWorkspaceState(workspaceDir)
            pdfDocument = runCatching { PDDocument.load(pdfFile) }.getOrNull()
            val totalPages = pdfDocument?.numberOfPages ?: readPageCountWithRenderer(pdfFile)
            if (totalPages <= 0) {
                return@withContext PdfConversionResult(
                    succeeded = false,
                    completedPages = 0,
                    totalPages = 0,
                    directTextPages = 0,
                    ocrPages = 0,
                )
            }

            if (workspaceState.totalPages != totalPages) {
                clearWorkspacePages(workspaceDir)
                workspaceState.reset(totalPages)
                saveWorkspaceState(workspaceDir, workspaceState)
            }

            var completedPages = workspaceState.completedPages(workspaceDir)
            if (completedPages > 0) {
                onProgress.onProgress(PdfConversionProgress(completedPages, totalPages))
            }

            val stripper = pdfDocument?.let {
                PDFTextStripper().apply {
                    sortByPosition = true
                }
            }

            fun requireRenderer(): PdfRenderer {
                val existing = renderer
                if (existing != null) {
                    return existing
                }
                val descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val createdRenderer = PdfRenderer(descriptor)
                rendererDescriptor = descriptor
                renderer = createdRenderer
                return createdRenderer
            }

            for (pageNumber in 1..totalPages) {
                currentCoroutineContext().ensureActive()
                val pageFile = workspacePageFile(workspaceDir, pageNumber)
                if (pageFile.exists() && workspaceState.hasPage(pageNumber)) {
                    continue
                }

                val directParagraphs = extractDirectTextParagraphs(
                    document = pdfDocument,
                    stripper = stripper,
                    pageNumber = pageNumber,
                )
                val (paragraphs, extractionMethod) = if (directParagraphs.hasUsableText()) {
                    directParagraphs to PdfTextExtractionMethod.DIRECT_TEXT
                } else {
                    recognizePageParagraphs(
                        recognizer = recognizer,
                        renderer = requireRenderer(),
                        pageIndex = pageNumber - 1,
                    ).let { recognizedParagraphs ->
                        val method = if (recognizedParagraphs.hasUsableText()) {
                            PdfTextExtractionMethod.OCR
                        } else {
                            PdfTextExtractionMethod.EMPTY
                        }
                        recognizedParagraphs to method
                    }
                }

                writeWorkspacePage(
                    workspaceDir = workspaceDir,
                    pageNumber = pageNumber,
                    bookTitle = title,
                    paragraphs = paragraphs,
                )
                workspaceState.record(
                    pageNumber = pageNumber,
                    characterCount = paragraphs.characterCount(),
                    extractionMethod = extractionMethod,
                )

                completedPages = workspaceState.completedPages(workspaceDir)
                if (completedPages == totalPages || completedPages % PDF_CONVERSION_PROGRESS_CHUNK_SIZE == 0) {
                    saveWorkspaceState(workspaceDir, workspaceState)
                }
                onProgress.onProgress(PdfConversionProgress(completedPages, totalPages))
            }

            saveWorkspaceState(workspaceDir, workspaceState)

            if (!workspaceState.qualifiesForGeneratedEpub()) {
                return@withContext workspaceState.toResult(
                    succeeded = false,
                    completedPages = completedPages,
                )
            }

            writeGeneratedEpub(
                outputFile = outputFile,
                workspaceDir = workspaceDir,
                title = title,
                author = author,
                totalPages = totalPages,
            )

            workspaceState.toResult(
                succeeded = true,
                completedPages = completedPages,
            )
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }
            AppLog.w(AppLog.PARSER, error) { "Failed to convert ${pdfFile.name} into EPUB" }
            PdfConversionResult(
                succeeded = false,
                completedPages = loadWorkspaceState(workspaceDir).completedPages(workspaceDir),
                totalPages = loadWorkspaceState(workspaceDir).totalPages,
                directTextPages = 0,
                ocrPages = 0,
            )
        } finally {
            renderer?.close()
            rendererDescriptor?.close()
            pdfDocument?.close()
            recognizer.close()
        }
    }
}

private enum class PdfTextExtractionMethod {
    DIRECT_TEXT,
    OCR,
    EMPTY,
}

private data class WorkspacePageStat(
    val pageNumber: Int,
    val characterCount: Int,
    val extractionMethod: PdfTextExtractionMethod,
)

private class PdfConversionWorkspaceState(
    var totalPages: Int = 0,
    private val pageStats: MutableMap<Int, WorkspacePageStat> = linkedMapOf(),
) {
    fun reset(totalPages: Int) {
        this.totalPages = totalPages
        pageStats.clear()
    }

    fun hasPage(pageNumber: Int): Boolean = pageStats.containsKey(pageNumber)

    fun record(
        pageNumber: Int,
        characterCount: Int,
        extractionMethod: PdfTextExtractionMethod,
    ) {
        pageStats[pageNumber] = WorkspacePageStat(
            pageNumber = pageNumber,
            characterCount = characterCount,
            extractionMethod = extractionMethod,
        )
    }

    fun completedPages(workspaceDir: File): Int {
        return pageStats.values.count { stat -> workspacePageFile(workspaceDir, stat.pageNumber).exists() }
    }

    fun qualifiesForGeneratedEpub(): Boolean {
        if (totalPages <= 0 || pageStats.isEmpty()) {
            return false
        }

        val nonEmptyPages = pageStats.values.count { it.characterCount >= PDF_PAGE_MIN_SIGNAL_CHARACTERS }
        val totalCharacters = pageStats.values.sumOf { it.characterCount }
        val requiredPages = when {
            totalPages <= 2 -> 1
            else -> max(1, totalPages / 3)
        }
        val requiredCharacters = max(60, totalPages * 20)
        return nonEmptyPages >= requiredPages && totalCharacters >= requiredCharacters
    }

    fun toJson(): JSONObject {
        val statsArray = JSONArray()
        pageStats.values
            .sortedBy { it.pageNumber }
            .forEach { stat ->
                statsArray.put(
                    JSONObject().apply {
                        put("pageNumber", stat.pageNumber)
                        put("characterCount", stat.characterCount)
                        put("extractionMethod", stat.extractionMethod.name)
                    },
                )
            }
        return JSONObject().apply {
            put("totalPages", totalPages)
            put("pageStats", statsArray)
        }
    }

    fun toResult(
        succeeded: Boolean,
        completedPages: Int = pageStats.size,
    ): PdfConversionResult {
        return PdfConversionResult(
            succeeded = succeeded,
            completedPages = completedPages,
            totalPages = totalPages,
            directTextPages = pageStats.values.count { it.extractionMethod == PdfTextExtractionMethod.DIRECT_TEXT },
            ocrPages = pageStats.values.count { it.extractionMethod == PdfTextExtractionMethod.OCR },
        )
    }
}

private suspend fun extractDirectTextParagraphs(
    document: PDDocument?,
    stripper: PDFTextStripper?,
    pageNumber: Int,
): List<String> {
    if (document == null || stripper == null) {
        return emptyList()
    }

    return runCatching {
        stripper.startPage = pageNumber
        stripper.endPage = pageNumber
        stripper.getText(document)
            .normalizePdfParagraphs()
    }.getOrElse {
        emptyList()
    }
}

private suspend fun recognizePageParagraphs(
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    renderer: PdfRenderer,
    pageIndex: Int,
): List<String> {
    return renderer.openPage(pageIndex).use { page ->
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
            recognizeBitmapParagraphs(recognizer, bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}

private suspend fun recognizeBitmapParagraphs(
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    bitmap: Bitmap,
): List<String> {
    val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                if (continuation.isActive) {
                    continuation.resume(text)
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

private fun writeWorkspaceStylesheet(workspaceDir: File) {
    val stylesDir = File(workspaceDir, "styles").apply { mkdirs() }
    File(stylesDir, "book.css").writeText(
        """
        body { margin: 0; padding: 0; }
        section.page { margin: 0; }
        h1.page-title { margin: 0 0 1rem; font-size: 1.15rem; text-align: center; }
        p { margin: 0 0 1rem; text-indent: 0; }
        """.trimIndent(),
    )
}

private fun clearWorkspacePages(workspaceDir: File) {
    File(workspaceDir, "pages").deleteRecursively()
    File(workspaceDir, WORKSPACE_STATE_FILE_NAME).delete()
}

private fun writeWorkspacePage(
    workspaceDir: File,
    pageNumber: Int,
    bookTitle: String,
    paragraphs: List<String>,
) {
    val pagesDir = File(workspaceDir, "pages").apply { mkdirs() }
    workspacePageFile(workspaceDir, pageNumber).writeText(
        buildPageDocument(
            pageNumber = pageNumber,
            bookTitle = bookTitle,
            paragraphs = paragraphs,
        ),
    )
}

private fun workspacePageFile(workspaceDir: File, pageNumber: Int): File {
    return File(workspaceDir, "pages/${pageFileName(pageNumber)}")
}

private fun buildPageDocument(
    pageNumber: Int,
    bookTitle: String,
    paragraphs: List<String>,
): String {
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
            <title>${"$bookTitle - Page $pageNumber".escapeForXhtml()}</title>
            <link rel="stylesheet" type="text/css" href="../styles/book.css"/>
          </head>
          <body>
            <section class="page">
              <h1 class="page-title">Page $pageNumber</h1>
              $body
            </section>
          </body>
        </html>
    """.trimIndent().trimStart()
}

private fun loadWorkspaceState(workspaceDir: File): PdfConversionWorkspaceState {
    val stateFile = File(workspaceDir, WORKSPACE_STATE_FILE_NAME)
    if (!stateFile.exists()) {
        return PdfConversionWorkspaceState()
    }

    return runCatching {
        val json = JSONObject(stateFile.readText())
        val state = PdfConversionWorkspaceState(totalPages = json.optInt("totalPages", 0))
        val stats = json.optJSONArray("pageStats") ?: JSONArray()
        for (index in 0 until stats.length()) {
            val entry = stats.optJSONObject(index) ?: continue
            val pageNumber = entry.optInt("pageNumber", 0)
            if (pageNumber <= 0) {
                continue
            }
            state.record(
                pageNumber = pageNumber,
                characterCount = entry.optInt("characterCount", 0),
                extractionMethod = runCatching {
                    PdfTextExtractionMethod.valueOf(
                        entry.optString("extractionMethod", PdfTextExtractionMethod.EMPTY.name),
                    )
                }.getOrDefault(PdfTextExtractionMethod.EMPTY),
            )
        }
        state
    }.getOrElse {
        PdfConversionWorkspaceState()
    }
}

private fun saveWorkspaceState(
    workspaceDir: File,
    state: PdfConversionWorkspaceState,
) {
    val stagedState = File(workspaceDir, "$WORKSPACE_STATE_FILE_NAME.tmp")
    val targetState = File(workspaceDir, WORKSPACE_STATE_FILE_NAME)
    stagedState.writeText(state.toJson().toString())
    replaceFileAtomically(stagedState, targetState)
}

private fun writeGeneratedEpub(
    outputFile: File,
    workspaceDir: File,
    title: String,
    author: String,
    totalPages: Int,
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
        addFileEntry(
            zip,
            "OEBPS/styles/book.css",
            File(workspaceDir, "styles/book.css"),
        )
        for (pageNumber in 1..totalPages) {
            addFileEntry(
                zip,
                "OEBPS/pages/${pageFileName(pageNumber)}",
                workspacePageFile(workspaceDir, pageNumber),
            )
        }
        addEntry(zip, "OEBPS/content.opf", buildOpfDocument(title, author, totalPages))
        addEntry(zip, "OEBPS/toc.ncx", buildNcxDocument(title, totalPages))
    }
}

private fun buildOpfDocument(
    title: String,
    author: String,
    totalPages: Int,
): String {
    val manifest = buildString {
        appendLine("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        appendLine("""    <item id="css" href="styles/book.css" media-type="text/css"/>""")
        for (pageNumber in 1..totalPages) {
            appendLine("""    <item id="page-$pageNumber" href="pages/${pageFileName(pageNumber)}" media-type="application/xhtml+xml"/>""")
        }
    }.trimEnd()

    val spine = buildString {
        for (pageNumber in 1..totalPages) {
            appendLine("""    <itemref idref="page-$pageNumber"/>""")
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

private fun buildNcxDocument(title: String, totalPages: Int): String {
    val navPoints = buildString {
        for (pageNumber in 1..totalPages) {
            appendLine(
                """
                <navPoint id="page-$pageNumber" playOrder="$pageNumber">
                  <navLabel><text>Page $pageNumber</text></navLabel>
                  <content src="pages/${pageFileName(pageNumber)}"/>
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

private fun addFileEntry(
    zip: ZipOutputStream,
    name: String,
    sourceFile: File,
) {
    zip.putNextEntry(ZipEntry(name))
    sourceFile.inputStream().use { input ->
        input.copyTo(zip)
    }
    zip.closeEntry()
}

private fun readPageCountWithRenderer(pdfFile: File): Int {
    return runCatching {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.pageCount
            }
        }
    }.getOrDefault(0)
}

private fun List<String>.hasUsableText(): Boolean = characterCount() >= PDF_PAGE_MIN_SIGNAL_CHARACTERS

private fun List<String>.characterCount(): Int = sumOf { paragraph ->
    paragraph.count { !it.isWhitespace() }
}

private fun String.normalizePdfParagraphs(): List<String> {
    return split('\n', '\r')
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .fold(mutableListOf<String>()) { acc, line ->
            if (acc.isEmpty()) {
                acc += line
            } else if (acc.last().length < PDF_PARAGRAPH_JOIN_THRESHOLD) {
                acc[acc.lastIndex] = "${acc.last()} $line".trim()
            } else {
                acc += line
            }
            acc
        }
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

private fun pageFileName(pageNumber: Int): String = "page-${pageNumber.toString().padStart(4, '0')}.xhtml"

private const val PDF_CONVERSION_RENDER_WIDTH_PX = 1080
private const val PDF_PAGE_MIN_SIGNAL_CHARACTERS = 40
private const val PDF_PARAGRAPH_JOIN_THRESHOLD = 100
private const val PDF_CONVERSION_PROGRESS_CHUNK_SIZE = 8
private const val WORKSPACE_STATE_FILE_NAME = "workspace_state.json"
