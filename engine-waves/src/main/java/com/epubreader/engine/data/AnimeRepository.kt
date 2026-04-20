package com.epubreader.engine.data

import com.epubreader.engine.WavesEngine
import com.epubreader.engine.model.AnimeEpisodeV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * AnimeRepository (V2 Lego)
 * 
 * Logic Lego for streaming anime content.
 */
class AnimeRepository(private val engine: WavesEngine) {
    
    private val _currentEpisode = MutableStateFlow<AnimeEpisodeV2?>(null)
    val currentEpisode: StateFlow<AnimeEpisodeV2?> = _currentEpisode

    suspend fun loadEpisode(episodeUrl: String): Result<AnimeEpisodeV2> = withContext(Dispatchers.IO) {
        try {
            val detail = engine.parseAnimeEpisode(episodeUrl)
            val episode = AnimeEpisodeV2(
                index = 0,
                title = "Episode",
                videoUrl = detail.videoUrl
            )
            _currentEpisode.value = episode
            Result.success(episode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clear() {
        _currentEpisode.value = null
    }
}
