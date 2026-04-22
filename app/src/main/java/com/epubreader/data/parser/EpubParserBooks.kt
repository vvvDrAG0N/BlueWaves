/**
 * FILE: EpubParserBooks.kt (Updated)
 * PURPOSE: Refactored book metadata rebuild and persistence without epublib.
 * DESIGN: Uses InternalEpubParser for O(1) metadata and TOC extraction.
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.math.max

internal const val EPUB_BOOKS_DIR_NAME = "books"
internal const val EPUB_ARCHIVE_FILE_NAME = "book.epub"
internal const val GENERATED_EPUB_FILE_NAME = "generated.epub"
internal const val PDF_DOCUMENT_FILE_NAME = "source.pdf"
internal const val LEGACY_PDF_DOCUMENT_FILE_NAME = "book.pdf"

private const val EPUB_METADATA_FILE_NAME = "metadata.json"
private const val EPUB_COVER_FILE_NAME = "cover_thumb.png"
internal const val EPUB_ORIGINAL_COVER_FILE_NAME = "cover_original_thumb.png"
internal const val EPUB_CURRENT_COVER_FILE_NAME = "cover_current_thumb.png"
private const val PDF_DEFAULT_AUTHOR = "PDF Document"
private const val PDF_FALLBACK_TITLE = "Untitled PDF"
private const val PDF_COVER_WIDTH_PX = 320
private const val EDITED_BOOK_CUSTOM_COVER_PREFIX = "bluewaves/custom-cover."

internal fun buildBookId(uri: Uri, fileSize: Long): String {
    val rawId = "$uri$fileSize"
    return MessageDigest.getInstance("MD5")
        .digest(rawId.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun rebuildBookMetadata(
    bookFolder: File,
    existingMetadata: EpubBook? = loadBookMetadata(bookFolder),
): EpubBook? {
    val sourceFormat = existingMetadata?.sourceFormat ?: BookFormat.EPUB
    val bookFile = activeEpubFile(bookFolder, sourceFormat)
    if (!bookFile.exists()) return null

    return runCatching {
        // [REFAC] Completely removed epublib. Using high-performance InternalEpubParser.
        ZipFile(bookFile).use { zip ->
            val meta = InternalEpubParser.parseEpub(zip) ?: return null

            val coverFiles = reconcileStoredBookCoverFiles(
                bookFolder = bookFolder,
                zip = zip,
                coverHref = meta.coverHref,
                existingMetadata = existingMetadata,
            )

            val spineHrefs = meta.spine
            val toc = if (meta.toc.isNotEmpty()) meta.toc else buildFallbackToc(spineHrefs)

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
            val preferredPdfFormat = existingMetadata?.format?.takeIf {
                it == BookFormat.EPUB || it == BookFormat.PDF
            } ?: if (ensureCanonicalSourcePdfFile(bookFolder) != null) {
                BookFormat.PDF
            } else {
                BookFormat.EPUB
            }

            val epubBook = EpubBook(
                id = bookFolder.name,
                title = meta.title ?: existingMetadata?.title ?: "Unknown Title",
                author = meta.author ?: existingMetadata?.author ?: "Unknown Author",
                coverPath = coverFiles.coverPath,
                originalCoverPath = coverFiles.originalCoverPath,
                currentCoverPath = coverFiles.currentCoverPath,
                rootPath = bookFolder.absolutePath,
                format = when (sourceFormat) {
                    BookFormat.PDF -> preferredPdfFormat
                    BookFormat.EPUB -> BookFormat.EPUB
                },
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
        }
    }.getOrNull()
}

/**
 * [SELF-HEALING] Reconstructs TOC from spine filenames if navigation files are missing.
 */
