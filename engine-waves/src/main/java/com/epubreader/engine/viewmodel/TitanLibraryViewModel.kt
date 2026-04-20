package com.epubreader.engine.viewmodel

import androidx.lifecycle.ViewModel
import com.epubreader.engine.data.TitanLibraryRepo

/**
 * TitanLibraryViewModel (V2 Lego)
 * 
 * Exposes the volumetric ocean state to the UI.
 */
class TitanLibraryViewModel : ViewModel() {
    val books = TitanLibraryRepo.books

    fun removeBook(book: com.epubreader.engine.ScraperResultV2) {
        TitanLibraryRepo.removeBook(book)
    }
}
