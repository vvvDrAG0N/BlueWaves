/**
 * FILE: AppNavigation.kt
 * PURPOSE: High-level UI orchestration for library, reader, and settings navigation.
 * RESPONSIBILITIES:
 *  - Manages screen transitions using [Screen].
 *  - Coordinates library organization, folder management, and drag-and-drop.
 *  - Bridges app-level state between persistence, parser, and UI features.
 */
package com.epubreader.app

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.epubreader.core.model.BookFormat
import com.epubreader.Screen
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.parser.EPUB_MIME_TYPE
import com.epubreader.data.parser.ZIP_COMPRESSED_MIME_TYPE
import com.epubreader.data.parser.ZIP_MIME_TYPE
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AI_LOAD_STRATEGY
 * - Read this file when you need app-shell state ownership, startup/version effects, or screen routing.
 * - Read `AppNavigationContracts.kt` when you need the app-shell state or callback contract map.
 * - Read `AppNavigationStartup.kt` for first-run/version/changelog evaluation.
 * - Read `AppNavigationOperations.kt` for import/delete/last-read side effects.
 * - Read `AppNavigationLibraryData.kt` for folder derivation, sorting, or drag-preview helpers.
 * - Read `AppNavigationLibrary.kt` for drawer/top bar/grid rendering.
 * - Read `AppNavigationDialogs.kt` for sort sheet and confirmation/welcome dialogs.
 */

/**
 * AI_ENTRY_POINT
 * AI_READ_FIRST
 * AI_RELEVANT_TO: [App Navigation, Library Management, Folder Drag-and-Drop]
 * AI_STATE_OWNER: AppNavigation owns [books, selectedBook, selectedFolderName].
 *
 * PURPOSE: High-level navigation and state container for the entire app.
 * RESPONSIBILITIES:
 *  - Orchestrates transitions between Library, Reader, and Settings.
 *  - Owns transient app-shell state and coordinates package-local helper files.
 *  - Keeps folder and sorting derivation, startup decisions, and side-effect operations wired together.
 * AI_CRITICAL: This is the "Brain" of the app. Adding UI here often requires corresponding
 * state logic updates in [SettingsManager].
 */
