package com.epubreader.feature.library.internal

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import org.json.JSONObject

internal fun buildLibraryScreenState(
    globalSettings: GlobalSettings,
    drawerState: DrawerState,
    snackbarHostState: SnackbarHostState,
    books: List<EpubBook>,
    libraryItems: List<EpubBook>,
    progressByBookId: Map<String, BookProgress>,
    bookFolderById: Map<String, String>,
    lastOpenedBook: EpubBook?,
    selectedFolderName: String,
    asyncState: LibraryAsyncUiState,
    isBookSelectionMode: Boolean,
    selectedBookIds: Set<String>,
    folders: List<String>,
    displayedFolders: List<String>,
    isMovingMode: Boolean,
    isFolderSelectionMode: Boolean,
    foldersToDelete: Set<String>,
    draggedFolderName: String?,
    dragOffset: Float,
): LibraryScreenState {
    return LibraryScreenState(
        globalSettings = globalSettings,
        drawerState = drawerState,
        snackbarHostState = snackbarHostState,
        books = books,
        libraryItems = libraryItems,
        progressByBookId = progressByBookId,
        bookFolderById = bookFolderById,
        lastOpenedBook = lastOpenedBook,
        selectedFolderName = selectedFolderName,
        asyncState = asyncState,
        selection = BookSelectionUiState(
            isBookSelectionMode = isBookSelectionMode,
            selectedBookIds = selectedBookIds,
        ),
        folderDrawer = FolderDrawerUiState(
            folders = folders,
            displayedFolders = displayedFolders,
            isMovingMode = isMovingMode,
            isFolderSelectionMode = isFolderSelectionMode,
            foldersToDelete = foldersToDelete,
            draggedFolderName = draggedFolderName,
            dragOffset = dragOffset,
        ),
    )
}

internal fun buildLibraryScreenActions(
    libraryItems: List<EpubBook>,
    selectedBookIds: Set<String>,
    globalSettings: GlobalSettings,
    onAddBookClick: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSetFavoriteFolder: () -> Unit,
    onShowSortMenu: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearBookSelection: () -> Unit,
    onOpenBook: (EpubBook) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    onSelectedBookIdsChange: (Set<String>) -> Unit,
    onLongPressFeedback: () -> Unit,
    folderDrawerActions: FolderDrawerActions,
): LibraryScreenActions {
    return LibraryScreenActions(
        onAddBookClick = onAddBookClick,
        onRefreshLibrary = onRefreshLibrary,
        onOpenDrawer = onOpenDrawer,
        onSetFavoriteFolder = onSetFavoriteFolder,
        onShowSortMenu = onShowSortMenu,
        onOpenSettings = onOpenSettings,
        onSelectAllBooks = { onSelectedBookIdsChange(libraryItems.map { it.id }.toSet()) },
        onClearBookSelection = onClearBookSelection,
        onOpenBook = onOpenBook,
        onToggleBookSelection = { book ->
            val isSelected = selectedBookIds.contains(book.id)
            onSelectedBookIdsChange(
                if (isSelected) {
                    val newSelection = selectedBookIds - book.id
                    if (newSelection.isEmpty()) {
                        onSelectionModeChange(false)
                    }
                    newSelection
                } else {
                    selectedBookIds + book.id
                },
            )
        },
        onStartBookSelection = { book ->
            if (globalSettings.hapticFeedback) {
                onLongPressFeedback()
            }
            onSelectionModeChange(true)
            onSelectedBookIdsChange(setOf(book.id))
        },
        folderDrawer = folderDrawerActions,
    )
}

internal fun buildSelectionBarState(
    isBookSelectionMode: Boolean,
    isMovingMode: Boolean,
    selectedEditableBook: EpubBook?,
): BookSelectionActionBarState {
    return BookSelectionActionBarState(
        visible = isBookSelectionMode && !isMovingMode,
        canEditSelection = selectedEditableBook != null,
    )
}

internal fun buildSelectionBarActions(
    selectedBookIds: Set<String>,
    onShowBulkBookDeleteConfirm: () -> Unit,
    onEnterMovingMode: () -> Unit,
    onEditSelectedBook: () -> Unit,
): BookSelectionActionBarActions {
    return BookSelectionActionBarActions(
        onDeleteSelectedBooks = {
            if (selectedBookIds.isNotEmpty()) {
                onShowBulkBookDeleteConfirm()
            }
        },
        onMoveSelectedBooks = {
            if (selectedBookIds.isNotEmpty()) {
                onEnterMovingMode()
            }
        },
        onEditSelectedBook = onEditSelectedBook,
    )
}

