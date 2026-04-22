package com.epubreader.data.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

internal data class PdfDocumentInfo(
    val pageCount: Int,
    val coverPath: String?,
)

internal fun readPdfDocumentInfo(bookFile: File, bookFolder: File): PdfDocumentInfo? {
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
