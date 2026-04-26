package com.epubreader.feature.reader

import android.content.ClipboardManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings

internal typealias ReaderComposeRule =
    AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

internal fun ReaderComposeRule.setReaderSelectionContent(
    settings: GlobalSettings = GlobalSettings(selectableText = true),
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    chapterElements: List<ChapterElement> = defaultReaderSelectionChapterElements(),
) {
    runOnUiThread {
        activity.setContent {
            MaterialTheme {
                ReaderSelectionTestSurface(
                    settings = settings,
                    onSelectionActiveChange = onSelectionActiveChange,
                    chapterElements = chapterElements,
                )
            }
        }
    }
}

internal fun ReaderComposeRule.activateSelection() {
    waitForIdle()
    onNodeWithTag("reader_compose_text_section", useUnmergedTree = true).performTouchInput {
        longClick(Offset(60f, 32f))
    }
    waitUntil(5_000) {
        onAllNodesWithTag("text_selection_action_bar", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ReaderComposeRule.requireClipboardManager(): ClipboardManager {
    return activity.getSystemService(ClipboardManager::class.java)
        ?: error("ClipboardManager was not available")
}

@Composable
internal fun ReaderSelectionTestSurface(
    settings: GlobalSettings,
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    chapterElements: List<ChapterElement> = defaultReaderSelectionChapterElements(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("selection_surface"),
    ) {
        ReaderChapterContent(
            settings = settings,
            themeColors = getThemeColors("light"),
            listState = rememberLazyListState(),
            chapterElements = chapterElements,
            isLoadingChapter = false,
            currentChapterIndex = 0,
            onSelectionActiveChange = onSelectionActiveChange,
        )
    }
}

private fun defaultReaderSelectionChapterElements(): List<ChapterElement> {
    return listOf(
        ChapterElement.Text("Scholarship", id = "p1"),
        ChapterElement.Text("Reading keeps the selection lego steady.", id = "p2"),
    )
}
