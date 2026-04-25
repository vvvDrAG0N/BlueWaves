package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.ReaderTheme

@Composable
internal fun EpubReaderRuntime(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionSessionEpoch: Int = 0,
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
) {
    if (currentChapterIndex == -1 || isLoadingChapter) {
        ReaderChapterLoadingContent(themeColors)
        return
    }

    val sections = remember(chapterElements) {
        buildReaderChapterSections(chapterElements)
    }
    val selectionDocument = remember(sections) {
        buildReaderSelectionDocument(sections)
    }

    key(currentChapterIndex, selectionSessionEpoch) {
        ReaderChapterSelectionHost(
            settings = settings,
            themeColors = themeColors,
            listState = listState,
            selectionDocument = selectionDocument,
            selectionSessionEpoch = selectionSessionEpoch,
            onSelectionActiveChange = onSelectionActiveChange,
            onSelectionHandleDragChange = onSelectionHandleDragChange,
        ) { selectionController ->
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
                items(sections, key = { it.id }) { section ->
                    when (section) {
                        is ReaderChapterSection.ImageSection -> {
                            ReaderChapterImage(
                                filePath = section.image.filePath,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                onTap = if (selectionController.selectionActive) selectionController::clearSelection else null,
                            )
                        }

                        is ReaderChapterSection.TextSection -> {
                            val documentSection = selectionDocument.sectionById(section.id)
                            if (documentSection != null) {
                                ReaderSelectableTextSection(
                                    section = documentSection,
                                    settings = settings,
                                    themeColors = themeColors,
                                    selectionController = selectionController,
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reader_compose_text_section"),
                                ) {
                                    section.blocks.forEach { block ->
                                        ReaderChapterTextBlock(
                                            element = block,
                                            settings = settings,
                                            themeColors = themeColors,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }        
    }
}
