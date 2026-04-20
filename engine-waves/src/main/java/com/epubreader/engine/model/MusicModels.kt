package com.epubreader.engine.model

import androidx.compose.runtime.Immutable

/**
 * Music Models (V2 Lego)
 * 
 * Immutable contracts for audio-heavy media.
 */

@Immutable
data class MusicTrackV2(
    val id: String,
    val title: String,
    val artist: String,
    val audioUrl: String,
    val durationMs: Long = 0,
    val albumArt: String? = null
)

@Immutable
data class MusicAlbumV2(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val tracks: List<MusicTrackV2> = emptyList()
)
