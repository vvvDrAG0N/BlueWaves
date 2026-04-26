package com.epubreader.feature.reader.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.GlobalSettingsTransform
import com.epubreader.feature.reader.ReaderTheme

@Composable
internal fun ReaderReadingControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    themeColors: ReaderTheme,
) {
    ReaderControlsSection(
        title = "Reading",
        testTag = "reader_controls_section_reading",
        themeColors = themeColors,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderGeneralToggleRow(
                label = "Show Scrubber",
                checked = settings.showScrubber,
                themeColors = themeColors,
                onCheckedChange = { showScrubber ->
                    onSettingsChange { current -> current.copy(showScrubber = showScrubber) }
                },
            )
            ReaderGeneralToggleRow(
                label = "Show Scroll-to-Top",
                checked = settings.showScrollToTop,
                themeColors = themeColors,
                onCheckedChange = { show ->
                    onSettingsChange { it.copy(showScrollToTop = show) }
                },
            )
            ReaderGeneralToggleRow(
                label = "Selectable Text",
                checked = settings.selectableText,
                themeColors = themeColors,
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
                primaryColor = themeColors.primary,
                onSurfaceColor = themeColors.foreground,
            )
        }
    }
}

@Composable
internal fun ReaderOtherControlsSection(
    settings: GlobalSettings,
    onSettingsChange: (GlobalSettingsTransform) -> Unit,
    isVisible: Boolean,
    themeColors: ReaderTheme,
) {
    ReaderControlsSection(
        title = "Others",
        testTag = "reader_controls_section_others",
        themeColors = themeColors,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderGeneralToggleRow(
                label = "Show System Bar",
                checked = settings.showSystemBar,
                themeColors = themeColors,
                onCheckedChange = { showSystemBar ->
                    onSettingsChange { current -> current.copy(showSystemBar = showSystemBar) }
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Translate To",
                    style = MaterialTheme.typography.labelSmall,
                    color = themeColors.variantForeground,
                )
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
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.Transparent,
                                selectedLabelColor = themeColors.primary,
                                labelColor = themeColors.foreground,
                                containerColor = Color.Transparent,
                            ),
                            border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = settings.targetTranslationLanguage == code,
                                borderColor = themeColors.foreground.copy(alpha = 0.2f),
                                selectedBorderColor = themeColors.primary,
                                borderWidth = 1.dp,
                                selectedBorderWidth = 2.dp,
                            ),
                        )
                    }
                }
            }
        }
    }
}
