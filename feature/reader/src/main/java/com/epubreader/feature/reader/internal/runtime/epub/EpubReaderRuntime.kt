package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.internal.ui.readerFontFamily

@Composable
internal fun EpubReaderRuntime(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterSections: List<ReaderChapterSection>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionSessionEpoch: Int = 0,
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetVisibilityChange: (Boolean) -> Unit = {},
    onLookupSheetDismissed: () -> Unit = {},
) {
    if (currentChapterIndex == -1 || isLoadingChapter) {
        ReaderChapterLoadingContent(themeColors)
        return
    }

    val renderingPlan = remember(chapterSections, settings.selectableText) {
        buildReaderRuntimeRenderingPlan(
            chapterSections = chapterSections,
            selectableTextEnabled = settings.selectableText,
        )
    }

    key(currentChapterIndex, selectionSessionEpoch) {
        val selectionDocument = renderingPlan.selectionDocument
        if (selectionDocument != null) {
            ReaderChapterSelectionHost(
                settings = settings,
                themeColors = themeColors,
                listState = listState,
                selectionDocument = selectionDocument,
                selectionSessionEpoch = selectionSessionEpoch,
                onSelectionActiveChange = onSelectionActiveChange,
                onSelectionHandleDragChange = onSelectionHandleDragChange,
                onLookupSheetVisibilityChange = onLookupSheetVisibilityChange,
                onLookupSheetDismissed = onLookupSheetDismissed,
            ) { selectionController ->
                ReaderRuntimeSectionList(
                    settings = settings,
                    themeColors = themeColors,
                    listState = listState,
                    currentChapterIndex = currentChapterIndex,
                    chapterSections = renderingPlan.chapterSections,
                    selectionDocument = selectionDocument,
                    selectionController = selectionController,
                )
            }
        } else {
            ReaderRuntimeSectionList(
                settings = settings,
                themeColors = themeColors,
                listState = listState,
                currentChapterIndex = currentChapterIndex,
                chapterSections = renderingPlan.chapterSections,
            )
        }
    }
}

@Composable
private fun ReaderRuntimeSectionList(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    currentChapterIndex: Int,
    chapterSections: List<ReaderChapterSection>,
    selectionDocument: ReaderSelectionDocument? = null,
    selectionController: ReaderSelectionController? = null,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader_runtime_chapter_$currentChapterIndex"),
        contentPadding = PaddingValues(
            horizontal = settings.horizontalPadding.dp,
            vertical = 80.dp,
        ),
    ) {
        items(chapterSections, key = { it.id }) { section ->
            when (section) {
                is ReaderChapterSection.ImageSection -> {
                    ReaderChapterImage(
                        filePath = section.image.filePath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        onTap = if (selectionController?.selectionActive == true) {
                            selectionController::clearSelection
                        } else {
                            null
                        },
                    )
                }

                is ReaderChapterSection.TextSection -> {
                    val documentSection = selectionDocument?.sectionById(section.id)
                    if (documentSection != null && selectionController != null) {
                        ReaderSelectableTextSection(
                            section = documentSection,
                            settings = settings,
                            themeColors = themeColors,
                            selectionController = selectionController,
                        )
                    } else {
                        ReaderPlainTextSection(
                            section = section,
                            settings = settings,
                            themeColors = themeColors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderPlainTextSection(
    section: ReaderChapterSection.TextSection,
    settings: GlobalSettings,
    themeColors: ReaderTheme,
) {
    val isHeading = section.blocks.firstOrNull()?.type == "h"
    val sectionText = remember(section.blocks) {
        section.blocks.joinToString(separator = ReaderSelectionParagraphSeparator) { block ->
            block.content
        }
    }
    val sectionStyle = if (isHeading) {
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = (settings.fontSize + 4).sp,
        )
    } else {
        MaterialTheme.typography.bodyLarge.copy(
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * settings.lineHeight).sp,
        )
    }

    Text(
        text = sectionText,
        style = sectionStyle,
        fontFamily = readerFontFamily(settings.fontType),
        color = themeColors.foreground,
        textAlign = if (isHeading) TextAlign.Center else TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("reader_compose_text_section"),
    )
}
