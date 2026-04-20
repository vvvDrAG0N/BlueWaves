package com.epubreader.engine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubreader.engine.MangaResultV2
import com.epubreader.engine.WavesEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MangaViewModel (V2 Lego)
 * 
 * Logic for Manga search and image sequence fetching.
 */
class MangaViewModel : ViewModel() {
    private val engine = WavesEngine()
    
    private val _results = MutableStateFlow<List<MangaResultV2>>(emptyList())
    val results = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isSearching.value = true
            try {
                val searchResults = engine.searchManga(query)
                _results.value = searchResults.toList()
            } catch (e: Exception) {
                // Log error
            } finally {
                _isSearching.value = false
            }
        }
    }
}
