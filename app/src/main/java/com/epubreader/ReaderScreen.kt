package com.epubreader

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

val KarlaFont = FontFamily(
    Font(R.font.karla, FontWeight.Normal)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    book: EpubBook,
    settingsManager: SettingsManager,
    parser: EpubParser,
    onBack: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val globalSettings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    val progress by settingsManager.getBookProgress(book.id).collectAsState(initial = BookProgress())

    var currentChapterIndex by remember { mutableIntStateOf(0) }
    var chapterElements by remember { mutableStateOf<List<ChapterElement>>(emptyList()) }
    var isInitialScrollDone by rememberSaveable(book.id) { mutableStateOf(false) }
    var isRestoringPosition by remember { mutableStateOf(false) }
    var skipRestoration by remember { mutableStateOf(false) }
    var isGestureNavigation by remember { mutableStateOf(false) }
    var shouldScrollToBottom by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val tocListState = rememberLazyListState()
    val themeColors = getThemeColors(globalSettings.theme)

    // Overscroll Navigation State
    var verticalOverscroll by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val overscrollThreshold = with(density) { 80.dp.toPx() }

    // 1. Initial State Sync
    LaunchedEffect(progress.lastChapterHref) {
        if (progress.lastChapterHref != null) {
            val index = book.spineHrefs.indexOf(progress.lastChapterHref)
            if (index != -1) {
                currentChapterIndex = index
                isInitialScrollDone = false // Reset to force restoration for this chapter
            }
        }
    }

    // Load Chapter with Preloading
    LaunchedEffect(currentChapterIndex) {
        val href = book.spineHrefs[currentChapterIndex]
        chapterElements = parser.parseChapter(book.rootPath, href)
        
        // Preload next and previous chapters in background
        scope.launch(Dispatchers.IO) {
            if (currentChapterIndex > 0) {
                parser.parseChapter(book.rootPath, book.spineHrefs[currentChapterIndex - 1])
            }
            if (currentChapterIndex < book.spineHrefs.size - 1) {
                parser.parseChapter(book.rootPath, book.spineHrefs[currentChapterIndex + 1])
            }
        }
    }

    // 3. Restore Reading Position - Wait for layout
    LaunchedEffect(chapterElements) {
        if (chapterElements.isNotEmpty()) {
            if (isGestureNavigation) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()
                isRestoringPosition = true
                if (shouldScrollToBottom) {
                    listState.scrollToItem(chapterElements.size - 1, 0)
                } else {
                    listState.scrollToItem(0, 0)
                }
                delay(100)
                isInitialScrollDone = true
                isRestoringPosition = false
                isGestureNavigation = false
                shouldScrollToBottom = false
            } else if (!isInitialScrollDone) {
                val savedProgress = settingsManager.getBookProgress(book.id).first()
                
                if (skipRestoration) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                        .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                        .first()
                    isRestoringPosition = true
                    listState.scrollToItem(0, 0)
                    delay(100)
                    isInitialScrollDone = true
                    isRestoringPosition = false
                    skipRestoration = false
                } else if (savedProgress.lastChapterHref == null || savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex]) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                        .filter { visibleItems ->
                            visibleItems.isNotEmpty() && 
                            listState.layoutInfo.totalItemsCount >= chapterElements.size
                        }
                        .first()

                    isRestoringPosition = true
                    if (savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex]) {
                        listState.scrollToItem(savedProgress.scrollIndex, savedProgress.scrollOffset)
                    } else {
                        listState.scrollToItem(0, 0)
                    }
                    delay(100)
                    isInitialScrollDone = true
                    isRestoringPosition = false
                } else {
                    isInitialScrollDone = true
                }
            } else if (shouldScrollToBottom) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()
                isRestoringPosition = true
                listState.scrollToItem(chapterElements.size - 1, 0)
                delay(100)
                shouldScrollToBottom = false
                isRestoringPosition = false
            } else {
                isRestoringPosition = true
                listState.scrollToItem(0, 0)
                delay(100)
                isRestoringPosition = false
            }
        }
    }

    // Save Progress
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, isInitialScrollDone, isRestoringPosition) {
        if (isInitialScrollDone && chapterElements.isNotEmpty() && !isRestoringPosition) {
            settingsManager.saveBookProgress(
                book.id,
                BookProgress(
                    scrollIndex = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                    lastChapterHref = book.spineHrefs[currentChapterIndex]
                )
            )
        }
    }

    // Scroll TOC to current chapter
    LaunchedEffect(drawerState.currentValue, currentChapterIndex) {
        if (drawerState.currentValue == DrawerValue.Open) {
            val currentHref = book.spineHrefs[currentChapterIndex]
            val tocIndex = book.toc.indexOfFirst { it.href.substringBefore("#") == currentHref }
            if (tocIndex != -1) {
                // Ensure layout is ready
                if (tocListState.layoutInfo.totalItemsCount == 0) {
                    snapshotFlow { tocListState.layoutInfo.totalItemsCount }
                        .filter { it > 0 }
                        .first()
                }
                
                tocListState.animateScrollToItem(
                    index = tocIndex,
                    scrollOffset = -250 // Position the item nicely in the view
                )
            }
        }
    }

    fun navigateNext() {
        if (currentChapterIndex < book.spineHrefs.size - 1) {
            shouldScrollToBottom = false
            isGestureNavigation = true
            currentChapterIndex++
        }
    }

    fun navigatePrev() {
        if (currentChapterIndex > 0) {
            shouldScrollToBottom = true
            isGestureNavigation = true
            currentChapterIndex--
        }
    }

    // Overscroll Connection
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    val delta = available.y
                    if (delta > 0 && !listState.canScrollBackward) {
                        verticalOverscroll = (verticalOverscroll + delta).coerceIn(0f, overscrollThreshold * 1.5f)
                        return Offset(0f, delta)
                    } else if (delta < 0 && !listState.canScrollForward) {
                        verticalOverscroll = (verticalOverscroll + delta).coerceIn(-overscrollThreshold * 1.5f, 0f)
                        return Offset(0f, delta)
                    }
                }
                return Offset.Zero
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && verticalOverscroll != 0f) {
                    val delta = available.y
                    if ((verticalOverscroll > 0 && delta < 0) || (verticalOverscroll < 0 && delta > 0)) {
                        val oldOverscroll = verticalOverscroll
                        verticalOverscroll = if (verticalOverscroll > 0) {
                            (verticalOverscroll + delta).coerceAtLeast(0f)
                        } else {
                            (verticalOverscroll + delta).coerceAtMost(0f)
                        }
                        return Offset(0f, verticalOverscroll - oldOverscroll)
                    }
                }
                return Offset.Zero
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Table of Contents", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = {
                        scope.launch {
                            val currentHref = book.spineHrefs[currentChapterIndex]
                            val tocIndex = book.toc.indexOfFirst { it.href.substringBefore("#") == currentHref }
                            if (tocIndex != -1) {
                                tocListState.animateScrollToItem(tocIndex, scrollOffset = -250)
                            }
                        }
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Locate Current")
                    }
                }
                HorizontalDivider()
                LazyColumn(state = tocListState) {
                    itemsIndexed(book.toc) { _, item ->
                        NavigationDrawerItem(
                            label = { Text(item.title) },
                            selected = book.spineHrefs[currentChapterIndex] == item.href.substringBefore("#"),
                            shape = RectangleShape,
                            onClick = {
                                val index = book.spineHrefs.indexOf(item.href.substringBefore("#"))
                                if (index != -1) {
                                    shouldScrollToBottom = false
                                    skipRestoration = true
                                    currentChapterIndex = index
                                    // Reset isInitialScrollDone to false so that the next chapter 
                                    // doesn't try to restore the old scroll position.
                                    isInitialScrollDone = false
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeColors.background)
                .nestedScroll(nestedScrollConnection)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                if (verticalOverscroll >= overscrollThreshold) {
                                    navigatePrev()
                                } else if (verticalOverscroll <= -overscrollThreshold) {
                                    navigateNext()
                                }
                                verticalOverscroll = 0f
                            }
                        }
                    }
                }
        ) {
            // Main Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showControls = !showControls }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = globalSettings.horizontalPadding.dp,
                        vertical = 80.dp
                    )
                ) {
                    itemsIndexed(chapterElements, key = { _, item -> item.hashCode() }) { _, element ->
                        when (element) {
                            is ChapterElement.Text -> {
                                val style = if (element.type == "h") {
                                    MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = (globalSettings.fontSize + 4).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = globalSettings.fontSize.sp,
                                        lineHeight = (globalSettings.fontSize * globalSettings.lineHeight).sp
                                    )
                                }
                                
                                val fontFamily = when(globalSettings.fontType) {
                                    "serif" -> FontFamily.Serif
                                    "sans-serif" -> FontFamily.SansSerif
                                    "monospace" -> FontFamily.Monospace
                                    "karla" -> KarlaFont
                                    else -> FontFamily.Default
                                }

                                Text(
                                    text = element.content,
                                    style = style,
                                    fontFamily = fontFamily,
                                    color = themeColors.foreground,
                                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
                                )
                            }
                            is ChapterElement.Image -> {
                                val bitmap = remember(element.data) {
                                    BitmapFactory.decodeByteArray(element.data, 0, element.data.size)
                                }
                                bitmap?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Overscroll Indicators
            if (verticalOverscroll > 0) {
                OverscrollIndicator(
                    text = if (verticalOverscroll >= overscrollThreshold) "Release for previous chapter" else "Pull for previous chapter",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp),
                    color = themeColors.foreground
                )
            } else if (verticalOverscroll < 0) {
                OverscrollIndicator(
                    text = if (abs(verticalOverscroll) >= overscrollThreshold) "Release for next chapter" else "Push for next chapter",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
                    color = themeColors.foreground
                )
            }

            // 4. Top Bar Icon Swap
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(book.title, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Text("Chapter ${currentChapterIndex + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "TOC")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeColors.background.copy(alpha = 0.95f),
                        titleContentColor = themeColors.foreground
                    )
                )
            }

            // Appearance Controls
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReaderControls(
                    settings = globalSettings,
                    onSettingsChange = {
                        scope.launch { settingsManager.updateGlobalSettings(it) }
                    },
                    themeColors = themeColors
                )
            }

            // 7. Floating "Scroll to Top" Button
            var isScrollingUp by remember { mutableStateOf(false) }
            var lastScrollOffset by remember { mutableIntStateOf(0) }
            var lastScrollIndex by remember { mutableIntStateOf(0) }

            LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                val currentIndex = listState.firstVisibleItemIndex
                val currentOffset = listState.firstVisibleItemScrollOffset
                
                if (currentIndex != lastScrollIndex || currentOffset != lastScrollOffset) {
                    isScrollingUp = if (currentIndex < lastScrollIndex) {
                        true
                    } else if (currentIndex > lastScrollIndex) {
                        false
                    } else {
                        currentOffset < lastScrollOffset
                    }
                    lastScrollIndex = currentIndex
                    lastScrollOffset = currentOffset
                }
            }

            val showScrollToTop by remember {
                derivedStateOf { isScrollingUp && listState.firstVisibleItemIndex > 0 }
            }
            AnimatedVisibility(
                visible = showScrollToTop && !showControls,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                FloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        }
    }
}

