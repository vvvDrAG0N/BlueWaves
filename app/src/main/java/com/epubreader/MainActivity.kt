package com.epubreader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            val context = LocalContext.current
            val settingsManager = remember { SettingsManager(context) }
            val globalSettings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
            
            val isDarkTheme = when (globalSettings.theme) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MaterialTheme(colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        AppNavigation(settingsManager)
                    }
                }
            }
        }
    }
}

enum class Screen { Library, Reader, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(settingsManager: SettingsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val parser = remember { EpubParser(context) }
    
    val globalSettings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    var showFirstTimeNote by remember { mutableStateOf(false) }
    var changelogToShow by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var detectedVersionCode by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val settings = settingsManager.globalSettings.first()
        val currentVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            detectedVersionCode = code
            code
        } catch (e: Exception) {
            0
        }
        
        val hasExistingData = withContext(Dispatchers.IO) {
            val booksDir = File(context.cacheDir, "books")
            booksDir.exists() && (booksDir.listFiles()?.any { it.isDirectory } == true)
        }

        if (settings.firstTime && !hasExistingData) {
            // First time install: Show welcome message, but skip changelog
            showFirstTimeNote = true
        } else if (currentVersion > 0) {
            var lastSeen = settings.lastSeenVersionCode
            
            // If it's an update from an unversioned build (firstTime was true but we have books)
            if (settings.firstTime && hasExistingData) {
                settingsManager.setFirstTime(false)
                // If they have data, they must have had version 1 (1.0.1) or earlier.
                // Setting lastSeen to 1 will show them changes since 1.0.1 (i.e., 1.0.2)
                if (lastSeen == 0) lastSeen = 1
            }

            if (lastSeen < currentVersion) {
                // Parse changelog
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
                    e.printStackTrace()
                    settingsManager.setLastSeenVersionCode(currentVersion)
                }
            }
        }
    }

    var books by remember { mutableStateOf(emptyList<EpubBook>()) }
    var selectedBook by remember { mutableStateOf<EpubBook?>(null) }
    var currentScreen by remember { mutableStateOf(Screen.Library) }
    var isLoading by remember { mutableStateOf(false) }
    var bookToDelete by remember { mutableStateOf<EpubBook?>(null) }

    fun refreshLibrary() {
        scope.launch(Dispatchers.IO) {
            val booksDir = File(context.cacheDir, "books")
            val loadedBooks = booksDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { folder ->
                parser.loadMetadata(folder)
            } ?: emptyList()
            withContext(Dispatchers.Main) {
                books = loadedBooks
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshLibrary()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                isLoading = true
                val newBook = withContext(Dispatchers.IO) { parser.parseAndExtract(it) }
                if (newBook != null) {
                    refreshLibrary()
                }
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                Screen.Settings -> {
                    SettingsScreen(settingsManager, onBack = { currentScreen = Screen.Library })
                }
                Screen.Reader -> {
                    selectedBook?.let { book ->
                        ReaderScreen(
                            book = book,
                            settingsManager = settingsManager,
                            parser = parser,
                            onBack = { currentScreen = Screen.Library }
                        )
                    }
                }
                Screen.Library -> {
                    Scaffold(
                        floatingActionButton = {
                            FloatingActionButton(onClick = { launcher.launch(arrayOf("application/epub+zip")) }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Book")
                            }
                        },
                        topBar = {
                            TopAppBar(
                                title = { Text("My Library") },
                                actions = {
                                    IconButton(onClick = { scope.launch { settingsManager.toggleTheme() } }) {
                                        Icon(Icons.Default.Brightness4, contentDescription = "Toggle Theme")
                                    }
                                    IconButton(onClick = { currentScreen = Screen.Settings }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        if (books.isEmpty() && !isLoading) {
                            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                                Text("No books yet. Add one!", style = MaterialTheme.typography.bodyLarge)
                            }
                        } else {
                            val gridState = rememberLazyGridState()
                            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Adaptive(140.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(books, key = { it.id }) { book ->
                                        BookItem(
                                            book = book,
                                            settingsManager = settingsManager,
                                            onClick = {
                                                selectedBook = book
                                                currentScreen = Screen.Reader
                                            },
                                            onLongClick = { bookToDelete = book }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isLoading) {
            LoadingOverlay()
        }

        if (showFirstTimeNote) {
            AlertDialog(
                onDismissRequest = {
                    scope.launch {
                        settingsManager.setFirstTime(false)
                        settingsManager.setLastSeenVersionCode(detectedVersionCode)
                        showFirstTimeNote = false
                    }
                },
                title = { Text("Welcome to Blue Waves") },
                text = {
                    Column {
                        Text("Blue Waves is a high-performance, native Android EPUB reader designed for a seamless reading experience.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Features:")
                        Text("• Smooth vertical scrolling")
                        Text("• Precise position restoration")
                        Text("• Immersive mode & Custom themes")
                        Text("• Quick Table of Contents access")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Created by vvvDrAGON", style = MaterialTheme.typography.labelLarge)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            settingsManager.setFirstTime(false)
                            settingsManager.setLastSeenVersionCode(detectedVersionCode)
                            showFirstTimeNote = false
                        }
                    }) {
                        Text("Get Started")
                    }
                }
            )
        }

        if (changelogToShow.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = {
                    scope.launch {
                        settingsManager.setLastSeenVersionCode(detectedVersionCode)
                        changelogToShow = emptyList()
                    }
                },
                title = { Text("What's New") },
                text = {
                    LazyColumn {
                        items(changelogToShow) { entry ->
                            val versionName = entry.optString("versionName", "Update")
                            val changes = entry.optJSONArray("changes")
                            
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text("Version $versionName", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                if (changes != null) {
                                    for (i in 0 until changes.length()) {
                                        Text("• ${changes.getString(i)}", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (changelogToShow.indexOf(entry) < changelogToShow.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            settingsManager.setLastSeenVersionCode(detectedVersionCode)
                            changelogToShow = emptyList()
                        }
                    }) {
                        Text("Great!")
                    }
                }
            )
        }

        bookToDelete?.let { book ->
            AlertDialog(
                onDismissRequest = { bookToDelete = null },
                title = { Text("Delete Book") },
                text = { Text("Are you sure you want to remove \"${book.title}\" from your library?") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            parser.deleteBook(book)
                            refreshLibrary()
                        }
                        bookToDelete = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { bookToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }

    BackHandler(enabled = currentScreen != Screen.Library) {
        currentScreen = Screen.Library
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookItem(book: EpubBook, settingsManager: SettingsManager, onClick: () -> Unit, onLongClick: () -> Unit) {
    val progress by settingsManager.getBookProgress(book.id).collectAsState(initial = BookProgress())
    val currentChapter = remember(progress.lastChapterHref, book.spineHrefs) {
        val index = book.spineHrefs.indexOf(progress.lastChapterHref)
        if (index != -1) index + 1 else 1
    }
    val totalChapters = book.spineHrefs.size

    Card(
        modifier = Modifier
            .padding(8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box {
            AsyncImage(
                model = book.coverPath,
                contentDescription = book.title,
                modifier = Modifier
                    .aspectRatio(0.66f)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 300f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 2
                )
                Text(
                    text = "Ch. $currentChapter / $totalChapters",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

