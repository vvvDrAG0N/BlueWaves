package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSelectionHost
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectableTextSection
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionController
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionDocument
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionDocumentSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectionReselectBehaviorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activeSelection_longPressingDifferentTextStartsANewSelectionWithoutManualClear() {
        lateinit var selectionController: ReaderSelectionController
        val firstText = "Scholarship"
        val secondText = "Navigation"
        val firstSection = ReaderSelectionDocumentSection(
            sectionId = "p1",
            sectionIndex = 0,
            text = firstText,
            paragraphStartOffsets = listOf(0),
            isHeading = false,
            documentStart = 0,
            documentEnd = firstText.length,
        )
        val secondSection = ReaderSelectionDocumentSection(
            sectionId = "p2",
            sectionIndex = 1,
            text = secondText,
            paragraphStartOffsets = listOf(0),
            isHeading = false,
            documentStart = firstText.length,
            documentEnd = firstText.length + secondText.length,
        )
        val selectionDocument = ReaderSelectionDocument(
            sections = listOf(firstSection, secondSection),
            totalTextLength = firstText.length + secondText.length,
        )

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("selection_surface"),
                    ) {
                        val listState = rememberLazyListState()
                        ReaderChapterSelectionHost(
                            settings = GlobalSettings(
                                selectableText = true,
                                fontSize = 48,
                            ),
                            themeColors = getThemeColors("light"),
                            listState = listState,
                            selectionDocument = selectionDocument,
                            selectionSessionEpoch = 0,
                            onSelectionActiveChange = { _, _ -> },
                        ) { controller ->
                            SideEffect {
                                selectionController = controller
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 80.dp),
                            ) {
                                items(selectionDocument.sections, key = { it.sectionId }) { section ->
                                    ReaderSelectableTextSection(
                                        section = section,
                                        settings = GlobalSettings(
                                            selectableText = true,
                                            fontSize = 48,
                                        ),
                                        themeColors = getThemeColors("light"),
                                        selectionController = controller,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()
        val textNodes = composeRule.onAllNodesWithTag("reader_compose_text_section", useUnmergedTree = true)
        textNodes[0].performTouchInput {
            longClick(Offset(x = 60f, y = height * 0.5f))
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(
                TextRange(0, firstText.length),
                selectionController.highlightRangeForSection(firstSection.sectionId),
            )
            assertNull(selectionController.highlightRangeForSection(secondSection.sectionId))
        }

        textNodes[1].performTouchInput {
            longClick(Offset(x = 60f, y = height * 0.5f))
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertNull(selectionController.highlightRangeForSection(firstSection.sectionId))
            assertEquals(
                TextRange(0, secondText.length),
                selectionController.highlightRangeForSection(secondSection.sectionId),
            )
        }
    }
}
