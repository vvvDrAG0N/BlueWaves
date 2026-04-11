/*
 * AI_SKIP_UNLESS_NEEDED
 * Reason: Simple UI composition; logic lives in SettingsManager.
 *
 * SettingsScreen.kt
 *
 * SETTINGS INTERFACE: Manages global reader preferences.
 *
 * ARCHITECTURAL CONSTRAINTS:
 * 1. [AI_NOTE] Direct DataStore Integration: Edits to these settings trigger immediate
 *    re-renders across the app via Flow collection in SettingsManager.
 * 2. [AI_WARNING] Parameter Sync: Values (fontSize, lineHeight, horizontalPadding) here MUST
 *    be kept in sync with the ranges defined in ReaderControls.kt to avoid UI inconsistencies.
 * 3. [AI_CRITICAL] Theme Identification: Uses string-based identifiers ("light", "sepia", "dark", "oled").
 *    Do not change these strings as they are persisted in DataStore and used for keying in getThemeColors().
 */

package com.epubreader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Global application settings screen.
 * [AI_NOTE] This screen primarily modifies the GlobalSettings object in SettingsManager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val settings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Reader Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            // Font Size
            Text("Font Size: ${settings.fontSize}sp", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = settings.fontSize.toFloat(),
                onValueChange = { 
                    scope.launch {
                         settingsManager.updateGlobalSettings(settings.copy(fontSize = it.toInt()))
                    }
                },
                valueRange = 12f..32f
            )

            // Line Height
            Text("Line Height: ${String.format(Locale.getDefault(), "%.1f", settings.lineHeight)}", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = settings.lineHeight,
                onValueChange = { 
                    scope.launch {
                        settingsManager.updateGlobalSettings(settings.copy(lineHeight = it))
                    }
                },
                valueRange = 1.2f..2.0f
            )

            // Horizontal Padding
            Text("Horizontal Padding: ${settings.horizontalPadding}dp", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = settings.horizontalPadding.toFloat(),
                onValueChange = { 
                    scope.launch {
                        settingsManager.updateGlobalSettings(settings.copy(horizontalPadding = it.toInt()))
                    }
                },
                valueRange = 0f..32f
            )

            // Font Family
            Spacer(Modifier.height(16.dp))
            Text("Font Family", style = MaterialTheme.typography.bodySmall)
            val fonts = listOf("default", "serif", "sans-serif", "monospace", "karla")
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                fonts.forEach { font ->
                    FilterChip(
                        selected = settings.fontType == font,
                        onClick = { 
                            scope.launch {
                                settingsManager.updateGlobalSettings(settings.copy(fontType = font))
                            }
                        },
                        label = { Text(font.replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            // Theme
            Spacer(Modifier.height(16.dp))
            Text("Theme", style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ThemeButton("light", Color.White, Color.Black, settings.theme == "light") {
                    scope.launch {
                        settingsManager.updateGlobalSettings(settings.copy(theme = "light"))
                    }
                }
                ThemeButton("sepia", Color(0xFFF4ECD8), Color(0xFF5B4636), settings.theme == "sepia") {
                    scope.launch {
                        settingsManager.updateGlobalSettings(settings.copy(theme = "sepia"))
                    }
                }
                ThemeButton("dark", Color(0xFF121212), Color.White, settings.theme == "dark") {
                    scope.launch {
                        settingsManager.updateGlobalSettings(settings.copy(theme = "dark"))
                    }
                }
                ThemeButton("oled", Color.Black, Color.White, settings.theme == "oled", label = "O") {
                    scope.launch {
                        settingsManager.updateGlobalSettings(settings.copy(theme = "oled"))
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeButton(
    name: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    label: String = "Abc",
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(60.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = textColor, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier) {
        content()
    }
}
