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
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReaderSelectableTextStructureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun epubRuntimeSelectableText_splitsRunsAroundImages() {
        val imageFile = File(composeRule.activity.cacheDir, "reader-selectable-image.bin").apply {
            writeBytes(byteArrayOf(1))
        }
        val chapterElements = listOf(
            ChapterElement.Text("Paragraph one", id = "p1"),
            ChapterElement.Image(imageFile.absolutePath, id = "img1"),
            ChapterElement.Text("Paragraph two", id = "p2"),
        )
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderChapterContent(
                        settings = GlobalSettings(selectableText = true),
                        themeColors = getThemeColors("light"),
                        listState = rememberLazyListState(),
                        chapterSections = buildReaderChapterSections(chapterElements),
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

    @Test
    fun composeLazyImprovedSelectableText_groupsConsecutiveParagraphsIntoSections() {
        val imageFile = File(composeRule.activity.cacheDir, "reader-compose-selectable-image.bin").apply {
            writeBytes(byteArrayOf(2))
        }
        val chapterElements = listOf(
            ChapterElement.Text("Paragraph one", id = "p1"),
            ChapterElement.Text("Paragraph two", id = "p2"),
            ChapterElement.Image(imageFile.absolutePath, id = "img1"),
            ChapterElement.Text("Paragraph three", id = "p3"),
            ChapterElement.Text("Paragraph four", id = "p4"),
        )
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderChapterContent(
                        settings = GlobalSettings(selectableText = true),
                        themeColors = getThemeColors("light"),
                        listState = rememberLazyListState(),
                        chapterSections = buildReaderChapterSections(chapterElements),
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
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            2,
            composeRule.onAllNodesWithTag("reader_compose_text_section")
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun selectableTextDisabled_rendersPlainTextSectionsWithoutSelectionScaffolding() {
        val imageFile = File(composeRule.activity.cacheDir, "reader-plain-text-image.bin").apply {
            writeBytes(byteArrayOf(3))
        }
        val chapterElements = listOf(
            ChapterElement.Text("Paragraph one", id = "p1"),
            ChapterElement.Text("Paragraph two", id = "p2"),
            ChapterElement.Image(imageFile.absolutePath, id = "img1"),
            ChapterElement.Text("Paragraph three", id = "p3"),
            ChapterElement.Text("Paragraph four", id = "p4"),
        )
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderChapterContent(
                        settings = GlobalSettings(selectableText = false),
                        themeColors = getThemeColors("light"),
                        listState = rememberLazyListState(),
                        chapterSections = buildReaderChapterSections(chapterElements),
                        isLoadingChapter = false,
                        currentChapterIndex = 0,
                    )
                }
            }
        }

        composeRule.waitForIdle()

        assertEquals(
            0,
            composeRule.onAllNodesWithTag("reader_selectable_text_item")
                .fetchSemanticsNodes().size,
        )
        assertEquals(
            2,
            composeRule.onAllNodesWithTag("reader_compose_text_section")
                .fetchSemanticsNodes().size,
        )
    }
}
