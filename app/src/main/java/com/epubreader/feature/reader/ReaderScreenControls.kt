/**
 * AI_READ_AFTER: ReaderScreen.kt
 * AI_RELEVANT_TO: [Reader Controls, Scrubber, Chapter Element Rendering]
 * PURPOSE: Reader-specific presentational helpers that do not own lifecycle or restoration state.
 * AI_WARNING: Scrubber drag behavior and chapter element rendering order are load-bearing UI behavior.
 */
package com.epubreader.feature.reader

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.availableThemeOptions
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.themeButtonLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ReaderControlsTab(
    val title: String,
    val icon: ImageVector,
    val testTag: String,
) {
    Chapter("Chapter", Icons.AutoMirrored.Filled.MenuBook, "reader_controls_tab_chapter"),
    Font("Font", Icons.Default.TextFormat, "reader_controls_tab_font"),
    Theme("Theme", Icons.Default.Palette, "reader_controls_tab_theme"),
    General("General", Icons.Default.Tune, "reader_controls_tab_general"),
}

@Composable
internal fun ReaderChapterContent(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionResetToken: Int = 0,
    onSelectionActiveChange: (Boolean) -> Unit = {}
) {
    if (currentChapterIndex == -1 || isLoadingChapter) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = themeColors.foreground)
        }
        return
    }

    val platformTextToolbar = LocalTextToolbar.current
    val selectionActiveChangeState = rememberUpdatedState(onSelectionActiveChange)
    val trackingTextToolbar = remember(platformTextToolbar) {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = platformTextToolbar.status

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                selectionActiveChangeState.value(true)
                platformTextToolbar.showMenu(
                    rect = rect,
                    onCopyRequested = onCopyRequested,
                    onPasteRequested = onPasteRequested,
                    onCutRequested = onCutRequested,
                    onSelectAllRequested = onSelectAllRequested
                )
            }

            override fun hide() {
                selectionActiveChangeState.value(false)
                platformTextToolbar.hide()
            }
        }
    }

    val content = @Composable {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("reader_chapter_content"),
            contentPadding = PaddingValues(
                horizontal = settings.horizontalPadding.dp,
                vertical = 80.dp
            )
        ) {
            items(chapterElements, key = { it.id }) { element ->
                when (element) {
                    is ChapterElement.Text -> {
                        val style = if (element.type == "h") {
                            MaterialTheme.typography.headlineSmall.copy(
                                fontSize = (settings.fontSize + 4).sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = settings.fontSize.sp,
                                lineHeight = (settings.fontSize * settings.lineHeight).sp
                            )
                        }

                        Text(
                            text = element.content,
                            style = style,
                            fontFamily = readerFontFamily(settings.fontType),
                            color = themeColors.foreground,
                            textAlign = if (element.type == "h") TextAlign.Center else TextAlign.Start,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .fillMaxWidth()
                        )
                    }

                    is ChapterElement.Image -> {
                        ReaderChapterImage(
                            data = element.data,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(settings.selectableText) {
        if (!settings.selectableText) {
            selectionActiveChangeState.value(false)
        }
    }

    LaunchedEffect(selectionResetToken, settings.selectableText) {
        if (settings.selectableText && selectionResetToken > 0) {
            selectionActiveChangeState.value(false)
            platformTextToolbar.hide()
        }
    }

    if (settings.selectableText) {
        key(selectionResetToken) {
            CompositionLocalProvider(LocalTextToolbar provides trackingTextToolbar) {
                SelectionContainer {
                    content()
                }
            }
        }
    } else {
        content()
    }
}

@Composable
private fun ReaderChapterImage(
    data: ByteArray,
    modifier: Modifier = Modifier,
) {
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, data) {
        value = if (data.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
            }
        }
    }

    imageBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
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

@Composable
fun ReaderControls(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    themeColors: ReaderTheme,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    itemCount: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    sectionLabel: String,
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
        var selectedTab by remember { mutableStateOf(ReaderControlsTab.Chapter) }

        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                ReaderControlsTab.Chapter -> ReaderChapterControlsTab(
                    themeColors = themeColors,
                    onNavigatePrev = onNavigatePrev,
                    onNavigateNext = onNavigateNext,
                    listState = listState,
                    itemCount = itemCount,
                    currentChapterIndex = currentChapterIndex,
                    totalChapters = totalChapters,
                    sectionLabel = sectionLabel,
                )

                ReaderControlsTab.Font -> ReaderFontControlsTab(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    themeColors = themeColors,
                )

                ReaderControlsTab.Theme -> ReaderThemeControlsTab(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )

                ReaderControlsTab.General -> ReaderGeneralControlsTab(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                )
            }

            HorizontalDivider(
                color = themeColors.foreground.copy(alpha = 0.2f)
            )

            ReaderControlsTabRow(
                selectedTab = selectedTab,
                themeColors = themeColors,
                onTabSelected = { selectedTab = it },
            )
        }
    }
}