private fun buildFallbackToc(spineHrefs: List<String>): List<TocItem> {
    return spineHrefs.mapIndexed { i, href ->
        val title = href.substringAfterLast("/").substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .replaceFirstChar { it.uppercase() }
        TocItem("${i + 1}. $title", href)
    }
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
    val reusableCoverPath = existingMetadata?.coverPath
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { File(it).exists() }
    val existingPageCount = existingMetadata?.pageCount ?: 0
    val canReuseExistingPdfInfo = existingPageCount > 0 && reusableCoverPath != null
    if (!canReuseExistingPdfInfo && reusableCoverPath == null) {
        File(bookFolder, EPUB_COVER_FILE_NAME).takeIf(File::exists)?.delete()
    }
    val pdfInfo = if (canReuseExistingPdfInfo) {
        PdfDocumentInfo(
            pageCount = existingPageCount,
            coverPath = reusableCoverPath,
        )
    } else {
        readPdfDocumentInfo(bookFile, bookFolder) ?: PdfDocumentInfo(
            pageCount = existingPageCount,
            coverPath = reusableCoverPath,
        )
    }
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
        originalCoverPath = pdfInfo.coverPath,
        currentCoverPath = pdfInfo.coverPath,
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
        put("coverPath", book.coverPath ?: JSONObject.NULL)
        put("originalCoverPath", book.originalCoverPath ?: JSONObject.NULL)
        put("currentCoverPath", book.currentCoverPath ?: JSONObject.NULL)
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

        val legacyCoverPath = json.optCoverPath("coverPath")
        val originalCoverPath = if (json.has("originalCoverPath")) {
            json.optCoverPath("originalCoverPath")
        } else {
            legacyCoverPath
        }
        val currentCoverPath = if (json.has("currentCoverPath")) {
            json.optCoverPath("currentCoverPath")
        } else {
            legacyCoverPath
        }
        val effectiveCoverPath = currentCoverPath ?: originalCoverPath ?: legacyCoverPath

        EpubBook(
            id = json.getString("id"),
            title = json.getString("title"),
            author = json.getString("author"),
            coverPath = effectiveCoverPath,
            originalCoverPath = originalCoverPath,
            currentCoverPath = currentCoverPath,
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
            dateAdded = json.optLong("dateAdded", folder.lastModified()),
            lastRead = json.optLong("lastRead", 0L)
        )
    } catch (e: Exception) {
        null
    }
}

private data class ResolvedBookCoverFiles(
    val coverPath: String?,
    val originalCoverPath: String?,
    val currentCoverPath: String?,
)

private fun reconcileStoredBookCoverFiles(
    bookFolder: File,
    zip: ZipFile,
    coverHref: String?,
    existingMetadata: EpubBook?,
): ResolvedBookCoverFiles {
    val effectiveCoverFile = File(bookFolder, EPUB_COVER_FILE_NAME)
    val originalCoverFile = File(bookFolder, EPUB_ORIGINAL_COVER_FILE_NAME)
    val currentCoverFile = File(bookFolder, EPUB_CURRENT_COVER_FILE_NAME)

    val previousOriginalFile = existingMetadata
        ?.originalCoverPath
        .toExistingCoverFile()
        ?: existingMetadata?.coverPath.toExistingCoverFile()
    val previousCurrentFile = existingMetadata
        ?.currentCoverPath
        .toExistingCoverFile()
    val shouldKeepCurrentCopy = when {
        existingMetadata == null -> true
        existingMetadata.currentCoverPath != null -> true
        else -> false
    }

    val coverEntry = coverHref?.let { zip.getEntry(it) }
    val coverBytes = coverEntry?.let { zip.getInputStream(it).use { input -> input.readBytes() } }
    val isCustomCover = coverHref?.let(::isEditedBookCustomCoverHref) == true

    var resolvedOriginalFile = when {
        coverBytes != null && !isCustomCover -> {
            writeBytesToFile(coverBytes, originalCoverFile)
            originalCoverFile
        }

        previousOriginalFile != null -> copyToCoverSlot(previousOriginalFile, originalCoverFile)
        previousCurrentFile != null -> copyToCoverSlot(previousCurrentFile, originalCoverFile)
        else -> {
            originalCoverFile.takeIf(File::exists)?.delete()
            null
        }
    }

    val resolvedCurrentFile = when {
        coverBytes != null && isCustomCover -> {
            writeBytesToFile(coverBytes, currentCoverFile)
            currentCoverFile
        }

        coverBytes != null && shouldKeepCurrentCopy -> {
            val source = resolvedOriginalFile ?: currentCoverFile.takeIf(File::exists)
            source?.let { copyToCoverSlot(it, currentCoverFile) }
        }

        previousCurrentFile != null && shouldKeepCurrentCopy -> copyToCoverSlot(previousCurrentFile, currentCoverFile)
        shouldKeepCurrentCopy && resolvedOriginalFile != null -> copyToCoverSlot(resolvedOriginalFile, currentCoverFile)
        else -> {
            currentCoverFile.takeIf(File::exists)?.delete()
            null
        }
    }

    if (resolvedOriginalFile == null && resolvedCurrentFile != null) {
        resolvedOriginalFile = copyToCoverSlot(resolvedCurrentFile, originalCoverFile)
    }

    val effectiveSource = resolvedCurrentFile ?: resolvedOriginalFile
    val resolvedEffectiveFile = effectiveSource?.let { source ->
        if (source.absolutePath == effectiveCoverFile.absolutePath) {
            source
        } else {
            copyToCoverSlot(source, effectiveCoverFile)
        }
    } ?: run {
        effectiveCoverFile.takeIf(File::exists)?.delete()
        null
    }

    return ResolvedBookCoverFiles(
        coverPath = resolvedEffectiveFile?.absolutePath,
        originalCoverPath = resolvedOriginalFile?.absolutePath,
        currentCoverPath = resolvedCurrentFile?.absolutePath,
    )
}

