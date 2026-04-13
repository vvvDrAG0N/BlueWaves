/**
 * AI_READ_AFTER: AppNavigation.kt
 * AI_RELEVANT_TO: [Folder Derivation, Folder Drag Preview, Library Sorting]
 * PURPOSE: Package-local pure helpers for derived app-shell library data.
 * AI_WARNING: Keep folder ordering and root-library behavior stable.
 */
package com.epubreader.app

import com.epubreader.core.model.EpubBook
import org.json.JSONArray
import org.json.JSONObject

internal data class FolderDragPreviewUpdate(
    val previewFolders: List<String>,
    val dragOffset: Float,
    val didReorder: Boolean,
)

internal fun parseJsonObject(value: String): JSONObject {
    return try {
        JSONObject(value)
    } catch (_: Exception) {
        JSONObject()
    }
}

internal fun parseFolderOrder(value: String): List<String> {
    return try {
        JSONArray(value).let { jsonArray ->
            List(jsonArray.length()) { index -> jsonArray.getString(index) }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

// Folder ordering logic:
// 1. Start with RootLibraryName (always first).
// 2. Add folders from the manual 'folderOrder' list if they still contain books or sort settings.
// 3. Append any newly discovered folders (from new book groups) alphabetically.
// This ensures that user-defined order is preserved while automatically handling new folders.
internal fun buildFolders(
    books: List<EpubBook>,
    bookGroups: JSONObject,
    folderSorts: JSONObject,
    folderOrder: List<String>,
): List<String> {
    val groups = mutableSetOf<String>()
    books.forEach { book ->
        val groupName = bookGroups.optString(book.id, "")
        if (groupName.isNotEmpty()) {
            groups.add(groupName)
        }
    }

    val sortKeys = folderSorts.keys()
    while (sortKeys.hasNext()) {
        groups.add(sortKeys.next())
    }

    val allFolders = (groups + RootLibraryName).toMutableList()
    val sortedFolders = mutableListOf(RootLibraryName)
    allFolders.remove(RootLibraryName)

    folderOrder.forEach { folderName ->
        if (allFolders.contains(folderName)) {
            sortedFolders.add(folderName)
            allFolders.remove(folderName)
        }
    }

    sortedFolders.addAll(allFolders.sorted())
    return sortedFolders
}

// Sorting and folder filtering are centralized here so the library UI can stay presentational.
internal fun buildLibraryItems(
    books: List<EpubBook>,
    bookGroups: JSONObject,
    currentSort: String,
    selectedFolderName: String,
): List<EpubBook> {
    val filteredBooks = if (selectedFolderName == RootLibraryName) {
        books.filter { bookGroups.optString(it.id, "").isEmpty() }
    } else {
        books.filter { bookGroups.optString(it.id, "") == selectedFolderName }
    }

    val sortParts = currentSort.split("_")
    val type = sortParts.getOrNull(0) ?: "added"
    val isDescending = sortParts.getOrNull(1) == "desc"

    val comparator = when (type) {
        "title" -> compareBy<EpubBook> { it.title.lowercase() }
        "author" -> compareBy<EpubBook> { it.author.lowercase() }
        "recent" -> compareBy<EpubBook> { it.lastRead }
        "added" -> compareBy<EpubBook> { it.dateAdded }
        "chapters" -> compareBy<EpubBook> { it.spineHrefs.size }
        else -> compareBy<EpubBook> { it.dateAdded }
    }

    val sortedBooks = filteredBooks.sortedWith(comparator)
    return if (isDescending) sortedBooks.reversed() else sortedBooks
}

internal fun updateFolderDragPreview(
    dragPreviewFolders: List<String>,
    draggedFolderName: String?,
    dragOffset: Float,
    dragAmountY: Float,
    itemHeightPx: Float,
): FolderDragPreviewUpdate {
    var nextDragOffset = dragOffset + dragAmountY
    val currentIndex = dragPreviewFolders.indexOf(draggedFolderName)
    if (currentIndex == -1) {
        return FolderDragPreviewUpdate(
            previewFolders = dragPreviewFolders,
            dragOffset = nextDragOffset,
            didReorder = false,
        )
    }

    if (nextDragOffset > itemHeightPx * 0.6f && currentIndex < dragPreviewFolders.lastIndex) {
        val updated = dragPreviewFolders.toMutableList()
        val item = updated.removeAt(currentIndex)
        updated.add(currentIndex + 1, item)
        nextDragOffset -= itemHeightPx
        return FolderDragPreviewUpdate(
            previewFolders = updated,
            dragOffset = nextDragOffset,
            didReorder = true,
        )
    }

    if (nextDragOffset < -itemHeightPx * 0.6f && currentIndex > 1) {
        val updated = dragPreviewFolders.toMutableList()
        val item = updated.removeAt(currentIndex)
        updated.add(currentIndex - 1, item)
        nextDragOffset += itemHeightPx
        return FolderDragPreviewUpdate(
            previewFolders = updated,
            dragOffset = nextDragOffset,
            didReorder = true,
        )
    }

    return FolderDragPreviewUpdate(
        previewFolders = dragPreviewFolders,
        dragOffset = nextDragOffset,
        didReorder = false,
    )
}
