package com.epubreader.feature.reader.internal.shell

import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.ReaderChromeCallbacks
import com.epubreader.feature.reader.ReaderSettingsDraft
import com.epubreader.feature.reader.TocSort
import com.epubreader.feature.reader.internal.logReaderSelectionTransition

internal fun buildReaderFeatureShellChromeCallbacks(
    selectionSessionEpoch: Int,
    invalidateSelectionSession: (String) -> Unit,
    onShowControlsChange: (Boolean) -> Unit,
    onTextSelectionSessionActiveChange: (Boolean) -> Unit,
    onSelectionHandleDragActiveChange: (Boolean) -> Unit,
    tocSort: TocSort,
    onTocSortChange: (TocSort) -> Unit,
    onReleaseOverscroll: () -> Unit,
    onSaveAndBack: () -> Unit,
    onOpenToc: () -> Unit,
    onCloseToc: () -> Unit,
    onLocateCurrentChapterInToc: () -> Unit,
    onJumpToChapter: (Int) -> Unit,
    onSelectTocChapter: (Int) -> Unit,
    effectiveSettings: GlobalSettings,
    globalSettings: GlobalSettings,
    settingsDraft: ReaderSettingsDraft?,
    onSettingsDraftChange: (ReaderSettingsDraft?) -> Unit,
    onPersistGlobalSettings: (GlobalSettings) -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onMainScrubberDragStart: () -> Unit,
    onLookupSheetDismissed: () -> Unit,
): ReaderChromeCallbacks {
    return buildReaderChromeCallbacks(
        onShowControlsChange = { shouldShow ->
            if (shouldShow) {
                invalidateSelectionSession("showControls")
            }
            onShowControlsChange(shouldShow)
        },
        onTextSelectionActiveChange = { epoch, isActive ->
            if (epoch == selectionSessionEpoch) {
                onTextSelectionSessionActiveChange(isActive)
            } else {
                logReaderSelectionTransition {
                    "selection.shell.ignoreActiveCallback staleEpoch=$epoch currentEpoch=$selectionSessionEpoch active=$isActive"
                }
            }
        },
        onSelectionHandleDragChange = { epoch, isDragging ->
            if (epoch == selectionSessionEpoch) {
                onSelectionHandleDragActiveChange(isDragging)
            } else {
                logReaderSelectionTransition {
                    "selection.shell.ignoreHandleCallback staleEpoch=$epoch currentEpoch=$selectionSessionEpoch dragging=$isDragging"
                }
            }
        },
        onClearTextSelection = { invalidateSelectionSession("chromeClearSelection") },
        onToggleTocSort = {
            onTocSortChange(
                if (tocSort == TocSort.Ascending) TocSort.Descending else TocSort.Ascending,
            )
        },
        onReleaseOverscroll = onReleaseOverscroll,
        onSaveAndBack = onSaveAndBack,
        onOpenToc = {
            invalidateSelectionSession("openToc")
            onOpenToc()
        },
        onCloseToc = onCloseToc,
        onLocateCurrentChapterInToc = onLocateCurrentChapterInToc,
        onJumpToChapter = onJumpToChapter,
        onSelectTocChapter = onSelectTocChapter,
        onPreviewSettings = { transform ->
            val previewedSettings = transform(effectiveSettings)
            onSettingsDraftChange(ReaderSettingsDraft.from(previewedSettings))
        },
        onPersistSettings = { transform ->
            val updatedSettings = transform(effectiveSettings)
            if (
                settingsDraft != null ||
                updatedSettings.fontSize != globalSettings.fontSize ||
                updatedSettings.lineHeight != globalSettings.lineHeight ||
                updatedSettings.horizontalPadding != globalSettings.horizontalPadding
            ) {
                onSettingsDraftChange(ReaderSettingsDraft.from(updatedSettings))
            }
            onPersistGlobalSettings(updatedSettings)
        },
        onNavigatePrev = onNavigatePrev,
        onNavigateNext = onNavigateNext,
        onMainScrubberDragStart = onMainScrubberDragStart,
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
