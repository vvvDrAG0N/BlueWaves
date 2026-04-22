/**
 * FILE: AppNavigation.kt
 * PURPOSE: High-level UI orchestration for library, reader, and settings navigation.
 * RESPONSIBILITIES:
 *  - Manages screen transitions using [Screen].
 *  - Coordinates library organization, folder management, and drag-and-drop.
 *  - Bridges app-level state between persistence, parser, and UI features.
 */
package com.epubreader.app

import android.app.Activity
import android.net.Uri
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.epubreader.core.model.BookFormat
import com.epubreader.Screen
import com.epubreader.core.model.EpubBook
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.parser.EPUB_MIME_TYPE
import com.epubreader.data.parser.ZIP_COMPRESSED_MIME_TYPE
import com.epubreader.data.parser.ZIP_MIME_TYPE
import com.epubreader.data.settings.SettingsManager
import com.epubreader.feature.editbook.EditBookScreen
import com.epubreader.feature.reader.ReaderScreen
import com.epubreader.feature.settings.SettingsScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
    val parser = remember { EpubParser(context) }

    var books by remember { mutableStateOf(emptyList<EpubBook>()) }
    var selectedBook by remember { mutableStateOf<EpubBook?>(null) }
    var editingBook by remember { mutableStateOf<EpubBook?>(null) }
    var currentScreen by remember { mutableStateOf(Screen.Library) }
    var asyncState by remember { mutableStateOf(LibraryAsyncUiState()) }

    // Disable view-level haptics specifically for the navigation shell view.
    LaunchedEffect(globalSettings.hapticFeedback) {
        view.isHapticFeedbackEnabled = globalSettings.hapticFeedback
    }
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-apply system bar hide after returning from external activities (like file picker)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen) {
            clearFolderSelection()
            isMovingMode = false
        }
    }

    val isLightAppTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    LaunchedEffect(currentScreen, globalSettings.showSystemBar, resumeTrigger, isLightAppTheme) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // Update appearance based on app theme (Library/Settings/EditBook)
        // Reader handles its own appearance in ReaderScreen.kt to support independent reader themes.
        if (currentScreen != Screen.Reader) {
            windowInsetsController.isAppearanceLightStatusBars = isLightAppTheme
            windowInsetsController.isAppearanceLightNavigationBars = isLightAppTheme

            if (globalSettings.showSystemBar) {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            } else if (currentScreen == Screen.Library) {
                // Hide for immersive library browsing if configured.
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                // Always show system bars in Settings for better accessibility and status visibility.
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Startup checks stay in the shell because they drive global dialogs and version bookkeeping.
    LaunchedEffect(Unit) {
        // Yield to the UI thread for initial rendering and JIT warm-up
        delay(150)
        
        val startupDecision = evaluateAppShellStartup(context, globalSettings)
        detectedVersionCode = startupDecision.detectedVersionCode
        showFirstTimeNote = startupDecision.showFirstTimeNote
        changelogToShow = startupDecision.changelogEntries

        if (startupDecision.shouldClearFirstTime) {
            settingsManager.setFirstTime(false)
        }
        startupDecision.versionCodeToMarkSeen?.let { settingsManager.setLastSeenVersionCode(it) }
        
        // Final refresh only if we are on the library screen
        if (currentScreen == Screen.Library) {
            refreshLibrary()
        }
    }

    // Lazy Refresh: Only scan when the user is actually looking at the library or switches folders.
    LaunchedEffect(currentScreen, selectedFolderName) {
        if (currentScreen == Screen.Library) {
            refreshLibrary()
        }
    }

    ObserveLegacyPdfConversionState(
        context = context,
        books = books,
        selectedBook = selectedBook,
        parser = parser,
        lifecycleOwner = lifecycleOwner,
        scope = scope,
        onBookUpdated = ::applyUpdatedBook,
    )

    // The derived models below are intentionally pure. They are the safest place to inspect
    // folder/sort bugs before touching rendering code.
    val bookGroups by produceState(initialValue = JSONObject(), globalSettings.bookGroups) {
        value = withContext(Dispatchers.Default) {
            parseJsonObject(globalSettings.bookGroups)
        }
    }
    val folderSorts by produceState(initialValue = JSONObject(), globalSettings.folderSorts) {
        value = withContext(Dispatchers.Default) {
            parseJsonObject(globalSettings.folderSorts)
        }
    }
    val folderOrder by produceState(initialValue = emptyList<String>(), globalSettings.folderOrder) {
        value = withContext(Dispatchers.Default) {
            parseFolderOrder(globalSettings.folderOrder)
        }
    }

    val folders by produceState(
        initialValue = emptyList<String>(),
        books, globalSettings.bookGroups, globalSettings.folderSorts, folderOrder
    ) {
        value = withContext(Dispatchers.Default) {
            buildFolders(
                books = books,
                bookGroups = bookGroups,
                folderSorts = folderSorts,
                folderOrder = folderOrder,
            )
        }
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

    val libraryItems by produceState(
        initialValue = emptyList(),
        books, globalSettings.bookGroups, currentSort, selectedFolderName
    ) {
        value = withContext(Dispatchers.Default) {
            buildLibraryItems(
                books = books,
                bookGroups = bookGroups,
                currentSort = currentSort,
                selectedFolderName = selectedFolderName,
            )
        }
    }

    val lastOpenedBook = remember(books) {
        books
            .filter { it.lastRead > 0 && it.sourceFormat != BookFormat.PDF }
            .maxByOrNull { it.lastRead }
    }
    val selectedEditableBook = remember(books, selectedBookIds) {
        if (selectedBookIds.size != 1) {
            null
        } else {
            books.firstOrNull { it.id == selectedBookIds.first() && it.sourceFormat == BookFormat.EPUB }
        }
    }

    // Action lambdas remain here so persistence writes and transient shell state stay coordinated.
    val startFolderDrag: (String) -> Unit = { folderName ->
        draggedFolderName = folderName
        dragOffset = 0f
        if (globalSettings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
            if (globalSettings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                asyncState = asyncState.copy(importInFlight = true)
                try {
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
                        is ImportBookResult.Imported -> {
                            applyUpdatedBook(result.book)
                        }
                        is ImportBookResult.Failed -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) {
                        throw error
                    }
                    snackbarHostState.showSnackbar("Couldn't import this book.")
                } finally {
                    asyncState = asyncState.copy(importInFlight = false)
                }
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
                selectedFolderName = trimmedNewName
            }
            dragPreviewFolders = dragPreviewFolders.map { folderName ->
                if (folderName == oldName) trimmedNewName else folderName
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
        asyncState = asyncState,
        selection = selectionState,
        folderDrawer = folderDrawerState,
    )
    val libraryScreenActions = LibraryScreenActions(
        onAddBookClick = {
            launcher.launch(
                arrayOf(
                    EPUB_MIME_TYPE,
                    ZIP_MIME_TYPE,
                    ZIP_COMPRESSED_MIME_TYPE,
                ),
            )
        },
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
                if (globalSettings.hapticFeedback) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
        canEditSelection = selectedEditableBook != null,
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
        onEditSelectedBook = {
            selectedEditableBook?.let(::openEditBook)
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                Screen.EditBook -> editingBook?.let { book ->
                    EditBookScreen(
                        book = book,
                        allowBlankCovers = globalSettings.allowBlankCovers,
                        isSaving = editBookSaveInFlight,
                        errorMessage = editBookErrorMessage,
                        onDismissError = { editBookErrorMessage = null },
                        onBack = ::exitEditBook,
                        onSave = { request ->
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
                        },
                    )
                }
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

    val shellBackAction = resolveShellBackAction(
        currentScreen = currentScreen,
        isDrawerOpen = drawerState.isOpen,
        isFolderSelectionMode = isFolderSelectionMode,
        isBookSelectionMode = isBookSelectionMode,
    )

    BackHandler(enabled = shellBackAction != null) {
        when (shellBackAction) {
            ShellBackAction.ClearFolderSelection -> clearFolderSelection()
            ShellBackAction.CloseDrawer -> closeDrawer()
            ShellBackAction.ClearBookSelection -> clearBookSelection()
            ShellBackAction.GoToLibrary -> currentScreen = Screen.Library
            null -> Unit
        }
    }
}
