/**
 * Deprecated background worker retained for legacy queued items and the upcoming safe refactor.
 * The app shell no longer enqueues new PDF imports.
 */
package com.epubreader.data.pdf.legacy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.epubreader.R
import com.epubreader.core.debug.AppLog
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.parser.PdfConversionProgressListener
import kotlinx.coroutines.CancellationException

internal fun enqueuePdfConversionWork(
    context: Context,
    bookId: String,
) {
    val request = OneTimeWorkRequestBuilder<PdfConversionWorker>()
        .addTag(PDF_CONVERSION_WORK_TAG)
        .addTag(bookId)
        .setInputData(workDataOf(PDF_CONVERSION_BOOK_ID_KEY to bookId))
        .build()

    WorkManager.getInstance(context)
        .enqueueUniqueWork(
            uniquePdfConversionWorkName(bookId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
}

internal fun cancelPdfConversionWork(
    context: Context,
    bookId: String,
) {
    WorkManager.getInstance(context).cancelUniqueWork(uniquePdfConversionWorkName(bookId))
}

internal fun uniquePdfConversionWorkName(bookId: String): String = "pdf-conversion-$bookId"

internal class PdfConversionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(PDF_CONVERSION_BOOK_ID_KEY)
            ?: return Result.failure()

        return try {
            setForeground(createForegroundInfo(completedPages = 0, totalPages = 0))
            val parser = EpubParser(applicationContext)
            val updatedBook = parser.convertStoredPdfForBook(
                bookId = bookId,
                onProgress = PdfConversionProgressListener { progress ->
                    setProgress(
                        workDataOf(
                            PDF_CONVERSION_COMPLETED_KEY to progress.completedPages,
                            PDF_CONVERSION_TOTAL_KEY to progress.totalPages,
                        ),
                    )
                    setForeground(
                        createForegroundInfo(
                            completedPages = progress.completedPages,
                            totalPages = progress.totalPages,
                        ),
                    )
                },
            )

            if (updatedBook == null) {
                Result.failure()
            } else {
                Result.success(
                    workDataOf(
                        PDF_CONVERSION_STATUS_KEY to updatedBook.conversionStatus.name,
                    ),
                )
            }
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }
            AppLog.w(AppLog.PARSER, error) { "PDF conversion worker crashed for $bookId" }
            Result.retry()
        }
    }

    private fun createForegroundInfo(
        completedPages: Int,
        totalPages: Int,
    ): ForegroundInfo {
        ensureNotificationChannel()
        val contentText = when {
            totalPages > 0 -> "Converted $completedPages / $totalPages pages"
            else -> "Preparing conversion"
        }
        val notification = NotificationCompat.Builder(applicationContext, PDF_CONVERSION_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Converting PDF")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(totalPages.coerceAtLeast(0), completedPages.coerceAtLeast(0), totalPages <= 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PDF_CONVERSION_NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PDF_CONVERSION_NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(PDF_CONVERSION_NOTIFICATION_CHANNEL_ID) != null) {
            return
        }

        notificationManager.createNotificationChannel(
            NotificationChannel(
                PDF_CONVERSION_NOTIFICATION_CHANNEL_ID,
                "PDF Conversion",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Background PDF to EPUB conversion"
            },
        )
    }
}

internal const val PDF_CONVERSION_WORK_TAG = "pdf-conversion"
internal const val PDF_CONVERSION_BOOK_ID_KEY = "pdf_conversion_book_id"
internal const val PDF_CONVERSION_COMPLETED_KEY = "pdf_conversion_completed"
internal const val PDF_CONVERSION_TOTAL_KEY = "pdf_conversion_total"
internal const val PDF_CONVERSION_STATUS_KEY = "pdf_conversion_status"

private const val PDF_CONVERSION_NOTIFICATION_ID = 2048
private const val PDF_CONVERSION_NOTIFICATION_CHANNEL_ID = "pdf_conversion"
