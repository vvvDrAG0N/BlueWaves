/**
 * AI_READ_AFTER: ReaderScreen.kt
 * AI_RELEVANT_TO: [Reader Drawer, Reader Chrome, Top/Bottom Bars, Overscroll UI]
 * PURPOSE: Presentational shell for the reader once lifecycle state and callbacks are already derived.
 * AI_WARNING: Do not move restoration or save-progress effects here.
 */
package com.epubreader.feature.reader.internal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.epubreader.feature.reader.readerTapGesture
import com.epubreader.feature.reader.ReaderChapterContent
import com.epubreader.feature.reader.ReaderChromeCallbacks
import com.epubreader.feature.reader.ReaderChromeState
import com.epubreader.feature.reader.ReaderOverlayRenderContext
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.ReaderToolRenderContext
import com.epubreader.feature.reader.shouldEnableReaderTocDrawerGestures
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreenChrome(
    state: ReaderChromeState,
    callbacks: ReaderChromeCallbacks,
) {
    ModalNavigationDrawer(
        drawerState = state.drawerState,
        gesturesEnabled = shouldEnableReaderTocDrawerGestures(state.isTextSelectionSessionActive),
        drawerContent = {
            ReaderTocDrawerContent(
                state = state,
                callbacks = callbacks,
            )
        },
        content = {
            ReaderContentSurface(
                state = state,
                callbacks = callbacks,
            )
        },
    )
}

@Composable
private fun ReaderContentSurface(
    state: ReaderChromeState,
    callbacks: ReaderChromeCallbacks,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader_content_surface")
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
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("reader_controls_overlay")
                .then(
                    if (!state.isTextSelectionSessionActive) {
                        Modifier.readerTapGesture(
                            onTap = { callbacks.onShowControlsChange(!state.showControls) },
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reader_chapter_content"),
            ) {
                ReaderChapterContent(
                    settings = state.settings,
                    themeColors = state.themeColors,
                    listState = state.listState,
                    chapterElements = state.chapterElements,
                    chapterSections = state.chapterSections,
                    isLoadingChapter = state.isLoadingChapter,
                    currentChapterIndex = state.currentChapterIndex,
                    selectionSessionEpoch = state.selectionSessionEpoch,
                    onSelectionActiveChange = callbacks.onTextSelectionActiveChange,
                    onSelectionHandleDragChange = callbacks.onSelectionHandleDragChange,
                    onLookupSheetVisibilityChange = callbacks.onLookupSheetVisibilityChange,
                    onLookupSheetDismissed = callbacks.onLookupSheetDismissed,
                )
            }
        }

        if (state.settings.showScrubber && !state.isLoadingChapter && state.currentChapterIndex != -1) {
            VerticalScrubber(
                listState = state.listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 80.dp, bottom = 80.dp)
                    .width(20.dp),
                color = state.themeColors.foreground,
                onDragStart = callbacks.onMainScrubberDragStart,
            )
        }

        ReaderOverscrollOverlay(
            verticalOverscrollState = state.verticalOverscrollState,
            overscrollThreshold = state.overscrollThreshold,
            themeColors = state.themeColors,
        )

        AnimatedVisibility(
            visible = state.showControls && !state.isTextSelectionSessionActive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = spring(stiffness = Spring.StiffnessLow),
            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                book = state.book,
                currentChapterIndex = state.currentChapterIndex,
                themeColors = state.themeColors,
                onSaveAndBack = callbacks.onSaveAndBack,
                onOpenToc = callbacks.onOpenToc,
            )
        }

        AnimatedVisibility(
            visible = state.showControls && !state.isTextSelectionSessionActive,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessLow),
            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderControls(
                book = state.book,
                settings = state.settings,
                onSettingsChange = callbacks.onPersistSettings,
                onPreviewSettingsChange = callbacks.onPreviewSettings,
                onPersistSettingsChange = callbacks.onPersistSettings,
                themeColors = state.themeColors,
                onNavigatePrev = callbacks.onNavigatePrev,
                onNavigateNext = callbacks.onNavigateNext,
                listState = state.listState,
                itemCount = state.renderedItemCount,
                currentChapterIndex = state.currentChapterIndex,
                totalChapters = state.book.spineHrefs.size,
                sectionLabel = state.book.navigationUnitLabel,
                progressPercentageState = state.progressPercentageState,
                onDismiss = { callbacks.onShowControlsChange(false) },
                isVisible = state.showControls,
                toolHosts = state.toolHosts,
            )
        }

        ReaderScrollToTopFab(
            listState = state.listState,
            showControls = state.showControls,
            isSettingEnabled = state.settings.showScrollToTop,
            themeColors = state.themeColors,
        )

        ReaderStatusOverlay(
            uiState = state.settings.readerStatusUi,
            isVisible = !state.showControls && !state.settings.showSystemBar,
            themeColors = state.themeColors,
            chapterIndex = state.currentChapterIndex + 1,
            maxChapters = state.book.spineHrefs.size,
            progressPercentageState = state.progressPercentageState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val overlayContext = ReaderOverlayRenderContext(
            book = state.book,
            settings = state.settings,
            themeColors = state.themeColors,
            showControls = state.showControls,
            currentChapterIndex = state.currentChapterIndex,
            progressPercentageState = state.progressPercentageState,
        )
        state.overlayHosts.forEach { overlayHost ->
            with(overlayHost) {
                Render(overlayContext)
            }
        }
    }
}

@Composable
private fun BoxScope.ReaderOverscrollOverlay(
    verticalOverscrollState: State<Float>,
    overscrollThreshold: Float,
    themeColors: ReaderTheme,
) {
    val verticalOverscroll = verticalOverscrollState.value

    if (verticalOverscroll > 0) {
        OverscrollIndicator(
            text = if (verticalOverscroll >= overscrollThreshold) {
                "Release for previous chapter"
            } else {
                "Pull for previous chapter"
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp),
            themeColors = themeColors,
        )
    } else if (verticalOverscroll < 0) {
        OverscrollIndicator(
            text = if (abs(verticalOverscroll) >= overscrollThreshold) {
                "Release for next chapter"
            } else {
                "Push for next chapter"
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            themeColors = themeColors,
        )
    }
}
