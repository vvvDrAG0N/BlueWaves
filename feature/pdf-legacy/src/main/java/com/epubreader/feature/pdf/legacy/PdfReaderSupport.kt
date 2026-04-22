package com.epubreader.feature.pdf.legacy

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.epubreader.core.ui.ReaderTheme
import java.io.File
import kotlin.math.max

@Composable
internal fun PdfReaderErrorState(
    theme: ReaderTheme,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = theme.foreground.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Couldn't open this PDF.",
            color = theme.foreground,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The file may be missing or unreadable.",
            color = theme.foreground.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(20.dp))
        TextButton(onClick = onBack) {
            Text(text = "Back to Library")
        }
    }
}

internal class PdfDocumentHandle private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageCount: Int,
    val defaultAspectRatio: Float,
) {
    private val renderLock = Any()

    fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap? {
        if (pageIndex !in 0 until pageCount || targetWidthPx <= 0) {
            return null
        }

        return runCatching {
            synchronized(renderLock) {
                renderer.openPage(pageIndex).use { page ->
                    val targetHeightPx = max(
                        1,
                        (targetWidthPx.toFloat() * page.height / page.width).toInt(),
                    )
                    val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    val matrix = Matrix().apply {
                        setScale(
                            targetWidthPx.toFloat() / page.width.toFloat(),
                            targetHeightPx.toFloat() / page.height.toFloat(),
                        )
                    }
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }.getOrNull()
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(pdfFile: File): PdfDocumentHandle? {
            if (!pdfFile.exists()) {
                return null
            }

            val descriptor = runCatching {
                ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            }.getOrNull() ?: return null

            var renderer: PdfRenderer? = null
            return try {
                renderer = PdfRenderer(descriptor)
                if (renderer.pageCount <= 0) {
                    renderer.close()
                    descriptor.close()
                    return null
                }

                val defaultAspectRatio = renderer.openPage(0).use { page ->
                    page.width.toFloat() / page.height.toFloat()
                }
                PdfDocumentHandle(
                    descriptor = descriptor,
                    renderer = renderer,
                    pageCount = renderer.pageCount,
                    defaultAspectRatio = defaultAspectRatio,
                )
            } catch (_: Exception) {
                renderer?.close()
                descriptor.close()
                null
            }
        }
    }
}
