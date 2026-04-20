package com.epubreader.engine.scraper

import com.epubreader.engine.WavesEngine
import com.epubreader.engine.model.MangaResultV2
import com.epubreader.engine.model.ScraperResultV2

/**
 * TitanScraperFactory (V2 Lego)
 * 
 * Factory for creating media-specific scrapers.
 */
enum class MediaType {
    WEBNOVEL, MANGA, ANIME, MUSIC
}

class TitanScraperFactory(private val engine: WavesEngine) {

    fun getScraper(type: MediaType): IScraper<out Any> {
        return when (type) {
            MediaType.WEBNOVEL -> WebnovelScraper(engine)
            MediaType.MANGA -> MangaScraper(engine)
            // Add Anime and Music scrapers as they are built
            else -> throw IllegalArgumentException("Unsupported Media Type")
        }
    }
}

class WebnovelScraper(private val engine: WavesEngine) : IScraper<ScraperResultV2> {
    override suspend fun search(query: String): List<ScraperResultV2> {
        return engine.searchVolumetric(query).toList()
    }
    override suspend fun parseDetail(url: String): Any {
        return engine.parseWebnovel(url, "", "") ?: ""
    }
}

class MangaScraper(private val engine: WavesEngine) : IScraper<MangaResultV2> {
    override suspend fun search(query: String): List<MangaResultV2> {
        // Need to map Array<MangaResultV2> from engine
        return emptyList() 
    }
    override suspend fun parseDetail(url: String): Any {
        return engine.parseMangaChapter(url)
    }
}
