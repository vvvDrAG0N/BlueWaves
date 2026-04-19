/**
 * AI_READ_AFTER: ReaderScreen.kt
 * AI_RELEVANT_TO: [Reader Drawer, Reader Chrome, Top/Bottom Bars, Overscroll UI]
 * PURPOSE: Presentational shell for the reader once lifecycle state and callbacks are already derived.
 * AI_WARNING: Do not move restoration or save-progress effects here.
 */
package com.epubreader.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreenChrome(
    state: ReaderChromeState,
    callbacks: ReaderChromeCallbacks
) {
    ModalNavigationDrawer(
        drawerState = state.drawerState,
        drawerContent = {
            ReaderTocDrawerContent(
                state = state,
                callbacks = callbacks
            )
        },
        content = {
            ReaderContentSurface(
                state = state,
                callbacks = callbacks
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTocDrawerContent(
    state: ReaderChromeState,
    callbacks: ReaderChromeCallbacks
) {
    ModalDrawerSheet(
        drawerContainerColor = state.themeColors.background,
        drawerContentColor = state.themeColors.foreground
    ) {
        var showChapterInputInToc by remember { mutableStateOf(false) }
        var inputChapter by remember { mutableStateOf("") }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val totalChapters = state.book.spineHrefs.size

        /**
         * Go to Chapter: Allows direct numeric entry to jump to a specific spine index.
         * [AI_NOTE] Uses FocusRequester to ensure the keyboard opens immediately when the 
         * input field is toggled. Interaction is kept local to the drawer to prevent 
         * unnecessary recompositions of the main ReaderScreen.
         */
        val performJump = {
            val targetChapter = inputChapter.toIntOrNull()?.coerceIn(1, totalChapters) ?: (state.currentChapterIndex + 1)
            val targetIndex = (targetChapter - 1).coerceIn(0, totalChapters - 1)
            keyboardController?.hide()
            callbacks.onJumpToChapter(targetIndex)
            inputChapter = ""
            showChapterInputInToc = false
            callbacks.onCloseToc()
        }

        LaunchedEffect(showChapterInputInToc, state.drawerState.currentValue) {
            if (showChapterInputInToc && state.drawerState.isOpen) {
                focusRequester.requestFocus()
            } else {
                keyboardController?.hide()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = callbacks.onToggleTocSort) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Toggle Sort",
                    modifier = if (state.tocSort == TocSort.Ascending) {
                        Modifier.graphicsLayer { rotationX = 180f }
                    } else {
                        Modifier
                    }
                )
            }
            IconButton(onClick = callbacks.onLocateCurrentChapterInToc) {
                Icon(Icons.Default.MyLocation, contentDescription = "Locate Current")
            }

            if (showChapterInputInToc) {
                BasicTextField(
                    value = inputChapter,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() } && it.length <= 4) {
                            inputChapter = it
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .padding(horizontal = 4.dp)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { performJump() }),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = inputChapter,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                            interactionSource = remember { MutableInteractionSource() },
                            placeholder = {
                                Text(
                                    "1-$totalChapters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = state.themeColors.foreground.copy(alpha = 0.5f)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = true,
                                    isError = false,
                                    interactionSource = remember { MutableInteractionSource() },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedBorderColor = state.themeColors.foreground.copy(alpha = 0.5f),
                                        unfocusedBorderColor = state.themeColors.foreground.copy(alpha = 0.1f),
                                        focusedTextColor = state.themeColors.foreground,
                                        unfocusedTextColor = state.themeColors.foreground,
                                        focusedPlaceholderColor = state.themeColors.foreground.copy(alpha = 0.5f),
                                        unfocusedPlaceholderColor = state.themeColors.foreground.copy(alpha = 0.5f)
                                    ),
                                    shape = MaterialTheme.shapes.small
                                )
                            }
                        )
                    }
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            IconButton(onClick = { showChapterInputInToc = !showChapterInputInToc }) {
                Icon(
                    imageVector = if (showChapterInputInToc) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Go to Chapter",
                    tint = if (showChapterInputInToc) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
            }
        }

        HorizontalDivider()
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(state = state.tocListState, modifier = Modifier.fillMaxSize()) {
                items(state.sortedToc, key = { it.href }) { item ->
                    val isSelected = remember(state.currentChapterIndex) {
                        state.currentChapterIndex != -1 &&
                            state.currentChapterIndex < state.book.spineHrefs.size &&
                            state.book.spineHrefs[state.currentChapterIndex] == item.href.substringBefore("#")
                    }
                    NavigationDrawerItem(
                        label = {
                            Text(
                                item.title,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        selected = isSelected,
                        shape = RectangleShape,
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = state.themeColors.foreground.copy(alpha = 0.1f),
                            unselectedTextColor = state.themeColors.foreground,
                            selectedTextColor = state.themeColors.foreground,
                            unselectedIconColor = state.themeColors.foreground,
                            selectedIconColor = state.themeColors.foreground
                        ),
                        onClick = {
                            val index = state.book.spineHrefs.indexOf(item.href.substringBefore("#"))
                            if (index != -1) {
                                callbacks.onSelectTocChapter(index)
                            }
                        }
                    )
                }
            }
            VerticalScrubber(
                listState = state.tocListState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 16.dp, bottom = 16.dp)
                    .width(16.dp),
                color = MaterialTheme.colorScheme.primary,
                isTOC = true
            )
        }
    }
}

