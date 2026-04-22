package com.epubreader.data.pdf.legacy

import com.epubreader.data.parser.PdfConversionResult
import com.epubreader.data.parser.replaceFileAtomically
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max

internal data class WorkspacePageStat(
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

internal class PdfConversionWorkspaceState(
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

internal fun writeWorkspaceStylesheet(workspaceDir: File) {
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

internal fun clearWorkspacePages(workspaceDir: File) {
    File(workspaceDir, "pages").deleteRecursively()
    File(workspaceDir, WORKSPACE_STATE_FILE_NAME).delete()
}

internal fun writeWorkspacePage(
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

internal fun workspacePageFile(workspaceDir: File, pageNumber: Int): File {
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

internal fun loadWorkspaceState(workspaceDir: File): PdfConversionWorkspaceState {
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

internal fun saveWorkspaceState(
    workspaceDir: File,
    state: PdfConversionWorkspaceState,
) {
    val stagedState = File(workspaceDir, "$WORKSPACE_STATE_FILE_NAME.tmp")
    val targetState = File(workspaceDir, WORKSPACE_STATE_FILE_NAME)
    stagedState.writeText(state.toJson().toString())
    replaceFileAtomically(stagedState, targetState)
}

internal fun List<String>.hasUsableText(): Boolean = characterCount() >= PDF_PAGE_MIN_SIGNAL_CHARACTERS

internal fun List<String>.characterCount(): Int = sumOf { paragraph ->
    paragraph.count { !it.isWhitespace() }
}

internal fun String.normalizePdfParagraphs(): List<String> {
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

internal fun String.normalizePdfParagraph(): String? {
    return lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(separator = "\n")
        .takeIf(String::isNotBlank)
}

private fun workspacePageFileName(pageNumber: Int): String = "page-${pageNumber.toString().padStart(4, '0')}.json"

private fun sectionFileName(sectionIndex: Int): String = "section-${sectionIndex.toString().padStart(4, '0')}.xhtml"

private fun pageAnchorId(pageNumber: Int): String = "page-${pageNumber.toString().padStart(4, '0')}"

internal const val PDF_CONVERSION_RENDER_WIDTH_PX = 1080
private const val PDF_PAGE_MIN_SIGNAL_CHARACTERS = 40
private const val PDF_PARAGRAPH_JOIN_THRESHOLD = 100
internal const val PDF_CONVERSION_PROGRESS_CHUNK_SIZE = 8
private const val PDF_REFLOW_SECTION_MAX_PAGES = 12
private const val PDF_REFLOW_SECTION_MAX_CHARACTERS = 15_000
private const val PDF_CONVERSION_WORKSPACE_SCHEMA_VERSION = 2
private const val WORKSPACE_STATE_FILE_NAME = "workspace_state.json"
