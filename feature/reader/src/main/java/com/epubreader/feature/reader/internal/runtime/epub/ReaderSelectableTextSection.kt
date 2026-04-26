package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.internal.ui.readerFontFamily

@Composable
internal fun ReaderSelectableTextSection(
    section: ReaderSelectionDocumentSection,
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    selectionController: ReaderSelectionController,
) {
    var textLayoutResult by remember(section.sectionId) { mutableStateOf<TextLayoutResult?>(null) }
    var boundsInRoot by remember(section.sectionId) { mutableStateOf<Rect?>(null) }
    var isSelectionGestureArmed by remember(section.sectionId) { mutableStateOf(false) }
    val currentSelectionController by rememberUpdatedState(selectionController)

    fun publishLayoutSnapshot(
        controller: ReaderSelectionController = currentSelectionController,
        latestLayoutResult: TextLayoutResult? = textLayoutResult,
        latestBoundsInRoot: Rect? = boundsInRoot,
    ) {
        val layoutResult = latestLayoutResult ?: return
        val currentBoundsInRoot = latestBoundsInRoot ?: return
        controller.updateSectionLayout(
            ReaderVisibleSectionLayout(
                sectionId = section.sectionId,
                boundsInHost = currentBoundsInRoot.translateBy(
                    Offset(
                        x = -controller.hostOriginInRoot.x,
                        y = -controller.hostOriginInRoot.y,
                    ),
                ),
                text = section.text,
                paragraphStartOffsets = section.paragraphStartOffsets,
                textLayoutResult = layoutResult,
                textLength = section.text.length,
                documentStart = section.documentStart,
                renderedTextTopInSection = layoutResult.renderedTextTopInSection(),
                renderedTextBottomInSection = layoutResult.renderedTextBottomInSection(),
            ),
        )
    }

    DisposableEffect(section.sectionId) {
        onDispose {
            currentSelectionController.removeSectionLayout(section.sectionId)
        }
    }

    LaunchedEffect(
        selectionController.hostOriginInRoot,
        textLayoutResult,
        boundsInRoot,
    ) {
        publishLayoutSnapshot()
    }

    val sectionStyle = if (section.isHeading) {
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = (settings.fontSize + 4).sp,
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineHeight).sp,
        )
    }

    val selectionHighlightRange = selectionController.highlightRangeForSection(section.sectionId)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reader_selectable_text_item"),
    ) {
        Text(
            text = section.text,
            style = sectionStyle,
            fontFamily = readerFontFamily(settings.fontType),
            color = themeColors.foreground,
            textAlign = if (section.isHeading) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("reader_compose_text_section")
                .drawWithContent {
                    selectionHighlightRange?.let { localRange ->
                        if (!localRange.collapsed) {
                            textLayoutResult?.let { layoutResult ->
                                drawPath(
                                    path = layoutResult.getPathForRange(localRange.start, localRange.end),
                                    brush = SolidColor(themeColors.primary.copy(alpha = 0.28f)),
                                )
                            }
                        }
                    }
                    drawContent()
                }
                .onGloballyPositioned { coordinates ->
                    val topLeftInRoot = coordinates.positionInRoot()
                    val latestBoundsInRoot = Rect(
                        left = topLeftInRoot.x,
                        top = topLeftInRoot.y,
                        right = topLeftInRoot.x + coordinates.size.width,
                        bottom = topLeftInRoot.y + coordinates.size.height,
                    )
                    boundsInRoot = latestBoundsInRoot
                    publishLayoutSnapshot(latestBoundsInRoot = latestBoundsInRoot)
                }
                .then(
                    if (selectionController.selectionEnabled) {
                        Modifier.readerSelectionLongPressGesture(
                            onLongPressStart = { localPosition ->
                                isSelectionGestureArmed = canStartReaderSelectionAt(
                                    text = section.text,
                                    layoutResult = textLayoutResult,
                                    localPosition = localPosition,
                                )
                                if (!isSelectionGestureArmed) {
                                    return@readerSelectionLongPressGesture
                                }
                                publishLayoutSnapshot()
                                selectionController.startSelectionAt(
                                    sectionId = section.sectionId,
                                    localPositionInSection = localPosition,
                                )
                            },
                            onLongPressDrag = { localPosition ->
                                if (!isSelectionGestureArmed) {
                                    return@readerSelectionLongPressGesture
                                }
                                publishLayoutSnapshot()
                                selectionController.updateSelectionGesture(
                                    sectionId = section.sectionId,
                                    localPositionInSection = localPosition,
                                )
                            },
                            onLongPressEnd = {
                                if (isSelectionGestureArmed) {
                                    selectionController.finishSelectionGesture()
                                }
                                isSelectionGestureArmed = false
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
            onTextLayout = { layoutResult ->
                textLayoutResult = layoutResult
                publishLayoutSnapshot(latestLayoutResult = layoutResult)
            },
        )
    }
}

private fun canStartReaderSelectionAt(
    text: String,
    layoutResult: TextLayoutResult?,
    localPosition: Offset,
): Boolean {
    if (text.isEmpty() || layoutResult == null || layoutResult.lineCount == 0) {
        return false
    }
    val lineIndex = layoutResult.getLineForVerticalPosition(localPosition.y)
    val lineLeft = layoutResult.getLineLeft(lineIndex)
    val lineRight = layoutResult.getLineRight(lineIndex)
    val lineTop = layoutResult.getLineTop(lineIndex)
    val lineBottom = layoutResult.getLineBottom(lineIndex)
    return localPosition.x in lineLeft..lineRight &&
        localPosition.y in lineTop..lineBottom
}

private fun TextLayoutResult.renderedTextTopInSection(): Float {
    return if (lineCount == 0) 0f else getLineTop(0)
}

private fun TextLayoutResult.renderedTextBottomInSection(): Float {
    return if (lineCount == 0) 0f else getLineBottom(lineCount - 1)
}

private fun Rect.translateBy(offset: Offset): Rect {
    return Rect(
        left = left + offset.x,
        top = top + offset.y,
        right = right + offset.x,
        bottom = bottom + offset.y,
    )
}
