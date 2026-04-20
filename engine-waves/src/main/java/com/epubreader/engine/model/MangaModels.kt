package com.epubreader.engine.model

import androidx.compose.runtime.Immutable

/**
 * Manga Models (V2 Lego)
 * 
 * Immutable contracts for image-heavy media.
 */

@Immutable
data class MangaPageV2(
    val index: Int,
    val imageUrl: String,
    val width: Int = 0,
    val height: Int = 0
)

@Immutable
data class MangaChapterV2(
    val id: String,
    val title: String,
    val url: String,
    val pages: List<MangaPageV2> = emptyList()
)

@Immutable
data class MangaBookV2(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val description: String = "",
    val chapters: List<MangaChapterV2> = emptyList()
)
