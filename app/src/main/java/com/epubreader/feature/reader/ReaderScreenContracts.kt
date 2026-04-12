/**
 * AI_READ_AFTER: ReaderScreen.kt
 * AI_RELEVANT_TO: [Reader Contracts, Theme Helpers, Reader Chrome Dependency Surface]
 * PURPOSE: Shared reader UI contracts and low-context dependency maps for the split reader files.
 * AI_WARNING: Theme names are persisted in DataStore and must remain stable.
 */
package com.epubreader.feature.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.epubreader.R
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.TocItem

val KarlaFont = FontFamily(
    Font(R.font.karla, FontWeight.Normal)
)

enum class TocSort { Ascending, Descending }

data class ReaderTheme(val background: Color, val foreground: Color)

fun getThemeColors(theme: String): ReaderTheme {
    return when (theme) {
        "oled" -> ReaderTheme(Color.Black, Color.White)
        "dark" -> ReaderTheme(Color(0xFF121212), Color.White)
        "sepia" -> ReaderTheme(Color(0xFFF4ECD8), Color(0xFF5B4636))
        else -> ReaderTheme(Color.White, Color.Black)
    }
}

internal data class ReaderChromeState(
    val book: EpubBook,
    val settings: GlobalSettings,
    val themeColors: ReaderTheme,
    val drawerState: DrawerState,
    val listState: LazyListState,
    val tocListState: LazyListState,
    val currentChapterIndex: Int,
    val chapterElements: List<ChapterElement>,
    val isLoadingChapter: Boolean,
    val showControls: Boolean,
    val tocSort: TocSort,
    val sortedToc: List<TocItem>,
    val verticalOverscroll: Float,
    val overscrollThreshold: Float,
    val nestedScrollConnection: NestedScrollConnection
)

internal data class ReaderChromeCallbacks(
    val onShowControlsChange: (Boolean) -> Unit,
    val onToggleTocSort: () -> Unit,
    val onReleaseOverscroll: () -> Unit,
    val onSaveAndBack: () -> Unit,
    val onOpenToc: () -> Unit,
    val onCloseToc: () -> Unit,
    val onLocateCurrentChapterInToc: () -> Unit,
    val onJumpToChapter: (Int) -> Unit,
    val onSelectTocChapter: (Int) -> Unit,
    val onUpdateSettings: (GlobalSettings) -> Unit,
    val onNavigatePrev: () -> Unit,
    val onNavigateNext: () -> Unit,
    val onMainScrubberDragStart: () -> Unit
)
