package com.epubreader.testing

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal object PdfAndroidTestFixtures {

    fun createTwoPageTextPdf(
        context: Context,
        namePrefix: String,
    ): File {
        val rootDir = File(context.cacheDir, "androidTest-pdfs").apply { mkdirs() }
        val pdfFile = File(rootDir, "$namePrefix-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()

        try {
            val pageTexts = listOf(
                listOf(
                    "Blue Waves PDF instrumentation page one",
                    "This text is large and high contrast for OCR.",
                    "Generated EPUB fallback should succeed for this file.",
                    "The original PDF must remain available as source.",
                    "Reader progress must stay separate between EPUB and PDF.",
                ),
                listOf(
                    "Blue Waves PDF instrumentation page two",
                    "This second page confirms multi-page conversion.",
                    "Open original PDF should switch to raw PDF mode.",
                    "Switching back should restore EPUB progress again.",
                    "These lines are repeated to help OCR quality.",
                ),
            )

            pageTexts.forEachIndexed { index, lines ->
                val pageInfo = PdfDocument.PageInfo.Builder(720, 1080, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.BLACK
                    textSize = 28f
                }

                var y = 100f
                lines.forEach { line ->
                    canvas.drawText(line, 48f, y, paint)
                    y += 68f
                }
                repeat(4) { repeatIndex ->
                    val repeatedLine = if (index == 0) {
                        "Instrumentation OCR pass ${repeatIndex + 1} on page one."
                    } else {
                        "Instrumentation OCR pass ${repeatIndex + 1} on page two."
                    }
                    canvas.drawText(repeatedLine, 48f, y, paint)
                    y += 68f
                }
                document.finishPage(page)
            }

            FileOutputStream(pdfFile).use(document::writeTo)
            return pdfFile
        } finally {
            document.close()
        }
    }
}
