/*
 * AI_ENTRY_POINT
 * AI_READ_FIRST
 * AI_RELEVANT_TO: [Reader UI, Scroll Restoration, Reading Progress, Chapter Navigation]
 * AI_STATE_OWNER: Local composable state (currentChapterIndex, chapterElements)
 * AI_CRITICAL: Scroll restoration uses fragile LaunchedEffect ordering.
 * 
 * ReaderScreen.kt
 *
 * MAIN READER COMPONENT: The core reading interface of Blue Waves.
 *
 * ARCHITECTURAL CONSTRAINTS:
 * 1. [AI_CRITICAL] Scroll Restoration: Relies on a complex state machine (isInitialScrollDone,
 *    isRestoringPosition) and precise timing (delay(100)). Modifying the order of operations in
 *    the chapter loading/restoration LaunchedEffects will cause "jumpy" UI or loss of reading position.
 * 2. [AI_NOTE] Global LTR: Enforces LayoutDirection.Ltr via CompositionLocalProvider to avoid
 *    mirroring issues with fixed-position UI elements (scrubbers, controls) in RTL locales.
 * 3. [AI_WARNING] Hardcoded Themes: Uses specific hex codes (Sepia: #F4ECD8, Dark: #121212)
 *    defined in getThemeColors(). Do not replace with MaterialTheme.colorScheme surfaces without
 *    updating the manual contrast logic in ReaderControls.
 */

package com.epubreader

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.abs

val KarlaFont = FontFamily(
    Font(R.font.karla, FontWeight.Normal)
)

enum class TocSort { Ascending, Descending }

