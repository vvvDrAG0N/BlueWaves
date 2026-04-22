package com.epubreader.app

import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.EpubBook

internal fun resolveDisplayedFolders(
    folders: List<String>,
    dragPreviewFolders: List<String>,
    draggedFolderName: String?,
    pendingFolderOrder: List<String>?,
): List<String> {
    return when {
        draggedFolderName != null -> dragPreviewFolders
        pendingFolderOrder != null -> pendingFolderOrder
        else -> folders
    }
}

internal fun findLastOpenedBook(books: List<EpubBook>): EpubBook? {
    return books
        .filter { it.lastRead > 0 && it.sourceFormat != BookFormat.PDF }
        .maxByOrNull { it.lastRead }
}

internal fun buildLibraryProgressTargets(
    libraryItems: List<EpubBook>,
    lastOpenedBook: EpubBook?,
): List<EpubBook> {
    return buildList {
        addAll(libraryItems)
        lastOpenedBook
            ?.takeIf { candidate -> none { visibleBook -> visibleBook.id == candidate.id } }
            ?.let(::add)
    }
}

internal fun findSelectedEditableBook(
    books: List<EpubBook>,
    selectedBookIds: Set<String>,
): EpubBook? {
    return if (selectedBookIds.size != 1) {
        null
    } else {
        books.firstOrNull { it.id == selectedBookIds.first() && it.sourceFormat == BookFormat.EPUB }
    }
}
