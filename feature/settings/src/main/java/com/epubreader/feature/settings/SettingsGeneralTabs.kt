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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Reader Interface", style = MaterialTheme.typography.titleLarge)

        SettingsToggleRow(
            title = "Show Reader Scrubber",
            subtitle = "Vertical progress handle on the right side.",
            checked = settings.showScrubber,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showScrubber = checked) } }
            },
        )
        SettingsToggleRow(
            title = "Show System Bars",
            subtitle = "Keep status and navigation bars visible while reading.",
            checked = settings.showSystemBar,
            switchTestTag = "show_system_bar_switch",
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showSystemBar = checked) } }
            },
        )
        SettingsToggleRow(
            title = "Show Scroll-to-Top Button",
            subtitle = "Quickly jump to the start of the chapter.",
            checked = settings.showScrollToTop,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showScrollToTop = checked) } }
            },
        )

        HorizontalDivider()
        Text("Status Overlay", style = MaterialTheme.typography.titleMedium)
        ReaderStatusSettingsRow(
            settings = settings,
            onUpdateSettings = { transform ->
                scope.launch { settingsManager.updateGlobalSettings(transform) }
            },
            isSystemBarVisible = settings.showSystemBar,
        )
    }
}

@Composable
internal fun InteractionTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Interaction", style = MaterialTheme.typography.titleLarge)

        SettingsToggleRow(
            title = "Selectable Text",
            subtitle = "Allow long-pressing to select text for copying or sharing.",
            checked = settings.selectableText,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(selectableText = checked) } }
            },
        )
        SettingsToggleRow(
            title = "Haptic Feedback",
            subtitle = "Enable vibration for gestures and clicks.",
            checked = settings.hapticFeedback,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(hapticFeedback = checked) } }
            },
        )

        HorizontalDivider()

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Translate To", style = MaterialTheme.typography.titleMedium)
            Text(
                "Target language for text translations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            LazyRow(
                state = langListState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(languages) { (code, name) ->
                    FilterChip(
                        selected = settings.targetTranslationLanguage == code,
                        onClick = { scope.launch { settingsManager.updateGlobalSettings { it.copy(targetTranslationLanguage = code) } } },
                        label = { Text(name) },
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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Library", style = MaterialTheme.typography.titleLarge)
        SettingsToggleRow(
            title = "Allow Blank Covers",
            subtitle = "Removing a cover won't fall back to the original file cover.",
            checked = settings.allowBlankCovers,
            switchTestTag = "allow_blank_covers_switch",
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(allowBlankCovers = checked) } }
            },
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchTestTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = switchTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}
