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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    AppNavigation(settingsManager)
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
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(140.dp),
                                contentPadding = padding,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(books, key = { it.id }) { book ->
                                    BookItem(
                                        book = book,
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

        if (isLoading) {
            LoadingOverlay()
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
fun BookItem(book: EpubBook, onClick: () -> Unit, onLongClick: () -> Unit) {
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
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                maxLines = 2
            )
        }
    }
}

@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Working...")
            }
        }
    }
}