@Composable
fun OverscrollIndicator(text: String, modifier: Modifier, color: Color) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape
    ) {
        Text(
            text = text,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderControls(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettings) -> Unit,
    themeColors: ReaderTheme
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp)
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = themeColors.background.copy(alpha = 0.98f),
            contentColor = themeColors.foreground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Font Size Numeric Label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFormat, null, modifier = Modifier.size(16.dp))
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(fontSize = it.toInt())) },
                    valueRange = 12f..32f,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Text("${settings.fontSize}sp", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
            }

            // Font Family
            FlowRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                val fonts = listOf("default", "serif", "sans-serif", "monospace", "karla")
                fonts.forEach { font ->
                    FilterChip(
                        selected = settings.fontType == font,
                        onClick = { onSettingsChange(settings.copy(fontType = font)) },
                        label = { Text(font.replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // Theme
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ReaderThemeButton("light", Color.White, Color.Black, settings.theme == "light") {
                    onSettingsChange(settings.copy(theme = "light"))
                }
                ReaderThemeButton("sepia", Color(0xFFF4ECD8), Color(0xFF5B4636), settings.theme == "sepia") {
                    onSettingsChange(settings.copy(theme = "sepia"))
                }
                ReaderThemeButton("dark", Color(0xFF121212), Color.White, settings.theme == "dark") {
                    onSettingsChange(settings.copy(theme = "dark"))
                }
            }

            // 1. Line Height & Padding Numeric Labels
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Line Height", style = MaterialTheme.typography.labelSmall)
                        Text(String.format(Locale.getDefault(), "%.1f", settings.lineHeight), style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = settings.lineHeight,
                        onValueChange = { onSettingsChange(settings.copy(lineHeight = it)) },
                        valueRange = 1.2f..2.0f
                    )
                }
                Spacer(Modifier.width(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Padding", style = MaterialTheme.typography.labelSmall)
                        Text("${settings.horizontalPadding}dp", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = settings.horizontalPadding.toFloat(),
                        onValueChange = { onSettingsChange(settings.copy(horizontalPadding = it.toInt())) },
                        valueRange = 0f..32f
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderThemeButton(name: String, bg: Color, fg: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("A", color = fg, fontWeight = FontWeight.Bold)
    }
}

data class ReaderTheme(val background: Color, val foreground: Color)

fun getThemeColors(theme: String): ReaderTheme {
    return when (theme) {
        "dark" -> ReaderTheme(Color(0xFF121212), Color.White)
        "sepia" -> ReaderTheme(Color(0xFFF4ECD8), Color(0xFF5B4636))
        else -> ReaderTheme(Color.White, Color.Black)
    }
}
