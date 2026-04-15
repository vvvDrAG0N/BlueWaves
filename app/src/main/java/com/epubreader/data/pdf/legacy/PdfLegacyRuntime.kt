package com.epubreader.data.pdf.legacy

import android.content.Context
import com.epubreader.data.parser.PdfConversionProgressListener
import com.epubreader.data.parser.PdfConversionResult
import com.epubreader.data.parser.PdfLegacyBridge
import java.io.File

/**
 * Legacy PDF runtime adapter used by the parser bridge.
 * This is intentionally the only place that wires converter and worker details together.
 */
internal class PdfLegacyRuntime(
    private val context: Context,
    private val converter: PdfToEpubConverter = MlKitPdfToEpubConverter(context),
) : PdfLegacyBridge {

    override val workspaceDirName: String
        get() = PDF_CONVERSION_WORKSPACE_DIR_NAME

    override suspend fun convert(
        pdfFile: File,
        workspaceDir: File,
        outputFile: File,
        title: String,
        author: String,
        onProgress: PdfConversionProgressListener,
    ): PdfConversionResult {
        return converter.convert(
            pdfFile = pdfFile,
            workspaceDir = workspaceDir,
            outputFile = outputFile,
            title = title,
            author = author,
            onProgress = onProgress,
        )
    }

    override fun enqueue(bookId: String) {
        enqueuePdfConversionWork(context, bookId)
    }

    override fun cancel(bookId: String) {
        cancelPdfConversionWork(context, bookId)
    }

    override fun uniqueWorkName(bookId: String): String {
        return uniquePdfConversionWorkName(bookId)
    }
}
