package com.epubreader.engine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubreader.engine.WavesEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TitanReaderViewModel (V2 Lego)
 * 
 * Logic Lego for managing the active novel reader state.
 */
class TitanReaderViewModel(private val engine: WavesEngine) : ViewModel() {
    
    private val _content = MutableStateFlow<String>("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadChapter(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // Native Hook: engine.parseWebnovel
            val result = engine.parseWebnovel(url, "", "")
            _content.value = result ?: "Failed to load content"
            _isLoading.value = false
        }
    }

    fun navigateNext() {
        // Logic to extract next URL from content or repo
    }

    fun navigatePrev() {
        // Logic to extract prev URL from content or repo
    }
}
