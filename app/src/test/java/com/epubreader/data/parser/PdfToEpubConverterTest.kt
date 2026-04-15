package com.epubreader.data.pdf.legacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

class PdfToEpubConverterTest {

    @Test
    fun buildPdfReflowSections_splitsAfterTwelvePages_andUsesSectionLabels() {
        val workspaceDir = createWorkspaceDir()
        try {
            repeat(13) { index ->
                writeWorkspacePageJson(
                    workspaceDir = workspaceDir,
                    pageNumber = index + 1,
                    paragraphs = listOf("Page ${index + 1} body text for the generated reflow."),
                )
            }

            val sections = buildPdfReflowSections(workspaceDir, totalPages = 13)

            assertEquals(listOf("Pages 1-12", "Page 13"), sections.map(PdfReflowSection::title))
            assertEquals("sections/section-0001.xhtml", sections.first().href)
            assertEquals("sections/section-0002.xhtml", sections.last().href)
        } finally {
            workspaceDir.deleteRecursively()
        }
    }

    @Test
    fun buildPdfReflowSections_splitsWhenCharacterBudgetWouldOverflow() {
        val workspaceDir = createWorkspaceDir()
        try {
            repeat(3) { index ->
                writeWorkspacePageJson(
                    workspaceDir = workspaceDir,
                    pageNumber = index + 1,
                    paragraphs = listOf("a".repeat(8_000)),
                )
            }

            val sections = buildPdfReflowSections(workspaceDir, totalPages = 3)

            assertEquals(3, sections.size)
            assertEquals(listOf("Page 1", "Page 2", "Page 3"), sections.map(PdfReflowSection::title))
        } finally {
            workspaceDir.deleteRecursively()
        }
    }

    @Test
    fun writeGeneratedEpub_writesSectionBasedSpineAndToc() {
        val workspaceDir = createWorkspaceDir()
        val outputFile = File(workspaceDir, "generated.epub")
        try {
            repeat(13) { index ->
                writeWorkspacePageJson(
                    workspaceDir = workspaceDir,
                    pageNumber = index + 1,
                    paragraphs = listOf("This is page ${index + 1} in the generated PDF reflow."),
                )
            }
            File(workspaceDir, "styles").mkdirs()
            File(workspaceDir, "styles/book.css").writeText("body { margin: 0; }")

            writeGeneratedEpub(
                outputFile = outputFile,
                workspaceDir = workspaceDir,
                title = "Converted PDF",
                author = "Blue Waves",
                totalPages = 13,
            )

            ZipFile(outputFile).use { zip ->
                assertNotNull(zip.getEntry("OEBPS/sections/section-0001.xhtml"))
                assertNotNull(zip.getEntry("OEBPS/sections/section-0002.xhtml"))
                assertNull(zip.getEntry("OEBPS/pages/page-0001.xhtml"))

                val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf"))
                    .bufferedReader()
                    .use { it.readText() }
                assertTrue(opf.contains("sections/section-0001.xhtml"))
                assertFalse(opf.contains("pages/page-0001.xhtml"))

                val ncx = zip.getInputStream(zip.getEntry("OEBPS/toc.ncx"))
                    .bufferedReader()
                    .use { it.readText() }
                assertTrue(ncx.contains("Pages 1-12"))
                assertTrue(ncx.contains("sections/section-0001.xhtml#page-0001"))
            }
        } finally {
            workspaceDir.deleteRecursively()
        }
    }

    private fun createWorkspaceDir(): File {
        return Files.createTempDirectory("pdf-reflow-test").toFile()
    }

    private fun writeWorkspacePageJson(
        workspaceDir: File,
        pageNumber: Int,
        paragraphs: List<String>,
    ) {
        val pagesDir = File(workspaceDir, "pages").apply { mkdirs() }
        File(
            pagesDir,
            "page-${pageNumber.toString().padStart(4, '0')}.json",
        ).writeText(
            PdfWorkspacePage(
                pageNumber = pageNumber,
                paragraphs = paragraphs,
            ).toJson().toString(),
        )
    }
}
