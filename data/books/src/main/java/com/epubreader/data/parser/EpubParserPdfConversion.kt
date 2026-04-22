package com.epubreader.data.parser

import com.epubreader.core.debug.AppLog
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import java.io.File
import kotlinx.coroutines.CancellationException

internal suspend fun convertStoredPdfForBook(
    booksDir: File,
    workspaceDirName: String,
    pdfLegacyBridge: PdfLegacyBridge,
    bookId: String,
    onProgress: PdfConversionProgressListener = PdfConversionProgressListener {},
): EpubBook? {
    val bookFolder = File(booksDir, bookId)
    if (!bookFolder.exists()) {
        return null
    }

    val existing = loadBookMetadata(bookFolder)
        ?: rebuildPdfMetadata(
            bookFolder = bookFolder,
            displayName = null,
            conversionStatus = ConversionStatus.NONE,
        )
        ?: return null
    if (existing.sourceFormat != BookFormat.PDF) {
        return existing
    }

    val generatedEpub = activeEpubFile(bookFolder, BookFormat.PDF)
    if (generatedEpub.exists() && validateGeneratedEpub(generatedEpub)) {
        val rebuiltBook = rebuildBookMetadata(bookFolder) ?: existing
        return if (isGeneratedEpubReadyForReading(bookFolder, rebuiltBook)) {
            rebuiltBook
        } else {
            generatedEpub.delete()
            rebuildPdfMetadata(
                bookFolder = bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.FAILED,
                preferredFormat = BookFormat.PDF,
                conversionCompletedPages = existing.conversionCompletedPages,
                conversionTotalPages = existing.conversionTotalPages.takeIf { it > 0 } ?: existing.pageCount,
            ) ?: existing
        }
    }

    val sourcePdf = ensureCanonicalSourcePdfFile(bookFolder) ?: return null
    val workspaceDir = File(bookFolder, workspaceDirName)
    val stagedGeneratedEpub = File(bookFolder, "$GENERATED_EPUB_FILE_NAME.importing")
    stagedGeneratedEpub.delete()
    generatedEpub.takeIf { it.exists() && !validateGeneratedEpub(it) }?.delete()

    val runningBook = rebuildPdfMetadata(
        bookFolder = bookFolder,
        displayName = null,
        conversionStatus = ConversionStatus.RUNNING,
        preferredFormat = BookFormat.PDF,
        conversionCompletedPages = existing.conversionCompletedPages,
        conversionTotalPages = existing.conversionTotalPages.takeIf { it > 0 } ?: existing.pageCount,
    ) ?: existing
    onProgress.onProgress(
        PdfConversionProgress(
            completedPages = runningBook.conversionCompletedPages,
            totalPages = runningBook.conversionTotalPages.takeIf { it > 0 } ?: runningBook.pageCount,
        ),
    )

    try {
        val conversionResult = pdfLegacyBridge.convert(
            pdfFile = sourcePdf,
            workspaceDir = workspaceDir,
            outputFile = stagedGeneratedEpub,
            title = runningBook.title,
            author = runningBook.author,
            onProgress = PdfConversionProgressListener { progress ->
                rebuildPdfMetadata(
                    bookFolder = bookFolder,
                    displayName = null,
                    conversionStatus = ConversionStatus.RUNNING,
                    preferredFormat = BookFormat.PDF,
                    conversionCompletedPages = progress.completedPages,
                    conversionTotalPages = progress.totalPages,
                )
                onProgress.onProgress(progress)
            },
        )

        if (conversionResult.succeeded && stagedGeneratedEpub.exists() && validateGeneratedEpub(stagedGeneratedEpub)) {
            replaceFileAtomically(stagedGeneratedEpub, generatedEpub)
            workspaceDir.deleteRecursively()
            val rebuiltBook = rebuildBookMetadata(bookFolder)
            if (rebuiltBook != null && isGeneratedEpubReadyForReading(bookFolder, rebuiltBook)) {
                return rebuiltBook
            }
            generatedEpub.takeIf(File::exists)?.delete()
            return rebuildPdfMetadata(
                bookFolder = bookFolder,
                displayName = null,
                conversionStatus = ConversionStatus.FAILED,
                preferredFormat = BookFormat.PDF,
                conversionCompletedPages = conversionResult.completedPages,
                conversionTotalPages = conversionResult.totalPages.takeIf { it > 0 } ?: existing.pageCount,
            )
        }

        stagedGeneratedEpub.takeIf(File::exists)?.delete()
        generatedEpub.takeIf(File::exists)?.delete()
        workspaceDir.deleteRecursively()
        return rebuildPdfMetadata(
            bookFolder = bookFolder,
            displayName = null,
            conversionStatus = ConversionStatus.FAILED,
            preferredFormat = BookFormat.PDF,
            conversionCompletedPages = conversionResult.completedPages,
            conversionTotalPages = conversionResult.totalPages.takeIf { it > 0 } ?: existing.pageCount,
        )
    } catch (error: Exception) {
        if (error is CancellationException) {
            throw error
        }
        AppLog.w(AppLog.PARSER, error) { "Stored PDF conversion failed for $bookId" }
        stagedGeneratedEpub.takeIf(File::exists)?.delete()
        generatedEpub.takeIf { it.exists() && !validateGeneratedEpub(it) }?.delete()
        workspaceDir.deleteRecursively()
        return rebuildPdfMetadata(
            bookFolder = bookFolder,
            displayName = null,
            conversionStatus = ConversionStatus.FAILED,
            preferredFormat = BookFormat.PDF,
            conversionCompletedPages = runningBook.conversionCompletedPages,
            conversionTotalPages = runningBook.conversionTotalPages.takeIf { it > 0 } ?: runningBook.pageCount,
        )
    }
}
