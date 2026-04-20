package com.epubreader.engine.data

import com.epubreader.engine.ScraperResultV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TitanLibraryRepo (V2 Lego)
 * 
 * This is the central repository for all "Volumetric" books in the Titan Ocean.
 * It handles the persistence and state of the user's collection.
 */
object TitanLibraryRepo {
    private val _books = MutableStateFlow<List<ScraperResultV2>>(emptyList())
    val books = _books.asStateFlow()

    /**
     * Adds a book to the library.
     * In a future refinement, this will also trigger a native cache operation.
     */
    fun addBook(book: ScraperResultV2) {
        val current = _books.value.toMutableList()
        if (!current.any { it.url == book.url }) {
            current.add(0, book) // Add to top
            _books.value = current
        }
    }

    /**
     * Removes a book from the library.
     */
    fun removeBook(book: ScraperResultV2) {
        val current = _books.value.toMutableList()
        current.removeAll { it.url == book.url }
        _books.value = current
    }
}
