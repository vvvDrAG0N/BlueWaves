package com.epubreader.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.pdf.legacy.PDF_CONVERSION_COMPLETED_KEY
import com.epubreader.data.pdf.legacy.PDF_CONVERSION_TOTAL_KEY
import com.epubreader.data.pdf.legacy.uniquePdfConversionWorkName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tiny shell-side bridge for the deprecated PDF path.
 * Keeps disabled messaging and legacy work observation out of `AppNavigation.kt`.
 */
internal const val PdfSupportDisabledSnackbarMessage =
    "PDF support is temporarily disabled while we prepare a safer refactor."

internal fun observeLegacyPdfWorkIds(
    books: List<EpubBook>,
): List<String> {
    return books
        .filter { it.sourceFormat == BookFormat.PDF && it.isPdfConversionInFlight }
        .map(EpubBook::id)
}

internal fun refreshPdfBookFromMetadata(
    scope: CoroutineScope,
    parser: EpubParser,
    selectedBook: EpubBook?,
    books: List<EpubBook>,
    bookId: String,
    onBookUpdated: (EpubBook) -> Unit,
) {
    val currentBook = (
        selectedBook?.takeIf { it.id == bookId }
            ?: books.find { it.id == bookId }
        )
        ?.takeIf { it.sourceFormat == BookFormat.PDF }
        ?: return

    scope.launch {
        val refreshedBook = withContext(Dispatchers.IO) {
            val latestMetadata = parser.loadMetadata(File(currentBook.rootPath)) ?: return@withContext null
            parser.prepareBookForReading(latestMetadata)
        }
        refreshedBook?.let(onBookUpdated)
    }
}

internal fun refreshPdfBookFromWork(
    scope: CoroutineScope,
    parser: EpubParser,
    selectedBook: EpubBook?,
    books: List<EpubBook>,
    bookId: String,
    workInfos: List<WorkInfo>,
    onBookUpdated: (EpubBook) -> Unit,
) {
    val currentBook = (
        selectedBook?.takeIf { it.id == bookId }
            ?: books.find { it.id == bookId }
        )
        ?.takeIf { it.sourceFormat == BookFormat.PDF }
        ?: return

    val latestWork = workInfos.firstOrNull()
    if (latestWork != null && !latestWork.state.isFinished) {
        onBookUpdated(
            currentBook.copy(
                conversionStatus = when (latestWork.state) {
                    WorkInfo.State.RUNNING -> ConversionStatus.RUNNING
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> ConversionStatus.QUEUED
                    else -> currentBook.conversionStatus
                },
                conversionCompletedPages = maxOf(
                    currentBook.conversionCompletedPages,
                    latestWork.progress.getInt(
                        PDF_CONVERSION_COMPLETED_KEY,
                        currentBook.conversionCompletedPages,
                    ),
                ),
                conversionTotalPages = maxOf(
                    currentBook.conversionTotalPages,
                    latestWork.progress.getInt(
                        PDF_CONVERSION_TOTAL_KEY,
                        currentBook.conversionTotalPages,
                    ),
                ),
            ),
        )
        return
    }

    refreshPdfBookFromMetadata(
        scope = scope,
        parser = parser,
        selectedBook = selectedBook,
        books = books,
        bookId = bookId,
        onBookUpdated = onBookUpdated,
    )
}

@Composable
internal fun ObserveLegacyPdfConversionState(
    context: Context,
    books: List<EpubBook>,
    selectedBook: EpubBook?,
    parser: EpubParser,
    lifecycleOwner: LifecycleOwner,
    scope: CoroutineScope,
    onBookUpdated: (EpubBook) -> Unit,
) {
    val observedPdfWorkIds = remember(books) {
        observeLegacyPdfWorkIds(books)
    }

    DisposableEffect(lifecycleOwner, observedPdfWorkIds, selectedBook?.id, context) {
        val workManager = WorkManager.getInstance(context)
        val liveDataByBookId = observedPdfWorkIds.associateWith { bookId ->
            workManager.getWorkInfosForUniqueWorkLiveData(uniquePdfConversionWorkName(bookId))
        }
        val observerByBookId = liveDataByBookId.mapValues { (bookId, _) ->
            Observer<List<WorkInfo>> { workInfos ->
                refreshPdfBookFromWork(
                    scope = scope,
                    parser = parser,
                    selectedBook = selectedBook,
                    books = books,
                    bookId = bookId,
                    workInfos = workInfos,
                    onBookUpdated = onBookUpdated,
                )
            }
        }

        liveDataByBookId.forEach { (bookId, workInfos) ->
            val observer = observerByBookId.getValue(bookId)
            workInfos.observe(lifecycleOwner, observer)
            refreshPdfBookFromWork(
                scope = scope,
                parser = parser,
                selectedBook = selectedBook,
                books = books,
                bookId = bookId,
                workInfos = workInfos.value.orEmpty(),
                onBookUpdated = onBookUpdated,
            )
        }

        onDispose {
            liveDataByBookId.forEach { (bookId, workInfos) ->
                workInfos.removeObserver(observerByBookId.getValue(bookId))
            }
        }
    }
}
