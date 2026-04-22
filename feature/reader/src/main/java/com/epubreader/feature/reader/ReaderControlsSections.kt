package com.epubreader.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.availableThemeOptions
import com.epubreader.core.model.themeButtonLabel
import com.epubreader.core.ui.ReaderStatusSettingsRow
import java.util.Locale
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun ReaderControlsDragHandle(
    themeColors: ReaderTheme,
    onDragDelta: (Float) -> Unit,
    onDragStopped: suspend CoroutineScope.(Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> onDragDelta(delta) },
                onDragStopped = onDragStopped,
            )
            .padding(top = 10.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(themeColors.foreground.copy(alpha = 0.45f)),
            )
        }
    }
}

@Composable
internal fun ReaderControlsSection(
    title: String,
    testTag: String,
    trailingTitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = LocalContentColor.current.copy(alpha = 1f),
            )
            if (trailingTitle != null) {
                Text(
                    text = trailingTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.65f),
                )
            }
        }
        content()
    }
}

@Composable
internal fun ReaderChapterControlsSection(
    themeColors: ReaderTheme,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    listState: LazyListState,
    itemCount: Int,
    currentChapterIndex: Int,
    totalChapters: Int,
    sectionLabel: String,
    progressPercentageState: State<Float>,
) {
    val requestScrollToItem = rememberConflatedScrollToItem(listState)
    var isDragging by remember { mutableStateOf(false) }
    var draggingValue by remember { mutableStateOf(0f) }
    val progressPercentage by progressPercentageState

    LaunchedEffect(progressPercentage) {
        if (!isDragging) {
            draggingValue = progressPercentage
        }
    }

    ReaderControlsSection(
        title = "$sectionLabel ${currentChapterIndex + 1} of $totalChapters",
        trailingTitle = "${(draggingValue * 100).toInt()}%",
        testTag = "reader_controls_section_chapter",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(onClick = onNavigatePrev) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous $sectionLabel")
                    }
                    Text(
                        text = if (currentChapterIndex > 0) "${sectionLabel.take(1)}. $currentChapterIndex" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.foreground.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }

                Slider(
                    value = draggingValue,
                    onValueChange = { progress ->
                        isDragging = true
                        draggingValue = progress
                        val targetIndex = (progress * (itemCount - 1)).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                        requestScrollToItem(targetIndex)
                    },
                    onValueChangeFinished = {
                        isDragging = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )

                Column(
                    modifier = Modifier.width(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(onClick = onNavigateNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next $sectionLabel")
                    }
                    Text(
                        text = if (currentChapterIndex < totalChapters - 1) "${sectionLabel.take(1)}. ${currentChapterIndex + 2}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = themeColors.foreground.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReaderFontControlsSection(
    settings: GlobalSettings,
    onPreviewSettingsChange: (GlobalSettingsTransform) -> Unit,
    onPersistSettingsChange: (GlobalSettingsTransform) -> Unit,
    isVisible: Boolean,
) {
    var pendingFontSize by remember(settings.fontSize) { mutableIntStateOf(settings.fontSize) }
    var pendingLineHeight by remember(settings.lineHeight) { mutableFloatStateOf(settings.lineHeight) }
    var pendingHorizontalPadding by remember(settings.horizontalPadding) { mutableIntStateOf(settings.horizontalPadding) }

    ReaderControlsSection(
        title = "Font",
        testTag = "reader_controls_section_font",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.TextFormat, null, modifier = Modifier.size(20.dp))
                }
                Slider(
                    value = pendingFontSize.toFloat(),
                    onValueChange = { fontSize ->
                        pendingFontSize = fontSize.toInt()
                        onPreviewSettingsChange { current -> current.copy(fontSize = pendingFontSize) }
                    },
                    onValueChangeFinished = {
                        onPersistSettingsChange { current -> current.copy(fontSize = pendingFontSize) }
                    },
                    valueRange = 12f..32f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = "Reader Font Size Slider" },
                )
                Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${pendingFontSize}sp",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Line Height", style = MaterialTheme.typography.labelSmall)
                    Text(
                        String.format(Locale.getDefault(), "%.2f", pendingLineHeight),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Slider(
                    value = pendingLineHeight,
                    onValueChange = { lineHeight ->
                        pendingLineHeight = lineHeight
                        onPreviewSettingsChange { current -> current.copy(lineHeight = pendingLineHeight) }
                    },
                    onValueChangeFinished = {
                        onPersistSettingsChange { current -> current.copy(lineHeight = pendingLineHeight) }
                    },
                    valueRange = 1.0f..2.0f,
                    modifier = Modifier.semantics { contentDescription = "Reader Line Height Slider" },
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Padding", style = MaterialTheme.typography.labelSmall)
                    Text("${pendingHorizontalPadding}dp", style = MaterialTheme.typography.bodySmall)
                }
                Slider(
                    value = pendingHorizontalPadding.toFloat(),
                    onValueChange = { padding ->
                        pendingHorizontalPadding = padding.toInt()
                        onPreviewSettingsChange { current ->
                            current.copy(horizontalPadding = pendingHorizontalPadding)
                        }
                    },
                    onValueChangeFinished = {
                        onPersistSettingsChange { current -> current.copy(horizontalPadding = pendingHorizontalPadding) }
                    },
                    valueRange = 0f..32f,
                    modifier = Modifier.semantics { contentDescription = "Reader Horizontal Padding Slider" },
                )
            }
        }
    }

    val fonts = listOf("default", "serif", "sans-serif", "monospace", "karla")
    val fontListState = rememberLazyListState()

    LaunchedEffect(isVisible) {
        if (isVisible) {
            val selectedIndex = fonts.indexOf(settings.fontType)
            val layoutInfo = fontListState.layoutInfo
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == selectedIndex }
            val isFullyVisible = if (itemInfo != null) {
                itemInfo.offset >= 0 && (itemInfo.offset + itemInfo.size) <= layoutInfo.viewportEndOffset
            } else {
                false
            }

            if (!isFullyVisible && selectedIndex != -1) {
                fontListState.scrollToItem(selectedIndex)
            }
        }
    }

    ReaderControlsSection(
        title = "Font Family",
        testTag = "reader_controls_section_font_family",
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            state = fontListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(fonts) { font ->
                FilterChip(
                    selected = settings.fontType == font,
                    onClick = {
                        onPersistSettingsChange { current -> current.copy(fontType = font) }
                    },
                    label = { Text(font.replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

@Composable
internal fun ReaderThemeControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    isVisible: Boolean,
) {
    ReaderControlsSection(
        title = "Theme",
        testTag = "reader_controls_section_theme",
    ) {
        val themeOptions = availableThemeOptions(settings.customThemes)
        val listState = rememberLazyListState()

        LaunchedEffect(isVisible) {
            if (isVisible) {
                val index = themeOptions.indexOfFirst { it.id == settings.theme }
                val layoutInfo = listState.layoutInfo
                val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                val isFullyVisible = if (itemInfo != null) {
                    itemInfo.offset >= 0 && (itemInfo.offset + itemInfo.size) <= layoutInfo.viewportEndOffset
                } else {
                    false
                }

                if (!isFullyVisible && index != -1) {
                    listState.scrollToItem(index)
                }
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
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
internal fun ReaderReadingControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
) {
    ReaderControlsSection(
        title = "Reading",
        testTag = "reader_controls_section_reading",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderGeneralToggleRow(
                label = "Show Scrubber",
                checked = settings.showScrubber,
                onCheckedChange = { showScrubber ->
                    onSettingsChange { current -> current.copy(showScrubber = showScrubber) }
                },
            )
            ReaderGeneralToggleRow(
                label = "Show Scroll-to-Top",
                checked = settings.showScrollToTop,
                onCheckedChange = { show ->
                    onSettingsChange { it.copy(showScrollToTop = show) }
                },
            )
            ReaderGeneralToggleRow(
                label = "Selectable Text",
                checked = settings.selectableText,
                onCheckedChange = { selectableText ->
                    onSettingsChange { current -> current.copy(selectableText = selectableText) }
                },
            )
            ReaderStatusSettingsRow(
                settings = settings,
                onUpdateSettings = onSettingsChange,
                isReaderUI = true,
                isSystemBarVisible = settings.showSystemBar,
                showHeader = false,
            )
        }
    }
}

@Composable
internal fun ReaderOtherControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    isVisible: Boolean,
) {
    ReaderControlsSection(
        title = "Others",
        testTag = "reader_controls_section_others",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderGeneralToggleRow(
                label = "Show System Bar",
                checked = settings.showSystemBar,
                onCheckedChange = { showSystemBar ->
                    onSettingsChange { current -> current.copy(showSystemBar = showSystemBar) }
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Translate To", style = MaterialTheme.typography.labelSmall)
                val languages = listOf(
                    "ar" to "العربية",
                    "en" to "English",
                    "es" to "Español",
                    "fr" to "Français",
                    "ja" to "日本語",
                )
                val langListState = rememberLazyListState()

                LaunchedEffect(isVisible) {
                    if (isVisible) {
                        val index = languages.indexOfFirst { it.first == settings.targetTranslationLanguage }
                        val layoutInfo = langListState.layoutInfo
                        val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                        val isFullyVisible = if (itemInfo != null) {
                            itemInfo.offset >= 0 && (itemInfo.offset + itemInfo.size) <= layoutInfo.viewportEndOffset
                        } else {
                            false
                        }

                        if (!isFullyVisible && index != -1) {
                            langListState.scrollToItem(index)
                        }
                    }
                }

                LazyRow(
                    state = langListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(languages) { (code, name) ->
                        FilterChip(
                            selected = settings.targetTranslationLanguage == code,
                            onClick = {
                                onSettingsChange { it.copy(targetTranslationLanguage = code) }
                            },
                            label = { Text(name) },
                        )
                    }
                }
            }
        }
    }
}
