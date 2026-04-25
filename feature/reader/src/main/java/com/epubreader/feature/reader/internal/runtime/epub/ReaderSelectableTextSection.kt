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
                textLayoutResult = layoutResult,
                textLength = section.text.length,
                documentStart = section.documentStart,
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
                    if (
                        selectionController.selectionEnabled &&
                        selectionController.selectionSessionPhase == ReaderSelectionSessionPhase.Idle
                    ) {
                        Modifier.readerSelectionLongPressGesture(
                            onLongPressStart = { localPosition ->
                                publishLayoutSnapshot()
                                selectionController.startSelectionAt(
                                    sectionId = section.sectionId,
                                    localPositionInSection = localPosition,
                                )
                            },
                            onLongPressDrag = { localPosition ->
                                publishLayoutSnapshot()
                                selectionController.updateSelectionGesture(
                                    sectionId = section.sectionId,
                                    localPositionInSection = localPosition,
                                )
                            },
                            onLongPressEnd = selectionController::finishSelectionGesture,
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

private fun Rect.translateBy(offset: Offset): Rect {
    return Rect(
        left = left + offset.x,
        top = top + offset.y,
        right = right + offset.x,
        bottom = bottom + offset.y,
    )
}
