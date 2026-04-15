package com.epubreader.data.parser

import java.io.File

/**
 * Small parser-owned seam for the parked PDF runtime.
 * Keeps `EpubParser` focused on metadata/state decisions instead of worker/viewer details.
 */
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

internal interface PdfLegacyBridge {
    val workspaceDirName: String

    suspend fun convert(
        pdfFile: File,
        workspaceDir: File,
        outputFile: File,
        title: String,
        author: String,
        onProgress: PdfConversionProgressListener = PdfConversionProgressListener {},
    ): PdfConversionResult

    fun enqueue(bookId: String)

    fun cancel(bookId: String)

    fun uniqueWorkName(bookId: String): String
}
