package com.epubreader.feature.library.internal

import com.epubreader.core.model.EpubBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFeatureDataTest {

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
    fun buildLibraryProgressTargets_appendsLastOpenedWhenHidden() {
        val libraryItems = listOf(
            book(id = "alpha", title = "Alpha"),
            book(id = "beta", title = "Beta"),
        )
        val recentBook = book(id = "recent", title = "Recent")

        val targets = buildLibraryProgressTargets(
            libraryItems = libraryItems,
            lastOpenedBook = recentBook,
        )

        assertEquals(listOf("alpha", "beta", "recent"), targets.map(EpubBook::id))
    }

    @Test
    fun updateFolderDragPreview_reordersWhenThresholdIsCrossed() {
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
    fun resolveLibraryBackAction_prioritizesFolderSelectionOverDrawer() {
        val action = resolveLibraryBackAction(
            isDrawerOpen = true,
            isFolderSelectionMode = true,
            isBookSelectionMode = true,
        )

        assertEquals(LibraryBackAction.ClearFolderSelection, action)
    }

    @Test
    fun resolveLibraryBackAction_returnsBookSelectionWhenOnlySelectionModeIsActive() {
        val action = resolveLibraryBackAction(
            isDrawerOpen = false,
            isFolderSelectionMode = false,
            isBookSelectionMode = true,
        )

        assertEquals(LibraryBackAction.ClearBookSelection, action)
        assertFalse(
            resolveLibraryBackAction(
                isDrawerOpen = false,
                isFolderSelectionMode = false,
                isBookSelectionMode = false,
            ) != null,
        )
    }

    @Test
    fun parseFolderOrder_returnsEmptyListForMalformedJson() {
        assertTrue(parseFolderOrder("{not valid json").isEmpty())
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
