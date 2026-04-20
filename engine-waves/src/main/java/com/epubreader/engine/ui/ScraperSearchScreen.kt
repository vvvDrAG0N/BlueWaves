package com.epubreader.engine.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.epubreader.engine.ui.components.VolumetricBookCard
import com.epubreader.engine.viewmodel.ScraperViewModel
import com.epubreader.engine.ScraperResultV2

import com.epubreader.engine.ui.utils.rememberTitanHaptics

/**
 * ScraperSearchScreen (V2 Atomic)
 */
@Composable
fun ScraperSearchScreen(
    viewModel: ScraperViewModel,
    modifier: Modifier = Modifier
) {
    val haptics = rememberTitanHaptics()
    var query by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    // Trigger impact when results arrive
    LaunchedEffect(results) {
        if (results.isNotEmpty()) {
            haptics.impact()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // 1. Search Bar
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            placeholder = { Text("Search Webnovels...", color = Color.White.copy(alpha = 0.5f)) },
            leadingIcon = { Text("SRC", color = Color.White, modifier = Modifier.padding(start = 8.dp)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            ),
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Button(onClick = { viewModel.search(query) }) {
                        Text("Go")
                    }
                }
            }
        )

        // 2. Results Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(results) { result ->
                VolumetricBookCard(
                    title = result.title,
                    coverPath = result.coverUrl,
                    onClick = { viewModel.selectBook(result) }
                )
            }
        }
    }
}
