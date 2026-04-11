/**
 * FILE: MainActivity.kt
 * PURPOSE: Entry point of the application and high-level UI orchestrator.
 * RESPONSIBILITIES:
 *  - Manages top-level navigation state using [Screen] enum.
 *  - Handles library organization (folders, sorting, drag-and-drop).
 *  - Orchestrates app themes (Light, Dark, OLED).
 *  - Manages application lifecycle and version-based updates (changelog).
 * NON-GOALS:
 *  - Does not handle low-level EPUB parsing (see EpubParser).
 *  - Does not manage reading session details (see ReaderScreen).
 * DEPENDENCIES: Material3, Compose, SettingsManager, EpubParser.
 * SIDE EFFECTS: Initializes and triggers library scans.
 */
package com.epubreader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * AI_ENTRY_POINT
 * AI_READ_FIRST
 * AI_RELEVANT_TO: [App Navigation, Library Management, Folder Drag-and-Drop]
 * AI_STATE_OWNER: AppNavigation owns [books, selectedBook, selectedFolderName].
 * 
 * Main Activity of the "Blue Waves" EPUB Reader.
 * AI_NOTE: This app uses a single-activity architecture without Fragment or Jetpack Navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsManager = SettingsManager(this)
        
        setContent {
            val globalSettingsState = settingsManager.globalSettings.collectAsState(initial = null)
            val globalSettings = globalSettingsState.value

            if (globalSettings != null) {
                val darkColorScheme = darkColorScheme(
                    primary = Color(0xFFD0BCFF),
                    secondary = Color(0xFFCCC2DC),
                    tertiary = Color(0xFFEFB8C8)
                )

                val oledColorScheme = darkColorScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color.White,
                    surfaceContainer = Color.Black,
                    surfaceContainerLow = Color.Black,
                    surfaceContainerHigh = Color.Black,
                    surfaceContainerHighest = Color.Black,
                    outline = Color.White.copy(alpha = 0.2f),
                    outlineVariant = Color.White.copy(alpha = 0.1f)
                )

                MaterialTheme(
                    colorScheme = when (globalSettings.theme) {
                        "oled" -> oledColorScheme
                        "dark" -> darkColorScheme
                        else -> lightColorScheme()
                    }
                ) {
                    AppNavigation(settingsManager, globalSettings)
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }
}

enum class Screen {
    Library, Reader, Settings
}

