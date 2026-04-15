package com.epubreader.data.parser

import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.ConversionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EpubParserBooksTest {

    @Test
    fun saveAndLoadBookMetadata_roundTripsValidMetadata() {
        val folder = createTempBookFolder()
        try {
            val original = EpubBook(
                id = "book-1",
                title = "Test Title",
                author = "Test Author",
                coverPath = File(folder, "cover_thumb.png").absolutePath,
                rootPath = folder.absolutePath,
                format = BookFormat.EPUB,
                sourceFormat = BookFormat.PDF,
                conversionStatus = ConversionStatus.READY,
                hasPdfFallback = true,
                conversionCompletedPages = 10,
                conversionTotalPages = 10,
                toc = listOf(
                    TocItem("1. Chapter One", "Text/ch1.xhtml"),
                    TocItem("2. Chapter Two", "Text/ch2.xhtml"),
                ),
                spineHrefs = listOf("Text/ch1.xhtml", "Text/ch2.xhtml"),
                pageCount = 10,
                dateAdded = 1234L,
                lastRead = 5678L,
            )

            saveBookMetadata(folder, original)

            val loaded = loadBookMetadata(folder)

            assertNotNull(loaded)
            assertEquals(original, loaded)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun loadBookMetadata_handlesMissingOptionalCoverPath() {
        val folder = createTempBookFolder()
        try {
            writeMetadata(
                folder,
                """
                {
                  "id":"book-2",
                  "title":"No Cover",
                  "author":"Author",
                  "rootPath":"${escapeJson(folder.absolutePath)}",
                  "dateAdded":42,
                  "lastRead":7,
                  "toc":[{"title":"1. Chapter","href":"ch1.xhtml"}],
                  "spineHrefs":["ch1.xhtml"]
                }
                """.trimIndent(),
            )

            val loaded = loadBookMetadata(folder)

            assertNotNull(loaded)
            assertEquals(null, loaded?.coverPath)
            assertEquals(listOf("ch1.xhtml"), loaded?.spineHrefs)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun loadBookMetadata_handlesMissingOptionalSpineHrefs() {
        val folder = createTempBookFolder()
        try {
            writeMetadata(
                folder,
                """
                {
                  "id":"book-3",
                  "title":"No Spine",
                  "author":"Author",
                  "coverPath":"${escapeJson(File(folder, "cover.png").absolutePath)}",
                  "rootPath":"${escapeJson(folder.absolutePath)}",
                  "dateAdded":42,
                  "lastRead":7,
                  "toc":[{"title":"1. Chapter","href":"ch1.xhtml"}]
                }
                """.trimIndent(),
            )

            val loaded = loadBookMetadata(folder)

            assertNotNull(loaded)
            assertEquals(emptyList<String>(), loaded?.spineHrefs)
            assertEquals(1, loaded?.toc?.size)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun loadBookMetadata_preservesExplicitlyRemovedCurrentCover() {
        val folder = createTempBookFolder()
        try {
            val originalCover = File(folder, "cover-original.png").apply { writeText("cover") }
            writeMetadata(
                folder,
                """
                {
                  "id":"book-removed-current",
                  "title":"Original Only",
                  "author":"Author",
                  "coverPath":"${escapeJson(originalCover.absolutePath)}",
                  "originalCoverPath":"${escapeJson(originalCover.absolutePath)}",
                  "currentCoverPath":null,
                  "rootPath":"${escapeJson(folder.absolutePath)}",
                  "toc":[{"title":"1. Chapter","href":"ch1.xhtml"}],
                  "spineHrefs":["ch1.xhtml"]
                }
                """.trimIndent(),
            )

            val loaded = loadBookMetadata(folder)

            assertNotNull(loaded)
            assertEquals(originalCover.absolutePath, loaded?.originalCoverPath)
            assertEquals(null, loaded?.currentCoverPath)
            assertEquals(originalCover.absolutePath, loaded?.coverPath)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun loadBookMetadata_returnsNullForMalformedMetadata() {
        val folder = createTempBookFolder()
        try {
            writeMetadata(folder, "{ broken json")

            assertNull(loadBookMetadata(folder))
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun saveBookMetadata_replacesExistingMetadataWithoutTempResidue() {
        val folder = createTempBookFolder()
        try {
            writeMetadata(folder, """{"id":"stale","title":"Stale","author":"Author","rootPath":"${escapeJson(folder.absolutePath)}","toc":[],"spineHrefs":[]}""")
            val updated = EpubBook(
                id = "book-4",
                title = "Fresh Title",
                author = "Fresh Author",
                coverPath = null,
                rootPath = folder.absolutePath,
                toc = listOf(TocItem("1. Fresh", "Text/ch1.xhtml")),
                spineHrefs = listOf("Text/ch1.xhtml"),
                dateAdded = 99L,
                lastRead = 100L,
            )

            saveBookMetadata(folder, updated)

            assertEquals(updated, loadBookMetadata(folder))
            assertFalse(File(folder, "metadata.json.tmp").exists())
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun loadBookMetadata_roundTripsQueuedConversionProgress() {
        val folder = createTempBookFolder()
        try {
            val original = EpubBook(
                id = "pdf-book",
                title = "Queued PDF",
                author = "PDF Author",
                coverPath = null,
                rootPath = folder.absolutePath,
                format = BookFormat.PDF,
                sourceFormat = BookFormat.PDF,
                conversionStatus = ConversionStatus.RUNNING,
                hasPdfFallback = true,
                conversionCompletedPages = 125,
                conversionTotalPages = 500,
                pageCount = 500,
                dateAdded = 55L,
                lastRead = 66L,
            )

            saveBookMetadata(folder, original)

            assertEquals(original, loadBookMetadata(folder))
        } finally {
            folder.deleteRecursively()
        }
    }

    private fun createTempBookFolder(): File {
        return Files.createTempDirectory("epub-parser-books-test").toFile()
    }

    private fun writeMetadata(folder: File, content: String) {
        File(folder, "metadata.json").writeText(content)
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
    }
}
