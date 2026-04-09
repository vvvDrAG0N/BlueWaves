package com.epubreader

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.abs

val KarlaFont = FontFamily(
    Font(R.font.karla, FontWeight.Normal)
)

enum class TocSort { Ascending, Descending }

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

    var currentChapterIndex by remember(book.id) { mutableIntStateOf(-1) }
    var chapterElements by remember(book.id) { mutableStateOf<List<ChapterElement>>(emptyList()) }
    var isLoadingChapter by remember { mutableStateOf(false) }
    var isInitialScrollDone by remember(book.id) { mutableStateOf(false) }
    var isRestoringPosition by remember { mutableStateOf(false) }
    var skipRestoration by remember { mutableStateOf(false) }
    var isGestureNavigation by remember { mutableStateOf(false) }
    var shouldScrollToBottom by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val tocListState = rememberLazyListState()
    val themeColors = getThemeColors(globalSettings.theme)

    var tocSort by remember { mutableStateOf(TocSort.Ascending) }
    var showGoToDialog by remember { mutableStateOf(false) }

    val sortedToc = remember(book.toc, tocSort) {
        when (tocSort) {
            TocSort.Ascending -> book.toc
            TocSort.Descending -> book.toc.reversed()
        }
    }

    // Overscroll Navigation State
    var verticalOverscroll by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val overscrollThreshold = with(density) { 80.dp.toPx() }

    // 1. Initial State Sync
    LaunchedEffect(book.id) {
        if (currentChapterIndex == -1) {
            val savedProgress = settingsManager.getBookProgress(book.id).first()
            if (book.spineHrefs.isNotEmpty()) {
                val index = if (savedProgress.lastChapterHref != null) {
                    val foundIndex = book.spineHrefs.indexOf(savedProgress.lastChapterHref)
                    if (foundIndex != -1) foundIndex else 0
                } else {
                    0
                }
                currentChapterIndex = index.coerceIn(book.spineHrefs.indices)
            }
        }
    }

    // Load Chapter
    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex == -1 || currentChapterIndex >= book.spineHrefs.size) return@LaunchedEffect
        
        isLoadingChapter = true
        val href = book.spineHrefs[currentChapterIndex]
        val elements = withContext(Dispatchers.IO) {
            parser.parseChapter(book.rootPath, href)
        }
        chapterElements = elements
        isLoadingChapter = false
        
        scope.launch(Dispatchers.IO) {
            if (currentChapterIndex > 0) {
                parser.parseChapter(book.rootPath, book.spineHrefs[currentChapterIndex - 1])
            }
            if (currentChapterIndex < book.spineHrefs.size - 1) {
                parser.parseChapter(book.rootPath, book.spineHrefs[currentChapterIndex + 1])
            }
        }
    }

    // Restore Reading Position
    LaunchedEffect(chapterElements) {
        if (chapterElements.isNotEmpty() && currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
            if (isGestureNavigation) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()
                
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
                isRestoringPosition = true
                val savedProgress = settingsManager.getBookProgress(book.id).first()
                
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()
                
                if (skipRestoration) {
                    listState.scrollToItem(0, 0)
                    skipRestoration = false
                } else if (savedProgress.lastChapterHref == null || savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex]) {
                    if (savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex]) {
                        listState.scrollToItem(savedProgress.scrollIndex, savedProgress.scrollOffset)
                    } else {
                        listState.scrollToItem(0, 0)
                    }
                } else {
                    listState.scrollToItem(0, 0)
                }
                delay(100)
                isInitialScrollDone = true
                isRestoringPosition = false
            } else if (shouldScrollToBottom) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()
                listState.scrollToItem(chapterElements.size - 1, 0)
                delay(100)
                shouldScrollToBottom = false
                isRestoringPosition = false
            }
        }
    }

    // Save Progress
    LaunchedEffect(currentChapterIndex, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, isInitialScrollDone, isRestoringPosition) {
        if (isInitialScrollDone && chapterElements.isNotEmpty() && !isRestoringPosition && currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
            delay(500)
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

    suspend fun saveAndBack() {
        if (currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
            withContext(NonCancellable) {
                val progress = when {
                    isInitialScrollDone && !isRestoringPosition -> {
                        BookProgress(
                            scrollIndex = listState.firstVisibleItemIndex,
                            scrollOffset = listState.firstVisibleItemScrollOffset,
                            lastChapterHref = book.spineHrefs[currentChapterIndex]
                        )
                    }
                    isGestureNavigation || skipRestoration -> {
                        BookProgress(
                            scrollIndex = if (shouldScrollToBottom) Int.MAX_VALUE else 0,
                            scrollOffset = 0,
                            lastChapterHref = book.spineHrefs[currentChapterIndex]
                        )
                    }
                    else -> null
                }
                progress?.let { settingsManager.saveBookProgress(book.id, it) }
            }
        }
        onBack()
    }

    BackHandler {
        scope.launch { saveAndBack() }
    }

    // Scroll TOC to current chapter
    LaunchedEffect(drawerState.currentValue, currentChapterIndex) {
        if (drawerState.currentValue == DrawerValue.Open && currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
            val currentHref = book.spineHrefs[currentChapterIndex]
            val tocIndex = sortedToc.indexOfFirst { it.href.substringBefore("#") == currentHref }
            if (tocIndex != -1) {
                if (tocListState.layoutInfo.totalItemsCount == 0) {
                    snapshotFlow { tocListState.layoutInfo.totalItemsCount }
                        .filter { it > 0 }
                        .first()
                }
                tocListState.animateScrollToItem(index = tocIndex, scrollOffset = -250)
            }
        }
    }

    fun navigateNext() {
        if (currentChapterIndex < book.spineHrefs.size - 1) {
            shouldScrollToBottom = false
            isGestureNavigation = true
            isInitialScrollDone = false
            currentChapterIndex++
        }
    }

    fun navigatePrev() {
        if (currentChapterIndex > 0) {
            shouldScrollToBottom = true
            isGestureNavigation = true
            isInitialScrollDone = false
            currentChapterIndex--
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !isLoadingChapter && isInitialScrollDone) {
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

    if (showGoToDialog) {
        var targetChapter by remember { mutableIntStateOf(currentChapterIndex + 1) }
        AlertDialog(
            onDismissRequest = { showGoToDialog = false },
            title = { Text("Go to Chapter") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Select chapter (1 - ${book.spineHrefs.size})")
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (targetChapter > 1) targetChapter-- }) {
                            Icon(Icons.Default.Remove, null)
                        }
                        Text(
                            targetChapter.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        IconButton(onClick = { if (targetChapter < book.spineHrefs.size) targetChapter++ }) {
                            Icon(Icons.Default.Add, null)
                        }
                    }
                    Slider(
                        value = targetChapter.toFloat(),
                        onValueChange = { targetChapter = it.toInt() },
                        valueRange = 1f..book.spineHrefs.size.toFloat(),
                        steps = (book.spineHrefs.size - 2).coerceAtLeast(0)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val index = (targetChapter - 1).coerceIn(book.spineHrefs.indices)
                    if (index != currentChapterIndex) {
                        shouldScrollToBottom = false
                        skipRestoration = true
                        currentChapterIndex = index
                        isInitialScrollDone = false
                    } else {
                        scope.launch { listState.scrollToItem(0) }
                    }
                    scope.launch { drawerState.close() }
                    showGoToDialog = false
                }) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                        Row {
                            IconButton(onClick = { showGoToDialog = true }) {
                                Icon(Icons.Default.Tag, contentDescription = "Go to Chapter")
                            }
                            IconButton(onClick = {
                                tocSort = if (tocSort == TocSort.Ascending) TocSort.Descending else TocSort.Ascending
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Toggle Sort",
                                    modifier = if (tocSort == TocSort.Ascending) Modifier.graphicsLayer { rotationX = 180f } else Modifier
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    if (currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
                                        val currentHref = book.spineHrefs[currentChapterIndex]
                                        val tocIndex = sortedToc.indexOfFirst { it.href.substringBefore("#") == currentHref }
                                        if (tocIndex != -1) {
                                            tocListState.animateScrollToItem(tocIndex, scrollOffset = -250)
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Locate Current")
                            }
                        }
                    }

                    HorizontalDivider()
                    LazyColumn(state = tocListState) {
                        items(sortedToc, key = { it.href }) { item ->
                            val isSelected = remember(currentChapterIndex) {
                                currentChapterIndex != -1 && 
                                currentChapterIndex < book.spineHrefs.size && 
                                book.spineHrefs[currentChapterIndex] == item.href.substringBefore("#")
                            }
                            NavigationDrawerItem(
                                label = { Text(item.title) },
                                selected = isSelected,
                                shape = RectangleShape,
                                onClick = {
                                    val index = book.spineHrefs.indexOf(item.href.substringBefore("#"))
                                    if (index != -1) {
                                        if (index != currentChapterIndex) {
                                            shouldScrollToBottom = false
                                            skipRestoration = true
                                            currentChapterIndex = index
                                            isInitialScrollDone = false
                                        } else {
                                            scope.launch { listState.animateScrollToItem(0) }
                                        }
                                    }
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                }
            },
            content = {
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showControls = !showControls }
                    ) {
                        if (currentChapterIndex == -1 || isLoadingChapter) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = themeColors.foreground)
                            }
                        } else {
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
                                                textAlign = if (element.type == "h") TextAlign.Center else TextAlign.Start,
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
                    }

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
                                    if (currentChapterIndex != -1) {
                                        Text("Chapter ${currentChapterIndex + 1}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { saveAndBack() } }) {
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
                            themeColors = themeColors,
                            onNavigatePrev = { navigatePrev() },
                            onNavigateNext = { navigateNext() },
                            listState = listState,
                            itemCount = chapterElements.size,
                            currentChapterIndex = currentChapterIndex,
                            totalChapters = book.spineHrefs.size
                        )
                    }

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
        )
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
    themeColors: ReaderTheme,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    itemCount: Int,
    currentChapterIndex: Int,
    totalChapters: Int
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
            // Chapter Navigation and Progress
            val scope = rememberCoroutineScope()
            val scrollProgress = remember(listState.firstVisibleItemIndex, itemCount) {
                if (itemCount > 1) {
                    listState.firstVisibleItemIndex.toFloat() / (itemCount - 1).toFloat()
                } else 0f
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = onNavigatePrev) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Chapter")
                    }
                    Text(
                        text = if (currentChapterIndex > 0) "Ch. $currentChapterIndex" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.foreground.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Slider(
                        value = scrollProgress,
                        onValueChange = { progress ->
                            val index = (progress * (itemCount - 1)).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                            scope.launch { listState.scrollToItem(index) }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "${(scrollProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.foreground.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.BottomCenter).offset(y = 12.dp)
                    )
                }

                Column(
                    modifier = Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = onNavigateNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next Chapter")
                    }
                    Text(
                        text = if (currentChapterIndex < totalChapters - 1) "Ch. ${currentChapterIndex + 2}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.foreground.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.TextFormat, null, modifier = Modifier.size(20.dp))
                }
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { onSettingsChange(settings.copy(fontSize = it.toInt())) },
                    valueRange = 12f..32f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${settings.fontSize}sp",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

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