/**
 * PURPOSE: High-level navigation and state container for the entire app.
 * RESPONSIBILITIES:
 *  - Orchestrates Transitions between Library, Reader, and Settings.
 *  - Manages Folder and Sorting logic for the Library.
 *  - Handles drag-and-drop state for book organization.
 * AI_CRITICAL: This is the "Brain" of the app. Adding UI here often requires corresponding 
 * state logic updates in [SettingsManager].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AppNavigation(settingsManager: SettingsManager, globalSettings: GlobalSettings) {
    // AI_STATE_OWNER: AppNavigation composable
    // AI_STATE_FLOW: SettingsManager → GlobalSettings Flow → UI
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val parser = remember { EpubParser(context) }
    
    // State
    // AI_STATE_OWNER: AppNavigation
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
    
    // Default to the user's favorite library or My Library
    var selectedFolderName by remember(globalSettings.favoriteLibrary) { 
        mutableStateOf(globalSettings.favoriteLibrary.ifEmpty { "My Library" }) 
    }
    var appOffset by remember { mutableStateOf(Offset.Zero) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Folder drag-and-drop state
    var dragPreviewFolders by remember { mutableStateOf(emptyList<String>()) }
    var pendingFolderOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggedFolderName by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Clear drag, move, and selection state when drawer closes
    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen) {
            isFolderSelectionMode = false
            foldersToDelete = emptySet()
            isMovingMode = false
        }
    }

    var showFirstTimeNote by remember { mutableStateOf(false) }
    var changelogToShow by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var detectedVersionCode by remember { mutableIntStateOf(0) }

    // Helper functions
    /**
     * PURPOSE: Re-scans the local file system for books and updates the [books] state.
     * AI_NOTE: Should be triggered after any file system change or folder reorganization.
     */
    fun refreshLibrary() {
        scope.launch {
            isLoading = true
            val scannedBooks = withContext(Dispatchers.IO) { parser.scanBooks() }
            books = scannedBooks
            isLoading = false
        }
    }

    // Initialize/Refresh Library and perform version-based checks/migrations
    LaunchedEffect(Unit) {
        refreshLibrary()
        
        // Version and First Time Logic: Checks if this is a new install or an update
        val currentVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) { 0 }
        
        detectedVersionCode = currentVersion
        
        val hasExistingData = withContext(Dispatchers.IO) {
            val booksDir = File(context.cacheDir, "books")
            booksDir.exists() && (booksDir.listFiles()?.any { it.isDirectory } == true)
        }

        if (globalSettings.firstTime && !hasExistingData) {
            showFirstTimeNote = true
        } else if (currentVersion > 0) {
            var lastSeen = globalSettings.lastSeenVersionCode
            if (globalSettings.firstTime && hasExistingData) {
                settingsManager.setFirstTime(false)
                if (lastSeen == 0) lastSeen = 1
            }

            if (lastSeen < currentVersion) {
                try {
                    val jsonString = context.assets.open("changelog.json").bufferedReader().use { it.readText() }
                    val fullChangelog = JSONArray(jsonString)
                    val newChanges = mutableListOf<JSONObject>()
                    for (i in 0 until fullChangelog.length()) {
                        val entry = fullChangelog.getJSONObject(i)
                        if (entry.getInt("versionCode") > lastSeen) {
                            newChanges.add(entry)
                        }
                    }
                    if (newChanges.isNotEmpty()) {
                        changelogToShow = newChanges
                    } else {
                        settingsManager.setLastSeenVersionCode(currentVersion)
                    }
                } catch (e: Exception) {
                    settingsManager.setLastSeenVersionCode(currentVersion)
                }
            }
        }
    }

    // Auto-refresh when folder changes
    LaunchedEffect(selectedFolderName) {
        refreshLibrary()
    }

    // Refresh when returning to library
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.Library) {
            refreshLibrary()
        }
    }

    // Memoized derived states
    val bookGroups = remember(globalSettings.bookGroups) {
        try { JSONObject(globalSettings.bookGroups) } catch (e: Exception) { JSONObject() }
    }
    
    val folderSorts = remember(globalSettings.folderSorts) {
        try { JSONObject(globalSettings.folderSorts) } catch (e: Exception) { JSONObject() }
    }

    val folderOrder = remember(globalSettings.folderOrder) {
        try { JSONArray(globalSettings.folderOrder).let { arr -> List(arr.length()) { arr.getString(it) } } } catch (e: Exception) { emptyList<String>() }
    }

    // Derived Folders: Computes the list of folders based on book metadata and custom order.
    // AI_NOTE: "My Library" is always present and usually first.
    val folders = remember(books, globalSettings.bookGroups, globalSettings.folderSorts, folderOrder) {
        val groups = mutableSetOf<String>()
        books.forEach { book ->
            val groupName = bookGroups.optString(book.id, "")
            if (groupName.isNotEmpty()) groups.add(groupName)
        }
        try {
            val sorts = JSONObject(globalSettings.folderSorts)
            sorts.keys().forEach { groups.add(it as String) }
        } catch (e: Exception) {}
        
        val allFolders = (groups + "My Library").toMutableList()
        val sortedFolders = mutableListOf<String>()
        
        // Always "My Library" first
        sortedFolders.add("My Library")
        allFolders.remove("My Library")
        
        // Add folders based on saved order
        folderOrder.forEach { name ->
            if (allFolders.contains(name)) {
                sortedFolders.add(name)
                allFolders.remove(name)
            }
        }
        
        // Add any remaining folders alphabetically
        sortedFolders.addAll(allFolders.sorted())
        sortedFolders.toList()
    }

    LaunchedEffect(folders, draggedFolderName, pendingFolderOrder) {
        if (draggedFolderName == null) {
            if (pendingFolderOrder == null || folders == pendingFolderOrder) {
                dragPreviewFolders = folders
                pendingFolderOrder = null
            }
        }
    }
    val displayedFolders = when {
        draggedFolderName != null -> dragPreviewFolders
        pendingFolderOrder != null -> pendingFolderOrder ?: folders
        else -> folders
    }

    val currentSort = remember(selectedFolderName, globalSettings.librarySort, globalSettings.folderSorts) {
        if (selectedFolderName == "My Library") {
            globalSettings.librarySort
        } else {
            folderSorts.optString(selectedFolderName, "added_desc")
        }
    }

    // Derived Library Items: Filters and sorts the books shown in the current folder.
    val libraryItems = remember(books, globalSettings.bookGroups, currentSort, selectedFolderName) {
        val filteredBooks = if (selectedFolderName == "My Library") {
            books.filter { bookGroups.optString(it.id, "").isEmpty() }
        } else {
            books.filter { bookGroups.optString(it.id, "") == selectedFolderName }
        }

        val sortParts = currentSort.split("_")
        val type = sortParts.getOrNull(0) ?: "added"
        val isDesc = sortParts.getOrNull(1) == "desc"

        val comparator = when (type) {
            "title" -> compareBy<EpubBook> { it.title.lowercase() }
            "author" -> compareBy<EpubBook> { it.author.lowercase() }
            "recent" -> compareBy<EpubBook> { it.lastRead }
            "added" -> compareBy<EpubBook> { it.dateAdded }
            "chapters" -> compareBy<EpubBook> { it.spineHrefs.size }
            else -> compareBy<EpubBook> { it.dateAdded }
        }

        if (isDesc) filteredBooks.sortedWith(comparator).reversed() else filteredBooks.sortedWith(comparator)
    }

    // Global Recently Viewed (Persists across folders)
    val lastOpenedBook = remember(books) {
        books.filter { it.lastRead > 0 }
            .maxByOrNull { it.lastRead }
    }

    // File Picker Launcher: Handles importing new EPUB files.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                val existingBook = withContext(Dispatchers.IO) {
                    val fileDescriptor = context.contentResolver.openFileDescriptor(it, "r")
                    val fileSize = fileDescriptor?.statSize ?: 0L
                    fileDescriptor?.close()
                    val rawId = "$it$fileSize"
                    val bookId = MessageDigest.getInstance("MD5")
                        .digest(rawId.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                    books.find { b -> b.id == bookId }
                }

                if (existingBook != null) {
                    val folderName = bookGroups.optString(existingBook.id, "").ifEmpty { "My Library" }
                    snackbarHostState.showSnackbar("This book is already in your library ($folderName).")
                } else {
                    val newBook = withContext(Dispatchers.IO) { parser.parseAndExtract(it) }
                    if (newBook != null) {
                        if (selectedFolderName != "My Library") {
                            settingsManager.updateBookGroup(newBook.id, selectedFolderName)
                        } else {
                            settingsManager.updateBookGroup(newBook.id, null)
                        }
                        refreshLibrary()
                    }
                }
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { appOffset = it.positionInWindow() }) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                Screen.Settings -> SettingsScreen(settingsManager, onBack = { currentScreen = Screen.Library })
                Screen.Reader -> selectedBook?.let { book ->
                    ReaderScreen(
                        book = book,
                        settingsManager = settingsManager,
                        parser = parser,
                        onBack = { currentScreen = Screen.Library }
                    )
                }
                Screen.Library -> {
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = !isBookSelectionMode || drawerState.isOpen,
                        drawerContent = {
                            ModalDrawerSheet {
                                val isMoveMode = isMovingMode
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isFolderSelectionMode) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(
                                                    if (foldersToDelete.isEmpty()) "Select Item" else "${foldersToDelete.size} Selected",
                                                    style = MaterialTheme.typography.titleLarge
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (foldersToDelete.isNotEmpty()) {
                                                    IconButton(onClick = { showBulkFolderDeleteConfirm = true }) {
                                                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }

                                                val allSelectableFolders = folders.filter { it != "My Library" }
                                                if (foldersToDelete.size < allSelectableFolders.size) {
                                                    IconButton(onClick = { foldersToDelete = allSelectableFolders.toSet() }) {
                                                        Icon(Icons.Default.SelectAll, "Select All")
                                                    }
                                                } else {
                                                    IconButton(onClick = { foldersToDelete = emptySet() }) {
                                                        Icon(Icons.Default.Deselect, "Deselect All")
                                                    }
                                                }
                                                
                                                IconButton(onClick = { 
                                                    isFolderSelectionMode = false
                                                    foldersToDelete = emptySet()
                                                }) {
                                                    Icon(Icons.Default.Close, "Cancel Selection")
                                                }
                                            }
                                        }
                                    } else {
                                        Text(if (isMoveMode) "Move To" else "Libraries", style = MaterialTheme.typography.titleLarge)
                                        if (isMoveMode) {
                                            IconButton(onClick = { 
                                                scope.launch { 
                                                    drawerState.close()
                                                }
                                            }) {
                                                Icon(Icons.Default.Close, contentDescription = "Cancel Move")
                                            }
                                        } else {
                                            Row {
                                                IconButton(onClick = { isFolderSelectionMode = true }) {
                                                    Icon(Icons.Default.Checklist, "Selection Mode")
                                                }
                                                IconButton(onClick = { showCreateFolderDialog = true }) {
                                                    Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                                val listState = rememberLazyListState()
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val itemHeightPx = with(density) { 60.dp.toPx() }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    itemsIndexed(displayedFolders, key = { _, it -> it }) { _, folderName ->
                                        val isFavorite = globalSettings.favoriteLibrary == folderName
                                        val isDragged = folderName == draggedFolderName
                                        val isSelected = foldersToDelete.contains(folderName)
                                        
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (isDragged) 10f else 0f)
                                                .graphicsLayer {
                                                    translationY = if (isDragged) dragOffset else 0f
                                                }
                                                .then(
                                                    if (folderName != "My Library" && !isMoveMode && !isFolderSelectionMode) {
                                                        Modifier.pointerInput(folderName) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = {
                                                                    draggedFolderName = folderName
                                                                    dragOffset = 0f
                                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    dragOffset += dragAmount.y

                                                                    val currentIdx = dragPreviewFolders.indexOf(draggedFolderName)
                                                                    if (currentIdx == -1) return@detectDragGesturesAfterLongPress

                                                                    if (dragOffset > itemHeightPx * 0.6f && currentIdx < dragPreviewFolders.size - 1) {
                                                                        val updated = dragPreviewFolders.toMutableList()
                                                                        val item = updated.removeAt(currentIdx)
                                                                        updated.add(currentIdx + 1, item)
                                                                        dragPreviewFolders = updated
                                                                        dragOffset -= itemHeightPx
                                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    } else if (dragOffset < -itemHeightPx * 0.6f && currentIdx > 1) {
                                                                        val updated = dragPreviewFolders.toMutableList()
                                                                        val item = updated.removeAt(currentIdx)
                                                                        updated.add(currentIdx - 1, item)
                                                                        dragPreviewFolders = updated
                                                                        dragOffset += itemHeightPx
                                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    }
                                                                },
                                                                onDragEnd = {
                                                                    val reorderedFolders = dragPreviewFolders.toList()
                                                                    pendingFolderOrder = reorderedFolders
                                                                    draggedFolderName = null
                                                                    dragOffset = 0f
                                                                    scope.launch {
                                                                        settingsManager.updateFolderOrder(reorderedFolders.filter { it != "My Library" })
                                                                    }
                                                                },
                                                                onDragCancel = {
                                                                    draggedFolderName = null
                                                                    pendingFolderOrder = null
                                                                    dragPreviewFolders = folders
                                                                    dragOffset = 0f
                                                                }
                                                            )
                                                        }
                                                    } else Modifier
                                                )
                                        ) {
                                            NavigationDrawerItem(
                                                label = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(folderName, modifier = Modifier.weight(1f))
                                                        if (isFavorite && !isMoveMode && !isFolderSelectionMode) {
                                                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                },
                                                selected = (selectedFolderName == folderName && !isMoveMode && !isFolderSelectionMode) || (isFolderSelectionMode && isSelected),
                                                onClick = {
                                                    if (isFolderSelectionMode) {
                                                        if (folderName != "My Library") {
                                                            foldersToDelete = if (isSelected) foldersToDelete - folderName else foldersToDelete + folderName
                                                        }
                                                    } else if (isMoveMode) {
                                                        scope.launch {
                                                            selectedBookIds.forEach { id ->
                                                                settingsManager.updateBookGroup(id, if (folderName == "My Library") null else folderName)
                                                            }
                                                            drawerState.close()
                                                            isMovingMode = false
                                                            isBookSelectionMode = false
                                                            selectedBookIds = emptySet()
                                                            refreshLibrary()
                                                        }
                                                    } else {
                                                        selectedFolderName = folderName
                                                        scope.launch { drawerState.close() }
                                                    }
                                                },
                                                icon = {
                                                    Icon(
                                                        if (folderName == "My Library") Icons.AutoMirrored.Filled.LibraryBooks else Icons.Default.Folder,
                                                        null,
                                                        tint = if (selectedFolderName == folderName && !isMoveMode && !isFolderSelectionMode) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                                    )
                                                },
                                                colors = NavigationDrawerItemDefaults.colors(
                                                    unselectedContainerColor = if (isDragged) {
                                                        // Use a semi-transparent white/gray for drag highlight in OLED to ensure visibility
                                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                                    } else Color.Transparent,
                                                    selectedContainerColor = if (isDragged) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                                                    } else MaterialTheme.colorScheme.primaryContainer,
                                                ),
                                                modifier = Modifier
                                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                                    .height(56.dp)
                                                    .graphicsLayer {
                                                        val s = if (isDragged) 1.05f else 1f
                                                        scaleX = s
                                                        scaleY = s
                                                        shadowElevation = if (isDragged) 10f else 0f
                                                        shape = CircleShape
                                                        clip = isDragged
                                                    },
                                                shape = CircleShape,
                                                badge = {
                                                    if (folderName != "My Library" && !isMoveMode) {
                                                        if (isFolderSelectionMode) {
                                                            if (isSelected) {
                                                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                            }
                                                        } else {
                                                            Row {
                                                                IconButton(onClick = { folderToRename = folderName }, modifier = Modifier.size(24.dp)) {
                                                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                                                                }
                                                                IconButton(onClick = { folderToDelete = folderName }, modifier = Modifier.size(24.dp)) {
                                                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            floatingActionButton = {
                                if (!isBookSelectionMode) {
                                    FloatingActionButton(onClick = { launcher.launch(arrayOf("application/epub+zip")) }) {
                                        Icon(Icons.Default.Add, "Add Book")
                                    }
                                }
                            },
                            topBar = {
                                if (isBookSelectionMode) {
                                    TopAppBar(
                                        title = { Text(if (selectedBookIds.isEmpty()) "Select Item" else "${selectedBookIds.size} Selected") },
                                        actions = {
                                            val allIds = libraryItems.map { it.id }.toSet()
                                            if (selectedBookIds.size < allIds.size) {
                                                IconButton(onClick = { selectedBookIds = allIds }) {
                                                    Icon(Icons.Default.SelectAll, "Select All")
                                                }
                                            } else {
                                                IconButton(onClick = { selectedBookIds = emptySet() }) {
                                                    Icon(Icons.Default.Deselect, "Deselect All")
                                                }
                                            }
                                            IconButton(onClick = {
                                                isBookSelectionMode = false
                                                selectedBookIds = emptySet()
                                                isMovingMode = false
                                            }) {
                                                Icon(Icons.Default.Close, "Cancel Selection")
                                            }
                                        }
                                    )
                                } else {
                                    TopAppBar(
                                        title = { Text(selectedFolderName) },
                                        navigationIcon = {
                                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                Icon(Icons.Default.Menu, "Menu")
                                            }
                                        },
                                        actions = {
                                            IconButton(onClick = {
                                                scope.launch { settingsManager.setFavoriteLibrary(selectedFolderName) }
                                            }) {
                                                val isFav = globalSettings.favoriteLibrary == selectedFolderName
                                                Icon(if (isFav) Icons.Default.Star else Icons.Default.StarOutline, "Favorite", tint = if (isFav) Color(0xFFFFD700) else LocalContentColor.current)
                                            }
                                            IconButton(onClick = { showSortMenu = true }) {
                                                Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                                            }
                                            IconButton(onClick = { currentScreen = Screen.Settings }) {
                                                Icon(Icons.Default.Settings, "Settings")
                                            }
                                        }
                                    )
                                }
                            }
                        ) { padding ->
                            val pullRefreshState = rememberPullRefreshState(
                                refreshing = isLoading,
                                onRefresh = { refreshLibrary() }
                            )

                            Box(modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .pullRefresh(pullRefreshState)
                            ) {
                                val hasAnyBooks = books.isNotEmpty()

                                if (!hasAnyBooks && !isLoading) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.AutoMirrored.Filled.LibraryBooks, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                                            Spacer(Modifier.height(16.dp))
                                            Text("Your library is empty", style = MaterialTheme.typography.titleMedium)
                                            Text("Add EPUB files to start reading", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(140.dp),
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Persistent "Recently Viewed" strip
                                        lastOpenedBook?.let { book ->
                                            item(span = { GridItemSpan(maxLineSpan) }) {
                                                RecentlyViewedStrip(book, settingsManager, globalSettings, onOpen = {
                                                    selectedBook = it
                                                    currentScreen = Screen.Reader
                                                    scope.launch {
                                                        val updated = it.copy(lastRead = System.currentTimeMillis())
                                                        withContext(Dispatchers.IO) { parser.updateLastRead(updated) }
                                                        books = books.map { b -> if (b.id == it.id) updated else b }
                                                    }
                                                })
                                            }
                                        }

                                        // Folder empty indicator
                                        if (libraryItems.isEmpty() && !isLoading) {
                                            item(span = { GridItemSpan(maxLineSpan) }) {
                                                Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                                                    Text("This folder is empty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }

                                        items(libraryItems, key = { it.id }) { book ->
                                            val isSelected = selectedBookIds.contains(book.id)
                                            Box(
                                                modifier = Modifier
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isBookSelectionMode) {
                                                                selectedBookIds = if (isSelected) {
                                                                    val newSet = selectedBookIds - book.id
                                                                    if (newSet.isEmpty()) isBookSelectionMode = false
                                                                    newSet
                                                                } else {
                                                                    selectedBookIds + book.id
                                                                }
                                                            } else {
                                                                selectedBook = book
                                                                currentScreen = Screen.Reader
                                                                scope.launch {
                                                                    val updated = book.copy(lastRead = System.currentTimeMillis())
                                                                    withContext(Dispatchers.IO) { parser.updateLastRead(updated) }
                                                                    books = books.map { b -> if (b.id == book.id) updated else b }
                                                                }
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (!isBookSelectionMode) {
                                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                isBookSelectionMode = true
                                                                selectedBookIds = setOf(book.id)
                                                            }
                                                        }
                                                    )
                                            ) {
                                                BookItem(book, settingsManager, isSelected = isSelected)
                                            }
                                        }
                                    }
                                }

                                PullRefreshIndicator(
                                    refreshing = isLoading,
                                    state = pullRefreshState,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overlays (Selection Bar, Dialogs)
        if (isBookSelectionMode && !isMovingMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Surface(
                    color = if (globalSettings.theme == "oled") Color.Black else MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = if (globalSettings.theme == "oled") 0.dp else 8.dp,
                    shadowElevation = 12.dp,
                    border = if (globalSettings.theme == "oled") BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .height(72.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Delete Button
                        Surface(
                            onClick = { if (selectedBookIds.isNotEmpty()) showBulkBookDeleteConfirm = true },
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Move Button
                        Surface(
                            onClick = { 
                                if (selectedBookIds.isNotEmpty()) {
                                    isMovingMode = true
                                    scope.launch { drawerState.open() }
                                }
                            },
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.DriveFileMove,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sort Menu
        if (showSortMenu) {
            ModalBottomSheet(onDismissRequest = { showSortMenu = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Sort By", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    val sortParts = currentSort.split("_")
                    val currentType = sortParts.getOrNull(0) ?: "added"
                    val currentOrder = sortParts.getOrNull(1) ?: "desc"

                    listOf("added" to "Date Added", "title" to "Title", "author" to "Author", "chapters" to "Chapters", "recent" to "Recent").forEach { (key, label) ->
                        Surface(
                            onClick = {
                                scope.launch {
                                    val newOrder = if (currentType == key) (if (currentOrder == "asc") "desc" else "asc") else (if (key == "recent" || key == "added") "desc" else "asc")
                                    settingsManager.setLibrarySort(selectedFolderName, "${key}_$newOrder")
                                }
                            },
                            color = if (currentType == key) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f))
                                if (currentType == key) {
                                    Icon(if (currentOrder == "asc") Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialogs
        if (showCreateFolderDialog) {
            var folderName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showCreateFolderDialog = false },
                title = { Text("Create Folder") },
                text = { TextField(value = folderName, onValueChange = { folderName = it }, placeholder = { Text("Folder Name") }, singleLine = true) },
                confirmButton = { TextButton(onClick = {
                    val newFolderName = folderName.trim()
                    if (newFolderName.isNotBlank() && !folders.contains(newFolderName)) {
                        scope.launch {
                            settingsManager.createFolder(newFolderName)
                            selectedFolderName = newFolderName
                            dragPreviewFolders = dragPreviewFolders + newFolderName
                        }
                    }
                    showCreateFolderDialog = false
                }) { Text("Create") } },
                dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
            )
        }

        folderToRename?.let { oldName ->
            var newName by remember { mutableStateOf(oldName) }
            AlertDialog(
                onDismissRequest = { folderToRename = null },
                title = { Text("Rename Folder") },
                text = { TextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
                confirmButton = { TextButton(onClick = {
                    val renamedFolder = newName.trim()
                    if (renamedFolder.isNotBlank() && renamedFolder != oldName) {
                        scope.launch {
                            settingsManager.renameFolder(oldName, renamedFolder)
                            if (selectedFolderName == oldName) selectedFolderName = renamedFolder
                            dragPreviewFolders = dragPreviewFolders.map { if (it == oldName) renamedFolder else it }
                            refreshLibrary()
                        }
                    }
                    folderToRename = null
                }) { Text("Rename") } },
                dismissButton = { TextButton(onClick = { folderToRename = null }) { Text("Cancel") } }
            )
        }

        folderToDelete?.let { folderName ->
            AlertDialog(
                onDismissRequest = { folderToDelete = null },
                title = { Text("Delete Folder") },
                text = { Text("Delete \"$folderName\"? Books inside will move to My Library.") },
                confirmButton = { TextButton(onClick = {
                    scope.launch {
                        settingsManager.deleteFolder(folderName)
                        if (selectedFolderName == folderName) selectedFolderName = "My Library"
                        dragPreviewFolders = dragPreviewFolders.filter { it != folderName }
                        refreshLibrary()
                    }
                    folderToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { folderToDelete = null }) { Text("Cancel") } }
            )
        }

        if (showBulkBookDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBulkBookDeleteConfirm = false },
                title = { Text("Delete Books") },
                text = { Text("Remove ${selectedBookIds.size} books from library?") },
                confirmButton = { TextButton(onClick = {
                    scope.launch {
                        val idsToDelete = selectedBookIds.toList()
                        withContext(Dispatchers.IO) {
                            idsToDelete.forEach { id ->
                                books.find { it.id == id }?.let { parser.deleteBook(it) }
                            }
                        }
                        idsToDelete.forEach { settingsManager.deleteBookData(it) }
                        isBookSelectionMode = false
                        selectedBookIds = emptySet()
                        refreshLibrary()
                    }
                    showBulkBookDeleteConfirm = false
                }) { Text("Delete All", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { showBulkBookDeleteConfirm = false }) { Text("Cancel") } }
            )
        }

        if (showBulkFolderDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showBulkFolderDeleteConfirm = false },
                title = { Text("Delete Folders") },
                text = { Text("Delete ${foldersToDelete.size} folders? Books inside will move to My Library.") },
                confirmButton = { TextButton(onClick = {
                    scope.launch {
                        val toDelete = foldersToDelete.toSet()
                        toDelete.forEach { settingsManager.deleteFolder(it) }
                        if (toDelete.contains(selectedFolderName)) selectedFolderName = "My Library"
                        dragPreviewFolders = dragPreviewFolders.filter { !toDelete.contains(it) }
                        isFolderSelectionMode = false
                        foldersToDelete = emptySet()
                        refreshLibrary()
                    }
                    showBulkFolderDeleteConfirm = false
                }) { Text("Delete All", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { showBulkFolderDeleteConfirm = false }) { Text("Cancel") } }
            )
        }

        // Changelog & Welcome
        if (showFirstTimeNote) {
            AlertDialog(
                onDismissRequest = { scope.launch { settingsManager.setFirstTime(false); showFirstTimeNote = false } },
                title = { Text("Welcome to Blue Waves") },
                text = { Text("Native high-performance EPUB reader.") },
                confirmButton = { Button(onClick = { scope.launch { settingsManager.setFirstTime(false); showFirstTimeNote = false } }) { Text("Start") } }
            )
        }

        if (changelogToShow.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { scope.launch { settingsManager.setLastSeenVersionCode(detectedVersionCode); changelogToShow = emptyList() } },
                title = { Text("What's New") },
                text = {
                    Column {
                        changelogToShow.forEach { entry ->
                            Text(entry.optString("versionName", "Update"), color = MaterialTheme.colorScheme.primary)
                            val changes = entry.optJSONArray("changes")
                            if (changes != null) {
                                for (i in 0 until changes.length()) {
                                    Text("• ${changes.getString(i)}")
                                }
                            }
                        }
                    }
                },
                confirmButton = { Button(onClick = { scope.launch { settingsManager.setLastSeenVersionCode(detectedVersionCode); changelogToShow = emptyList() } }) { Text("OK") } }
            )
        }
    }

    BackHandler(enabled = currentScreen != Screen.Library || drawerState.isOpen || isFolderSelectionMode || isBookSelectionMode) {
        if (drawerState.isOpen) {
            scope.launch {
                drawerState.close()
            }
        } else if (isBookSelectionMode) {
            isBookSelectionMode = false
            selectedBookIds = emptySet()
            isMovingMode = false
        } else if (isFolderSelectionMode) {
            isFolderSelectionMode = false
            foldersToDelete = emptySet()
        } else {
            currentScreen = Screen.Library
        }
    }
}

@Composable
fun RecentlyViewedStrip(book: EpubBook, settingsManager: SettingsManager, globalSettings: GlobalSettings, onOpen: (EpubBook) -> Unit) {
    // AI_SKIP_UNLESS_NEEDED
    // Reason: UI composition for recently viewed book. Logic is in AppNavigation.
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("Recently Viewed", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        val bookFolder = remember(book.id, globalSettings.bookGroups) {
            try { JSONObject(globalSettings.bookGroups).optString(book.id, "My Library") } catch (e: Exception) { "My Library" }
        }
        val progress by settingsManager.getBookProgress(book.id).collectAsState(initial = BookProgress())
        val chapterIndex = remember(progress, book) {
            val idx = book.spineHrefs.indexOf(progress.lastChapterHref)
            if (idx == -1) 1 else idx + 1
        }

        Surface(
            onClick = { onOpen(book) },
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(vertical = 4.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(30.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp))) {
                    if (book.coverPath != null) {
                        AsyncImage(model = File(book.coverPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Book, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = bookFolder, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                Text(text = "$chapterIndex / ${book.spineHrefs.size}", style = MaterialTheme.typography.labelSmall)
                Icon(Icons.AutoMirrored.Filled.ArrowRight, null, modifier = Modifier.size(16.dp))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp).alpha(0.3f))
    }
}

@Composable
fun BookItem(book: EpubBook, settingsManager: SettingsManager, isCompact: Boolean = false, isSelected: Boolean = false) {
    // AI_SKIP_UNLESS_NEEDED
    // Reason: UI composition for library items.
    val progress by settingsManager.getBookProgress(book.id).collectAsState(initial = BookProgress())
    val currentChapter = remember(progress, book.spineHrefs) {
        val index = book.spineHrefs.indexOf(progress.lastChapterHref)
        if (index != -1) index + 1 else 1
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCompact) 0.dp else 2.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
            if (book.coverPath != null) {
                AsyncImage(model = File(book.coverPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                
                if (!isCompact) {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = 300f)))
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                        Text(text = book.title, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                        Text(text = book.author, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(text = "$currentChapter / ${book.spineHrefs.size} ch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Book, null, modifier = Modifier.size(if (isCompact) 32.dp else 48.dp).alpha(0.2f), tint = MaterialTheme.colorScheme.primary)
                }
                
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)), startY = if (isCompact) 100f else 250f)))
                
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(if (isCompact) 4.dp else 8.dp)) {
                    Text(
                        text = book.title, 
                        style = if (isCompact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge, 
                        color = Color.White, 
                        maxLines = 2, 
                        overflow = TextOverflow.Ellipsis, 
                        fontWeight = FontWeight.Bold, 
                        lineHeight = if (isCompact) 10.sp else 14.sp,
                        fontSize = if (isCompact) 9.sp else 12.sp
                    )
                    if (!isCompact) {
                        Text(text = book.author, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(text = "$currentChapter / ${book.spineHrefs.size} ch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                )
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}
