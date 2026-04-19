package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectableTextStructureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectableText_usesSelectableWrapperPerTextElement() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderChapterContent(
                        settings = GlobalSettings(selectableText = true),
                        themeColors = getThemeColors("light"),
                        listState = rememberLazyListState(),
                        chapterElements = listOf(
                            ChapterElement.Text("Paragraph one", id = "p1"),
                            ChapterElement.Image(byteArrayOf(1), id = "img1"),
                            ChapterElement.Text("Paragraph two", id = "p2"),
                        ),
                        isLoadingChapter = false,
                        currentChapterIndex = 0,
                    )
                }
            }
        }

        composeRule.waitForIdle()

        assertEquals(
            2,
            composeRule.onAllNodesWithTag("reader_selectable_text_item")
                .fetchSemanticsNodes().size
        )
    }
}