@Composable
private fun ReaderChapterControlsTab(
    themeColors: ReaderTheme,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    itemCount: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    sectionLabel: String,
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var draggingValue by remember { mutableStateOf(0f) }

    val scrollProgress = remember(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, itemCount) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isNotEmpty() && itemCount > 0) {
            val viewportStart = layoutInfo.viewportStartOffset
            val topItem = visibleItems.firstOrNull { it.offset + it.size > viewportStart } ?: visibleItems.first()
            val itemTop = topItem.offset
            val relativeOffset = (viewportStart - itemTop).coerceAtLeast(0)
            val itemHeight = if (topItem.size > 0) topItem.size else 1
            ((topItem.index.toFloat() + (relativeOffset.toFloat() / itemHeight.toFloat())) / itemCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    LaunchedEffect(scrollProgress) {
        if (!isDragging) {
            draggingValue = scrollProgress
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$sectionLabel ${currentChapterIndex + 1} of $totalChapters",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${(draggingValue * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = themeColors.foreground.copy(alpha = 0.65f),
            )
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
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous $sectionLabel")
                }
                Text(
                    text = if (currentChapterIndex > 0) "${sectionLabel.take(1)}. $currentChapterIndex" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.foreground.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }

            Slider(
                value = draggingValue,
                onValueChange = { progress ->
                    isDragging = true
                    draggingValue = progress
                    val targetIndex = (progress * (itemCount - 1)).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                    scope.launch { listState.scrollToItem(targetIndex) }
                },
                onValueChangeFinished = {
                    isDragging = false
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            Column(
                modifier = Modifier.width(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onNavigateNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next $sectionLabel")
                }
                Text(
                    text = if (currentChapterIndex < totalChapters - 1) "${sectionLabel.take(1)}. ${currentChapterIndex + 2}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.foreground.copy(alpha = 0.6f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ReaderFontControlsTab(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    themeColors: ReaderTheme,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.TextFormat, null, modifier = Modifier.size(20.dp))
            }
            Slider(
                value = settings.fontSize.toFloat(),
                onValueChange = { fontSize ->
                    onSettingsChange { current -> current.copy(fontSize = fontSize.toInt()) }
                },
                valueRange = 12f..32f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "${settings.fontSize}sp",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Line Height", style = MaterialTheme.typography.labelSmall)
                Text(
                    String.format(Locale.getDefault(), "%.1f", settings.lineHeight),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Slider(
                value = settings.lineHeight,
                onValueChange = { lineHeight ->
                    onSettingsChange { current -> current.copy(lineHeight = lineHeight) }
                },
                valueRange = 1.2f..2.0f
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("reader_font_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fonts = listOf("default", "serif", "sans-serif", "monospace", "karla")
            fonts.forEach { font ->
                FilterChip(
                    selected = settings.fontType == font,
                    onClick = {
                        onSettingsChange { current -> current.copy(fontType = font) }
                    },
                    label = { Text(font.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Text(
            text = "Choose a reading font and spacing that stays comfortable over long sessions.",
            style = MaterialTheme.typography.bodySmall,
            color = themeColors.foreground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ReaderThemeControlsTab(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val themeOptions = availableThemeOptions(settings.customThemes)
        val listState = rememberLazyListState()
        
        LaunchedEffect(settings.theme) {
            val index = themeOptions.indexOfFirst { it.id == settings.theme }
            if (index != -1) {
                listState.animateScrollToItem(index)
            }
        }
        
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reader_theme_row"),
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(themeOptions) { _, option ->
                ReaderThemeButton(
                    name = option.name,
                    bg = Color(option.palette.readerBackground),
                    fg = Color(option.palette.readerForeground),
                    selected = settings.theme == option.id,
                    label = themeButtonLabel(option.name, option.id),
                ) {
                    onSettingsChange { current -> current.copy(theme = option.id) }
                }
            }
        }
    }
}

@Composable
private fun ReaderGeneralControlsTab(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Padding", style = MaterialTheme.typography.labelSmall)
                Text("${settings.horizontalPadding}dp", style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = settings.horizontalPadding.toFloat(),
                onValueChange = { padding ->
                    onSettingsChange { current -> current.copy(horizontalPadding = padding.toInt()) }
                },
                valueRange = 0f..32f
            )
        }

        ReaderGeneralToggleRow(
            label = "Show Scrubber",
            checked = settings.showScrubber,
            onCheckedChange = { showScrubber ->
                onSettingsChange { current -> current.copy(showScrubber = showScrubber) }
            }
        )

        ReaderGeneralToggleRow(
            label = "Show System Bar",
            checked = settings.showSystemBar,
            onCheckedChange = { showSystemBar ->
                onSettingsChange { current -> current.copy(showSystemBar = showSystemBar) }
            }
        )

        ReaderGeneralToggleRow(
            label = "Selectable Text",
            checked = settings.selectableText,
            onCheckedChange = { selectableText ->
                onSettingsChange { current -> current.copy(selectableText = selectableText) }
            }
        )
    }
}

@Composable
private fun ReaderGeneralToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ReaderControlsTabRow(
    selectedTab: ReaderControlsTab,
    themeColors: ReaderTheme,
    onTabSelected: (ReaderControlsTab) -> Unit,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = Color.Transparent,
        contentColor = themeColors.foreground,
        divider = {},
    ) {
        ReaderControlsTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                selectedContentColor = themeColors.foreground,
                unselectedContentColor = themeColors.foreground.copy(alpha = 0.6f),
                modifier = Modifier
                    .testTag(tab.testTag)
                    .semantics {
                        contentDescription = "${tab.title} tab"
                    },
                text = {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                    )
                }
            )
        }
    }
}

@Composable
fun ReaderThemeButton(
    name: String,
    bg: Color,
    fg: Color,
    selected: Boolean,
    label: String = "A",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = "Theme $name"
                this.selected = selected
            }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable { onClick() }
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bg)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = fg, fontWeight = FontWeight.Bold)
        }
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

/**
 * VerticalScrubber: Provides a fast-scroll handle.
 * [AI_NOTE] Directly manipulates LazyListState.scrollToItem for instantaneous feedback.
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
                } else {
                    0f
                }
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
                                scope.launch { listState.scrollToItem(targetIndex) }
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

private fun readerFontFamily(fontType: String): FontFamily {
    return when (fontType) {
        "serif" -> FontFamily.Serif
        "sans-serif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "karla" -> KarlaFont
        else -> FontFamily.Default
    }
}
