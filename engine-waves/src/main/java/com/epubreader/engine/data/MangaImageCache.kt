package com.epubreader.engine.data

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * MangaImageCache (V2 Lego)
 * 
 * Logic Lego for high-speed image decoding and memory management.
 */
class MangaImageCache(private val context: Context) {
    
    val imageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25) // Use 25% of app memory for manga pages
                .build()
        }
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .build()

    fun preloadPage(url: String) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
        imageLoader.enqueue(request)
    }

    fun clear() {
        imageLoader.memoryCache?.clear()
    }
}
