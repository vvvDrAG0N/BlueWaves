package com.epubreader.engine.model

import androidx.compose.runtime.Immutable

/**
 * Anime Models (V2 Lego)
 * 
 * Immutable contracts for video-heavy media.
 */

@Immutable
data class AnimeEpisodeV2(
    val index: Int,
    val title: String,
    val videoUrl: String, // HLS (.m3u8) or Direct Stream
    val thumbnail: String? = null
)

@Immutable
data class AnimeBookV2(
    val id: String,
    val title: String,
    val author: String = "",
    val coverUrl: String,
    val description: String = "",
    val episodes: List<AnimeEpisodeV2> = emptyList()
)
