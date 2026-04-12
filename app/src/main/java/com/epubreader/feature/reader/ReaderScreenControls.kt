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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFormat
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.epubreader.core.model.ChapterElement
import com.epubreader.core.model.GlobalSettings
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun ReaderChapterContent(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int
) {
    if (currentChapterIndex == -1 || isLoadingChapter) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = themeColors.foreground)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
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
                            scope.launch { listState.scrollToItem(targetIndex) }
                        },
                        onValueChangeFinished = {
                            isDragging = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        text = "${(draggingValue * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.foreground.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 12.dp)
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

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
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

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = themeColors.foreground.copy(alpha = 0.2f)
            )
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
