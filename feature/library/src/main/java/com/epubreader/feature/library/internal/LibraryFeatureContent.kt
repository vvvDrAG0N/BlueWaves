package com.epubreader.feature.library.internal

import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.epubreader.core.model.EpubBook
import com.epubreader.data.parser.EPUB_MIME_TYPE
import com.epubreader.data.parser.ZIP_COMPRESSED_MIME_TYPE
import com.epubreader.data.parser.ZIP_MIME_TYPE
import com.epubreader.feature.library.LibraryDependencies
import com.epubreader.feature.library.LibraryEvent
import com.epubreader.feature.library.LibraryRoute
import com.epubreader.feature.library.internal.ui.LibraryScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LibraryFeatureContent(
    route: LibraryRoute,
    dependencies: LibraryDependencies,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }
    var hasCompletedInitialRefresh by remember(route) { mutableStateOf(false) }

    var books by remember { mutableStateOf(emptyList<EpubBook>()) }
    var asyncState by remember { mutableStateOf(LibraryAsyncUiState()) }
    var selectedBookIds by remember { mutableStateOf(emptySet<String>()) }
    var isBookSelectionMode by remember { mutableStateOf(false) }
    var isMovingMode by remember { mutableStateOf(false) }
    var showBulkBookDeleteConfirm by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<String?>(null) }
    var foldersToDelete by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isFolderSelectionMode by remember { mutableStateOf(false) }
    var showBulkFolderDeleteConfirm by remember { mutableStateOf(false) }

    var selectedFolderName by remember(dependencies.globalSettings.favoriteLibrary) {
        mutableStateOf(dependencies.globalSettings.favoriteLibrary.ifEmpty { RootLibraryName })
    }
    var dragPreviewFolders by remember { mutableStateOf(emptyList<String>()) }
    var pendingFolderOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggedFolderName by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun refreshLibrary(onComplete: (() -> Unit)? = null) {
        if (asyncState.libraryRefresh) {
            return
        }

        scope.launch {
            asyncState = asyncState.copy(libraryRefresh = true)
            try {
                books = withContext(Dispatchers.IO) { scanLibrary(dependencies.parser) }
            } finally {
                asyncState = asyncState.copy(libraryRefresh = false)
                if (!hasCompletedInitialRefresh) {
                    hasCompletedInitialRefresh = true
                    onEvent(LibraryEvent.InitialLibraryRefreshCompleted)
                }
                onComplete?.invoke()
            }
        }
    }

    fun clearBookSelection() {
        isBookSelectionMode = false
        selectedBookIds = emptySet()
        isMovingMode = false
    }

    fun clearFolderSelection() {
        isFolderSelectionMode = false
        foldersToDelete = emptySet()
    }

    fun applyUpdatedBook(updatedBook: EpubBook) {
        var replaced = false
        books = books.map { existing ->
            if (existing.id == updatedBook.id) {
                replaced = true
                updatedBook
            } else {
                existing
            }
        }.let { currentBooks ->
            if (replaced) currentBooks else currentBooks + updatedBook
        }
    }

    val openBook: (EpubBook) -> Unit = openBook@{ book ->
        if (book.sourceFormat == com.epubreader.core.model.BookFormat.PDF) {
            scope.launch {
                snackbarHostState.showSnackbar(PdfImportDisabledMessage)
            }
            return@openBook
        }
        if (asyncState.bookOpenInFlight != book.id) {
            scope.launch {
                asyncState = asyncState.copy(bookOpenInFlight = book.id)
                try {
                    val preparedBook = withContext(Dispatchers.IO) {
                        dependencies.parser.prepareBookForReading(book)
                    }
                    val updated = touchBookLastRead(dependencies.parser, preparedBook)
                    applyUpdatedBook(updated)
                    onEvent(LibraryEvent.OpenReader(updated.id))
                } finally {
                    asyncState = asyncState.copy(bookOpenInFlight = null)
                }
            }
        }
    }

    LaunchedEffect(route) {
        refreshLibrary()
    }

    val libraryDerivedData by produceState(
        initialValue = LibraryDerivedData(),
        books,
        dependencies.globalSettings.bookGroups,
        dependencies.globalSettings.folderSorts,
        dependencies.globalSettings.folderOrder,
        dependencies.globalSettings.librarySort,
        selectedFolderName,
    ) {
        value = withContext(Dispatchers.Default) {
            buildLibraryDerivedData(
                books = books,
                bookGroupsRaw = dependencies.globalSettings.bookGroups,
                folderSortsRaw = dependencies.globalSettings.folderSorts,
                folderOrderRaw = dependencies.globalSettings.folderOrder,
                librarySort = dependencies.globalSettings.librarySort,
                selectedFolderName = selectedFolderName,
            )
        }
    }
    val bookGroups = libraryDerivedData.bookGroups
    val currentSort = libraryDerivedData.currentSort
    val folders = libraryDerivedData.folders
    val libraryItems = libraryDerivedData.libraryItems
    val bookFolderById = libraryDerivedData.bookFolderById

    LaunchedEffect(folders, draggedFolderName, pendingFolderOrder) {
        if (draggedFolderName == null && (pendingFolderOrder == null || folders == pendingFolderOrder)) {
            dragPreviewFolders = folders
            pendingFolderOrder = null
        }
    }

    val displayedFolders = resolveDisplayedFolders(
        folders = folders,
        dragPreviewFolders = dragPreviewFolders,
        draggedFolderName = draggedFolderName,
        pendingFolderOrder = pendingFolderOrder,
    )
    val lastOpenedBook = remember(books) { findLastOpenedBook(books) }
    val progressTargets = remember(libraryItems, lastOpenedBook) {
        buildLibraryProgressTargets(
            libraryItems = libraryItems,
            lastOpenedBook = lastOpenedBook,
        )
    }
    val progressByBookId by dependencies.settingsManager.observeBookProgresses(progressTargets)
        .collectAsState(initial = emptyMap())
    val selectedEditableBook = remember(books, selectedBookIds) {
        findSelectedEditableBook(
            books = books,
            selectedBookIds = selectedBookIds,
        )
    }

    val launchBookImport = rememberBookImportLauncher(
        books = books,
        context = context,
        parser = dependencies.parser,
        settingsManager = dependencies.settingsManager,
        selectedFolderName = selectedFolderName,
        bookGroups = bookGroups,
        snackbarHostState = snackbarHostState,
        scope = scope,
        onImportStateChange = { isImporting ->
            asyncState = asyncState.copy(importInFlight = isImporting)
        },
        onBookImported = ::applyUpdatedBook,
    )
    val libraryMutations = buildLibraryFeatureMutations(
        globalSettings = dependencies.globalSettings,
        haptics = haptics,
        scope = scope,
        settingsManager = dependencies.settingsManager,
        parser = dependencies.parser,
        selectedBookIds = selectedBookIds,
        books = books,
        folders = folders,
        foldersToDelete = foldersToDelete,
        selectedFolderName = selectedFolderName,
        dragPreviewFolders = dragPreviewFolders,
        draggedFolderName = draggedFolderName,
        dragOffset = dragOffset,
        onPendingFolderOrderChange = { pendingFolderOrder = it },
        onDraggedFolderNameChange = { draggedFolderName = it },
        onDragOffsetChange = { dragOffset = it },
        onDragPreviewFoldersChange = { dragPreviewFolders = it },
        onSelectedFolderNameChange = { selectedFolderName = it },
        onClearBookSelection = ::clearBookSelection,
        onClearFolderSelection = ::clearFolderSelection,
        onRefreshLibrary = ::refreshLibrary,
        onCloseDrawer = { drawerState.close() },
    )

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    val libraryScreenState = buildLibraryScreenState(
        globalSettings = dependencies.globalSettings,
        drawerState = drawerState,
        snackbarHostState = snackbarHostState,
        books = books,
        libraryItems = libraryItems,
        progressByBookId = progressByBookId,
        bookFolderById = bookFolderById,
        lastOpenedBook = lastOpenedBook,
        selectedFolderName = selectedFolderName,
        asyncState = asyncState,
        isBookSelectionMode = isBookSelectionMode,
        selectedBookIds = selectedBookIds,
        folders = folders,
        displayedFolders = displayedFolders,
        isMovingMode = isMovingMode,
        isFolderSelectionMode = isFolderSelectionMode,
        foldersToDelete = foldersToDelete,
        draggedFolderName = draggedFolderName,
        dragOffset = dragOffset,
    )
    val folderDrawerActions = buildFolderDrawerActions(
        folders = folders,
        onCloseDrawer = closeDrawer,
        onSelectFolder = { folderName ->
            selectedFolderName = folderName
            closeDrawer()
        },
        onMoveSelectedBooks = libraryMutations.moveSelectedBooksToFolder,
        onEnterFolderSelectionMode = { isFolderSelectionMode = true },
        onExitFolderSelectionMode = ::clearFolderSelection,
        foldersToDelete = foldersToDelete,
        onFoldersToDeleteChange = { foldersToDelete = it },
        onRequestDeleteSelectedFolders = { showBulkFolderDeleteConfirm = true },
        onShowCreateFolderDialog = { showCreateFolderDialog = true },
        onRequestRenameFolder = { folderToRename = it },
        onRequestDeleteFolder = { folderToDelete = it },
        onStartFolderDrag = libraryMutations.startFolderDrag,
        onDragFolder = libraryMutations.dragFolderBy,
        onEndFolderDrag = libraryMutations.completeFolderDrag,
        onCancelFolderDrag = libraryMutations.cancelFolderDrag,
    )
    val libraryScreenActions = buildLibraryScreenActions(
        libraryItems = libraryItems,
        selectedBookIds = selectedBookIds,
        globalSettings = dependencies.globalSettings,
        onAddBookClick = {
            launchBookImport(arrayOf(EPUB_MIME_TYPE, ZIP_MIME_TYPE, ZIP_COMPRESSED_MIME_TYPE))
        },
        onRefreshLibrary = ::refreshLibrary,
        onOpenDrawer = openDrawer,
        onSetFavoriteFolder = {
            scope.launch {
                dependencies.settingsManager.setFavoriteLibrary(selectedFolderName)
            }
        },
        onShowSortMenu = { showSortMenu = true },
        onOpenSettings = { onEvent(LibraryEvent.OpenSettings) },
        onClearBookSelection = ::clearBookSelection,
        onOpenBook = openBook,
        onSelectionModeChange = { isBookSelectionMode = it },
        onSelectedBookIdsChange = { selectedBookIds = it },
        onLongPressFeedback = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
        folderDrawerActions = folderDrawerActions,
    )
    val selectionBarState = buildSelectionBarState(
        isBookSelectionMode = isBookSelectionMode,
        isMovingMode = isMovingMode,
        selectedEditableBook = selectedEditableBook,
    )
    val selectionBarActions = buildSelectionBarActions(
        selectedBookIds = selectedBookIds,
        onShowBulkBookDeleteConfirm = { showBulkBookDeleteConfirm = true },
        onEnterMovingMode = {
            isMovingMode = true
            openDrawer()
        },
        onEditSelectedBook = {
            selectedEditableBook?.let { book ->
                clearBookSelection()
                onEvent(LibraryEvent.OpenEditBook(book.id))
            }
        },
    )
    val dialogState = buildDialogState(
        showSortMenu = showSortMenu,
        currentSort = currentSort,
        folders = folders,
        showCreateFolderDialog = showCreateFolderDialog,
        folderToRename = folderToRename,
        folderToDelete = folderToDelete,
        showBulkBookDeleteConfirm = showBulkBookDeleteConfirm,
        selectedBookCount = selectedBookIds.size,
        showBulkFolderDeleteConfirm = showBulkFolderDeleteConfirm,
        selectedFolderCount = foldersToDelete.size,
        showFirstTimeNote = dependencies.startupPresentation.showFirstTimeNote,
        changelogEntries = dependencies.startupPresentation.changelogEntries,
    )
    val dialogActions = buildDialogActions(
        onDismissSortMenu = { showSortMenu = false },
        onSelectSort = { sort ->
            scope.launch {
                dependencies.settingsManager.setLibrarySort(selectedFolderName, sort)
            }
        },
        onDismissCreateFolder = { showCreateFolderDialog = false },
        onCreateFolder = libraryMutations.createFolder,
        onDismissRenameFolder = { folderToRename = null },
        onRenameFolder = libraryMutations.renameFolder,
        onDismissDeleteFolder = { folderToDelete = null },
        onDeleteFolder = libraryMutations.deleteFolder,
        onDismissDeleteBooks = { showBulkBookDeleteConfirm = false },
        onDeleteBooks = libraryMutations.deleteSelectedBooksAction,
        onDismissDeleteFolders = { showBulkFolderDeleteConfirm = false },
        onDeleteFolders = libraryMutations.deleteSelectedFolders,
        onDismissWelcome = { onEvent(LibraryEvent.DismissWelcome) },
        onDismissChangelog = { onEvent(LibraryEvent.DismissChangelog) },
    )

    LibraryScreen(
        modifier = modifier,
        state = libraryScreenState,
        actions = libraryScreenActions,
        selectionBarState = selectionBarState,
        selectionBarActions = selectionBarActions,
        dialogState = dialogState,
        dialogActions = dialogActions,
    )

    val backAction = resolveLibraryBackAction(
        isDrawerOpen = drawerState.isOpen,
        isFolderSelectionMode = isFolderSelectionMode,
        isBookSelectionMode = isBookSelectionMode,
    )
    BackHandler(enabled = backAction != null) {
        when (backAction) {
            LibraryBackAction.ClearFolderSelection -> clearFolderSelection()
            LibraryBackAction.CloseDrawer -> closeDrawer()
            LibraryBackAction.ClearBookSelection -> clearBookSelection()
            null -> Unit
        }
    }
}
