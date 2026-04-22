package com.epubreader.data.parser

import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import java.io.File
import java.util.zip.ZipFile

internal fun importPdfSource(
    bookFolder: File,
    displayName: String?,
    workspaceDirName: String,
): EpubBook? {
    cleanupArtifactsForPdfImport(bookFolder, workspaceDirName)

    val queuedPdfBook = rebuildPdfMetadata(
        bookFolder,
        displayName = displayName,
        conversionStatus = ConversionStatus.QUEUED,
        preferredFormat = BookFormat.PDF,
        conversionCompletedPages = 0,
        conversionTotalPages = 0,
    ) ?: return null

    val sourcePdf = ensureCanonicalSourcePdfFile(bookFolder) ?: return queuedPdfBook
    File(bookFolder, LEGACY_PDF_DOCUMENT_FILE_NAME)
        .takeIf { it.exists() && it.absolutePath != sourcePdf.absolutePath }
        ?.delete()
    return queuedPdfBook
}

internal fun healCachedBook(
    bookFolder: File,
    book: EpubBook,
): EpubBook? {
    if (book.sourceFormat != BookFormat.PDF) {
        return book
    }

    val generatedEpub = activeEpubFile(bookFolder, BookFormat.PDF)
    return when {
        book.format == BookFormat.EPUB && !generatedEpub.exists() -> {
            rebuildPdfMetadata(
                bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.FAILED,
                preferredFormat = BookFormat.PDF,
                conversionCompletedPages = book.conversionCompletedPages,
                conversionTotalPages = book.conversionTotalPages,
            )
        }

        book.conversionStatus == ConversionStatus.READY && !isGeneratedEpubReadyForReading(bookFolder, book) -> {
            rebuildPdfMetadata(
                bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.FAILED,
                preferredFormat = BookFormat.PDF,
                conversionCompletedPages = book.conversionCompletedPages,
                conversionTotalPages = book.conversionTotalPages,
            )
        }

        else -> book.copy(
            hasPdfFallback = ensureCanonicalSourcePdfFile(bookFolder) != null,
            conversionTotalPages = book.conversionTotalPages.takeIf { it > 0 } ?: book.pageCount,
        )
    }
}

internal fun validateGeneratedEpub(epubFile: File): Boolean {
    val looksLikeZip = runCatching {
        epubFile.inputStream().use { input ->
            val headerBytes = ByteArray(4)
            val read = input.read(headerBytes)
            isZipSignature(if (read > 0) headerBytes.copyOf(read) else ByteArray(0))
        }
    }.getOrDefault(false)
    if (!looksLikeZip) return false

    return try {
        ZipFile(epubFile).use { zip ->
            val metadata = InternalEpubParser.parseEpub(zip)
            metadata != null && metadata.spine.isNotEmpty()
        }
    } catch (_: Exception) {
        false
    }
}

internal fun isGeneratedEpubReadyForReading(
    bookFolder: File,
    book: EpubBook,
): Boolean {
    if (book.sourceFormat != BookFormat.PDF) {
        return true
    }

    val generatedEpub = activeEpubFile(bookFolder, BookFormat.PDF)
    if (!generatedEpub.exists() || !validateGeneratedEpub(generatedEpub)) {
        return false
    }

    val spineHrefs = book.spineHrefs
        .map { it.substringBefore("#").removePrefix("/") }
        .filter(String::isNotBlank)
    if (spineHrefs.isEmpty()) {
        return false
    }

    val allSpineEntriesExist = runCatching {
        ZipFile(generatedEpub).use { zip ->
            spineHrefs.all { cleanHref ->
                zip.getEntry(cleanHref) != null ||
                    zip.getEntry("OEBPS/$cleanHref") != null ||
                    zip.getEntry("OPS/$cleanHref") != null
            }
        }
    }.getOrDefault(false)
    if (!allSpineEntriesExist) {
        return false
    }

    return parseBookChapter(bookFolder.absolutePath, book.spineHrefs.first()).isNotEmpty()
}