internal fun buildDialogState(
    showSortMenu: Boolean,
    currentSort: String,
    folders: List<String>,
    showCreateFolderDialog: Boolean,
    folderToRename: String?,
    folderToDelete: String?,
    showBulkBookDeleteConfirm: Boolean,
    selectedBookCount: Int,
    showBulkFolderDeleteConfirm: Boolean,
    selectedFolderCount: Int,
    showFirstTimeNote: Boolean,
    changelogEntries: List<JSONObject>,
): LibraryDialogState {
    return LibraryDialogState(
        showSortMenu = showSortMenu,
        currentSort = currentSort,
        existingFolders = folders,
        showCreateFolderDialog = showCreateFolderDialog,
        folderToRename = folderToRename,
        folderToDelete = folderToDelete,
        showBulkBookDeleteConfirm = showBulkBookDeleteConfirm,
        selectedBookCount = selectedBookCount,
        showBulkFolderDeleteConfirm = showBulkFolderDeleteConfirm,
        selectedFolderCount = selectedFolderCount,
        showFirstTimeNote = showFirstTimeNote,
        changelogEntries = changelogEntries,
    )
}

internal fun buildDialogActions(
    onDismissSortMenu: () -> Unit,
    onSelectSort: (String) -> Unit,
    onDismissCreateFolder: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onDismissRenameFolder: () -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDismissDeleteFolder: () -> Unit,
    onDeleteFolder: (String) -> Unit,
    onDismissDeleteBooks: () -> Unit,
    onDeleteBooks: () -> Unit,
    onDismissDeleteFolders: () -> Unit,
    onDeleteFolders: () -> Unit,
    onDismissWelcome: () -> Unit,
    onDismissChangelog: () -> Unit,
): LibraryDialogActions {
    return LibraryDialogActions(
        onDismissSortMenu = onDismissSortMenu,
        onSelectSort = onSelectSort,
        onDismissCreateFolder = onDismissCreateFolder,
        onCreateFolder = onCreateFolder,
        onDismissRenameFolder = onDismissRenameFolder,
        onRenameFolder = onRenameFolder,
        onDismissDeleteFolder = onDismissDeleteFolder,
        onDeleteFolder = onDeleteFolder,
        onDismissDeleteBooks = onDismissDeleteBooks,
        onDeleteBooks = onDeleteBooks,
        onDismissDeleteFolders = onDismissDeleteFolders,
        onDeleteFolders = onDeleteFolders,
        onDismissWelcome = onDismissWelcome,
        onDismissChangelog = onDismissChangelog,
    )
}

internal fun buildFolderDrawerActions(
    folders: List<String>,
    onCloseDrawer: () -> Unit,
    onSelectFolder: (String) -> Unit,
    onMoveSelectedBooks: (String) -> Unit,
    onEnterFolderSelectionMode: () -> Unit,
    onExitFolderSelectionMode: () -> Unit,
    foldersToDelete: Set<String>,
    onFoldersToDeleteChange: (Set<String>) -> Unit,
    onRequestDeleteSelectedFolders: () -> Unit,
    onShowCreateFolderDialog: () -> Unit,
    onRequestRenameFolder: (String) -> Unit,
    onRequestDeleteFolder: (String) -> Unit,
    onStartFolderDrag: (String) -> Unit,
    onDragFolder: (Float, Float) -> Unit,
    onEndFolderDrag: () -> Unit,
    onCancelFolderDrag: () -> Unit,
): FolderDrawerActions {
    return FolderDrawerActions(
        onCloseDrawer = onCloseDrawer,
        onSelectFolder = onSelectFolder,
        onMoveSelectedBooks = onMoveSelectedBooks,
        onEnterFolderSelectionMode = onEnterFolderSelectionMode,
        onExitFolderSelectionMode = onExitFolderSelectionMode,
        onToggleFolderSelection = { folderName ->
            if (folderName != RootLibraryName) {
                onFoldersToDeleteChange(
                    if (foldersToDelete.contains(folderName)) {
                        foldersToDelete - folderName
                    } else {
                        foldersToDelete + folderName
                    },
                )
            }
        },
        onSelectAllFolders = {
            onFoldersToDeleteChange(folders.filter { it != RootLibraryName }.toSet())
        },
        onClearFolderSelection = { onFoldersToDeleteChange(emptySet()) },
        onRequestDeleteSelectedFolders = onRequestDeleteSelectedFolders,
        onShowCreateFolderDialog = onShowCreateFolderDialog,
        onRequestRenameFolder = onRequestRenameFolder,
        onRequestDeleteFolder = onRequestDeleteFolder,
        onStartFolderDrag = onStartFolderDrag,
        onDragFolder = onDragFolder,
        onEndFolderDrag = onEndFolderDrag,
        onCancelFolderDrag = onCancelFolderDrag,
    )
}
