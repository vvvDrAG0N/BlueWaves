/**
 * AI_READ_AFTER: EpubParser.kt
 * AI_RELEVANT_TO: [Book Import, Metadata Cache, TOC Reconstruction, Book ID Generation]
 * PURPOSE: Package-local helpers for EPUB book rebuild and metadata persistence.
 * AI_WARNING: Preserve book ID generation and metadata.json shape.
 */
package com.epubreader.data.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.TOCReference
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.max

internal const val EPUB_BOOKS_DIR_NAME = "books"
internal const val EPUB_ARCHIVE_FILE_NAME = "book.epub"
internal const val GENERATED_EPUB_FILE_NAME = "generated.epub"
internal const val PDF_DOCUMENT_FILE_NAME = "source.pdf"
internal const val LEGACY_PDF_DOCUMENT_FILE_NAME = "book.pdf"

private const val EPUB_METADATA_FILE_NAME = "metadata.json"
private const val EPUB_COVER_FILE_NAME = "cover_thumb.png"
private const val PDF_DEFAULT_AUTHOR = "PDF Document"
private const val PDF_FALLBACK_TITLE = "Untitled PDF"
private const val PDF_COVER_WIDTH_PX = 320

internal fun buildBookId(uri: Uri, fileSize: Long): String {
    // Book ID must remain MD5(uri + fileSize) to preserve cached folders and reading progress keys.
    val rawId = "$uri$fileSize"
    return MessageDigest.getInstance("MD5")
        .digest(rawId.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun rebuildBookMetadata(bookFolder: File): EpubBook? {
    val existingMetadata = loadBookMetadata(bookFolder)
    val sourceFormat = existingMetadata?.sourceFormat ?: BookFormat.EPUB
    val bookFile = activeEpubFile(bookFolder, sourceFormat)
    if (!bookFile.exists()) return null

    return runCatching {
        // [SELF-HEALING] Always re-read the binary to ensure metadata is perfectly synced
        // with the actual file content, even if metadata.json was corrupted or outdated.
        val book: Book = bookFile.inputStream().use { input ->
            nl.siegmann.epublib.epub.EpubReader().readEpub(input)
        }

        val coverPath = book.coverImage?.let { coverResource ->
            val coverFile = File(bookFolder, EPUB_COVER_FILE_NAME)
            coverResource.inputStream.use { input ->
                FileOutputStream(coverFile).use { output ->
                    input.copyTo(output)
                }
            }
            coverFile.absolutePath
        } ?: existingMetadata?.coverPath

        val spineHrefs = book.spine.spineReferences.map { it.resource.href }
        val toc = buildTableOfContents(book, spineHrefs)
        val conversionTotalPages = when (sourceFormat) {
            BookFormat.PDF -> {
                existingMetadata?.conversionTotalPages
                    ?.takeIf { it > 0 }
                    ?: existingMetadata?.pageCount
                    ?: spineHrefs.size
            }

            BookFormat.EPUB -> 0
        }
        val conversionCompletedPages = when (sourceFormat) {
            BookFormat.PDF -> conversionTotalPages
            BookFormat.EPUB -> 0
        }

        val epubBook = EpubBook(
            id = bookFolder.name,
            // [SELF-HEALING] Fallbacks for missing core metadata to prevent UI crashes.
            title = book.title ?: existingMetadata?.title ?: "Unknown Title",
            author = book.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}" }
                ?: existingMetadata?.author
                ?: "Unknown Author",
            coverPath = coverPath,
            rootPath = bookFolder.absolutePath,
            format = BookFormat.EPUB,
            sourceFormat = sourceFormat,
            conversionStatus = when (sourceFormat) {
                BookFormat.PDF -> ConversionStatus.READY
                BookFormat.EPUB -> ConversionStatus.NONE
            },
            hasPdfFallback = sourceFormat == BookFormat.PDF && ensureCanonicalSourcePdfFile(bookFolder) != null,
            conversionCompletedPages = conversionCompletedPages,
            conversionTotalPages = conversionTotalPages,
            toc = toc,
            spineHrefs = spineHrefs,
            pageCount = if (sourceFormat == BookFormat.PDF) existingMetadata?.pageCount ?: spineHrefs.size else 0,
            dateAdded = existingMetadata?.dateAdded ?: System.currentTimeMillis(),
            lastRead = existingMetadata?.lastRead ?: 0L,
        )

        saveBookMetadata(bookFolder, epubBook)
        epubBook
    }.getOrNull()
}

internal fun rebuildPdfMetadata(
    bookFolder: File,
    displayName: String?,
    conversionStatus: ConversionStatus = ConversionStatus.NONE,
    preferredFormat: BookFormat = BookFormat.PDF,
    conversionCompletedPages: Int? = null,
    conversionTotalPages: Int? = null,
): EpubBook? {
    val bookFile = ensureCanonicalSourcePdfFile(bookFolder) ?: return null
    val existingMetadata = loadBookMetadata(bookFolder)

    File(bookFolder, EPUB_COVER_FILE_NAME).takeIf(File::exists)?.delete()
    val pdfInfo = readPdfDocumentInfo(bookFile, bookFolder) ?: PdfDocumentInfo(
        pageCount = existingMetadata?.pageCount ?: 0,
        coverPath = existingMetadata?.coverPath,
    )
    val title = when {
        !displayName.isNullOrBlank() -> deriveTitleFromName(displayName, existingMetadata?.title ?: PDF_FALLBACK_TITLE)
        !existingMetadata?.title.isNullOrBlank() -> existingMetadata!!.title
        else -> deriveTitleFromName(bookFile.name, PDF_FALLBACK_TITLE)
    }
    val totalPages = conversionTotalPages
        ?: existingMetadata?.conversionTotalPages
            ?.takeIf { it > 0 }
        ?: pdfInfo.pageCount
    val completedPages = (conversionCompletedPages
        ?: existingMetadata?.conversionCompletedPages
        ?: if (conversionStatus == ConversionStatus.READY) totalPages else 0)
        .coerceIn(0, totalPages)

    val pdfBook = EpubBook(
        id = bookFolder.name,
        title = title,
        author = existingMetadata?.author?.takeIf { it.isNotBlank() } ?: PDF_DEFAULT_AUTHOR,
        coverPath = pdfInfo.coverPath,
        rootPath = bookFolder.absolutePath,
        format = preferredFormat,
        sourceFormat = BookFormat.PDF,
        conversionStatus = conversionStatus,
        hasPdfFallback = true,
        conversionCompletedPages = completedPages,
        conversionTotalPages = totalPages,
        toc = emptyList(),
        spineHrefs = emptyList(),
        pageCount = pdfInfo.pageCount,
        dateAdded = existingMetadata?.dateAdded ?: System.currentTimeMillis(),
        lastRead = existingMetadata?.lastRead ?: 0L,
    )

    saveBookMetadata(bookFolder, pdfBook)
    return pdfBook
}

internal fun saveBookMetadata(folder: File, book: EpubBook) {
    val json = JSONObject().apply {
        put("id", book.id)
        put("title", book.title)
        put("author", book.author)
        put("coverPath", book.coverPath)
        put("rootPath", book.rootPath)
        put("format", book.format.name)
        put("sourceFormat", book.sourceFormat.name)
        put("conversionStatus", book.conversionStatus.name)
        put("hasPdfFallback", book.hasPdfFallback)
        put("conversionCompletedPages", book.conversionCompletedPages)
        put("conversionTotalPages", book.conversionTotalPages)
        put("dateAdded", book.dateAdded)
        put("lastRead", book.lastRead)
        val tocArray = JSONArray()
        book.toc.forEach { item ->
            tocArray.put(JSONObject().apply {
                put("title", item.title)
                put("href", item.href)
            })
        }
        put("toc", tocArray)
        val spineArray = JSONArray()
        book.spineHrefs.forEach { spineArray.put(it) }
        put("spineHrefs", spineArray)
        put("pageCount", book.pageCount)
    }
    val metadataFile = File(folder, EPUB_METADATA_FILE_NAME)
    val stagedMetadataFile = File(folder, "$EPUB_METADATA_FILE_NAME.tmp")
    try {
        stagedMetadataFile.writeText(json.toString())
        replaceFileAtomically(stagedMetadataFile, metadataFile)
    } finally {
        if (stagedMetadataFile.exists()) {
            stagedMetadataFile.delete()
        }
    }
}

internal fun loadBookMetadata(folder: File): EpubBook? {
    val file = File(folder, EPUB_METADATA_FILE_NAME)
    if (!file.exists()) return null
    return try {
        val json = JSONObject(file.readText())
        val tocArray = json.getJSONArray("toc")
        val toc = mutableListOf<TocItem>()
        for (i in 0 until tocArray.length()) {
            val item = tocArray.getJSONObject(i)
            toc.add(TocItem(item.getString("title"), item.getString("href")))
        }

        val spineArray = json.optJSONArray("spineHrefs")
        val spineHrefs = mutableListOf<String>()
        if (spineArray != null) {
            for (i in 0 until spineArray.length()) {
                spineHrefs.add(spineArray.getString(i))
            }
        }

        val format = runCatching {
            BookFormat.valueOf(json.optString("format", BookFormat.EPUB.name))
        }.getOrDefault(BookFormat.EPUB)

        val sourceFormat = runCatching {
            BookFormat.valueOf(json.optString("sourceFormat", format.name))
        }.getOrDefault(format)

        EpubBook(
            id = json.getString("id"),
            title = json.getString("title"),
            author = json.getString("author"),
            // [SELF-HEALING] Resilience against null/empty cover paths.
            coverPath = json.optString("coverPath").takeIf { it.isNotEmpty() && it != "null" },
            rootPath = json.getString("rootPath"),
            format = format,
            sourceFormat = sourceFormat,
            conversionStatus = runCatching {
                ConversionStatus.valueOf(json.optString("conversionStatus", ConversionStatus.NONE.name))
            }.getOrDefault(ConversionStatus.NONE),
            hasPdfFallback = json.optBoolean("hasPdfFallback", sourceFormat == BookFormat.PDF),
            conversionCompletedPages = json.optInt("conversionCompletedPages", 0),
            conversionTotalPages = json.optInt("conversionTotalPages", json.optInt("pageCount", 0)),
            toc = toc,
            spineHrefs = spineHrefs,
            pageCount = json.optInt("pageCount", 0),
            // [SELF-HEALING] Preserve sort order even if dateAdded is missing from JSON.
            dateAdded = json.optLong("dateAdded", folder.lastModified()),
            lastRead = json.optLong("lastRead", 0L)
        )
    } catch (e: Exception) {
        // [SELF-HEALING] If JSON is corrupt, return null so scanBooks can ignore it or 
        // the caller can trigger a re-import/re-parse.
        null
    }
}

private fun buildTableOfContents(book: Book, spineHrefs: List<String>): List<TocItem> {
    val toc = mutableListOf<TocItem>()
    addTocReferences(book.tableOfContents.tocReferences, toc)

    // [SELF-HEALING] If the EPUB lacks a proper Table of Contents, reconstruct one 
    // from the spine filenames to ensure navigation is always possible.
    if (toc.isNotEmpty()) return toc

    spineHrefs.forEachIndexed { i, href ->
        val title = href.substringAfterLast("/").substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .replaceFirstChar { it.uppercase() }
        toc.add(TocItem("${i + 1}. $title", href))
    }

    return toc
}

private fun addTocReferences(
    references: List<TOCReference>,
    toc: MutableList<TocItem>,
    prefix: String = ""
) {
    references.forEachIndexed { i, ref ->
        val currentNumber = if (prefix.isEmpty()) "${i + 1}" else "$prefix.${i + 1}"
        // [SELF-HEALING] Multi-step title fallback for malformed TOC entries.
        val rawTitle = ref.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: ref.resource?.title?.trim()?.takeIf { it.isNotEmpty() }
            ?: ref.resource?.href?.substringAfterLast("/")?.substringBeforeLast(".")?.replace("_", " ")?.replace("-", " ")
            ?: "Chapter"

        val displayTitle = "$currentNumber. $rawTitle"
        toc.add(TocItem(title = displayTitle, href = ref.completeHref ?: ref.resource.href))
        if (ref.children.isNotEmpty()) {
            addTocReferences(ref.children, toc, currentNumber)
        }
    }
}

internal fun activeEpubFile(bookFolder: File, sourceFormat: BookFormat): File {
    return when (sourceFormat) {
        BookFormat.EPUB -> File(bookFolder, EPUB_ARCHIVE_FILE_NAME)
        BookFormat.PDF -> File(bookFolder, GENERATED_EPUB_FILE_NAME)
    }
}

internal fun ensureCanonicalSourcePdfFile(bookFolder: File): File? {
    val sourceFile = File(bookFolder, PDF_DOCUMENT_FILE_NAME)
    if (sourceFile.exists()) {
        return sourceFile
    }

    val legacyFile = File(bookFolder, LEGACY_PDF_DOCUMENT_FILE_NAME)
    if (!legacyFile.exists()) {
        return null
    }

    return runCatching {
        replaceFileAtomically(legacyFile, sourceFile)
        sourceFile
    }.getOrElse {
        legacyFile
    }
}

internal fun replaceFileAtomically(source: File, target: File) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

private data class PdfDocumentInfo(
    val pageCount: Int,
    val coverPath: String?,
)

private fun readPdfDocumentInfo(bookFile: File, bookFolder: File): PdfDocumentInfo? {
    return runCatching {
        ParcelFileDescriptor.open(bookFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount <= 0) {
                    null
                } else {
                    PdfDocumentInfo(
                        pageCount = renderer.pageCount,
                        coverPath = renderPdfCoverThumbnail(renderer, bookFolder),
                    )
                }
            }
        }
    }.getOrNull()
}

private fun renderPdfCoverThumbnail(renderer: PdfRenderer, bookFolder: File): String? {
    if (renderer.pageCount <= 0) {
        return null
    }

    return runCatching {
        renderer.openPage(0).use { page ->
            val targetWidth = PDF_COVER_WIDTH_PX
            val targetHeight = max(
                1,
                (targetWidth.toFloat() * page.height / page.width).toInt(),
            )
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                val matrix = Matrix().apply {
                    setScale(
                        targetWidth.toFloat() / page.width.toFloat(),
                        targetHeight.toFloat() / page.height.toFloat(),
                    )
                }
                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val coverFile = File(bookFolder, EPUB_COVER_FILE_NAME)
                FileOutputStream(coverFile).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                coverFile.absolutePath
            } finally {
                bitmap.recycle()
            }
        }
    }.getOrNull()
}
