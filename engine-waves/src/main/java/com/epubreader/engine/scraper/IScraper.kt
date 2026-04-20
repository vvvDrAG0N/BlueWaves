package com.epubreader.engine.scraper

import com.epubreader.engine.model.MangaResultV2
import com.epubreader.engine.model.ScraperResultV2 // Standard Webnovel result

/**
 * IScraper (V2 Lego)
 * 
 * The Universal Bridge for all media scraping.
 */
interface IScraper<T> {
    suspend fun search(query: String): List<T>
    suspend fun parseDetail(url: String): Any
}
