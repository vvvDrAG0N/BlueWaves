package com.epubreader.engine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubreader.engine.WavesEngine
import com.epubreader.engine.ScraperResultV2
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * ScraperViewModel (V2 Hybrid)
 * 
 * Orchestrates the relationship between the Native Titan Scraper
 * and the Kotlin Networking layer.
 */
class ScraperViewModel(private val engine: WavesEngine) : ViewModel() {
    private val _results = MutableStateFlow<List<ScraperResultV2>>(emptyList())
    val results = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _selectedBook = MutableStateFlow<ScraperResultV2?>(null)
    val selectedBook = _selectedBook.asStateFlow()

    private val _chapterContent = MutableStateFlow<String?>(null)
    val chapterContent = _chapterContent.asStateFlow()

    private val _isLoadingContent = MutableStateFlow(false)
    val isLoadingContent = _isLoadingContent.asStateFlow()

    private val client = HttpClient(CIO)

    init {
        engine.contentFetcher = { url -> fetchHtml(url) }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSearching.value = true
            try {
                val nativeResults = engine.searchVolumetric(query)
                _results.value = nativeResults.toList()
            } catch (e: Exception) {
                // Log error
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun selectBook(book: ScraperResultV2) {
        _selectedBook.value = book
        _chapterContent.value = null // Reset
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoadingContent.value = true
            try {
                // For the "Bridge", we fetch the HTML from the result's URL
                val html = fetchHtml(book.url)
                
                // PARSE the HTML using the native engine
                // Royal Road selectors as default for now
                val parsed = engine.parseWebnovel(
                    html = html,
                    titleSelector = "h1",
                    contentSelector = ".chapter-inner"
                )
                
                if (parsed != null) {
                    // Assemble: Add to Library Repo
                    com.epubreader.engine.data.TitanLibraryRepo.addBook(book)
                }
                
                _chapterContent.value = parsed ?: html // Fallback to raw if parsing fails
            } catch (e: Exception) {
                // Log error
            } finally {
                _isLoadingContent.value = false
            }
        }
    }

    fun clearSelection() {
        _selectedBook.value = null
        _chapterContent.value = null
    }

    /**
     * This method is called from Native C++ via JNI.
     */
    fun fetchHtml(url: String): String = runBlocking {
        try {
            val response = client.get(url)
            response.bodyAsText()
        } catch (e: Exception) {
            ""
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}