/**
 * The main reader view. Handles chapter rendering, scroll position persistence,
 * TOC navigation, and reader settings (font, theme, etc.).
 *
 * [AI_CRITICAL] This composable manages three distinct "loading" states:
 * 1. book.id change: Resets the entire reader state.
 * 2. currentChapterIndex change: Loads new content from disk (IO).
 * 3. chapterElements change: Restores the scroll position within the loaded content.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    book: EpubBook,
    settingsManager: SettingsManager,
    parser: EpubParser,
    onBack: () -> Unit
) {
    // AI_STATE_OWNER: ReaderScreen composable
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val globalSettings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())

    // AI_STATE_OWNER: ReaderScreen (Owned here, reset on book change)
    var currentChapterIndex by remember(book.id) { mutableIntStateOf(-1) }
    // AI_STATE_OWNER: ReaderScreen (Extracted from EPUB, used for LazyColumn rendering)
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
    // Removed showGoToDialog as it is now inline in ReaderControls

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

    // 1. Initial State Sync: Runs once per book to find the last read chapter.
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

    // Load Chapter: Fetches content for currentChapterIndex and pre-fetches neighbors.
    // [AI_NOTE] Pre-fetching is done on Dispatchers.IO to prevent UI stutter during rapid navigation.
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

    /**
     * Restore Reading Position: THE MOST FRAGILE PART OF THE CODEBASE.
     * [AI_CRITICAL] Sequence is vital:
     * 1. Wait for LazyColumn to report totalItemsCount >= chapterElements.size using snapshotFlow.
     * 2. Perform the scroll (scrollToItem).
     * 3. delay(100) to allow the compose runtime to settle.
     * 4. Set isInitialScrollDone = true to enable the "Save Progress" effect.
     */
    LaunchedEffect(chapterElements) {
        if (chapterElements.isNotEmpty() && currentChapterIndex != -1 && currentChapterIndex < book.spineHrefs.size) {
            if (skipRestoration) {
                isRestoringPosition = true
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .filter { it.isNotEmpty() && listState.layoutInfo.totalItemsCount >= chapterElements.size }
                    .first()
                listState.scrollToItem(0, 0)
                delay(100)
                skipRestoration = false
                isInitialScrollDone = true
                isRestoringPosition = false
                isGestureNavigation = false
                shouldScrollToBottom = false
            } else if (isGestureNavigation) {
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

    // Save Progress: Throttled (500ms) to avoid over-writing DataStore during active scrolling.
    // [AI_WARNING] Only saves if isInitialScrollDone is true AND we aren't currently restoring position.
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

    fun jumpToChapter(targetIndex: Int) {
        showControls = false
        if (targetIndex != currentChapterIndex) {
            skipRestoration = true
            isInitialScrollDone = false
            isRestoringPosition = true
            shouldScrollToBottom = false
            currentChapterIndex = targetIndex
        } else {
            scope.launch { listState.scrollToItem(0, 0) }
        }
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
            isInitialScrollDone = true // Sacred flag: ensure isInitialScrollDone is true during navigation
            isRestoringPosition = false
            currentChapterIndex++
        }
    }

    fun navigatePrev(toBottom: Boolean = true) {
        if (currentChapterIndex > 0) {
            shouldScrollToBottom = toBottom
            isGestureNavigation = true
            isInitialScrollDone = true // Sacred flag: ensure isInitialScrollDone is true during navigation
            isRestoringPosition = false
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

    // Remove any logic related to showGoToDialog as it is now inline
    // if (showGoToDialog) { ... }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = themeColors.background,
                    drawerContentColor = themeColors.foreground
                ) {
                    var showChapterInputInToc by remember { mutableStateOf(false) }
                    var inputChapter by remember { mutableStateOf("") }
                    val focusRequester = remember { FocusRequester() }
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val totalChapters = book.spineHrefs.size

                    val performJump = {
                        val targetChapter = inputChapter.toIntOrNull()?.coerceIn(1, totalChapters) ?: (currentChapterIndex + 1)
                        val targetIndex = (targetChapter - 1).coerceIn(0, totalChapters - 1)
                        keyboardController?.hide()
                        jumpToChapter(targetIndex)
                        inputChapter = ""
                        showChapterInputInToc = false
                        scope.launch { drawerState.close() }
                    }

                    LaunchedEffect(showChapterInputInToc, drawerState.currentValue) {
                        if (showChapterInputInToc && drawerState.isOpen) {
                            focusRequester.requestFocus()
                        } else {
                            keyboardController?.hide()
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        if (showChapterInputInToc) {
                            BasicTextField(
                                value = inputChapter,
                                onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 4) inputChapter = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .padding(horizontal = 4.dp)
                                    .focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { performJump() }
                                ),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                decorationBox = { innerTextField ->
                                    OutlinedTextFieldDefaults.DecorationBox(
                                        value = inputChapter,
                                        innerTextField = innerTextField,
                                        enabled = true,
                                        singleLine = true,
                                        visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                                        interactionSource = remember { MutableInteractionSource() },
                                        placeholder = { 
                                            Text(
                                                "1-$totalChapters", 
                                                style = MaterialTheme.typography.bodySmall,
                                                color = themeColors.foreground.copy(alpha = 0.5f)
                                            ) 
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        container = {
                                            OutlinedTextFieldDefaults.Container(
                                                enabled = true,
                                                isError = false,
                                                interactionSource = remember { MutableInteractionSource() },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedBorderColor = themeColors.foreground.copy(alpha = 0.5f),
                                                    unfocusedBorderColor = themeColors.foreground.copy(alpha = 0.1f),
                                                    focusedTextColor = themeColors.foreground,
                                                    unfocusedTextColor = themeColors.foreground,
                                                    focusedPlaceholderColor = themeColors.foreground.copy(alpha = 0.5f),
                                                    unfocusedPlaceholderColor = themeColors.foreground.copy(alpha = 0.5f)
                                                ),
                                                shape = MaterialTheme.shapes.small,
                                            )
                                        }
                                    )
                                }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        IconButton(onClick = { 
                            if (showChapterInputInToc) {
                                showChapterInputInToc = false
                            } else {
                                showChapterInputInToc = true
                            }
                        }) {
                            Icon(
                                imageVector = if (showChapterInputInToc) Icons.Default.Close else Icons.Default.Tag, 
                                contentDescription = "Go to Chapter",
                                tint = if (showChapterInputInToc) MaterialTheme.colorScheme.error else LocalContentColor.current
                            )
                        }
                    }

                    HorizontalDivider()
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(state = tocListState, modifier = Modifier.fillMaxSize()) {
                            items(sortedToc, key = { it.href }) { item ->
                                val isSelected = remember(currentChapterIndex) {
                                    currentChapterIndex != -1 && 
                                    currentChapterIndex < book.spineHrefs.size && 
                                    book.spineHrefs[currentChapterIndex] == item.href.substringBefore("#")
                                }
                                NavigationDrawerItem(
                                    label = { 
                                        Text(
                                            item.title,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        ) 
                                    },
                                    selected = isSelected,
                                    shape = RectangleShape,
                                    colors = NavigationDrawerItemDefaults.colors(
                                        unselectedContainerColor = Color.Transparent,
                                        selectedContainerColor = themeColors.foreground.copy(alpha = 0.1f),
                                        unselectedTextColor = themeColors.foreground,
                                        selectedTextColor = themeColors.foreground,
                                        unselectedIconColor = themeColors.foreground,
                                        selectedIconColor = themeColors.foreground
                                    ),
                                    onClick = {
                                        val index = book.spineHrefs.indexOf(item.href.substringBefore("#"))
                                        if (index != -1) {
                                            if (index != currentChapterIndex) {
                                                shouldScrollToBottom = false
                                                skipRestoration = true
                                                currentChapterIndex = index
                                                isInitialScrollDone = true
                                                isRestoringPosition = false
                                            } else {
                                                scope.launch { listState.animateScrollToItem(0) }
                                            }
                                        }
                                        showControls = false
                                        scope.launch { drawerState.close() }
                                    }
                                )
                            }
                        }
                        VerticalScrubber(
                            listState = tocListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 2.dp, top = 16.dp, bottom = 16.dp)
                                .width(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            isTOC = true,
                            onDragStart = {
                                // showGoToDialog removed
                            }
                        )
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
                                items(chapterElements, key = { it.id }) { element ->
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

                    if (globalSettings.showScrubber && !isLoadingChapter && currentChapterIndex != -1) {
                        VerticalScrubber(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 80.dp, bottom = 80.dp)
                                .width(20.dp),
                            color = themeColors.foreground,
                            onDragStart = {
                                showControls = false
                                scope.launch { drawerState.close() }
                                isInitialScrollDone = true // Sacred flag: ensure isInitialScrollDone is true during scrubber interaction
                                isRestoringPosition = false
                            }
                        )
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
                        onNavigatePrev = { navigatePrev(toBottom = false) },
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
            
            // Interaction state for the slider
            var isDragging by remember { mutableStateOf(false) }
            var draggingValue by remember { mutableStateOf(0f) }

            // Calculate progress based on pixel-perfect scroll position relative to total content height
            val scrollProgress = remember(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, itemCount) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo

                if (visibleItems.isNotEmpty() && itemCount > 0) {
                    // 1. Find the item crossing the viewport boundary (accounts for content padding)
                    val viewportStart = layoutInfo.viewportStartOffset
                    val topItem = visibleItems.firstOrNull { it.offset + it.size > viewportStart } ?: visibleItems.first()

                    // 2. Calculate current absolute scroll position based on viewport logic
                    val itemTop = topItem.offset
                    val relativeOffset = (viewportStart - itemTop).coerceAtLeast(0)

                    // Use a more stable progress calculation: (index + offset/size) / total
                    val itemHeight = if (topItem.size > 0) topItem.size else 1
                    ((topItem.index.toFloat() + (relativeOffset.toFloat() / itemHeight.toFloat())) / itemCount.toFloat()).coerceIn(0f, 1f)
                } else 0f
            }

            // Sync dragging value with scroll progress when not dragging
            LaunchedEffect(scrollProgress) {
                if (!isDragging) {
                    draggingValue = scrollProgress
                }
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
                        value = draggingValue,
                        onValueChange = { progress ->
                            isDragging = true
                            draggingValue = progress
                            val targetIndex = (progress * (itemCount - 1)).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                            scope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        },
                        onValueChangeFinished = {
                            isDragging = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "${(draggingValue * 100).toInt()}%",
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
                ReaderThemeButton("oled", Color.Black, Color.White, settings.theme == "oled", label = "O") {
                    onSettingsChange(settings.copy(theme = "oled"))
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = themeColors.foreground.copy(alpha = 0.2f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Scrubber", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.showScrubber,
                    onCheckedChange = { onSettingsChange(settings.copy(showScrubber = it)) }
                )
            }
        }
    }
}

@Composable
fun ReaderThemeButton(name: String, bg: Color, fg: Color, selected: Boolean, label: String = "A", onClick: () -> Unit) {
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
        Text(label, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    themeColors: ReaderTheme,
    onClick: () -> Unit
) {
    val isAction = text == "Clear" || text == "Confirm"
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        color = if (isAction) themeColors.foreground.copy(alpha = 0.15f) else themeColors.foreground.copy(alpha = 0.05f),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = if (isAction) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
                color = themeColors.foreground,
                fontWeight = if (isAction) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

data class ReaderTheme(val background: Color, val foreground: Color)

fun getThemeColors(theme: String): ReaderTheme {
    return when (theme) {
        "oled" -> ReaderTheme(Color.Black, Color.White)
        "dark" -> ReaderTheme(Color(0xFF121212), Color.White)
        "sepia" -> ReaderTheme(Color(0xFFF4ECD8), Color(0xFF5B4636))
        else -> ReaderTheme(Color.White, Color.Black)
    }
}

    /**
     * VerticalScrubber: Provides a fast-scroll handle.
     * [AI_NOTE] Directly manipulates LazyListState.scrollToItem for instantaneous feedback.
     * [AI_CRITICAL] Sets isInitialScrollDone = true to prevent the scroll restoration logic
     * from "fighting" the user's manual dragging.
     */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun VerticalScrubber(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onDragStart: () -> Unit = {},
    isTOC: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val totalHeight = with(density) { maxHeight.toPx() }
        val thumbHeight = with(density) { 48.dp.toPx() }

        val scrollProgress by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                val totalItems = layoutInfo.totalItemsCount
                
                if (visibleItems.isNotEmpty() && totalItems > 0) {
                    val viewportStart = layoutInfo.viewportStartOffset
                    val topItem = visibleItems.firstOrNull { it.offset + it.size > viewportStart } ?: visibleItems.first()

                    val itemTop = topItem.offset
                    val relativeOffset = (viewportStart - itemTop).coerceAtLeast(0)
                    val itemHeight = if (topItem.size > 0) topItem.size else 1

                    ((topItem.index.toFloat() + (relativeOffset.toFloat() / itemHeight.toFloat())) / totalItems.toFloat()).coerceIn(0f, 1f)
                } else 0f
            }
        }

        val thumbOffset by remember {
            derivedStateOf {
                (totalHeight - thumbHeight) * scrollProgress.coerceIn(0f, 1f)
            }
        }

        val showScrubber by remember {
            derivedStateOf { listState.layoutInfo.totalItemsCount > 1 }
        }

        if (showScrubber) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(if (isTOC) 16.dp else 20.dp)
                    .pointerInput(totalHeight) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, _ ->
                                change.consume()
                                val y = change.position.y
                                val progress = (y / totalHeight).coerceIn(0f, 1f)
                                val currentTotalItems = listState.layoutInfo.totalItemsCount
                                val targetIndex = (progress * currentTotalItems).toInt().coerceIn(0, currentTotalItems - 1)
                                scope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = with(density) { thumbOffset.toDp() })
                        .width(if (isTOC) 6.dp else 4.dp)
                        .height(with(density) { thumbHeight.toDp() })
                        .background(color.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}
