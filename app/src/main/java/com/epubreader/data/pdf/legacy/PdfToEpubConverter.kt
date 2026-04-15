/**
 * Deprecated PDF conversion internals retained for the upcoming safe refactor.
 * The active app shell no longer imports or opens PDF-origin books.
 */
package com.epubreader.data.pdf.legacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.epubreader.core.debug.AppLog
import com.epubreader.data.parser.EPUB_MIME_TYPE
import com.epubreader.data.parser.PdfConversionProgress
import com.epubreader.data.parser.PdfConversionProgressListener
import com.epubreader.data.parser.PdfConversionResult
import com.epubreader.data.parser.replaceFileAtomically
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

internal data class PdfWorkspacePage(
    val pageNumber: Int,
    val paragraphs: List<String>,
) {
    fun toJson(): JSONObject {
        val paragraphArray = JSONArray()
        paragraphs.forEach(paragraphArray::put)
        return JSONObject().apply {
            put("pageNumber", pageNumber)
            put("paragraphs", paragraphArray)
        }
    }
}

internal data class PdfReflowSection(
    val index: Int,
    val startPage: Int,
    val endPage: Int,
    val pages: List<PdfWorkspacePage>,
) {
    val title: String
        get() = if (startPage == endPage) {
            "Page $startPage"
        } else {
            "Pages $startPage-$endPage"
        }

    val fileName: String
        get() = sectionFileName(index)

    val href: String
        get() = "sections/$fileName"

    val anchorId: String
        get() = pageAnchorId(startPage)
}

private class PdfConversionWorkspaceState(
    var totalPages: Int = 0,
    val schemaVersion: Int = PDF_CONVERSION_WORKSPACE_SCHEMA_VERSION,
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
            put("schemaVersion", schemaVersion)
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
        body {
            margin: 0 auto;
            padding: 1.4rem 1.25rem 2rem;
            max-width: 42rem;
            line-height: 1.62;
        }
        section.reflow-section { margin: 0; }
        h1.section-title {
            margin: 0 0 1.25rem;
            font-size: 1rem;
            font-weight: 600;
            letter-spacing: 0.03em;
            text-transform: uppercase;
        }
        .page-marker {
            margin: 1.5rem 0 0.75rem;
            padding-top: 0.65rem;
            border-top: 1px solid rgba(0, 0, 0, 0.12);
            font-size: 0.82rem;
        }
        .page-marker span {
            display: inline-block;
            padding: 0.1rem 0.45rem;
            border-radius: 999px;
            background: rgba(0, 0, 0, 0.06);
        }
        p {
            margin: 0 0 0.95rem;
            text-indent: 0;
        }
        p.page-note {
            font-style: italic;
            opacity: 0.72;
        }
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
    paragraphs: List<String>,
) {
    val pagesDir = File(workspaceDir, "pages").apply { mkdirs() }
    val targetFile = workspacePageFile(workspaceDir, pageNumber)
    val stagedFile = File(pagesDir, "${targetFile.name}.tmp")
    stagedFile.writeText(
        PdfWorkspacePage(
            pageNumber = pageNumber,
            paragraphs = paragraphs,
        ).toJson().toString(),
    )
    replaceFileAtomically(stagedFile, targetFile)
}

private fun workspacePageFile(workspaceDir: File, pageNumber: Int): File {
    return File(workspaceDir, "pages/${workspacePageFileName(pageNumber)}")
}

private fun loadWorkspacePage(
    workspaceDir: File,
    pageNumber: Int,
): PdfWorkspacePage {
    val pageFile = workspacePageFile(workspaceDir, pageNumber)
    if (!pageFile.exists()) {
        return PdfWorkspacePage(pageNumber = pageNumber, paragraphs = emptyList())
    }

    return runCatching {
        val json = JSONObject(pageFile.readText())
        val paragraphArray = json.optJSONArray("paragraphs") ?: JSONArray()
        val paragraphs = buildList {
            for (index in 0 until paragraphArray.length()) {
                add(paragraphArray.optString(index))
            }
        }
        PdfWorkspacePage(
            pageNumber = json.optInt("pageNumber", pageNumber).takeIf { it > 0 } ?: pageNumber,
            paragraphs = paragraphs.filter(String::isNotBlank),
        )
    }.getOrElse {
        PdfWorkspacePage(pageNumber = pageNumber, paragraphs = emptyList())
    }
}

internal fun buildPdfReflowSections(
    workspaceDir: File,
    totalPages: Int,
): List<PdfReflowSection> {
    if (totalPages <= 0) {
        return emptyList()
    }

    val sections = mutableListOf<PdfReflowSection>()
    var currentPages = mutableListOf<PdfWorkspacePage>()
    var currentCharacterCount = 0

    fun flushSection() {
        if (currentPages.isEmpty()) {
            return
        }
        sections += PdfReflowSection(
            index = sections.size + 1,
            startPage = currentPages.first().pageNumber,
            endPage = currentPages.last().pageNumber,
            pages = currentPages.toList(),
        )
        currentPages = mutableListOf()
        currentCharacterCount = 0
    }

    for (pageNumber in 1..totalPages) {
        val page = loadWorkspacePage(workspaceDir, pageNumber)
        val pageCharacterCount = page.paragraphs.characterCount()
        val reachedPageLimit = currentPages.size >= PDF_REFLOW_SECTION_MAX_PAGES
        val reachedCharacterLimit = currentPages.isNotEmpty() &&
            currentCharacterCount + pageCharacterCount > PDF_REFLOW_SECTION_MAX_CHARACTERS
        if (reachedPageLimit || reachedCharacterLimit) {
            flushSection()
        }
        currentPages += page
        currentCharacterCount += pageCharacterCount
    }
    flushSection()

    return sections
}

