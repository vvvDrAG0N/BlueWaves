package com.epubreader.feature.library.internal

import androidx.compose.material3.DrawerState
import androidx.compose.material3.SnackbarHostState
import com.epubreader.core.model.BookProgress
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import org.json.JSONObject

internal val LibrarySortOptions = listOf(
    "added" to "Date Added",
    "title" to "Title",
    "author" to "Author",
    "chapters" to "Chapters",
    "recent" to "Recent",
)

internal data class LibraryAsyncUiState(
    val libraryRefresh: Boolean = false,
    val importInFlight: Boolean = false,
    val bookOpenInFlight: String? = null,
)

internal data class LibraryScreenState(
    val globalSettings: GlobalSettings,
    val drawerState: DrawerState,
    val snackbarHostState: SnackbarHostState,
    val books: List<EpubBook>,
    val libraryItems: List<EpubBook>,
    val progressByBookId: Map<String, BookProgress>,
    val bookFolderById: Map<String, String>,
    val lastOpenedBook: EpubBook?,
    val selectedFolderName: String,
    val asyncState: LibraryAsyncUiState,
    val selection: BookSelectionUiState,
    val folderDrawer: FolderDrawerUiState,
)

internal data class BookSelectionUiState(
    val isBookSelectionMode: Boolean,
    val selectedBookIds: Set<String>,
) {
    val selectedCount: Int
        get() = selectedBookIds.size
}

internal data class FolderDrawerUiState(
    val folders: List<String>,
    val displayedFolders: List<String>,
    val isMovingMode: Boolean,
    val isFolderSelectionMode: Boolean,
    val foldersToDelete: Set<String>,
    val draggedFolderName: String?,
    val dragOffset: Float,
)

internal data class LibraryScreenActions(
    val onAddBookClick: () -> Unit,
    val onRefreshLibrary: () -> Unit,
    val onOpenDrawer: () -> Unit,
    val onSetFavoriteFolder: () -> Unit,
    val onShowSortMenu: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onSelectAllBooks: () -> Unit,
    val onClearBookSelection: () -> Unit,
    val onOpenBook: (EpubBook) -> Unit,
    val onToggleBookSelection: (EpubBook) -> Unit,
    val onStartBookSelection: (EpubBook) -> Unit,
    val folderDrawer: FolderDrawerActions,
)

internal data class FolderDrawerActions(
    val onCloseDrawer: () -> Unit,
    val onSelectFolder: (String) -> Unit,
    val onMoveSelectedBooks: (String) -> Unit,
    val onEnterFolderSelectionMode: () -> Unit,
    val onExitFolderSelectionMode: () -> Unit,
    val onToggleFolderSelection: (String) -> Unit,
    val onSelectAllFolders: () -> Unit,
    val onClearFolderSelection: () -> Unit,
    val onRequestDeleteSelectedFolders: () -> Unit,
    val onShowCreateFolderDialog: () -> Unit,
    val onRequestRenameFolder: (String) -> Unit,
    val onRequestDeleteFolder: (String) -> Unit,
    val onStartFolderDrag: (String) -> Unit,
    val onDragFolder: (Float, Float) -> Unit,
    val onEndFolderDrag: () -> Unit,
    val onCancelFolderDrag: () -> Unit,
)

internal data class BookSelectionActionBarState(
    val visible: Boolean,
    val canEditSelection: Boolean,
)

internal data class BookSelectionActionBarActions(
    val onDeleteSelectedBooks: () -> Unit,
    val onMoveSelectedBooks: () -> Unit,
    val onEditSelectedBook: () -> Unit,
)

internal enum class LibraryBackAction {
    ClearFolderSelection,
    CloseDrawer,
    ClearBookSelection,
}

internal fun resolveLibraryBackAction(
    isDrawerOpen: Boolean,
    isFolderSelectionMode: Boolean,
    isBookSelectionMode: Boolean,
): LibraryBackAction? {
    return when {
        isFolderSelectionMode -> LibraryBackAction.ClearFolderSelection
        isDrawerOpen -> LibraryBackAction.CloseDrawer
        isBookSelectionMode -> LibraryBackAction.ClearBookSelection
        else -> null
    }
}

internal data class LibraryDialogState(
    val showSortMenu: Boolean,
    val currentSort: String,
    val existingFolders: List<String>,
    val showCreateFolderDialog: Boolean,
    val folderToRename: String?,
    val folderToDelete: String?,
    val showBulkBookDeleteConfirm: Boolean,
    val selectedBookCount: Int,
    val showBulkFolderDeleteConfirm: Boolean,
    val selectedFolderCount: Int,
    val showFirstTimeNote: Boolean,
    val changelogEntries: List<JSONObject>,
)

internal data class LibraryDialogActions(
    val onDismissSortMenu: () -> Unit,
    val onSelectSort: (String) -> Unit,
    val onDismissCreateFolder: () -> Unit,
    val onCreateFolder: (String) -> Unit,
    val onDismissRenameFolder: () -> Unit,
    val onRenameFolder: (String, String) -> Unit,
    val onDismissDeleteFolder: () -> Unit,
    val onDeleteFolder: (String) -> Unit,
    val onDismissDeleteBooks: () -> Unit,
    val onDeleteBooks: () -> Unit,
    val onDismissDeleteFolders: () -> Unit,
    val onDeleteFolders: () -> Unit,
    val onDismissWelcome: () -> Unit,
    val onDismissChangelog: () -> Unit,
)