@Composable
private fun ReaderContentSurface(
    state: ReaderChromeState,
    callbacks: ReaderChromeCallbacks
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(state.themeColors.background)
            .nestedScroll(state.nestedScrollConnection)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release) {
                            callbacks.onReleaseOverscroll()
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("reader_controls_overlay")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (state.isTextSelectionSessionActive) {
                        callbacks.onClearTextSelection()
                    } else {
                        callbacks.onShowControlsChange(!state.showControls)
                    }
                }
        ) {
            ReaderChapterContent(
                settings = state.settings,
                themeColors = state.themeColors,
                listState = state.listState,
                chapterElements = state.chapterElements,
                isLoadingChapter = state.isLoadingChapter,
                currentChapterIndex = state.currentChapterIndex,
                selectionResetToken = state.selectionResetToken,
                onSelectionActiveChange = callbacks.onTextSelectionActiveChange
            )
        }

        if (state.settings.showScrubber && !state.isLoadingChapter && state.currentChapterIndex != -1) {
            VerticalScrubber(
                listState = state.listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 80.dp, bottom = 80.dp)
                    .width(20.dp),
                color = state.themeColors.foreground,
                onDragStart = callbacks.onMainScrubberDragStart
            )
        }

        if (state.verticalOverscroll > 0) {
            OverscrollIndicator(
                text = if (state.verticalOverscroll >= state.overscrollThreshold) {
                    "Release for previous chapter"
                } else {
                    "Pull for previous chapter"
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp),
                color = state.themeColors.foreground
            )
        } else if (state.verticalOverscroll < 0) {
            OverscrollIndicator(
                text = if (abs(state.verticalOverscroll) >= state.overscrollThreshold) {
                    "Release for next chapter"
                } else {
                    "Push for next chapter"
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp),
                color = state.themeColors.foreground
            )
        }

        AnimatedVisibility(
            visible = state.showControls && !state.isTextSelectionSessionActive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(400, delayMillis = 100)) + fadeOut(animationSpec = tween(400, delayMillis = 50)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                book = state.book,
                currentChapterIndex = state.currentChapterIndex,
                themeColors = state.themeColors,
                onSaveAndBack = callbacks.onSaveAndBack,
                onOpenToc = callbacks.onOpenToc
            )
        }

        AnimatedVisibility(
            visible = state.showControls && !state.isTextSelectionSessionActive,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400, delayMillis = 100)) + fadeOut(animationSpec = tween(400, delayMillis = 50)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderControls(
                settings = state.settings,
                onSettingsChange = callbacks.onUpdateSettings,
                themeColors = state.themeColors,
                onNavigatePrev = callbacks.onNavigatePrev,
                onNavigateNext = callbacks.onNavigateNext,
                listState = state.listState,
                itemCount = state.chapterElements.size,
                currentChapterIndex = state.currentChapterIndex,
                totalChapters = state.book.spineHrefs.size,
                sectionLabel = state.book.navigationUnitLabel,
                progressPercentage = state.progressPercentage ?: 0f,
            )
        }

        ReaderScrollToTopFab(
            listState = state.listState,
            showControls = state.showControls,
            isSettingEnabled = state.settings.showScrollToTop
        )

        ReaderStatusOverlay(
            uiState = state.settings.readerStatusUi,
            isVisible = !state.showControls && !state.settings.showSystemBar,
            themeColors = state.themeColors,
            chapterIndex = state.currentChapterIndex + 1,
            maxChapters = state.book.spineHrefs.size,
            progressPercentage = state.progressPercentage,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    book: com.epubreader.core.model.EpubBook,
    currentChapterIndex: Int,
    themeColors: ReaderTheme,
    onSaveAndBack: () -> Unit,
    onOpenToc: () -> Unit
) {
    TopAppBar(
        modifier = Modifier.testTag("reader_top_bar"),
        title = {
            Column {
                Text(book.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                if (currentChapterIndex != -1) {
                    Text("${book.navigationUnitLabel} ${currentChapterIndex + 1}", style = MaterialTheme.typography.labelSmall)
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
            titleContentColor = themeColors.foreground
        )
    )
}

@Composable
private fun BoxScope.ReaderScrollToTopFab(
    listState: LazyListState,
    showControls: Boolean,
    isSettingEnabled: Boolean,
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
            .padding(end = 16.dp, bottom = 32.dp)
    ) {
        FloatingActionButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
        }
    }
}
@Composable
internal fun ReaderStatusOverlay(
    uiState: com.epubreader.core.model.ReaderStatusUiState,
    isVisible: Boolean,
    themeColors: ReaderTheme,
    chapterIndex: Int,
    maxChapters: Int,
    progressPercentage: Float?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var timeText by remember { mutableStateOf("") }
    var batteryText by remember { mutableStateOf("") }

    if (uiState.showClock) {
        LaunchedEffect(Unit) {
            while (true) {
                val now = java.util.Calendar.getInstance()
                // Explicitly use 12-hour format with AM/PM
                timeText = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(now.time)
                kotlinx.coroutines.delay(1000 * 30) // Update every 30 seconds
            }
        }
    }

    if (uiState.showBattery) {
        val batteryStatus = remember(context) {
            context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }
        val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        batteryText = if (level != -1 && scale != -1) {
            "${(level * 100 / scale.toFloat()).toInt()}%"
        } else ""
    }

    val batteryLevel = remember(context) {
        val batteryStatus = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else null
    }

    val chapterText = remember(uiState.showChapterNumber, chapterIndex) {
        if (uiState.showChapterNumber) "CH $chapterIndex" else ""
    }

    AnimatedVisibility(
        visible = uiState.isEnabled && isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Chapter Block
                if (chapterText.isNotEmpty() || (uiState.showChapterProgress && progressPercentage != null)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        if (chapterText.isNotEmpty()) {
                            Text(
                                text = chapterText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeColors.foreground.copy(alpha = 0.5f)
                                )
                            )
                        }
                        if (uiState.showChapterProgress && progressPercentage != null) {
                            Text(
                                text = "${(progressPercentage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeColors.foreground.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                // 2. Spacer to push Time and Battery to the right
                Spacer(modifier = Modifier.weight(1f))

                // 3. Time
                if (uiState.showClock) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeColors.foreground.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                // 4. Battery
                if (uiState.showBattery && batteryText.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BatteryFull,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = themeColors.foreground.copy(alpha = 0.5f)
                        )
                        Text(
                            text = batteryText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = themeColors.foreground.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}
