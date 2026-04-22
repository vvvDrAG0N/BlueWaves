/**
 * AI_RELEVANT_TO: [Folder Derivation, Folder Drag Preview, Library Sorting]
 * PURPOSE: Pure helpers for derived library data shared by the app shell and library tests.
 * AI_WARNING: Keep folder ordering and root-library behavior stable.
 */
package com.epubreader.app

import com.epubreader.core.model.EpubBook
import org.json.JSONArray
import org.json.JSONObject

data class LibraryDerivedData(
    val bookGroups: Map<String, String> = emptyMap(),
    val folderSorts: Map<String, String> = emptyMap(),
    val folderOrder: List<String> = emptyList(),
    val currentSort: String = DefaultLibrarySort,
    val folders: List<String> = listOf(RootLibraryName),
    val libraryItems: List<EpubBook> = emptyList(),
    val bookFolderById: Map<String, String> = emptyMap(),
)

data class FolderDragPreviewUpdate(
    val previewFolders: List<String>,
    val dragOffset: Float,
    val didReorder: Boolean,
)

fun parseStringMap(value: String): Map<String, String> {
    return try {
        buildMap {
            val jsonObject = JSONObject(value)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, jsonObject.optString(key))
            }
        }
    } catch (_: Exception) {
        emptyMap()
    }
}

fun parseFolderOrder(value: String): List<String> {
    return try {
        JSONArray(value).let { jsonArray ->
            List(jsonArray.length()) { index -> jsonArray.getString(index) }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun buildLibraryDerivedData(
    books: List<EpubBook>,
    bookGroupsRaw: String,
    folderSortsRaw: String,
    folderOrderRaw: String,
    librarySort: String,
    selectedFolderName: String,
): LibraryDerivedData {
    val bookGroups = parseStringMap(bookGroupsRaw)
    val folderSorts = parseStringMap(folderSortsRaw)
    val folderOrder = parseFolderOrder(folderOrderRaw)
    val currentSort = resolveCurrentSort(
        folderSorts = folderSorts,
        librarySort = librarySort,
        selectedFolderName = selectedFolderName,
    )

    return LibraryDerivedData(
        bookGroups = bookGroups,
        folderSorts = folderSorts,
        folderOrder = folderOrder,
        currentSort = currentSort,
        folders = buildFolders(
            books = books,
            bookGroups = bookGroups,
            folderSorts = folderSorts,
            folderOrder = folderOrder,
        ),
        libraryItems = buildLibraryItems(
            books = books,
            bookGroups = bookGroups,
            currentSort = currentSort,
            selectedFolderName = selectedFolderName,
        ),
        bookFolderById = books.associate { book ->
            val folderName = bookGroups[book.id].orEmpty().ifBlank { RootLibraryName }
            book.id to folderName
        },
    )
}

fun resolveCurrentSort(
    folderSorts: Map<String, String>,
    librarySort: String,
    selectedFolderName: String,
): String {
    return if (selectedFolderName == RootLibraryName) {
        librarySort
    } else {
        folderSorts[selectedFolderName] ?: DefaultLibrarySort
    }
}

fun buildFolders(
    books: List<EpubBook>,
    bookGroups: Map<String, String>,
    folderSorts: Map<String, String>,
    folderOrder: List<String>,
): List<String> {
    val groups = mutableSetOf<String>()
    books.forEach { book ->
        val groupName = bookGroups[book.id].orEmpty()
        if (groupName.isNotEmpty()) {
            groups.add(groupName)
        }
    }

    groups.addAll(folderSorts.keys)

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

fun buildLibraryItems(
    books: List<EpubBook>,
    bookGroups: Map<String, String>,
    currentSort: String,
    selectedFolderName: String,
): List<EpubBook> {
    val filteredBooks = if (selectedFolderName == RootLibraryName) {
        books.filter { bookGroups[it.id].isNullOrBlank() }
    } else {
        books.filter { bookGroups[it.id] == selectedFolderName }
    }

    val sortParts = currentSort.split("_")
    val type = sortParts.getOrNull(0) ?: "added"
    val isDescending = sortParts.getOrNull(1) == "desc"

    val comparator = when (type) {
        "title" -> compareBy<EpubBook> { it.title.lowercase() }
        "author" -> compareBy<EpubBook> { it.author.lowercase() }
        "recent" -> compareBy<EpubBook> { it.lastRead }
        "added" -> compareBy<EpubBook> { it.dateAdded }
        "chapters" -> compareBy<EpubBook> { it.readingUnitCount }
        else -> compareBy<EpubBook> { it.dateAdded }
    }

    val sortedBooks = filteredBooks.sortedWith(comparator)
    return if (isDescending) sortedBooks.reversed() else sortedBooks
}

fun buildVisibleProgressTargets(
    libraryItems: List<EpubBook>,
    lastOpenedBook: EpubBook?,
    visibleGridIndices: List<Int>,
    fallbackWindowSize: Int = 12,
): List<EpubBook> {
    if (libraryItems.isEmpty() && lastOpenedBook == null) {
        return emptyList()
    }

    val bookStartIndex = if (lastOpenedBook != null) 1 else 0
    val visibleBookIndices = visibleGridIndices
        .asSequence()
        .map { gridIndex -> gridIndex - bookStartIndex }
        .filter { bookIndex -> bookIndex in libraryItems.indices }
        .distinct()
        .toList()

    val visibleBooks = if (visibleBookIndices.isEmpty()) {
        libraryItems.take(fallbackWindowSize)
    } else {
        visibleBookIndices.map(libraryItems::get)
    }

    return buildList {
        addAll(visibleBooks)
        lastOpenedBook
            ?.takeIf { candidate -> none { visibleBook -> visibleBook.id == candidate.id } }
            ?.let(::add)
    }
}

fun updateFolderDragPreview(
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
