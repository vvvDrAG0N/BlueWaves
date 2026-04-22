package com.epubreader.app

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class AppNavigationLibraryMutations(
    val startFolderDrag: (String) -> Unit,
    val dragFolderBy: (Float, Float) -> Unit,
    val completeFolderDrag: () -> Unit,
    val cancelFolderDrag: () -> Unit,
    val moveSelectedBooksToFolder: (String) -> Unit,
    val createFolder: (String) -> Unit,
    val renameFolder: (String, String) -> Unit,
    val deleteFolder: (String) -> Unit,
    val deleteSelectedBooksAction: () -> Unit,
    val deleteSelectedFolders: () -> Unit,
    val dismissFirstTimeNote: () -> Unit,
    val dismissChangelog: () -> Unit,
)

internal fun buildAppNavigationLibraryMutations(
    globalSettings: GlobalSettings,
    haptics: HapticFeedback,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    parser: EpubParser,
    selectedBookIds: Set<String>,
    books: List<com.epubreader.core.model.EpubBook>,
    folders: List<String>,
    foldersToDelete: Set<String>,
    selectedFolderName: String,
    detectedVersionCode: Int,
    dragPreviewFolders: List<String>,
    draggedFolderName: String?,
    dragOffset: Float,
    onPendingFolderOrderChange: (List<String>?) -> Unit,
    onDraggedFolderNameChange: (String?) -> Unit,
    onDragOffsetChange: (Float) -> Unit,
    onDragPreviewFoldersChange: (List<String>) -> Unit,
    onSelectedFolderNameChange: (String) -> Unit,
    onShowFirstTimeNoteChange: (Boolean) -> Unit,
    onChangelogChange: (List<org.json.JSONObject>) -> Unit,
    onClearBookSelection: () -> Unit,
    onClearFolderSelection: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onCloseDrawer: suspend () -> Unit,
): AppNavigationLibraryMutations {
    return AppNavigationLibraryMutations(
        startFolderDrag = { folderName ->
            onDraggedFolderNameChange(folderName)
            onDragOffsetChange(0f)
            if (globalSettings.hapticFeedback) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        },
        dragFolderBy = dragFolder@{ dragAmountY, itemHeightPx ->
            val dragUpdate = updateFolderDragPreview(
                dragPreviewFolders = dragPreviewFolders,
                draggedFolderName = draggedFolderName,
                dragOffset = dragOffset,
                dragAmountY = dragAmountY,
                itemHeightPx = itemHeightPx,
            )
            onDragPreviewFoldersChange(dragUpdate.previewFolders)
            onDragOffsetChange(dragUpdate.dragOffset)
            if (dragUpdate.didReorder && globalSettings.hapticFeedback) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        },
        completeFolderDrag = {
            val reorderedFolders = dragPreviewFolders.toList()
            onPendingFolderOrderChange(reorderedFolders)
            onDraggedFolderNameChange(null)
            onDragOffsetChange(0f)
            scope.launch {
                settingsManager.updateFolderOrder(reorderedFolders.filter { it != RootLibraryName })
            }
        },
        cancelFolderDrag = {
            onDraggedFolderNameChange(null)
            onPendingFolderOrderChange(null)
            onDragPreviewFoldersChange(folders)
            onDragOffsetChange(0f)
        },
        moveSelectedBooksToFolder = { folderName ->
            scope.launch {
                moveBooksToFolder(settingsManager, selectedBookIds, folderName)
                onCloseDrawer()
                onClearBookSelection()
            }
        },
        createFolder = { newFolderName ->
            scope.launch {
                settingsManager.createFolder(newFolderName)
                onSelectedFolderNameChange(newFolderName)
                onDragPreviewFoldersChange(dragPreviewFolders + newFolderName)
            }
        },
        renameFolder = { oldName, newName ->
            scope.launch {
                val trimmedNewName = newName.trim()
                val isInvalidRename = oldName == RootLibraryName ||
                    trimmedNewName.isBlank() ||
                    trimmedNewName == oldName ||
                    trimmedNewName == RootLibraryName ||
                    folders.any { it == trimmedNewName && it != oldName }
                if (isInvalidRename) {
                    return@launch
                }

                settingsManager.renameFolder(oldName, trimmedNewName)
                if (selectedFolderName == oldName) {
                    onSelectedFolderNameChange(trimmedNewName)
                }
                onDragPreviewFoldersChange(
                    dragPreviewFolders.map { folderName ->
                        if (folderName == oldName) trimmedNewName else folderName
                    },
                )
            }
        },
        deleteFolder = { folderName ->
            scope.launch {
                settingsManager.deleteFolder(folderName)
                if (selectedFolderName == folderName) {
                    onSelectedFolderNameChange(RootLibraryName)
                }
                onDragPreviewFoldersChange(dragPreviewFolders.filter { it != folderName })
            }
        },
        deleteSelectedBooksAction = {
            scope.launch {
                deleteSelectedBooks(parser, settingsManager, books, selectedBookIds)
                onClearBookSelection()
                onRefreshLibrary()
            }
        },
        deleteSelectedFolders = {
            scope.launch {
                val toDelete = foldersToDelete.toSet()
                settingsManager.deleteFolders(toDelete)
                if (toDelete.contains(selectedFolderName)) {
                    onSelectedFolderNameChange(RootLibraryName)
                }
                onDragPreviewFoldersChange(dragPreviewFolders.filter { !toDelete.contains(it) })
                onClearFolderSelection()
            }
        },
        dismissFirstTimeNote = {
            scope.launch {
                settingsManager.setFirstTime(false)
                onShowFirstTimeNoteChange(false)
            }
        },
        dismissChangelog = {
            scope.launch {
                settingsManager.setLastSeenVersionCode(detectedVersionCode)
                onChangelogChange(emptyList())
            }
        },
    )
}
