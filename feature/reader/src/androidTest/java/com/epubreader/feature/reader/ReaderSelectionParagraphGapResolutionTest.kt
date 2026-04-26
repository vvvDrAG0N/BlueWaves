package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.feature.reader.internal.runtime.epub.ReaderResolvedSelectionPosition
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionLayoutRegistry
import com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionParagraphSeparator
import com.epubreader.feature.reader.internal.runtime.epub.ReaderVisibleSectionLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSelectionParagraphGapResolutionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun upperParagraphSeamBand_resolvesToTheParagraphBoundaryForTheClarifiedEndHandleCase() {
        val firstParagraph = "The tyrant, in comparison, was moving much faster."
        val secondParagraph = "Pushing Hero back with a deadly swipe of his lower arms, he"
        val fixture = composeSingleSectionFixture(
            firstParagraph = firstParagraph,
            secondParagraph = secondParagraph,
        )
        val seam = findParagraphSeamMetrics(
            firstParagraph = firstParagraph,
            layoutResult = fixture.textLayoutResult,
        )
        val registry = createSingleSectionRegistry(
            sectionId = fixture.sectionId,
            sectionText = fixture.sectionText,
            paragraphStartOffsets = fixture.paragraphStartOffsets,
            boundsInRoot = fixture.boundsInRoot,
            layoutResult = fixture.textLayoutResult,
        )

        val resolvedOffsets = composeRule.runOnIdle {
            seam.representativeXCandidates(fixture.boundsInRoot.width, fixture.textLayoutResult)
                .mapNotNull { x ->
                    registry.resolvePositionInSection(
                        sectionId = fixture.sectionId,
                        localPositionInSection = Offset(x = x, y = seam.upperGapMidY),
                    )?.localOffset
                }
        }

        assertEquals(
            "Expected the logical anchor in the upper seam band to stay at the paragraph boundary.",
            setOf(seam.boundaryOffset),
            resolvedOffsets.toSet(),
        )
    }

    @Test
    fun sameSectionParagraphSeam_resolvesToTheBoundaryAcrossTheEntireSeamBand() {
        val firstParagraph = "The tyrant, in comparison, was moving much faster."
        val secondParagraph = "Pushing Hero back with a deadly swipe of his lower arms, he"
        val fixture = composeSingleSectionFixture(
            firstParagraph = firstParagraph,
            secondParagraph = secondParagraph,
        )
        val seam = findParagraphSeamMetrics(
            firstParagraph = firstParagraph,
            layoutResult = fixture.textLayoutResult,
        )
        val registry = createSingleSectionRegistry(
            sectionId = fixture.sectionId,
            sectionText = fixture.sectionText,
            paragraphStartOffsets = fixture.paragraphStartOffsets,
            boundsInRoot = fixture.boundsInRoot,
            layoutResult = fixture.textLayoutResult,
        )

        val resolvedOffsets = composeRule.runOnIdle {
            buildList {
                seam.seamBandYCandidates().forEach { y ->
                    seam.representativeXCandidates(fixture.boundsInRoot.width, fixture.textLayoutResult)
                        .forEach { x ->
                            val resolved = registry.resolvePositionInSection(
                                sectionId = fixture.sectionId,
                                localPositionInSection = Offset(x = x, y = y),
                            )
                            add("x=${x.toInt()} y=${y.toInt()} -> ${resolved?.localOffset}")
                        }
                }
            }
        }

        assertEquals(
            "Expected every x/y sample in the paragraph seam band to resolve to the same boundary.\n" +
                resolvedOffsets.joinToString(separator = "\n"),
            setOf(seam.boundaryOffset),
            resolvedOffsets.mapNotNull { sample -> sample.substringAfter("-> ").toIntOrNull() }.toSet(),
        )
    }

    @Test
    fun interSectionGap_resolvesToTheSharedDocumentBoundary() {
        val firstText = "The tyrant, in comparison, was moving much faster."
        val secondText = "Pushing Hero back with a deadly swipe of his lower arms, he"
        val fixture = composeTwoSectionFixture(
            firstText = firstText,
            secondText = secondText,
        )
        val registry = ReaderSelectionLayoutRegistry()
        composeRule.runOnIdle {
            registry.update(
                ReaderVisibleSectionLayout(
                    sectionId = fixture.firstSectionId,
                    boundsInHost = fixture.firstBoundsInRoot,
                    text = firstText,
                    paragraphStartOffsets = listOf(0),
                    textLayoutResult = fixture.firstLayoutResult,
                    textLength = firstText.length,
                    documentStart = 0,
                    renderedTextTopInSection = fixture.firstLayoutResult.renderedTextTopInSection(),
                    renderedTextBottomInSection = fixture.firstLayoutResult.renderedTextBottomInSection(),
                ),
            )
            registry.update(
                ReaderVisibleSectionLayout(
                    sectionId = fixture.secondSectionId,
                    boundsInHost = fixture.secondBoundsInRoot,
                    text = secondText,
                    paragraphStartOffsets = listOf(0),
                    textLayoutResult = fixture.secondLayoutResult,
                    textLength = secondText.length,
                    documentStart = firstText.length,
                    renderedTextTopInSection = fixture.secondLayoutResult.renderedTextTopInSection(),
                    renderedTextBottomInSection = fixture.secondLayoutResult.renderedTextBottomInSection(),
                ),
            )
        }

        val gapMidY = midpoint(
            fixture.firstBoundsInRoot.top + fixture.firstLayoutResult.renderedTextBottomInSection(),
            fixture.secondBoundsInRoot.top + fixture.secondLayoutResult.renderedTextTopInSection(),
        )
        val resolvedPositions = composeRule.runOnIdle {
            listOf(32f, (fixture.secondBoundsInRoot.width * 0.75f).coerceAtLeast(32f)).map { x ->
                registry.resolvePositionInVisibleSections(
                    positionInHost = Offset(x = x, y = gapMidY),
                )
            }
        }

        assertNotNull(resolvedPositions.first())
        assertEquals(
            "Expected the gap between visible sections to resolve to the shared document seam.",
            setOf(firstText.length),
            resolvedPositions.mapNotNull { resolvedPosition -> resolvedPosition?.documentOffset }.toSet(),
        )
    }

    private fun composeSingleSectionFixture(
        firstParagraph: String,
        secondParagraph: String,
    ): SingleSectionFixture {
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

        return SingleSectionFixture(
            sectionId = sectionId,
            sectionText = sectionText,
            paragraphStartOffsets = paragraphStartOffsets,
            boundsInRoot = boundsInRoot,
            textLayoutResult = textLayoutResult,
        )
    }

    private fun createSingleSectionRegistry(
        sectionId: String,
        sectionText: String,
        paragraphStartOffsets: List<Int>,
        boundsInRoot: Rect,
        layoutResult: TextLayoutResult,
    ): ReaderSelectionLayoutRegistry {
        val registry = ReaderSelectionLayoutRegistry()
        composeRule.runOnIdle {
            registry.update(
                ReaderVisibleSectionLayout(
                    sectionId = sectionId,
                    boundsInHost = boundsInRoot,
                    text = sectionText,
                    paragraphStartOffsets = paragraphStartOffsets,
                    textLayoutResult = layoutResult,
                    textLength = sectionText.length,
                    documentStart = 0,
                    renderedTextTopInSection = layoutResult.renderedTextTopInSection(),
                    renderedTextBottomInSection = layoutResult.renderedTextBottomInSection(),
                ),
            )
        }
        return registry
    }

    private fun composeTwoSectionFixture(
        firstText: String,
        secondText: String,
    ): TwoSectionFixture {
        lateinit var firstLayoutResult: TextLayoutResult
        lateinit var secondLayoutResult: TextLayoutResult
        lateinit var firstBoundsInRoot: Rect
        lateinit var secondBoundsInRoot: Rect

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(
                            text = firstText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 48.sp,
                                lineHeight = 72.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .onGloballyPositioned { coordinates ->
                                    val topLeft = coordinates.positionInRoot()
                                    firstBoundsInRoot = Rect(
                                        left = topLeft.x,
                                        top = topLeft.y,
                                        right = topLeft.x + coordinates.size.width,
                                        bottom = topLeft.y + coordinates.size.height,
                                    )
                                },
                            onTextLayout = { layoutResult ->
                                firstLayoutResult = layoutResult
                            },
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = secondText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 48.sp,
                                lineHeight = 72.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .onGloballyPositioned { coordinates ->
                                    val topLeft = coordinates.positionInRoot()
                                    secondBoundsInRoot = Rect(
                                        left = topLeft.x,
                                        top = topLeft.y,
                                        right = topLeft.x + coordinates.size.width,
                                        bottom = topLeft.y + coordinates.size.height,
                                    )
                                },
                            onTextLayout = { layoutResult ->
                                secondLayoutResult = layoutResult
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        return TwoSectionFixture(
            firstSectionId = "text:p1:p1",
            secondSectionId = "text:p2:p2",
            firstBoundsInRoot = firstBoundsInRoot,
            secondBoundsInRoot = secondBoundsInRoot,
            firstLayoutResult = firstLayoutResult,
            secondLayoutResult = secondLayoutResult,
        )
    }
}

private data class SingleSectionFixture(
    val sectionId: String,
    val sectionText: String,
    val paragraphStartOffsets: List<Int>,
    val boundsInRoot: Rect,
    val textLayoutResult: TextLayoutResult,
)

private data class TwoSectionFixture(
    val firstSectionId: String,
    val secondSectionId: String,
    val firstBoundsInRoot: Rect,
    val secondBoundsInRoot: Rect,
    val firstLayoutResult: TextLayoutResult,
    val secondLayoutResult: TextLayoutResult,
)

private data class ParagraphSeamMetrics(
    val previousContentLineIndex: Int,
    val nextContentLineIndex: Int,
    val boundaryOffset: Int,
    val seamTop: Float,
    val seamBottom: Float,
    val upperGapMidY: Float,
    val lowerGapMidY: Float,
) {
    fun seamBandYCandidates(): List<Float> = listOf(upperGapMidY, midpoint(seamTop, seamBottom), lowerGapMidY).distinct()

    fun representativeXCandidates(
        boundsWidth: Float,
        layoutResult: TextLayoutResult,
    ): List<Float> {
        val previousLineRight = layoutResult.getLineRight(previousContentLineIndex)
        return listOf(
            32f,
            (previousLineRight * 0.7f).coerceAtLeast(32f),
            (previousLineRight + 24f).coerceAtMost(boundsWidth - 1f),
            (boundsWidth * 0.75f).coerceAtMost(boundsWidth - 1f),
        ).distinct()
    }
}

private fun findParagraphSeamMetrics(
    firstParagraph: String,
    layoutResult: TextLayoutResult,
): ParagraphSeamMetrics {
    val boundaryOffset = firstParagraph.length + ReaderSelectionParagraphSeparator.length
    val previousContentOffset = (boundaryOffset - ReaderSelectionParagraphSeparator.length - 1).coerceAtLeast(0)
    val previousContentLineIndex = layoutResult.getLineForOffset(previousContentOffset)
    val nextContentLineIndex = layoutResult.getLineForOffset(boundaryOffset)
    val seamTop = layoutResult.getLineBottom(previousContentLineIndex)
    val seamBottom = layoutResult.getLineTop(nextContentLineIndex)
    return ParagraphSeamMetrics(
        previousContentLineIndex = previousContentLineIndex,
        nextContentLineIndex = nextContentLineIndex,
        boundaryOffset = boundaryOffset,
        seamTop = seamTop,
        seamBottom = seamBottom,
        upperGapMidY = interpolate(seamTop, seamBottom, 0.25f),
        lowerGapMidY = interpolate(seamTop, seamBottom, 0.75f),
    )
}

private fun midpoint(
    start: Float,
    end: Float,
): Float = (start + end) / 2f

private fun interpolate(
    start: Float,
    end: Float,
    fraction: Float,
): Float = start + ((end - start) * fraction)

private fun TextLayoutResult.renderedTextTopInSection(): Float {
    return if (lineCount == 0) 0f else getLineTop(0)
}

private fun TextLayoutResult.renderedTextBottomInSection(): Float {
    return if (lineCount == 0) 0f else getLineBottom(lineCount - 1)
}
