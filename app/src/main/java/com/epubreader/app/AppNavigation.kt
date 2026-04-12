/**
 * FILE: AppNavigation.kt
 * PURPOSE: High-level UI orchestration for library, reader, and settings navigation.
 * RESPONSIBILITIES:
 *  - Manages screen transitions using [Screen].
 *  - Coordinates library organization, folder management, and drag-and-drop.
 *  - Bridges app-level state between persistence, parser, and UI features.
 */
package com.epubreader.app

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.epubreader.Screen
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.reader.ReaderScreen
import com.epubreader.feature.settings.SettingsScreen
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val parser = remember { EpubParser(context) }

    var books by remember { mutableStateOf(emptyList<EpubBook>()) }
    var selectedBook by remember { mutableStateOf<EpubBook?>(null) }
    var currentScreen by remember { mutableStateOf(Screen.Library) }
    var isLoading by remember { mutableStateOf(false) }
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
    val snackbarHostState = remember { SnackbarHostState() }

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
        scope.launch {
            isLoading = true
            books = scanLibrary(parser)
            isLoading = false
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

    val openBook: (EpubBook) -> Unit = { book ->
        selectedBook = book
        currentScreen = Screen.Reader
        scope.launch {
            val updated = touchBookLastRead(parser, book)
            books = books.map { existing -> if (existing.id == book.id) updated else existing }
        }
    }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen) {
            clearFolderSelection()
            isMovingMode = false
        }
    }

    // Startup checks stay in the shell because they drive global dialogs and version bookkeeping.
    LaunchedEffect(Unit) {
        refreshLibrary()

        val startupDecision = evaluateAppShellStartup(context, globalSettings)
        detectedVersionCode = startupDecision.detectedVersionCode
        showFirstTimeNote = startupDecision.showFirstTimeNote
        changelogToShow = startupDecision.changelogEntries

        if (startupDecision.shouldClearFirstTime) {
            settingsManager.setFirstTime(false)
        }
        startupDecision.versionCodeToMarkSeen?.let { settingsManager.setLastSeenVersionCode(it) }
    }

    LaunchedEffect(selectedFolderName) {
        refreshLibrary()
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.Library) {
            refreshLibrary()
        }
    }

    // The derived models below are intentionally pure. They are the safest place to inspect
    // folder/sort bugs before touching rendering code.
    val bookGroups = remember(globalSettings.bookGroups) {
        parseJsonObject(globalSettings.bookGroups)
    }
    val folderSorts = remember(globalSettings.folderSorts) {
        parseJsonObject(globalSettings.folderSorts)
    }
    val folderOrder = remember(globalSettings.folderOrder) {
        parseFolderOrder(globalSettings.folderOrder)
    }

    val folders = remember(books, globalSettings.bookGroups, globalSettings.folderSorts, folderOrder) {
        buildFolders(
            books = books,
            bookGroups = bookGroups,
            folderSorts = folderSorts,
            folderOrder = folderOrder,
        )
    }

    LaunchedEffect(folders, draggedFolderName, pendingFolderOrder) {
        if (draggedFolderName == null && (pendingFolderOrder == null || folders == pendingFolderOrder)) {
            dragPreviewFolders = folders
            pendingFolderOrder = null
        }
    }

    val displayedFolders = when {
        draggedFolderName != null -> dragPreviewFolders
        pendingFolderOrder != null -> pendingFolderOrder ?: folders
        else -> folders
    }

    val currentSort = remember(selectedFolderName, globalSettings.librarySort, globalSettings.folderSorts) {
        if (selectedFolderName == RootLibraryName) {
            globalSettings.librarySort
        } else {
            folderSorts.optString(selectedFolderName, DefaultLibrarySort)
        }
    }

    val libraryItems = remember(books, globalSettings.bookGroups, currentSort, selectedFolderName) {
        buildLibraryItems(
            books = books,
            bookGroups = bookGroups,
            currentSort = currentSort,
            selectedFolderName = selectedFolderName,
        )
    }

    val lastOpenedBook = remember(books) {
        books.filter { it.lastRead > 0 }.maxByOrNull { it.lastRead }
    }

    // Action lambdas remain here so persistence writes and transient shell state stay coordinated.
    val startFolderDrag: (String) -> Unit = { folderName ->
        draggedFolderName = folderName
        dragOffset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val dragFolderBy: (Float, Float) -> Unit = dragFolder@{ dragAmountY, itemHeightPx ->
        val dragUpdate = updateFolderDragPreview(
            dragPreviewFolders = dragPreviewFolders,
            draggedFolderName = draggedFolderName,
            dragOffset = dragOffset,
            dragAmountY = dragAmountY,
            itemHeightPx = itemHeightPx,
        )
        dragPreviewFolders = dragUpdate.previewFolders
        dragOffset = dragUpdate.dragOffset
        if (dragUpdate.didReorder) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val completeFolderDrag: () -> Unit = {
        val reorderedFolders = dragPreviewFolders.toList()
        pendingFolderOrder = reorderedFolders
        draggedFolderName = null
        dragOffset = 0f
        scope.launch {
            settingsManager.updateFolderOrder(reorderedFolders.filter { it != RootLibraryName })
        }
    }
    val cancelFolderDrag: () -> Unit = {
        draggedFolderName = null
        pendingFolderOrder = null
        dragPreviewFolders = folders
        dragOffset = 0f
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                isLoading = true
                when (
                    val result = importBookIntoLibrary(
                        books = books,
                        context = context,
                        uri = selectedUri,
                        parser = parser,
                        settingsManager = settingsManager,
                        selectedFolderName = selectedFolderName,
                        bookGroups = bookGroups,
                    )
                ) {
                    is ImportBookResult.Duplicate -> {
                        snackbarHostState.showSnackbar("This book is already in your library (${result.folderName}).")
                    }
                    ImportBookResult.Imported -> {
                        refreshLibrary()
                    }
                    ImportBookResult.Failed -> Unit
                }
                isLoading = false
            }
        }
    }

    val moveSelectedBooksToFolder: (String) -> Unit = { folderName ->
        scope.launch {
            moveBooksToFolder(settingsManager, selectedBookIds, folderName)
            drawerState.close()
            clearBookSelection()
            refreshLibrary()
        }
    }
    val createFolder: (String) -> Unit = { newFolderName ->
        scope.launch {
            settingsManager.createFolder(newFolderName)
            selectedFolderName = newFolderName
            dragPreviewFolders = dragPreviewFolders + newFolderName
        }
    }
    val renameFolder: (String, String) -> Unit = { oldName, newName ->
        scope.launch {
            settingsManager.renameFolder(oldName, newName)
            if (selectedFolderName == oldName) {
                selectedFolderName = newName
            }
            dragPreviewFolders = dragPreviewFolders.map { folderName ->
                if (folderName == oldName) newName else folderName
            }
            refreshLibrary()
        }
    }
    val deleteFolder: (String) -> Unit = { folderName ->
        scope.launch {
            settingsManager.deleteFolder(folderName)
            if (selectedFolderName == folderName) {
                selectedFolderName = RootLibraryName
            }
            dragPreviewFolders = dragPreviewFolders.filter { it != folderName }
            refreshLibrary()
        }
    }
    val deleteSelectedBooksAction: () -> Unit = {
        scope.launch {
            deleteSelectedBooks(parser, settingsManager, books, selectedBookIds)
            clearBookSelection()
            refreshLibrary()
        }
    }
    val deleteSelectedFolders: () -> Unit = {
        scope.launch {
            val toDelete = foldersToDelete.toSet()
            toDelete.forEach { settingsManager.deleteFolder(it) }
            if (toDelete.contains(selectedFolderName)) {
                selectedFolderName = RootLibraryName
            }
            dragPreviewFolders = dragPreviewFolders.filter { !toDelete.contains(it) }
            clearFolderSelection()
            refreshLibrary()
        }
    }
    val dismissFirstTimeNote: () -> Unit = {
        scope.launch {
            settingsManager.setFirstTime(false)
            showFirstTimeNote = false
        }
    }
    val dismissChangelog: () -> Unit = {
        scope.launch {
            settingsManager.setLastSeenVersionCode(detectedVersionCode)
            changelogToShow = emptyList()
        }
    }

    // The bundled contracts below are the second-pass context reduction layer. They keep the
    // rendering files small enough to inspect without replaying every shell variable by hand.
    val selectionState = BookSelectionUiState(
        isBookSelectionMode = isBookSelectionMode,
        selectedBookIds = selectedBookIds,
    )
    val folderDrawerState = FolderDrawerUiState(
        folders = folders,
        displayedFolders = displayedFolders,
        isMovingMode = isMovingMode,
        isFolderSelectionMode = isFolderSelectionMode,
        foldersToDelete = foldersToDelete,
        draggedFolderName = draggedFolderName,
        dragOffset = dragOffset,
    )
    val libraryScreenState = LibraryScreenState(
        settingsManager = settingsManager,
        globalSettings = globalSettings,
        drawerState = drawerState,
        snackbarHostState = snackbarHostState,
        books = books,
        libraryItems = libraryItems,
        lastOpenedBook = lastOpenedBook,
        selectedFolderName = selectedFolderName,
        isLoading = isLoading,
        selection = selectionState,
        folderDrawer = folderDrawerState,
    )
    val libraryScreenActions = LibraryScreenActions(
        onAddBookClick = { launcher.launch(arrayOf("application/epub+zip")) },
        onRefreshLibrary = ::refreshLibrary,
        onOpenDrawer = openDrawer,
        onSetFavoriteFolder = {
            scope.launch { settingsManager.setFavoriteLibrary(selectedFolderName) }
        },
        onShowSortMenu = { showSortMenu = true },
        onOpenSettings = { currentScreen = Screen.Settings },
        onSelectAllBooks = { selectedBookIds = libraryItems.map { it.id }.toSet() },
        onClearBookSelection = { clearBookSelection() },
        onOpenBook = openBook,
        onToggleBookSelection = { book ->
            val isSelected = selectedBookIds.contains(book.id)
            selectedBookIds = if (isSelected) {
                val newSelection = selectedBookIds - book.id
                if (newSelection.isEmpty()) {
                    isBookSelectionMode = false
                }
                newSelection
            } else {
                selectedBookIds + book.id
            }
        },
        onStartBookSelection = { book ->
            if (!isBookSelectionMode) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                isBookSelectionMode = true
                selectedBookIds = setOf(book.id)
            }
        },
        folderDrawer = FolderDrawerActions(
            onCloseDrawer = closeDrawer,
            onSelectFolder = { folderName ->
                selectedFolderName = folderName
                closeDrawer()
            },
            onMoveSelectedBooks = moveSelectedBooksToFolder,
            onEnterFolderSelectionMode = { isFolderSelectionMode = true },
            onExitFolderSelectionMode = { clearFolderSelection() },
            onToggleFolderSelection = { folderName ->
                if (folderName != RootLibraryName) {
                    foldersToDelete = if (foldersToDelete.contains(folderName)) {
                        foldersToDelete - folderName
                    } else {
                        foldersToDelete + folderName
                    }
                }
            },
            onSelectAllFolders = {
                foldersToDelete = folders.filter { it != RootLibraryName }.toSet()
            },
            onClearFolderSelection = { foldersToDelete = emptySet() },
            onRequestDeleteSelectedFolders = { showBulkFolderDeleteConfirm = true },
            onShowCreateFolderDialog = { showCreateFolderDialog = true },
            onRequestRenameFolder = { folderToRename = it },
            onRequestDeleteFolder = { folderToDelete = it },
            onStartFolderDrag = startFolderDrag,
            onDragFolder = dragFolderBy,
            onEndFolderDrag = completeFolderDrag,
            onCancelFolderDrag = cancelFolderDrag,
        ),
    )
    val selectionBarState = BookSelectionActionBarState(
        visible = isBookSelectionMode && !isMovingMode,
        theme = globalSettings.theme,
    )
    val selectionBarActions = BookSelectionActionBarActions(
        onDeleteSelectedBooks = {
            if (selectedBookIds.isNotEmpty()) {
                showBulkBookDeleteConfirm = true
            }
        },
        onMoveSelectedBooks = {
            if (selectedBookIds.isNotEmpty()) {
                isMovingMode = true
                openDrawer()
            }
        },
    )
    val dialogState = LibraryDialogState(
        showSortMenu = showSortMenu,
        currentSort = currentSort,
        existingFolders = folders,
        showCreateFolderDialog = showCreateFolderDialog,
        folderToRename = folderToRename,
        folderToDelete = folderToDelete,
        showBulkBookDeleteConfirm = showBulkBookDeleteConfirm,
        selectedBookCount = selectedBookIds.size,
        showBulkFolderDeleteConfirm = showBulkFolderDeleteConfirm,
        selectedFolderCount = foldersToDelete.size,
        showFirstTimeNote = showFirstTimeNote,
        changelogEntries = changelogToShow,
    )
    val dialogActions = LibraryDialogActions(
        onDismissSortMenu = { showSortMenu = false },
        onSelectSort = { sort ->
            scope.launch {
                settingsManager.setLibrarySort(selectedFolderName, sort)
            }
        },
        onDismissCreateFolder = { showCreateFolderDialog = false },
        onCreateFolder = createFolder,
        onDismissRenameFolder = { folderToRename = null },
        onRenameFolder = renameFolder,
        onDismissDeleteFolder = { folderToDelete = null },
        onDeleteFolder = deleteFolder,
        onDismissDeleteBooks = { showBulkBookDeleteConfirm = false },
        onDeleteBooks = deleteSelectedBooksAction,
        onDismissDeleteFolders = { showBulkFolderDeleteConfirm = false },
        onDeleteFolders = deleteSelectedFolders,
        onDismissWelcome = dismissFirstTimeNote,
        onDismissChangelog = dismissChangelog,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Rendering delegates to smaller files, but screen ownership stays here.
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "ScreenTransition",
        ) { screen ->
            when (screen) {
                Screen.Settings -> SettingsScreen(
                    settingsManager = settingsManager,
                    onBack = { currentScreen = Screen.Library },
                )
                Screen.Reader -> selectedBook?.let { book ->
                    ReaderScreen(
                        book = book,
                        settingsManager = settingsManager,
                        parser = parser,
                        onBack = { currentScreen = Screen.Library },
                    )
                }
                Screen.Library -> LibraryScreen(
                    state = libraryScreenState,
                    actions = libraryScreenActions,
                )
            }
        }

        BookSelectionActionBar(
            state = selectionBarState,
            actions = selectionBarActions,
        )

        LibraryDialogHost(
            state = dialogState,
            actions = dialogActions,
        )
    }

    BackHandler(
        enabled = currentScreen != Screen.Library || drawerState.isOpen || isFolderSelectionMode || isBookSelectionMode,
    ) {
        when {
            drawerState.isOpen -> closeDrawer()
            isBookSelectionMode -> clearBookSelection()
            isFolderSelectionMode -> clearFolderSelection()
            else -> currentScreen = Screen.Library
        }
    }
}
