package com.epubreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.ui.ReaderStatusSettingsRow
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun InterfaceTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    primaryColor: androidx.compose.ui.graphics.Color? = null,
    onSurfaceColor: androidx.compose.ui.graphics.Color? = null,
) {
    val effectiveOnSurface = onSurfaceColor ?: MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Reader Interface", style = MaterialTheme.typography.titleLarge, color = effectiveOnSurface)

        SettingsToggleRow(
            title = "Show Reader Scrubber",
            subtitle = "Vertical progress handle on the right side.",
            checked = settings.showScrubber,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showScrubber = checked) } }
            },
            primaryColor = primaryColor,
            onSurfaceColor = effectiveOnSurface,
        )
        SettingsToggleRow(
            title = "Show System Bars",
            subtitle = "Keep status and navigation bars visible while reading.",
            checked = settings.showSystemBar,
            switchTestTag = "show_system_bar_switch",
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showSystemBar = checked) } }
            },
            primaryColor = primaryColor,
            onSurfaceColor = effectiveOnSurface,
        )
        SettingsToggleRow(
            title = "Show Scroll-to-Top Button",
            subtitle = "Quickly jump to the start of the chapter.",
            checked = settings.showScrollToTop,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showScrollToTop = checked) } }
            },
            primaryColor = primaryColor,
            onSurfaceColor = effectiveOnSurface,
        )

        HorizontalDivider(color = effectiveOnSurface.copy(alpha = 0.1f))
        Text("Status Overlay", style = MaterialTheme.typography.titleMedium, color = effectiveOnSurface)
        ReaderStatusSettingsRow(
            settings = settings,
            onUpdateSettings = { transform ->
                scope.launch { settingsManager.updateGlobalSettings(transform) }
            },
            isSystemBarVisible = settings.showSystemBar,
            primaryColor = primaryColor,
            onSurfaceColor = effectiveOnSurface,
        )
    }
}

@Composable
internal fun InteractionTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    primaryColor: androidx.compose.ui.graphics.Color? = null,
    onSurfaceColor: androidx.compose.ui.graphics.Color? = null,
) {
    val effectivePrimary = primaryColor ?: MaterialTheme.colorScheme.primary
    val effectiveOnSurface = onSurfaceColor ?: MaterialTheme.colorScheme.onSurface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Interaction", style = MaterialTheme.typography.titleLarge, color = effectiveOnSurface)

        SettingsToggleRow(
            title = "Selectable Text",
            subtitle = "Allow long-pressing to select text for copying or sharing.",
            checked = settings.selectableText,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(selectableText = checked) } }
            },
            primaryColor = primaryColor,
            onSurfaceColor = effectiveOnSurface,
        )
        SettingsToggleRow(
            title = "Haptic Feedback",
            subtitle = "Enable vibration for gestures and clicks.",
            checked = settings.hapticFeedback,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(hapticFeedback = checked) } }
            },
            primaryColor = primaryColor,
            onSurfaceColor = effectiveOnSurface,
        )

        HorizontalDivider(color = effectiveOnSurface.copy(alpha = 0.1f))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Translate To", style = MaterialTheme.typography.titleMedium, color = effectiveOnSurface)
            Text(
                "Target language for text translations",
                style = MaterialTheme.typography.bodySmall,
                color = effectiveOnSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            val languages = listOf("ar" to "العربية", "en" to "English", "es" to "Español", "fr" to "Français", "ja" to "日本語")
            val langListState = rememberLazyListState()

            LaunchedEffect(Unit) {
                val index = languages.indexOfFirst { it.first == settings.targetTranslationLanguage }
                if (index != -1) {
                    langListState.scrollToItem(index)
                }
            }

            val chipColors = FilterChipDefaults.filterChipColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                labelColor = effectiveOnSurface.copy(alpha = 0.5f),
                selectedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                selectedLabelColor = effectivePrimary,
            )
            val chipBorder = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = true,
                borderColor = effectiveOnSurface.copy(alpha = 0.1f),
                selectedBorderColor = effectivePrimary,
                borderWidth = 1.dp,
                selectedBorderWidth = 2.dp,
            )

            LazyRow(
                state = langListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(languages) { (code, name) ->
                    val isSelected = settings.targetTranslationLanguage == code
                    FilterChip(
                        selected = isSelected,
                        onClick = { scope.launch { settingsManager.updateGlobalSettings { it.copy(targetTranslationLanguage = code) } } },
                        label = { Text(name) },
                        colors = chipColors,
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = effectiveOnSurface.copy(alpha = 0.1f),
                            selectedBorderColor = effectivePrimary,
                            borderWidth = 1.dp,
                            selectedBorderWidth = 2.dp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun LibraryTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    primaryColor: androidx.compose.ui.graphics.Color? = null,
    onSurfaceColor: androidx.compose.ui.graphics.Color? = null,
) {
    val effectiveOnSurface = onSurfaceColor ?: MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Library", style = MaterialTheme.typography.titleLarge, color = effectiveOnSurface)
        SettingsToggleRow(
            title = "Allow Blank Covers",
            subtitle = "Removing a cover won't fall back to the original file cover.",
            checked = settings.allowBlankCovers,
            switchTestTag = "allow_blank_covers_switch",
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(allowBlankCovers = checked) } }
            },
            primaryColor = primaryColor,
            onSurfaceColor = effectiveOnSurface,
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchTestTag: String? = null,
    primaryColor: androidx.compose.ui.graphics.Color? = null,
    onSurfaceColor: androidx.compose.ui.graphics.Color? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val effectiveOnSurface = onSurfaceColor ?: MaterialTheme.colorScheme.onSurface
    val effectivePrimary = primaryColor ?: MaterialTheme.colorScheme.primary
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = effectiveOnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = effectiveOnSurface.copy(alpha = 0.6f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = effectivePrimary,
                uncheckedThumbColor = effectiveOnSurface.copy(alpha = 0.4f),
                uncheckedTrackColor = effectiveOnSurface.copy(alpha = 0.12f),
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        )
    }
}