@Composable
fun AppNavigation(settingsManager: SettingsManager, globalSettings: GlobalSettings) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val parser = remember { EpubParser.create(context) }

    var books by remember { mutableStateOf(emptyList<EpubBook>()) }
    var selectedBook by remember { mutableStateOf<EpubBook?>(null) }
    var editingBook by remember { mutableStateOf<EpubBook?>(null) }
    var currentScreen by remember { mutableStateOf(Screen.Library) }
    var asyncState by remember { mutableStateOf(LibraryAsyncUiState()) }

    var editBookSaveInFlight by remember { mutableStateOf(false) }
    var editBookErrorMessage by remember { mutableStateOf<String?>(null) }
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
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var selectedFolderName by remember(globalSettings.favoriteLibrary) {
        mutableStateOf(globalSettings.favoriteLibrary.ifEmpty { RootLibraryName })
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var dragPreviewFolders by remember { mutableStateOf(emptyList<String>()) }
    var pendingFolderOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggedFolderName by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    var showFirstTimeNote by remember { mutableStateOf(false) }
    var changelogToShow by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var detectedVersionCode by remember { mutableIntStateOf(0) }

    // Shell-owned refresh. Keep scanning here so screen transitions and dialogs share one source.
    fun refreshLibrary() {
        if (asyncState.libraryRefresh) return // Guard: Don't stack parallel scans
        
        scope.launch {
            asyncState = asyncState.copy(libraryRefresh = true)
            try {
                books = withContext(Dispatchers.IO) { scanLibrary(parser) }
            } finally {
                asyncState = asyncState.copy(libraryRefresh = false)
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

    fun openEditBook(book: EpubBook) {
        if (book.sourceFormat != BookFormat.EPUB || editBookSaveInFlight) {
            return
        }
        clearBookSelection()
        editingBook = book
        editBookErrorMessage = null
        currentScreen = Screen.EditBook
    }

    fun exitEditBook() {
        // Do not clear editingBook here; AnimatedContent needs it to render the outgoing exit transition.
        editBookErrorMessage = null
        editBookSaveInFlight = false
        currentScreen = Screen.Library
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
        if (selectedBook?.id == updatedBook.id) {
            selectedBook = updatedBook
        }
    }

    val openBook: (EpubBook) -> Unit = openBook@{ book ->
        if (book.sourceFormat == BookFormat.PDF) {
            scope.launch {
                snackbarHostState.showSnackbar(PdfSupportDisabledSnackbarMessage)
            }
            return@openBook
        }
        if (asyncState.bookOpenInFlight != book.id) {
            scope.launch {
                asyncState = asyncState.copy(bookOpenInFlight = book.id)
                try {
                    val preparedBook = withContext(Dispatchers.IO) {
                        parser.prepareBookForReading(book)
                    }
                    selectedBook = preparedBook
                    currentScreen = Screen.Reader

                    val updated = touchBookLastRead(parser, preparedBook)
                    applyUpdatedBook(updated)
                } finally {
                    asyncState = asyncState.copy(bookOpenInFlight = null)
                }
            }
        }
    }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    AppNavigationStartupEffect(
        context = context,
        currentScreen = currentScreen,
        globalSettings = globalSettings,
        settingsManager = settingsManager,
        onDetectedVersionCode = { detectedVersionCode = it },
        onShowFirstTimeNoteChange = { showFirstTimeNote = it },
        onChangelogChange = { changelogToShow = it },
        onRefreshLibrary = ::refreshLibrary,
    )

    AppNavigationSideEffects(
        view = view,
        globalSettings = globalSettings,
        drawerState = drawerState,
        currentScreen = currentScreen,
        books = books,
        selectedBook = selectedBook,
        parser = parser,
        lifecycleOwner = lifecycleOwner,
        scope = scope,
        onRefreshLibrary = ::refreshLibrary,
        onClearFolderSelection = ::clearFolderSelection,
        onMovingModeChange = { isMovingMode = it },
        onBookUpdated = ::applyUpdatedBook,
    )

    // The derived models below are intentionally pure. They are the safest place to inspect
    // folder/sort bugs before touching rendering code.
    // GHOST PROTOCOL: Only parse library data if the library is visible.
    // This eliminates background CPU jitter while swiping themes in Settings.
    val libraryDerivedData by produceState(
        initialValue = LibraryDerivedData(),
        books,
        globalSettings.bookGroups,
        globalSettings.folderSorts,
        globalSettings.folderOrder,
        globalSettings.librarySort,
        selectedFolderName,
        currentScreen,
    ) {
        if (currentScreen == Screen.Library) {
            value = withContext(Dispatchers.Default) {
                buildLibraryDerivedData(
                    books = books,
                    bookGroupsRaw = globalSettings.bookGroups,
                    folderSortsRaw = globalSettings.folderSorts,
                    folderOrderRaw = globalSettings.folderOrder,
                    librarySort = globalSettings.librarySort,
                    selectedFolderName = selectedFolderName,
                )
            }
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
    val progressByBookId by settingsManager.observeBookProgresses(progressTargets)
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
        parser = parser,
        settingsManager = settingsManager,
        selectedFolderName = selectedFolderName,
        bookGroups = bookGroups,
        snackbarHostState = snackbarHostState,
        scope = scope,
        onImportStateChange = { isImporting ->
            asyncState = asyncState.copy(importInFlight = isImporting)
        },
        onBookImported = ::applyUpdatedBook,
    )
    val libraryMutations = buildAppNavigationLibraryMutations(
        globalSettings = globalSettings,
        haptics = haptics,
        scope = scope,
        settingsManager = settingsManager,
        parser = parser,
        selectedBookIds = selectedBookIds,
        books = books,
        folders = folders,
        foldersToDelete = foldersToDelete,
        selectedFolderName = selectedFolderName,
        detectedVersionCode = detectedVersionCode,
        dragPreviewFolders = dragPreviewFolders,
        draggedFolderName = draggedFolderName,
        dragOffset = dragOffset,
        onPendingFolderOrderChange = { pendingFolderOrder = it },
        onDraggedFolderNameChange = { draggedFolderName = it },
        onDragOffsetChange = { dragOffset = it },
        onDragPreviewFoldersChange = { dragPreviewFolders = it },
        onSelectedFolderNameChange = { selectedFolderName = it },
        onShowFirstTimeNoteChange = { showFirstTimeNote = it },
        onChangelogChange = { changelogToShow = it },
        onClearBookSelection = ::clearBookSelection,
        onClearFolderSelection = ::clearFolderSelection,
        onRefreshLibrary = ::refreshLibrary,
        onCloseDrawer = { drawerState.close() },
    )

    // The bundled contracts below are the second-pass context reduction layer. They keep the
    // rendering files small enough to inspect without replaying every shell variable by hand.
    val libraryScreenState = buildLibraryScreenState(
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
        onExitFolderSelectionMode = { clearFolderSelection() },
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
        folders = folders,
        globalSettings = globalSettings,
        onAddBookClick = {
            launchBookImport(arrayOf(EPUB_MIME_TYPE, ZIP_MIME_TYPE, ZIP_COMPRESSED_MIME_TYPE))
        },
        onRefreshLibrary = ::refreshLibrary,
        onOpenDrawer = openDrawer,
        onSetFavoriteFolder = { scope.launch { settingsManager.setFavoriteLibrary(selectedFolderName) } },
        onShowSortMenu = { showSortMenu = true },
        onOpenSettings = { currentScreen = Screen.Settings },
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
        globalSettings = globalSettings,
        selectedEditableBook = selectedEditableBook,
    )
    val selectionBarActions = buildSelectionBarActions(
        selectedBookIds = selectedBookIds,
        onShowBulkBookDeleteConfirm = { showBulkBookDeleteConfirm = true },
        onEnterMovingMode = {
            isMovingMode = true
            openDrawer()
        },
        onEditSelectedBook = { selectedEditableBook?.let(::openEditBook) },
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
        showFirstTimeNote = showFirstTimeNote,
        changelogToShow = changelogToShow,
    )
    val dialogActions = buildDialogActions(
        onDismissSortMenu = { showSortMenu = false },
        onSelectSort = { sort -> scope.launch { settingsManager.setLibrarySort(selectedFolderName, sort) } },
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
        onDismissWelcome = libraryMutations.dismissFirstTimeNote,
        onDismissChangelog = libraryMutations.dismissChangelog,
    )

    AppNavigationScreenHost(
        currentScreen = currentScreen,
        editingBook = editingBook,
        selectedBook = selectedBook,
        settingsManager = settingsManager,
        globalSettings = globalSettings,
        parser = parser,
        libraryScreenState = libraryScreenState,
        libraryScreenActions = libraryScreenActions,
        selectionBarState = selectionBarState,
        selectionBarActions = selectionBarActions,
        dialogState = dialogState,
        dialogActions = dialogActions,
        editBookSaveInFlight = editBookSaveInFlight,
        editBookErrorMessage = editBookErrorMessage,
        onEditBookErrorDismiss = { editBookErrorMessage = null },
        onExitEditBook = ::exitEditBook,
        onSaveEditBook = { book ->
            { request ->
                if (!editBookSaveInFlight) {
                    scope.launch {
                        editBookSaveInFlight = true
                        when (
                            val result = editBookInLibrary(
                                parser = parser,
                                settingsManager = settingsManager,
                                book = book,
                                request = request,
                            )
                        ) {
                            is EditBookResult.Updated -> {
                                editBookErrorMessage = null
                                applyUpdatedBook(result.book)
                                exitEditBook()
                            }
                            is EditBookResult.Failed -> {
                                editBookErrorMessage = result.message
                                editBookSaveInFlight = false
                            }
                        }
                    }
                }
            }
        },
        onGoToLibrary = { currentScreen = Screen.Library },
        onClearFolderSelection = ::clearFolderSelection,
        onCloseDrawer = closeDrawer,
        onClearBookSelection = ::clearBookSelection,
    )
}
