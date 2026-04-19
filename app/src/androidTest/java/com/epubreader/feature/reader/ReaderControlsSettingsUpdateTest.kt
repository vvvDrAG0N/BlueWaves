package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderControlsSettingsUpdateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun controlsApplyEachChangeToLatestPersistedSettingsState() {
        var persistedSettings by mutableStateOf(GlobalSettings())

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                ReaderControlsHost(
                    settings = persistedSettings,
                    onSettingsChange = { transform ->
                        persistedSettings = transform(persistedSettings)
                    },
                )
            }
        }

        composeRule.onNode(hasContentDescription("Theme OLED") and hasClickAction()).performScrollTo().performClick()
        composeRule.onNodeWithTag("reader_controls_section_font_family").performScrollTo()
        composeRule.onNodeWithTag("reader_font_chip_karla").performScrollTo().performClick()

        composeRule.waitForIdle()

        assertEquals("karla", persistedSettings.fontType)
        assertEquals("oled", persistedSettings.theme)
    }

    @Test
    fun controlsShowMergedSectionsWithoutTabs() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                ReaderControlsHost()
            }
        }

        composeRule.onNodeWithTag("reader_controls_drag_handle").assertIsDisplayed()
        val chapterNodes = composeRule.onAllNodesWithTag("reader_controls_section_chapter").fetchSemanticsNodes()
        val themeNodes = composeRule.onAllNodesWithTag("reader_controls_section_theme").fetchSemanticsNodes()
        val fontNodes = composeRule.onAllNodesWithTag("reader_controls_section_font").fetchSemanticsNodes()
        val readingNodes = composeRule.onAllNodesWithTag("reader_controls_section_reading").fetchSemanticsNodes()
        val othersNodes = composeRule.onAllNodesWithTag("reader_controls_section_others").fetchSemanticsNodes()

        assertTrue(chapterNodes.isNotEmpty())
        assertTrue(themeNodes.isNotEmpty())
        assertTrue(fontNodes.isNotEmpty())
        assertTrue(readingNodes.isNotEmpty())
        assertTrue(othersNodes.isNotEmpty())

        assertTrue(composeRule.onAllNodesWithTag("reader_controls_tab_font").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("reader_controls_tab_theme").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("reader_controls_tab_general").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun controlsCloseWhenDraggedBelowMinimumDetent() {
        var showControls by mutableStateOf(true)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (showControls) {
                            ReaderControls(
                                settings = GlobalSettings(),
                                onSettingsChange = {},
                                themeColors = getThemeColors("light"),
                                onNavigatePrev = {},
                                onNavigateNext = {},
                                listState = rememberLazyListState(),
                                itemCount = 8,
                                currentChapterIndex = 0,
                                totalChapters = 3,
                                sectionLabel = "Chapter",
                                progressPercentage = 0f,
                                onDismiss = { showControls = false },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("reader_controls_drag_handle").performTouchInput {
            down(center)
            repeat(6) {
                moveBy(Offset(0f, 120f))
                advanceEventTime(16)
            }
            up()
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_controls_sheet").fetchSemanticsNodes().isEmpty()
        }
    }

    @Composable
    private fun ReaderControlsHost(
        settings: GlobalSettings = GlobalSettings(),
        onSettingsChange: (GlobalSettingsTransform) -> Unit = {},
    ) {
        MaterialTheme {
            Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ReaderControls(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    themeColors = getThemeColors(settings.theme),
                    onNavigatePrev = {},
                    onNavigateNext = {},
                    listState = rememberLazyListState(),
                    itemCount = 8,
                    currentChapterIndex = 0,
                    totalChapters = 3,
                    sectionLabel = "Chapter",
                    progressPercentage = 0f,
                    onDismiss = {},
                )
            }
        }
    }
}
