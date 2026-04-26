package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.BookFormat
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.unit.dp

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

        composeRule.onNodeWithTag("reader_theme_chip_oled").performScrollTo().performClick()
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
                            val progressState = remember { mutableFloatStateOf(0f) }
                            ReaderControls(
                                book = sampleBook(),
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
                                progressPercentageState = progressState,
                                onDismiss = { showControls = false },
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("reader_controls_drag_handle").performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(0f, 160f))
                advanceEventTime(16)
            }
            up()
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("reader_controls_sheet").fetchSemanticsNodes().isEmpty()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fontSlider_previewsDuringDrag_andPersistsExactlyOnceOnRelease() {
        var displayedSettings by mutableStateOf(GlobalSettings())
        var persistedSettings by mutableStateOf(GlobalSettings())
        var previewCount = 0
        var persistCount = 0
        var firstPreviewFontSize: Int? = null
        val eventOrder = mutableListOf<String>()

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val progressState = remember { mutableFloatStateOf(0f) }
                        ReaderControls(
                            book = sampleBook(),
                            settings = displayedSettings,
                            onSettingsChange = {},
                            onPreviewSettingsChange = { transform ->
                                previewCount++
                                eventOrder += "preview"
                                displayedSettings = transform(displayedSettings)
                                firstPreviewFontSize = firstPreviewFontSize ?: displayedSettings.fontSize
                            },
                            onPersistSettingsChange = { transform ->
                                persistCount++
                                eventOrder += "persist"
                                persistedSettings = transform(displayedSettings)
                                displayedSettings = persistedSettings
                            },
                            themeColors = getThemeColors(displayedSettings.theme),
                            onNavigatePrev = {},
                            onNavigateNext = {},
                            listState = rememberLazyListState(),
                            itemCount = 8,
                            currentChapterIndex = 0,
                            totalChapters = 3,
                            sectionLabel = "Chapter",
                            progressPercentageState = progressState,
                            onDismiss = {},
                            isVisible = true,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("reader_font_size_slider").performScrollTo().performTouchInput {
            down(Offset(x = width * 0.35f, y = centerY))
            moveTo(Offset(x = width * 0.85f, y = centerY))
            up()
        }

        composeRule.waitForIdle()

        assertTrue(previewCount > 0)
        assertEquals(1, persistCount)
        assertTrue((firstPreviewFontSize ?: 18) > 18)
        assertEquals(persistedSettings.fontSize, displayedSettings.fontSize)
        assertTrue(eventOrder.first() == "preview")
        assertTrue(eventOrder.last() == "persist")
    }

    @Test
    fun verticalScrubber_scrollsListToLatestDragPosition() {
        lateinit var listState: LazyListState

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    listState = rememberLazyListState()
                    VerticalScrubberHost(listState = listState)
                }
            }
        }

        composeRule.onNodeWithTag("reader_vertical_scrubber").performTouchInput {
            down(topCenter)
            repeat(8) {
                moveBy(Offset(0f, bottomCenter.y / 8f))
                advanceEventTime(16)
            }
            up()
        }

        composeRule.waitUntil(5_000) {
            listState.firstVisibleItemIndex >= 60
        }
    }

    @Test
    fun statusOverlay_onlyRendersWhenVisible() {
        var overlayVisible by mutableStateOf(false)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    val progressState = remember { mutableFloatStateOf(0.42f) }
                    ReaderStatusOverlay(
                        uiState = com.epubreader.core.model.ReaderStatusUiState(
                            isEnabled = true,
                            showClock = false,
                            showBattery = false,
                            showChapterProgress = true,
                            showChapterNumber = true,
                        ),
                        isVisible = overlayVisible,
                        themeColors = getThemeColors("light"),
                        chapterIndex = 2,
                        maxChapters = 12,
                        progressPercentageState = progressState,
                    )
                }
            }
        }

        assertTrue(composeRule.onAllNodes(hasText("CH 2")).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodes(hasText("42%")).fetchSemanticsNodes().isEmpty())

        composeRule.runOnUiThread {
            overlayVisible = true
        }

        composeRule.onNodeWithText("CH 2").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()

        composeRule.runOnUiThread {
            overlayVisible = false
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("CH 2")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Composable
    private fun ReaderControlsHost(
        settings: GlobalSettings = GlobalSettings(),
        onSettingsChange: (GlobalSettingsTransform) -> Unit = {},
    ) {
        MaterialTheme {
            val progressState = remember { mutableFloatStateOf(0f) }
            Box(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ReaderControls(
                    book = sampleBook(),
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
                    progressPercentageState = progressState,
                    onDismiss = {},
                )
            }
        }
    }

    @Composable
    private fun VerticalScrubberHost(
        listState: LazyListState,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(280.dp),
            ) {
                items((0 until 100).toList()) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        androidx.compose.material3.Text("Item $index")
                    }
                }
            }

            VerticalScrubber(
                listState = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .width(20.dp),
            )
        }
    }

    private fun sampleBook(): EpubBook {
        return EpubBook(
            id = "reader-controls-test",
            title = "Reader Controls Test",
            author = "Test",
            coverPath = null,
            rootPath = "/tests/reader-controls",
            format = BookFormat.EPUB,
            sourceFormat = BookFormat.EPUB,
            spineHrefs = listOf("chapter-1.xhtml"),
        )
    }
}
