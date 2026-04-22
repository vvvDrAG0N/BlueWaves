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
import com.epubreader.data.parser.PdfConversionProgress
import com.epubreader.data.parser.PdfConversionProgressListener
import com.epubreader.data.parser.PdfConversionResult
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
import java.io.File
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

internal enum class PdfTextExtractionMethod {
    DIRECT_TEXT,
    OCR,
    EMPTY,
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

private fun readPageCountWithRenderer(pdfFile: File): Int {
    return runCatching {
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.pageCount
            }
        }
    }.getOrDefault(0)
}
