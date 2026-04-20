package com.epubreader.engine.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.epubreader.engine.WavesEngine
import com.epubreader.engine.ui.ProReaderScreen
import java.io.File

/**
 * TitanIntegrationDemo
 * 
 * A live test component that verifies the full Titan V2 pipeline:
 * Native Unzip -> Native Read -> Native Layout -> Liquid Canvas Rendering.
 */
@Composable
fun TitanIntegrationDemo(
    epubPath: String,
    cacheDir: File,
    engine: WavesEngine
) {
    var isLoading by remember { mutableStateOf(true) }
    var content by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val bookCacheDir = remember(epubPath) {
        File(cacheDir, "titan_temp_book")
    }

    LaunchedEffect(epubPath) {
        try {
            // 1. Native Unzip
            val success = engine.extractEpub(epubPath, bookCacheDir.absolutePath)
            if (!success) {
                error = "Native Unzip Failed"
                isLoading = false
                return@LaunchedEffect
            }

            // 2. Simple Discovery (Find first .xhtml or .html)
            val firstChapter = bookCacheDir.walkTopDown()
                .find { it.extension == "xhtml" || it.extension == "html" }

            if (firstChapter == null) {
                error = "No readable chapters found"
                isLoading = false
                return@LaunchedEffect
            }

            // 3. Native Read
            content = engine.readEntry(firstChapter.absolutePath)
            isLoading = false
        } catch (e: Exception) {
            error = "Integration Error: ${e.message}"
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator()
            error != null -> Text("Error: $error")
            content != null -> {
                ProReaderScreen(
                    rawContent = content!!,
                    engine = engine,
                    fontSize = 20f
                )
            }
        }
    }
}
