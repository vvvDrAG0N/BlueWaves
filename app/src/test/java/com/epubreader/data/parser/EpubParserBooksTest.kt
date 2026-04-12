package com.epubreader.data.parser

import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.TocItem
import org.junit.Assert.assertEquals
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
                toc = listOf(
                    TocItem("1. Chapter One", "Text/ch1.xhtml"),
                    TocItem("2. Chapter Two", "Text/ch2.xhtml"),
                ),
                spineHrefs = listOf("Text/ch1.xhtml", "Text/ch2.xhtml"),
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
    fun loadBookMetadata_returnsNullForMalformedMetadata() {
        val folder = createTempBookFolder()
        try {
            writeMetadata(folder, "{ broken json")

            assertNull(loadBookMetadata(folder))
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
