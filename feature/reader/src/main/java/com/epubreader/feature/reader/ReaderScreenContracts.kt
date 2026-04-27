/**
 * AI_READ_AFTER: ReaderScreen.kt
 * AI_RELEVANT_TO: [Reader Contracts, Theme Helpers, Reader Chrome Dependency Surface]
 * PURPOSE: Shared reader UI contracts and low-context dependency maps for the split reader files.
 * AI_WARNING: Built-in theme names are persisted in DataStore and must remain stable.
 */
package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.TocItem

enum class TocSort { Ascending, Descending }

typealias ReaderTheme = com.epubreader.core.ui.ReaderTheme
typealias GlobalSettingsTransform = com.epubreader.core.ui.GlobalSettingsTransform

internal data class ReaderSettingsDraft(
    val fontSize: Int? = null,
    val lineHeight: Float? = null,
    val horizontalPadding: Int? = null,
) {
    fun applyTo(settings: GlobalSettings): GlobalSettings {
        return settings.copy(
            fontSize = fontSize ?: settings.fontSize,
            lineHeight = lineHeight ?: settings.lineHeight,
            horizontalPadding = horizontalPadding ?: settings.horizontalPadding,
        )
    }

    fun matches(settings: GlobalSettings): Boolean {
        return (fontSize == null || fontSize == settings.fontSize) &&
            (lineHeight == null || kotlin.math.abs(lineHeight - settings.lineHeight) < 0.001f) &&
            (horizontalPadding == null || horizontalPadding == settings.horizontalPadding)
    }

    companion object {
        fun from(settings: GlobalSettings): ReaderSettingsDraft {
            return ReaderSettingsDraft(
                fontSize = settings.fontSize,
                lineHeight = settings.lineHeight,
                horizontalPadding = settings.horizontalPadding,
            )
        }
    }
}

internal enum class ReaderBackAction {
    CloseToc,
    ClearTextSelection,
    HideControls,
    ExitReader,
}

internal fun resolveReaderBackAction(
    isDrawerOpen: Boolean,
    isTextSelectionSessionActive: Boolean,
    showControls: Boolean,
): ReaderBackAction {
    return when {
        isDrawerOpen -> ReaderBackAction.CloseToc
        isTextSelectionSessionActive -> ReaderBackAction.ClearTextSelection
        showControls -> ReaderBackAction.HideControls
        else -> ReaderBackAction.ExitReader
    }
}

internal fun shouldForceClearReaderSelectionSession(
    selectableTextEnabled: Boolean,
    isTextSelectionSessionActive: Boolean,
): Boolean {
    return !selectableTextEnabled && isTextSelectionSessionActive
}

internal fun shouldEnableReaderTocDrawerGestures(
    isTextSelectionSessionActive: Boolean,
): Boolean {
    return !isTextSelectionSessionActive
}

val KarlaFont
    get() = com.epubreader.core.ui.KarlaFont

fun getThemeColors(
    theme: String,
    customThemes: List<com.epubreader.core.model.CustomTheme> = emptyList(),
): ReaderTheme = com.epubreader.core.ui.getThemeColors(theme, customThemes)

@Stable
internal data class ReaderChromeState(
    val book: EpubBook,
    val settings: GlobalSettings,
    val themeColors: ReaderTheme,
    val drawerState: DrawerState,
    val listState: LazyListState,
    val tocListState: LazyListState,
    val currentChapterIndex: Int,
    val chapterElements: List<ChapterElement>,
    val chapterSections: List<ReaderChapterSection>,
    val renderedItemCount: Int,
    val isLoadingChapter: Boolean,
    val showControls: Boolean,
    val isTextSelectionSessionActive: Boolean,
    val tocSort: TocSort,
    val sortedToc: List<TocItem>,
    val verticalOverscrollState: State<Float>,
    val overscrollThreshold: Float,
    val nestedScrollConnection: NestedScrollConnection,
    val progressPercentageState: State<Float>,
    val selectionSessionEpoch: Int,
    val overlayHosts: List<ReaderOverlayHost> = emptyList(),
    val toolHosts: List<ReaderToolHost> = emptyList(),
)

@Stable
internal data class ReaderChromeCallbacks(
    val onShowControlsChange: (Boolean) -> Unit,
    val onTextSelectionActiveChange: (Int, Boolean) -> Unit,
    val onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    val onClearTextSelection: () -> Unit,
    val onToggleTocSort: () -> Unit,
    val onReleaseOverscroll: () -> Unit,
    val onSaveAndBack: () -> Unit,
    val onOpenToc: () -> Unit,
    val onCloseToc: () -> Unit,
    val onLocateCurrentChapterInToc: () -> Unit,
    val onJumpToChapter: (Int) -> Unit,
    val onSelectTocChapter: (Int) -> Unit,
    val onPreviewSettings: (GlobalSettingsTransform) -> Unit,
    val onPersistSettings: (GlobalSettingsTransform) -> Unit,
    val onNavigatePrev: () -> Unit,
    val onNavigateNext: () -> Unit,
    val onMainScrubberDragStart: () -> Unit,
    val onLookupSheetVisibilityChange: (Boolean) -> Unit = {},
    val onLookupSheetDismissed: () -> Unit = {},
)