private fun String?.toExistingCoverFile(): File? {
    val path = this?.takeIf { it.isNotBlank() } ?: return null
    return File(path).takeIf(File::exists)
}

private fun copyToCoverSlot(source: File, target: File): File {
    if (source.absolutePath == target.absolutePath) {
        return source
    }

    source.inputStream().use { input ->
        FileOutputStream(target).use { output ->
            input.copyTo(output)
        }
    }
    return target
}

private fun writeBytesToFile(bytes: ByteArray, target: File) {
    FileOutputStream(target).use { output ->
        output.write(bytes)
    }
}

private fun isEditedBookCustomCoverHref(rawHref: String): Boolean {
    return rawHref
        .substringBefore("#")
        .removePrefix("/")
        .trim()
        .startsWith(EDITED_BOOK_CUSTOM_COVER_PREFIX)
}

private fun JSONObject.optCoverPath(key: String): String? {
    return when (val value = opt(key)) {
        null,
        JSONObject.NULL -> null
        else -> value.toString().takeIf { it.isNotBlank() && it != "null" }
    }
}

private fun deriveTitleFromName(name: String, fallback: String): String {
    return name.substringBeforeLast(".")
        .replace("_", " ")
        .replace("-", " ")
        .replaceFirstChar { it.uppercase() }
        .takeIf { it.isNotBlank() } ?: fallback
}

internal fun activeEpubFile(bookFolder: File, sourceFormat: BookFormat): File {
    return when (sourceFormat) {
        BookFormat.EPUB -> File(bookFolder, EPUB_ARCHIVE_FILE_NAME)
        BookFormat.PDF -> File(bookFolder, GENERATED_EPUB_FILE_NAME)
    }
}

internal fun ensureCanonicalSourcePdfFile(bookFolder: File): File? {
    val sourceFile = File(bookFolder, PDF_DOCUMENT_FILE_NAME)
    if (sourceFile.exists()) return sourceFile

    val legacyFile = File(bookFolder, LEGACY_PDF_DOCUMENT_FILE_NAME)
    if (!legacyFile.exists()) return null

    return runCatching {
        replaceFileAtomically(legacyFile, sourceFile)
        sourceFile
    }.getOrElse { legacyFile }
}

internal fun replaceFileAtomically(source: File, target: File) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: Exception) {
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
                if (renderer.pageCount <= 0) null else {
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
    if (renderer.pageCount <= 0) return null

    return runCatching {
        renderer.openPage(0).use { page ->
            val targetWidth = PDF_COVER_WIDTH_PX
            val targetHeight = max(1, (targetWidth.toFloat() * page.height / page.width).toInt())
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
