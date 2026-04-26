package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionDocument
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionDocumentSection
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionLayoutRegistry
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionOffsetAffinity
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionParagraphSeparator
import com.epubreader.feature.reader.internal.runtime.epub.ReaderVisibleSectionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.junit4.createAndroidComposeRule

@RunWith(AndroidJUnit4::class)
class ReaderSelectionHandleAnchorBoundaryTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun endHandleAnchor_atParagraphBoundary_usesTheWhitespaceCursorPosition() {
        val firstParagraph = "The tyrant, in comparison, was moving much faster."
        val secondParagraph = "Pushing Hero back with a deadly swipe of his lower arms, he"
        val boundaryOffset = firstParagraph.length + ReaderSelectionParagraphSeparator.length
        val fixture = composeSingleSectionFixture(
            firstParagraph = firstParagraph,
            secondParagraph = secondParagraph,
        )
        val registry = ReaderSelectionLayoutRegistry()
        val document = ReaderSelectionDocument(
            sections = listOf(
                ReaderSelectionDocumentSection(
                    sectionId = fixture.sectionId,
                    sectionIndex = 0,
                    text = fixture.sectionText,
                    paragraphStartOffsets = fixture.paragraphStartOffsets,
                    isHeading = false,
                    documentStart = 0,
                    documentEnd = fixture.sectionText.length,
                ),
            ),
            totalTextLength = fixture.sectionText.length,
        )

        composeRule.runOnIdle {
            registry.update(
                ReaderVisibleSectionLayout(
                    sectionId = fixture.sectionId,
                    boundsInHost = fixture.boundsInRoot,
                    text = fixture.sectionText,
                    paragraphStartOffsets = fixture.paragraphStartOffsets,
                    textLayoutResult = fixture.textLayoutResult,
                    textLength = fixture.sectionText.length,
                    documentStart = 0,
                    renderedTextTopInSection = fixture.textLayoutResult.renderedTextTopInSection(),
                    renderedTextBottomInSection = fixture.textLayoutResult.renderedTextBottomInSection(),
                ),
            )
        }

        val anchor = composeRule.runOnIdle {
            registry.resolveHandleAnchor(
                offset = boundaryOffset,
                affinity = ReaderSelectionOffsetAffinity.Upstream,
                document = document,
            )
        } ?: error("Expected an end-handle anchor at the paragraph boundary")

        val boundaryCursorRect = fixture.textLayoutResult.getCursorRect(boundaryOffset)
        val nextParagraphLineTop = fixture.textLayoutResult.getLineTop(
            fixture.textLayoutResult.getLineForOffset(boundaryOffset),
        )

        assertEquals(
            fixture.boundsInRoot.left + boundaryCursorRect.right,
            anchor.x,
            1f,
        )
        assertEquals(
            fixture.boundsInRoot.top + boundaryCursorRect.bottom,
            anchor.y,
            1f,
        )
        assertTrue(
            "Expected the end-handle anchor to stay at or below the whitespace cursor line, but anchor=$anchor nextTop=${fixture.boundsInRoot.top + nextParagraphLineTop}",
            anchor.y >= fixture.boundsInRoot.top + nextParagraphLineTop,
        )
    }

    private fun composeSingleSectionFixture(
        firstParagraph: String,
        secondParagraph: String,
    ): BoundaryAnchorFixture {
        lateinit var textLayoutResult: TextLayoutResult
        lateinit var boundsInRoot: Rect
        val sectionText = firstParagraph + ReaderSelectionParagraphSeparator + secondParagraph
        val sectionId = "text:p1:p2"
        val paragraphStartOffsets = listOf(0, firstParagraph.length + ReaderSelectionParagraphSeparator.length)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(
                            text = sectionText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 48.sp,
                                lineHeight = 72.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .onGloballyPositioned { coordinates ->
                                    val topLeft = coordinates.positionInRoot()
                                    boundsInRoot = Rect(
                                        left = topLeft.x,
                                        top = topLeft.y,
                                        right = topLeft.x + coordinates.size.width,
                                        bottom = topLeft.y + coordinates.size.height,
                                    )
                                },
                            onTextLayout = { layoutResult ->
                                textLayoutResult = layoutResult
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        return BoundaryAnchorFixture(
            sectionId = sectionId,
            sectionText = sectionText,
            paragraphStartOffsets = paragraphStartOffsets,
            boundsInRoot = boundsInRoot,
            textLayoutResult = textLayoutResult,
        )
    }
}

private data class BoundaryAnchorFixture(
    val sectionId: String,
    val sectionText: String,
    val paragraphStartOffsets: List<Int>,
    val boundsInRoot: Rect,
    val textLayoutResult: TextLayoutResult,
)

private fun TextLayoutResult.renderedTextTopInSection(): Float {
    return if (lineCount == 0) 0f else getLineTop(0)
}

private fun TextLayoutResult.renderedTextBottomInSection(): Float {
    return if (lineCount == 0) 0f else getLineBottom(lineCount - 1)
}
