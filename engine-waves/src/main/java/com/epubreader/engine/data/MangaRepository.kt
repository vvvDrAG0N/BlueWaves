package com.epubreader.engine.data

import com.epubreader.engine.WavesEngine
import com.epubreader.engine.model.MangaChapterV2
import com.epubreader.engine.model.MangaPageV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * MangaRepository (V2 Lego)
 * 
 * Logic Lego for streaming manga content.
 */
class MangaRepository(private val engine: WavesEngine) {
    
    private val _currentChapter = MutableStateFlow<MangaChapterV2?>(null)
    val currentChapter: StateFlow<MangaChapterV2?> = _currentChapter

    suspend fun loadChapter(chapterUrl: String): Result<MangaChapterV2> = withContext(Dispatchers.IO) {
        try {
            val detail = engine.parseMangaChapter(chapterUrl)
            val pages = detail.pages.mapIndexed { index, url ->
                MangaPageV2(index, url)
            }
            val chapter = MangaChapterV2(
                id = chapterUrl,
                title = "Chapter", // We can refine this later to get chapter title
                url = chapterUrl,
                pages = pages
            )
            _currentChapter.value = chapter
            Result.success(chapter)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clear() {
        _currentChapter.value = null
    }
}
