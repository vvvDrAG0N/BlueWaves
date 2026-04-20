package com.epubreader.engine.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CircularProgressIndicator
import com.epubreader.engine.WavesEngine
import com.epubreader.engine.viewmodel.ScraperViewModel
import com.epubreader.engine.ui.navigation.*
import com.epubreader.engine.ui.components.*

import com.epubreader.engine.ui.utils.rememberTitanHaptics

/**
 * TitanOceanShell (V2 Root)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TitanOceanShell(
    engine: WavesEngine
) {
    val haptics = rememberTitanHaptics()
    val navController = rememberTitanNavigationController()
    val overscrollState = rememberLiquidOverscrollState(haptics)
    
    // Hoist ViewModel to shell level
    val scraperViewModel = remember { ScraperViewModel(engine) }
    val selectedBook by scraperViewModel.selectedBook.collectAsState()
    val chapterContent by scraperViewModel.chapterContent.collectAsState()
    val isLoadingContent by scraperViewModel.isLoadingContent.collectAsState()

    // Bridge: Navigate to Reader when book is selected
    LaunchedEffect(selectedBook) {
        if (selectedBook != null) {
            navController.navigateTo(TitanScreen.Reader)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(overscrollState.modifier)
    ) {
        // 1. PERSISTENT OCEAN
        LiquidOceanBackground()
        
        // 2. LIQUID NAVIGATION CONTENT
        AnimatedContent(
            targetState = navController.currentScreen,
            transitionSpec = {
                (fadeIn(animationSpec = tween(700)) + scaleIn(initialScale = 0.95f))
                    .togetherWith(fadeOut(animationSpec = tween(700)))
            },
            label = "MeltTransition"
        ) { screen ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    TitanScreen.Library -> {
                        val libraryViewModel: com.epubreader.engine.viewmodel.TitanLibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                        val libraryBooks by libraryViewModel.books.collectAsState()
                        
                        if (libraryBooks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("The Ocean is Empty", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(libraryBooks) { book ->
                                    VolumetricBookCard(
                                        title = book.title, 
                                        coverPath = book.coverUrl,
                                        onClick = {
                                            // Selection logic
                                            scraperViewModel.selectBook(book)
                                            screen = TitanScreen.Reader
                                        }
                                    )
                                }
                            }
                        }
                    }
                    TitanScreen.Reader -> {
                        if (isLoadingContent) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        } else {
                            ProReaderScreen(
                                rawContent = chapterContent ?: "No Content Found",
                                engine = engine
                            )
                        }
                    }
                    TitanScreen.Search -> {
                        ScraperSearchScreen(viewModel = scraperViewModel)
                    }
                }
            }
        }

        // 3. ATOMIC NAVBAR
        TitanNavbar(
            currentScreen = navController.currentScreen,
            onNavigate = { navController.navigateTo(it) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