private fun loadWorkspaceState(workspaceDir: File): PdfConversionWorkspaceState {
    val stateFile = File(workspaceDir, WORKSPACE_STATE_FILE_NAME)
    if (!stateFile.exists()) {
        return PdfConversionWorkspaceState()
    }

    return runCatching {
        val json = JSONObject(stateFile.readText())
        val schemaVersion = json.optInt("schemaVersion", 0)
        if (schemaVersion != PDF_CONVERSION_WORKSPACE_SCHEMA_VERSION) {
            return@runCatching PdfConversionWorkspaceState(totalPages = -1)
        }
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

internal fun writeGeneratedEpub(
    outputFile: File,
    workspaceDir: File,
    title: String,
    author: String,
    totalPages: Int,
) {
    val sections = buildPdfReflowSections(workspaceDir, totalPages)
    require(sections.isNotEmpty()) { "No reflow sections were generated for this PDF." }

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
        for (section in sections) {
            addEntry(
                zip,
                "OEBPS/${section.href}",
                buildSectionDocument(
                    section = section,
                    bookTitle = title,
                ),
            )
        }
        addEntry(zip, "OEBPS/content.opf", buildOpfDocument(title, author, sections))
        addEntry(zip, "OEBPS/toc.ncx", buildNcxDocument(title, sections))
    }
}

internal fun buildOpfDocument(
    title: String,
    author: String,
    sections: List<PdfReflowSection>,
): String {
    val manifest = buildString {
        appendLine("""    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        appendLine("""    <item id="css" href="styles/book.css" media-type="text/css"/>""")
        for (section in sections) {
            appendLine(
                """    <item id="section-${section.index}" href="${section.href}" media-type="application/xhtml+xml"/>""",
            )
        }
    }.trimEnd()

    val spine = buildString {
        for (section in sections) {
            appendLine("""    <itemref idref="section-${section.index}"/>""")
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

internal fun buildNcxDocument(title: String, sections: List<PdfReflowSection>): String {
    val navPoints = buildString {
        for (section in sections) {
            appendLine(
                """
                <navPoint id="section-${section.index}" playOrder="${section.index}">
                  <navLabel><text>${section.title.escapeForXhtml()}</text></navLabel>
                  <content src="${section.href}#${section.anchorId}"/>
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

internal fun buildSectionDocument(
    section: PdfReflowSection,
    bookTitle: String,
): String {
    val body = section.pages.joinToString(separator = "\n") { page ->
        val pageBody = if (page.paragraphs.isEmpty()) {
            """<p class="page-note">This page did not yield extractable reflow text.</p>"""
        } else {
            page.paragraphs.joinToString(separator = "\n") { paragraph ->
                "<p>${paragraph.escapeForXhtml().replace("\n", "<br />")}</p>"
            }
        }
        """
        <div class="page-marker" id="${pageAnchorId(page.pageNumber)}"><span>Page ${page.pageNumber}</span></div>
        $pageBody
        """.trimIndent()
    }

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head>
            <title>${"$bookTitle - ${section.title}".escapeForXhtml()}</title>
            <link rel="stylesheet" type="text/css" href="../styles/book.css"/>
          </head>
          <body>
            <section class="reflow-section">
              <h1 class="section-title">${section.title.escapeForXhtml()}</h1>
              $body
            </section>
          </body>
        </html>
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

private fun workspacePageFileName(pageNumber: Int): String = "page-${pageNumber.toString().padStart(4, '0')}.json"

private fun sectionFileName(sectionIndex: Int): String = "section-${sectionIndex.toString().padStart(4, '0')}.xhtml"

private fun pageAnchorId(pageNumber: Int): String = "page-${pageNumber.toString().padStart(4, '0')}"

private const val PDF_CONVERSION_RENDER_WIDTH_PX = 1080
private const val PDF_PAGE_MIN_SIGNAL_CHARACTERS = 40
private const val PDF_PARAGRAPH_JOIN_THRESHOLD = 100
private const val PDF_CONVERSION_PROGRESS_CHUNK_SIZE = 8
private const val PDF_REFLOW_SECTION_MAX_PAGES = 12
private const val PDF_REFLOW_SECTION_MAX_CHARACTERS = 15_000
private const val PDF_CONVERSION_WORKSPACE_SCHEMA_VERSION = 2
private const val WORKSPACE_STATE_FILE_NAME = "workspace_state.json"
