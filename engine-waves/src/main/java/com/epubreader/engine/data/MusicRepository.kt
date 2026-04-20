package com.epubreader.engine.data

import com.epubreader.engine.WavesEngine
import com.epubreader.engine.model.MusicTrackV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * MusicRepository (V2 Lego)
 * 
 * Logic Lego for streaming music content.
 */
class MusicRepository(private val engine: WavesEngine) {
    
    private val _currentTrack = MutableStateFlow<MusicTrackV2?>(null)
    val currentTrack: StateFlow<MusicTrackV2?> = _currentTrack

    suspend fun loadTrack(trackUrl: String): Result<MusicTrackV2> = withContext(Dispatchers.IO) {
        try {
            val detail = engine.parseMusicTrack(trackUrl)
            val track = MusicTrackV2(
                id = trackUrl,
                title = "Track",
                artist = "Unknown",
                audioUrl = detail.audioUrl,
                durationMs = detail.durationMs
            )
            _currentTrack.value = track
            Result.success(track)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clear() {
        _currentTrack.value = null
    }
}
