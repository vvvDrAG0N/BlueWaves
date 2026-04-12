package com.epubreader.app

import com.epubreader.core.model.EpubBook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationLibraryDataTest {

    @Test
    fun parseFolderOrder_returnsEmptyList_forMalformedJson() {
        assertEquals(emptyList<String>(), parseFolderOrder("{not valid json"))
    }

    @Test
    fun buildFolders_includesRootInferredFoldersAndOrderedMetadataFolders() {
        val books = listOf(
            book(id = "1", title = "Root Book"),
            book(id = "2", title = "Sci-Fi Book"),
            book(id = "3", title = "Mystery Book"),
        )
        val bookGroups = JSONObject(
            mapOf(
                "2" to "Sci-Fi",
                "3" to "Mystery",
            ),
        )
        val folderSorts = JSONObject(mapOf("Fantasy" to "title_asc"))

        val folders = buildFolders(
            books = books,
            bookGroups = bookGroups,
            folderSorts = folderSorts,
            folderOrder = listOf("Fantasy", "Sci-Fi"),
        )

        assertEquals(
            listOf(RootLibraryName, "Fantasy", "Sci-Fi", "Mystery"),
            folders,
        )
    }

    @Test
    fun buildLibraryItems_sortsByAdded() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = JSONObject(),
            currentSort = "added_desc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("gamma", "beta", "alpha"), items.map(EpubBook::id))
    }

    @Test
    fun buildLibraryItems_sortsByTitle() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = JSONObject(),
            currentSort = "title_asc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("gamma", "beta", "alpha"), items.map(EpubBook::id))
    }

    @Test
    fun buildLibraryItems_sortsByAuthor() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = JSONObject(),
            currentSort = "author_asc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("beta", "alpha", "gamma"), items.map(EpubBook::id))
    }

    @Test
    fun buildLibraryItems_sortsByRecent() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = JSONObject(),
            currentSort = "recent_desc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("gamma", "beta", "alpha"), items.map(EpubBook::id))
    }

    @Test
    fun buildLibraryItems_sortsByChapterCount() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = JSONObject(),
            currentSort = "chapters_desc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("gamma", "beta", "alpha"), items.map(EpubBook::id))
    }

    @Test
    fun updateFolderDragPreview_doesNotReorder_untilThresholdIsCrossed() {
        val update = updateFolderDragPreview(
            dragPreviewFolders = listOf(RootLibraryName, "Sci-Fi", "Fantasy"),
            draggedFolderName = "Fantasy",
            dragOffset = 0f,
            dragAmountY = 20f,
            itemHeightPx = 50f,
        )

        assertFalse(update.didReorder)
        assertEquals(listOf(RootLibraryName, "Sci-Fi", "Fantasy"), update.previewFolders)
        assertEquals(20f, update.dragOffset, 0.001f)
    }

    @Test
    fun updateFolderDragPreview_reorders_whenThresholdIsCrossed() {
        val update = updateFolderDragPreview(
            dragPreviewFolders = listOf(RootLibraryName, "Sci-Fi", "Fantasy", "History"),
            draggedFolderName = "Fantasy",
            dragOffset = 0f,
            dragAmountY = 40f,
            itemHeightPx = 50f,
        )

        assertTrue(update.didReorder)
        assertEquals(
            listOf(RootLibraryName, "Sci-Fi", "History", "Fantasy"),
            update.previewFolders,
        )
        assertEquals(-10f, update.dragOffset, 0.001f)
    }

    @Test
    fun updateFolderDragPreview_doesNotMoveFolderAboveMyLibrary() {
        val update = updateFolderDragPreview(
            dragPreviewFolders = listOf(RootLibraryName, "Sci-Fi", "Fantasy"),
            draggedFolderName = "Sci-Fi",
            dragOffset = 0f,
            dragAmountY = -40f,
            itemHeightPx = 50f,
        )

        assertFalse(update.didReorder)
        assertEquals(listOf(RootLibraryName, "Sci-Fi", "Fantasy"), update.previewFolders)
        assertEquals(-40f, update.dragOffset, 0.001f)
    }

    private fun sampleBooks(): List<EpubBook> {
        return listOf(
            book(
                id = "alpha",
                title = "Gamma",
                author = "Bob",
                dateAdded = 100L,
                lastRead = 200L,
                chapterCount = 1,
            ),
            book(
                id = "beta",
                title = "Beta",
                author = "Alice",
                dateAdded = 200L,
                lastRead = 500L,
                chapterCount = 2,
            ),
            book(
                id = "gamma",
                title = "alpha",
                author = "Carol",
                dateAdded = 300L,
                lastRead = 900L,
                chapterCount = 3,
            ),
        )
    }

    private fun book(
        id: String,
        title: String,
        author: String = "Author",
        dateAdded: Long = 0L,
        lastRead: Long = 0L,
        chapterCount: Int = 0,
    ): EpubBook {
        return EpubBook(
            id = id,
            title = title,
            author = author,
            coverPath = null,
            rootPath = "/books/$id",
            spineHrefs = List(chapterCount) { index -> "chapter-$index.xhtml" },
            dateAdded = dateAdded,
            lastRead = lastRead,
        )
    }
}
