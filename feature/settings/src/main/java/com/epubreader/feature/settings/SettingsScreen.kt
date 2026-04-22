package com.epubreader.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.data.settings.SettingsManager
import java.util.UUID
import kotlinx.coroutines.launch

private enum class SettingsSection(val title: String) {
    Appearance("Appearance"),
    Interface("Interface"),
    Interaction("Interaction"),
    Library("Library"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit,
) {
    val settings by settingsManager.globalSettings.collectAsState(initial = GlobalSettings())
    val scope = rememberCoroutineScope()
    var activeSection by remember { mutableStateOf<SettingsSection?>(null) }
    var editorSession by remember { mutableStateOf<ThemeEditorSession?>(null) }
    var themeToDelete by remember { mutableStateOf<CustomTheme?>(null) }

    fun openCreateThemeEditor() {
        editorSession = ThemeEditorSession(
            themeId = "$CustomThemeIdPrefix${UUID.randomUUID()}",
            isNew = true,
            draft = ThemeEditorDraft.fromPalette(
                name = "",
                palette = themePaletteSeed(settings.theme, settings.customThemes),
                isAdvanced = false,
            ),
        )
    }

    fun openEditThemeEditor(theme: CustomTheme) {
        editorSession = ThemeEditorSession(
            themeId = theme.id,
            isNew = false,
            draft = ThemeEditorDraft.fromTheme(theme),
        )
    }

    val currentSection = activeSection
    val isAppearance = currentSection == SettingsSection.Appearance

    Scaffold(
        contentWindowInsets = if (isAppearance) WindowInsets(0, 0, 0, 0) else getStaticWindowInsets(),
        containerColor = if (isAppearance) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (!isAppearance) {
                TopAppBar(
                    windowInsets = getStaticWindowInsets(),
                    title = { Text(currentSection?.title ?: "Settings") },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (currentSection != null) activeSection = null else onBack()
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isAppearance) PaddingValues(0.dp) else padding),
        ) {
            val currentPalette = themePaletteSeed(settings.theme, settings.customThemes)
            val themePrimary = androidx.compose.ui.graphics.Color(currentPalette.primary)
            val themeOnSurface = androidx.compose.ui.graphics.Color(currentPalette.systemForeground)

            when (currentSection) {
                null -> SettingsMenuList(settings = settings, onNavigate = { activeSection = it })
                SettingsSection.Appearance -> AppearanceTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                    onOpenCreateThemeEditor = ::openCreateThemeEditor,
                    onOpenEditThemeEditor = ::openEditThemeEditor,
                    onDeleteTheme = { themeToDelete = it },
                    onBack = { activeSection = null },
                )
                SettingsSection.Interface -> InterfaceTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                    primaryColor = themePrimary,
                    onSurfaceColor = themeOnSurface,
                )
                SettingsSection.Interaction -> InteractionTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                    primaryColor = themePrimary,
                    onSurfaceColor = themeOnSurface,
                )
                SettingsSection.Library -> LibraryTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                    primaryColor = themePrimary,
                    onSurfaceColor = themeOnSurface,
                )
            }
        }
    }

    editorSession?.let { session ->
        val currentPalette = themePaletteSeed(settings.theme, settings.customThemes)
        CustomThemeEditorDialog(
            session = session,
            activeThemeId = settings.theme,
            existingThemes = settings.customThemes,
            primaryColor = androidx.compose.ui.graphics.Color(currentPalette.primary),
            onSurfaceColor = androidx.compose.ui.graphics.Color(currentPalette.systemForeground),
            onDismiss = { editorSession = null },
            onSave = { theme, shouldActivate ->
                scope.launch {
                    settingsManager.saveCustomTheme(theme, activate = shouldActivate)
                    editorSession = null
                }
            },
            onDelete = {
                scope.launch {
                    settingsManager.deleteCustomTheme(session.themeId)
                    editorSession = null
                }
            },
        )
    }

    themeToDelete?.let { customTheme ->
        AlertDialog(
            onDismissRequest = { themeToDelete = null },
            title = { Text("Delete Theme") },
            text = {
                Text("Delete \"${customTheme.name}\"? If it is active, the app will switch to the previous theme.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            settingsManager.deleteCustomTheme(customTheme.id)
                            themeToDelete = null
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { themeToDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
        )
    }

    BackHandler(enabled = activeSection != null) {
        activeSection = null
    }
}

@Composable
private fun SettingsMenuList(
    settings: GlobalSettings,
    onNavigate: (SettingsSection) -> Unit,
) {
    data class SectionEntry(
        val section: SettingsSection,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val subtitle: String?,
    )

    val entries = listOf(
        SectionEntry(SettingsSection.Appearance, Icons.Outlined.Palette, "Themes · ${settings.fontSize}sp · ${settings.fontType}"),
        SectionEntry(SettingsSection.Interface, Icons.Outlined.Settings, "Scrubber · System Bars · Status"),
        SectionEntry(SettingsSection.Interaction, Icons.Default.TouchApp, "Selection · Haptics · Translation"),
        SectionEntry(SettingsSection.Library, Icons.Default.MenuBook, "Book covers & management"),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        entries.forEachIndexed { index, entry ->
            ListItem(
                headlineContent = { Text(entry.section.title) },
                supportingContent = entry.subtitle?.let { subtitle ->
                    { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                leadingContent = {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open ${entry.section.title}",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(entry.section) }
                    .testTag("settings_section_${entry.section.name.lowercase()}"),
            )
            if (index < entries.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun NetworkPlaceholderTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Network settings coming soon.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
