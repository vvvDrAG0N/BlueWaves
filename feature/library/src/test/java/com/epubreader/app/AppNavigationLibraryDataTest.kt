package com.epubreader.app

import com.epubreader.core.model.EpubBook
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
        val bookGroups = mapOf(
            "2" to "Sci-Fi",
            "3" to "Mystery",
        )
        val folderSorts = mapOf("Fantasy" to "title_asc")

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
            bookGroups = emptyMap(),
            currentSort = "added_desc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("gamma", "beta", "alpha"), items.map(EpubBook::id))
    }

    @Test
    fun buildLibraryItems_sortsByTitle() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = emptyMap(),
            currentSort = "title_asc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("gamma", "beta", "alpha"), items.map(EpubBook::id))
    }

    @Test
    fun buildLibraryItems_sortsByAuthor() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = emptyMap(),
            currentSort = "author_asc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("beta", "alpha", "gamma"), items.map(EpubBook::id))
    }

    @Test
    fun buildLibraryItems_sortsByRecent() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = emptyMap(),
            currentSort = "recent_desc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("gamma", "beta", "alpha"), items.map(EpubBook::id))
    }

    @Test
    fun buildLibraryItems_sortsByChapterCount() {
        val items = buildLibraryItems(
            books = sampleBooks(),
            bookGroups = emptyMap(),
            currentSort = "chapters_desc",
            selectedFolderName = RootLibraryName,
        )

        assertEquals(listOf("gamma", "beta", "alpha"), items.map(EpubBook::id))
    }

    @Test
    fun buildVisibleProgressTargets_returnsEmptyForEmptyLibraryWithoutRecentBook() {
        val targets = buildVisibleProgressTargets(
            libraryItems = emptyList(),
            lastOpenedBook = null,
            visibleGridIndices = emptyList(),
        )

        assertTrue(targets.isEmpty())
    }

    @Test
    fun buildVisibleProgressTargets_returnsFallbackWindowWhenGridIsCold() {
        val libraryItems = List(14) { index -> book(id = "book-$index", title = "Book $index") }

        val targets = buildVisibleProgressTargets(
            libraryItems = libraryItems,
            lastOpenedBook = null,
            visibleGridIndices = emptyList(),
        )

        assertEquals((0 until 12).map { "book-$it" }, targets.map(EpubBook::id))
    }

    @Test
    fun buildVisibleProgressTargets_mapsVisibleIndicesWithoutHeader() {
        val libraryItems = listOf(
            book(id = "alpha", title = "Alpha"),
            book(id = "beta", title = "Beta"),
            book(id = "gamma", title = "Gamma"),
        )

        val targets = buildVisibleProgressTargets(
            libraryItems = libraryItems,
            lastOpenedBook = null,
            visibleGridIndices = listOf(2, 0),
        )

        assertEquals(listOf("gamma", "alpha"), targets.map(EpubBook::id))
    }

    @Test
    fun buildVisibleProgressTargets_offsetsVisibleIndicesWhenRecentlyViewedHeaderIsPresent() {
        val libraryItems = listOf(
            book(id = "alpha", title = "Alpha"),
            book(id = "beta", title = "Beta"),
            book(id = "gamma", title = "Gamma"),
        )
        val recentBook = book(id = "recent", title = "Recent")

        val targets = buildVisibleProgressTargets(
            libraryItems = libraryItems,
            lastOpenedBook = recentBook,
            visibleGridIndices = listOf(0, 1, 3),
        )

        assertEquals(listOf("alpha", "gamma", "recent"), targets.map(EpubBook::id))
    }

    @Test
    fun buildVisibleProgressTargets_dedupesLastOpenedBookWhenAlreadyVisible() {
        val libraryItems = listOf(
            book(id = "alpha", title = "Alpha"),
            book(id = "beta", title = "Beta"),
            book(id = "gamma", title = "Gamma"),
        )
        val recentBook = book(id = "beta", title = "Beta")

        val targets = buildVisibleProgressTargets(
            libraryItems = libraryItems,
            lastOpenedBook = recentBook,
            visibleGridIndices = listOf(2),
        )

        assertEquals(listOf("beta"), targets.map(EpubBook::id))
    }

    @Test
    fun buildLibraryDerivedData_movesBookBackToRootWithoutGhostMembership() {
        val books = sampleBooks()
        val bookGroupsRaw = """{}"""

        val sourceFolderSnapshot = buildLibraryDerivedData(
            books = books,
            bookGroupsRaw = bookGroupsRaw,
            folderSortsRaw = "{}",
            folderOrderRaw = "[]",
            librarySort = "added_desc",
            selectedFolderName = "Sci-Fi",
        )
        val rootSnapshot = buildLibraryDerivedData(
            books = books,
            bookGroupsRaw = bookGroupsRaw,
            folderSortsRaw = "{}",
            folderOrderRaw = "[]",
            librarySort = "added_desc",
            selectedFolderName = RootLibraryName,
        )

        assertTrue(sourceFolderSnapshot.libraryItems.isEmpty())
        assertTrue(rootSnapshot.libraryItems.any { it.id == "beta" })
        assertEquals(RootLibraryName, rootSnapshot.bookFolderById["beta"])
    }

    @Test
    fun buildLibraryDerivedData_movesBookIntoTargetFolderWithoutWarmup() {
        val books = listOf(
            book(id = "root", title = "Root Book", dateAdded = 400L),
            book(id = "late", title = "Zeta", dateAdded = 300L),
            book(id = "early", title = "Alpha", dateAdded = 100L),
        )
        val bookGroupsRaw = """{"late":"Sci-Fi","early":"Sci-Fi"}"""

        val rootSnapshot = buildLibraryDerivedData(
            books = books,
            bookGroupsRaw = bookGroupsRaw,
            folderSortsRaw = "{}",
            folderOrderRaw = "[]",
            librarySort = "added_desc",
            selectedFolderName = RootLibraryName,
        )
        val targetFolderSnapshot = buildLibraryDerivedData(
            books = books,
            bookGroupsRaw = bookGroupsRaw,
            folderSortsRaw = """{"Sci-Fi":"title_asc"}""",
            folderOrderRaw = """["Sci-Fi"]""",
            librarySort = "added_desc",
            selectedFolderName = "Sci-Fi",
        )

        assertEquals(listOf("root"), rootSnapshot.libraryItems.map(EpubBook::id))
        assertEquals(listOf("early", "late"), targetFolderSnapshot.libraryItems.map(EpubBook::id))
        assertEquals("title_asc", targetFolderSnapshot.currentSort)
        assertEquals(listOf(RootLibraryName, "Sci-Fi"), targetFolderSnapshot.folders)
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
