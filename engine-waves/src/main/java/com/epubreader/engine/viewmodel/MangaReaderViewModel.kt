package com.epubreader.engine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubreader.engine.data.MangaRepository
import com.epubreader.engine.model.MangaPageV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MangaReaderViewModel (V2 Lego)
 * 
 * Logic Lego for managing the infinite scroll state of a Manga.
 */
class MangaReaderViewModel(
    private val repository: MangaRepository,
    private val imageCache: MangaImageCache
) : ViewModel() {
    
    private val _pages = MutableStateFlow<List<MangaPageV2>>(emptyList())
    val pages: StateFlow<List<MangaPageV2>> = _pages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadChapter(chapterUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.loadChapter(chapterUrl).onSuccess { chapter ->
                _pages.value = chapter.pages
                // Prefetch first 3 pages
                chapter.pages.take(3).forEach { imageCache.preloadPage(it.imageUrl) }
            }.onFailure {
                // TODO: Error state
            }
            _isLoading.value = false
        }
    }

    fun onPageReached(index: Int) {
        // Prefetch next 3 pages
        val pages = _pages.value
        for (i in (index + 1)..(index + 3)) {
            if (i < pages.size) {
                imageCache.preloadPage(pages[i].imageUrl)
            }
        }
    }
}
