package com.epubreader.feature.reader.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.EpubBook
import com.epubreader.feature.reader.ReaderTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    book: EpubBook,
    currentChapterIndex: Int,
    themeColors: ReaderTheme,
    onSaveAndBack: () -> Unit,
    onOpenToc: () -> Unit,
) {
    TopAppBar(
        modifier = Modifier.testTag("reader_top_bar"),
        title = {
            Column {
                Text(book.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                if (currentChapterIndex != -1) {
                    Text(
                        "${book.navigationUnitLabel} ${currentChapterIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onSaveAndBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onOpenToc) {
                Icon(Icons.Default.Menu, contentDescription = "TOC")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = themeColors.background.copy(alpha = 0.95f),
            titleContentColor = themeColors.foreground,
            navigationIconContentColor = themeColors.foreground,
            actionIconContentColor = themeColors.foreground,
        ),
    )
}

@Composable
internal fun BoxScope.ReaderScrollToTopFab(
    listState: androidx.compose.foundation.lazy.LazyListState,
    showControls: Boolean,
    isSettingEnabled: Boolean,
    themeColors: ReaderTheme,
) {
    val scope = rememberCoroutineScope()
    var isScrollingUp by remember { mutableStateOf(false) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    var lastScrollIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset

        if (currentIndex != lastScrollIndex || currentOffset != lastScrollOffset) {
            isScrollingUp = if (currentIndex < lastScrollIndex) {
                true
            } else if (currentIndex > lastScrollIndex) {
                false
            } else {
                currentOffset < lastScrollOffset
            }
            lastScrollIndex = currentIndex
            lastScrollOffset = currentOffset
        }
    }

    val showScrollToTop by remember {
        derivedStateOf { isScrollingUp && listState.firstVisibleItemIndex > 0 }
    }

    AnimatedVisibility(
        visible = showScrollToTop && !showControls && isSettingEnabled,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = 32.dp),
    ) {
        FloatingActionButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            containerColor = themeColors.foreground,
            contentColor = themeColors.background,
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
        }
    }
}
