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
 * 3. [AI_CRITICAL] Theme Identification: Built-in theme identifiers ("light", "sepia", "dark", "oled")
 *    remain stable. Custom theme identifiers must stay additive so older stored theme names keep working.
 */

package com.epubreader.feature.settings

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.core.model.parseThemeColorOrNull
import com.epubreader.core.model.themeButtonLabel
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.data.settings.SettingsManager
import java.util.UUID
import java.util.zip.ZipInputStream
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/** Which top-level section the user has drilled into. Null = top-level list. */
private enum class SettingsSection(val title: String) {
    General("General"),
    Fonts("Fonts & Reading"),
    Themes("Themes"),
    Network("Network"), // future placeholder
}

/**
 * Global application settings screen.
 * [AI_NOTE] This screen primarily modifies the GlobalSettings object in SettingsManager.
 *
 * Structure: top-level list → focused subview per section.
 * Tab-style content (Fonts, Themes, General) is preserved inside each subview.
 */
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

    val fontsScrollState = rememberScrollState()
    val themesScrollState = rememberScrollState()
    val generalScrollState = rememberScrollState()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentSection?.title ?: "Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSection != null) {
                            activeSection = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentSection) {
                null -> SettingsMenuList(
                    settings = settings,
                    onNavigate = { activeSection = it },
                )
                SettingsSection.General -> GeneralTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                    scrollState = generalScrollState,
                )
                SettingsSection.Fonts -> FontsTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                    scrollState = fontsScrollState,
                )
                SettingsSection.Themes -> ThemesTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                    onOpenCreateThemeEditor = ::openCreateThemeEditor,
                    onOpenEditThemeEditor = ::openEditThemeEditor,
                    onDeleteTheme = { themeToDelete = it },
                    scrollState = themesScrollState,
                )
                SettingsSection.Network -> NetworkPlaceholderTab()
            }
        }
    }

    editorSession?.let { session ->
        CustomThemeEditorDialog(
            session = session,
            activeThemeId = settings.theme,
            existingThemes = settings.customThemes,
            onDismiss = { editorSession = null },
            onSave = { theme, shouldActivate ->
                scope.launch {
                    settingsManager.saveCustomTheme(theme, activate = shouldActivate)
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
                Text("Delete \"${customTheme.name}\"? If it is active, the app falls back to Light.")
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
}

/** Top-level settings menu showing available sections as tappable list items. */
@Composable
private fun SettingsMenuList(
    settings: GlobalSettings,
    onNavigate: (SettingsSection) -> Unit,
) {
    data class SectionEntry(
        val section: SettingsSection,
        val icon: ImageVector,
        val subtitle: String?,
    )

    val activeThemeName = remember(settings.theme, settings.customThemes) {
        (BuiltInThemeOptions.firstOrNull { it.id == settings.theme }?.name)
            ?: settings.customThemes.firstOrNull { it.id == settings.theme }?.name
            ?: "Unknown"
    }

    val entries = listOf(
        SectionEntry(SettingsSection.General, Icons.Outlined.Settings, null),
        SectionEntry(SettingsSection.Fonts, Icons.Outlined.FormatSize, "${settings.fontSize}sp · ${settings.fontType}"),
        SectionEntry(SettingsSection.Themes, Icons.Outlined.Palette, activeThemeName),
        SectionEntry(SettingsSection.Network, Icons.Outlined.WifiOff, "Coming soon"),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        entries.forEachIndexed { index, entry ->
            ListItem(
                headlineContent = { Text(entry.section.title) },
                supportingContent = entry.subtitle?.let { sub -> { Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
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
                    .clickable(
                        enabled = entry.section != SettingsSection.Network,
                        onClick = { onNavigate(entry.section) },
                    )
                    .testTag("settings_section_${entry.section.name.lowercase()}"),
            )
            if (index < entries.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/** Placeholder for the future Network section. */
@Composable
private fun NetworkPlaceholderTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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



@Composable
private fun FontsTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text("Font Size", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { fontSize ->
                        scope.launch {
                            settingsManager.updateGlobalSettings { it.copy(fontSize = fontSize.toInt()) }
                        }
                    },
                    valueRange = 12f..32f,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Font Size Slider" }
                )
                Text(
                    "${settings.fontSize}sp",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        Column {
            Text("Line Height", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.lineHeight,
                    onValueChange = { lineHeight ->
                        scope.launch {
                            settingsManager.updateGlobalSettings { it.copy(lineHeight = lineHeight) }
                        }
                    },
                    valueRange = 1.2f..2.0f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    String.format(Locale.getDefault(), "%.1f", settings.lineHeight),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        Column {
            Text("Horizontal Padding", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = settings.horizontalPadding.toFloat(),
                    onValueChange = { padding ->
                        scope.launch {
                            settingsManager.updateGlobalSettings { it.copy(horizontalPadding = padding.toInt()) }
                        }
                    },
                    valueRange = 0f..32f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${settings.horizontalPadding}dp",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        Column {
            Text("Font Family", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fonts = listOf("default", "serif", "sans-serif", "monospace", "karla")
                fonts.forEach { font ->
                    FilterChip(
                        selected = settings.fontType == font,
                        onClick = {
                            scope.launch {
                                settingsManager.updateGlobalSettings { it.copy(fontType = font) }
                            }
                        },
                        label = { Text(font.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemesTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    onOpenCreateThemeEditor: () -> Unit,
    onOpenEditThemeEditor: (CustomTheme) -> Unit,
    onDeleteTheme: (CustomTheme) -> Unit,
    scrollState: ScrollState
) {
    val context = LocalContext.current
    var themeToExport by remember { mutableStateOf<CustomTheme?>(null) }

    // Shared helper: import one parsed JSONObject as a custom theme.
    // seenPalettes / seenNames track within-batch state to avoid intra-batch duplicates.
    // Returns the imported theme name on success, null on skip (duplicate/invalid).
    suspend fun importSingleThemeJson(
        json: JSONObject,
        seenPalettes: MutableSet<ThemePalette>,
        seenNames: MutableSet<String>,
    ): String? {
        val keys = listOf("primary", "secondary", "background", "surface", "surfaceVariant", "outline", "readerBackground", "readerText")
        if (keys.none { json.has(it) }) return null // silently skip non-theme JSON entries in ZIPs

        val palette = ThemePalette(
            primary = parseThemeColorOrNull(json.optString("primary")) ?: 0xFF6200EE,
            secondary = parseThemeColorOrNull(json.optString("secondary")) ?: 0xFF03DAC6,
            background = parseThemeColorOrNull(json.optString("background")) ?: 0xFFFFFFFF,
            surface = parseThemeColorOrNull(json.optString("surface")) ?: 0xFFFFFFFF,
            surfaceVariant = parseThemeColorOrNull(json.optString("surfaceVariant")) ?: 0xFFEEEEEE,
            outline = parseThemeColorOrNull(json.optString("outline")) ?: 0xFF757575,
            readerBackground = parseThemeColorOrNull(json.optString("readerBackground")) ?: 0xFFFFFFFF,
            readerForeground = parseThemeColorOrNull(json.optString("readerText")) ?: 0xFF000000,
            systemForeground = parseThemeColorOrNull(json.optString("systemForeground")) ?: 0xFF000000,
        )

        // Skip exact palette duplicates (both persisted and already-imported-this-batch)
        if (seenPalettes.contains(palette)) return null

        val baseName = json.optString("name", "Imported Theme")
        var finalName = baseName
        if (seenNames.contains(finalName.lowercase().trim())) {
            var counter = 1
            while (seenNames.contains("${baseName.lowercase().trim()} ($counter)")) counter++
            finalName = "$baseName ($counter)"
        }

        val themeId = "${CustomThemeIdPrefix}${UUID.randomUUID()}"
        val newTheme = CustomTheme(id = themeId, name = finalName, palette = palette, isAdvanced = true)
        settingsManager.saveCustomTheme(newTheme, activate = false)

        seenPalettes.add(palette)
        seenNames.add(finalName.lowercase().trim())
        return finalName
    }

    val bulkImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var imported = 0
            var skipped = 0
            var failed = 0
            // Seed the seen-sets from current persisted themes so we detect inter-session duplicates.
            val seenPalettes: MutableSet<ThemePalette> = settings.customThemes.map { it.palette }.toMutableSet()
            val seenNames: MutableSet<String> = settings.customThemes.map { it.name.lowercase().trim() }.toMutableSet()

            for (uri in uris) {
                try {
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val isZip = mimeType.contains("zip") || uri.toString().endsWith(".zip", ignoreCase = true)
                    if (isZip) {
                        context.contentResolver.openInputStream(uri)?.use { raw ->
                            ZipInputStream(raw).use { zip ->
                                var entry = zip.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                                        try {
                                            val content = zip.bufferedReader(Charsets.UTF_8).readText()
                                            if (content.isNotBlank()) {
                                                val json = try { JSONObject(content) } catch (_: Exception) { null }
                                                if (json != null) {
                                                    val name = importSingleThemeJson(json, seenPalettes, seenNames)
                                                    if (name != null) imported++ else skipped++
                                                } else {
                                                    failed++
                                                }
                                            }
                                        } catch (_: Exception) {
                                            failed++
                                        }
                                        zip.closeEntry()
                                    }
                                    entry = zip.nextEntry
                                }
                            }
                        }
                    } else {
                        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        if (content.isNullOrBlank()) { failed++; continue }
                        val json = try { JSONObject(content) } catch (_: Exception) { null }
                        if (json == null) { failed++; continue }
                        val name = importSingleThemeJson(json, seenPalettes, seenNames)
                        if (name != null) imported++ else skipped++
                    }
                } catch (_: Exception) {
                    failed++
                }
            }
            val msg = buildString {
                if (imported > 0) append("Imported $imported theme${if (imported > 1) "s" else ""}. ")
                if (skipped > 0) append("$skipped duplicate${if (skipped > 1) "s" else ""} skipped. ")
                if (failed > 0) append("$failed failed.")
            }.trim()
            Toast.makeText(context, msg.ifEmpty { "Nothing imported." }, Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { exportUri ->
            val theme = themeToExport ?: return@let
            scope.launch {
                try {
                    val json = JSONObject().apply {
                        put("name", theme.name)
                        put("primary", formatThemeColor(theme.palette.primary))
                        put("secondary", formatThemeColor(theme.palette.secondary))
                        put("background", formatThemeColor(theme.palette.background))
                        put("surface", formatThemeColor(theme.palette.surface))
                        put("surfaceVariant", formatThemeColor(theme.palette.surfaceVariant))
                        put("outline", formatThemeColor(theme.palette.outline))
                        put("readerBackground", formatThemeColor(theme.palette.readerBackground))
                        put("readerText", formatThemeColor(theme.palette.readerForeground))
                        put("systemForeground", formatThemeColor(theme.palette.systemForeground))
                    }
                    context.contentResolver.openOutputStream(exportUri)?.use { out ->
                        out.write(json.toString(4).toByteArray())
                    }
                    Toast.makeText(context, "Theme exported", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                } finally {
                    themeToExport = null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text("Built-in Themes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val builtInListState = rememberLazyListState()
            LaunchedEffect(settings.theme) {
                val index = BuiltInThemeOptions.indexOfFirst { it.id == settings.theme }
                if (index != -1) {
                    builtInListState.animateScrollToItem(index)
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = builtInListState,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(BuiltInThemeOptions) { _, option ->
                    ThemeButton(
                        name = option.name,
                        bgColor = Color(option.palette.readerBackground),
                        textColor = Color(option.palette.readerForeground),
                        isSelected = settings.theme == option.id,
                        label = themeButtonLabel(option.name, option.id),
                        onClick = {
                            scope.launch {
                                settingsManager.setActiveTheme(option.id)
                            }
                        }
                    )
                }
            }
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Custom Themes", style = MaterialTheme.typography.titleMedium)
                Row {
                    IconButton(
                        onClick = { bulkImportLauncher.launch("*/*") },
                        modifier = Modifier.testTag("import_custom_theme_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import Themes (JSON or ZIP)")
                    }
                    IconButton(
                        onClick = onOpenCreateThemeEditor,
                        modifier = Modifier.testTag("create_custom_theme_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Theme")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (settings.customThemes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No custom themes yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val customListState = rememberLazyListState()
                LaunchedEffect(settings.theme, settings.customThemes) {
                    val index = settings.customThemes.indexOfFirst { it.id == settings.theme }
                    if (index != -1) {
                        customListState.animateScrollToItem(index)
                    }
                }
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    state = customListState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(settings.customThemes) { _, theme ->
                        CompactCustomThemeCard(
                            theme = theme,
                            isSelected = settings.theme == theme.id,
                            onSelect = {
                                scope.launch {
                                    settingsManager.setActiveTheme(theme.id)
                                }
                            },
                            onEdit = { onOpenEditThemeEditor(theme) },
                            onDelete = { onDeleteTheme(theme) },
                            onExport = {
                                themeToExport = it
                                exportLauncher.launch("theme_${it.name.replace(" ", "_")}.json")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show Reader Scrubber", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Display a vertical progress handle on the right side of the screen while reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.showScrubber,
                onCheckedChange = { checked ->
                    scope.launch {
                        settingsManager.updateGlobalSettings { it.copy(showScrubber = checked) }
                    }
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Show System Bars", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Keep status and navigation bars visible while reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.showSystemBar,
                onCheckedChange = { checked ->
                    scope.launch {
                        settingsManager.updateGlobalSettings { it.copy(showSystemBar = checked) }
                    }
                },
                modifier = Modifier.testTag("show_system_bar_switch")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Selectable Text", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Allow long-pressing to select text for copying or sharing. May conflict with some navigation gestures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.selectableText,
                onCheckedChange = { checked ->
                    scope.launch {
                        settingsManager.updateGlobalSettings { it.copy(selectableText = checked) }
                    }
                },
                modifier = Modifier.testTag("selectable_text_switch")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Allow Blank Covers", style = MaterialTheme.typography.titleMedium)
                Text(
                    "When enabled, removing the current cover can leave the book blank instead of falling back to the stored original cover.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.allowBlankCovers,
                onCheckedChange = { checked ->
                    scope.launch {
                        settingsManager.updateGlobalSettings { it.copy(allowBlankCovers = checked) }
                    }
                },
                modifier = Modifier.testTag("allow_blank_covers_switch")
            )
        }
    }
}

@Composable
fun ThemeButton(
    name: String,
    bgColor: Color,
    textColor: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    label: String = "A",
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = modifier
            .padding(8.dp)
            .size(60.dp)
            .semantics {
                contentDescription = "Theme $name"
                selected = isSelected
            },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = textColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CompactCustomThemeCard(
    theme: CustomTheme,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: (CustomTheme) -> Unit,
) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ThemeButton(
                name = theme.name,
                bgColor = Color(theme.palette.readerBackground),
                textColor = Color(theme.palette.readerForeground),
                isSelected = isSelected,
                label = themeButtonLabel(theme.name, theme.id),
                onClick = onSelect,
            )
            Text(
                text = theme.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { onExport(theme) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.FileUpload,
                        contentDescription = "Export ${theme.name}",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit ${theme.name}",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${theme.name}",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomThemeEditorDialog(
    session: ThemeEditorSession,
    activeThemeId: String,
    existingThemes: List<CustomTheme>,
    onDismiss: () -> Unit,
    onSave: (CustomTheme, Boolean) -> Unit,
) {
    var draft by remember(session) { mutableStateOf(session.draft) }

    // Auto-generation logic
    LaunchedEffect(draft.primary, draft.background, draft.isAdvanced) {
        if (!draft.isAdvanced) {
            val p = parseThemeColorOrNull(draft.primary)
            val b = parseThemeColorOrNull(draft.background)
            if (p != null && b != null) {
                val generated = generatePaletteFromBase(p, b)
                draft = draft.copy(
                    secondary = formatThemeColor(generated.secondary),
                    surface = formatThemeColor(generated.surface),
                    surfaceVariant = formatThemeColor(generated.surfaceVariant),
                    outline = formatThemeColor(generated.outline),
                    readerBackground = formatThemeColor(generated.readerBackground),
                    readerForeground = formatThemeColor(generated.readerForeground),
                    systemForeground = formatThemeColor(generated.systemForeground),
                )
            }
        }
    }

    val parsedTheme = remember(draft, session.themeId) {
        draft.toCustomTheme(session.themeId)
    }
    val trimmedName = draft.name.trim()
    val nameConflict = existingThemes.any { existing ->
        existing.id != session.themeId && existing.name.equals(trimmedName, ignoreCase = true)
    }
    val isValid = parsedTheme != null && trimmedName.isNotEmpty() && !nameConflict
    val shouldActivate = session.isNew || activeThemeId == session.themeId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (session.isNew) "Create Custom Theme" else "Edit Custom Theme")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Theme Name") },
                    singleLine = true,
                    isError = trimmedName.isEmpty() || nameConflict,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_theme_name"),
                )
                if (nameConflict) {
                    Text(
                        text = "Theme names must be unique.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Advanced Mode", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = draft.isAdvanced,
                        onCheckedChange = { draft = draft.copy(isAdvanced = it) },
                        modifier = Modifier.testTag("custom_theme_advanced_toggle"),
                    )
                }

                ThemeColorField(
                    label = "Primary",
                    value = draft.primary,
                    onValueChange = { draft = draft.copy(primary = it) },
                    testTag = "custom_theme_primary",
                )
                ThemeColorField(
                    label = "Background",
                    value = draft.background,
                    onValueChange = { draft = draft.copy(background = it) },
                    testTag = "custom_theme_background",
                )

                if (draft.isAdvanced) {
                    ThemeColorField(
                        label = "Secondary",
                        value = draft.secondary,
                        onValueChange = { draft = draft.copy(secondary = it) },
                        testTag = "custom_theme_secondary",
                    )
                    ThemeColorField(
                        label = "Surface",
                        value = draft.surface,
                        onValueChange = { draft = draft.copy(surface = it) },
                        testTag = "custom_theme_surface",
                    )
                    ThemeColorField(
                        label = "Surface Variant",
                        value = draft.surfaceVariant,
                        onValueChange = { draft = draft.copy(surfaceVariant = it) },
                        testTag = "custom_theme_surface_variant",
                    )
                    ThemeColorField(
                        label = "Outline",
                        value = draft.outline,
                        onValueChange = { draft = draft.copy(outline = it) },
                        testTag = "custom_theme_outline",
                    )
                    ThemeColorField(
                        label = "Reader Background",
                        value = draft.readerBackground,
                        onValueChange = { draft = draft.copy(readerBackground = it) },
                        testTag = "custom_theme_reader_background",
                    )
                    ThemeColorField(
                        label = "Reader Text",
                        value = draft.readerForeground,
                        onValueChange = { draft = draft.copy(readerForeground = it) },
                        testTag = "custom_theme_reader_foreground",
                    )
                    ThemeColorField(
                        label = "System Text",
                        value = draft.systemForeground,
                        onValueChange = { draft = draft.copy(systemForeground = it) },
                        testTag = "custom_theme_system_foreground",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    parsedTheme?.let { onSave(it, shouldActivate) }
                },
            ) {
                Text(if (session.isNew) "Create Theme" else "Save Theme")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

@Composable
private fun ThemeColorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
) {
    val parsedColor = parseThemeColorOrNull(value)
    var pickerExpanded by remember { mutableStateOf(false) }
    var pickerHue by remember { mutableFloatStateOf(0f) }
    var pickerSaturation by remember { mutableFloatStateOf(0f) }
    var pickerValue by remember { mutableFloatStateOf(1f) }

    fun openPicker() {
        val hsv = (parsedColor ?: DefaultPickerColor).toHsvColor()
        pickerHue = hsv.hue
        pickerSaturation = hsv.saturation
        pickerValue = hsv.value
        pickerExpanded = true
    }

    fun updatePickerColor(
        hue: Float = pickerHue,
        saturation: Float = pickerSaturation,
        valueBrightness: Float = pickerValue,
    ) {
        pickerHue = hue
        pickerSaturation = saturation
        pickerValue = valueBrightness
        onValueChange(
            formatThemeColor(
                HsvColor(
                    hue = pickerHue,
                    saturation = pickerSaturation,
                    value = pickerValue,
                ).toColorLong()
            )
        )
    }

    val previewColor = Color(
        HsvColor(
            hue = pickerHue,
            saturation = pickerSaturation,
            value = pickerValue,
        ).toColorLong()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .testTag("${testTag}_swatch")
                    .semantics { contentDescription = "Open color picker for $label" }
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = ::openPicker)
                    .padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(parsedColor?.let(::Color) ?: MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(9.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            DropdownMenu(
                expanded = pickerExpanded,
                onDismissRequest = { pickerExpanded = false },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 240.dp, max = 280.dp)
                        .padding(12.dp)
                        .testTag("${testTag}_picker_menu"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("$label Color", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = formatThemeColor(
                            HsvColor(
                                hue = pickerHue,
                                saturation = pickerSaturation,
                                value = pickerValue,
                            ).toColorLong()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(previewColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                    )
                    Text("Hue", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = pickerHue,
                        onValueChange = { updatePickerColor(hue = it) },
                        valueRange = 0f..360f,
                        modifier = Modifier.testTag("${testTag}_picker_hue"),
                    )
                    Text("Saturation", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = pickerSaturation,
                        onValueChange = { updatePickerColor(saturation = it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("${testTag}_picker_saturation"),
                    )
                    Text("Brightness", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = pickerValue,
                        onValueChange = { updatePickerColor(valueBrightness = it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.testTag("${testTag}_picker_value"),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = parsedColor == null,
            supportingText = {
                if (parsedColor == null) {
                    Text("Use a hex color like #F4ECD8")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
        )
    }
}

private const val DefaultPickerColor = 0xFFFFFFFFL

private data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toColorLong(): Long {
        return AndroidColor.HSVToColor(
            floatArrayOf(
                hue.coerceIn(0f, 360f),
                saturation.coerceIn(0f, 1f),
                value.coerceIn(0f, 1f),
            )
        ).toLong() and 0xFFFFFFFFL
    }
}

private fun Long.toHsvColor(): HsvColor {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(this.toInt(), hsv)
    return HsvColor(
        hue = hsv[0],
        saturation = hsv[1],
        value = hsv[2],
    )
}

private data class ThemeEditorSession(
    val themeId: String,
    val isNew: Boolean,
    val draft: ThemeEditorDraft,
)

private data class ThemeEditorDraft(
    val name: String,
    val primary: String,
    val secondary: String,
    val background: String,
    val surface: String,
    val surfaceVariant: String,
    val outline: String,
    val readerBackground: String,
    val readerForeground: String,
    val systemForeground: String,
    val isAdvanced: Boolean = true,
) {
    fun toCustomTheme(themeId: String): CustomTheme? {
        val parsedPrimary = parseThemeColorOrNull(primary) ?: return null
        val parsedSecondary = parseThemeColorOrNull(secondary) ?: return null
        val parsedBackground = parseThemeColorOrNull(background) ?: return null
        val parsedSurface = parseThemeColorOrNull(surface) ?: return null
        val parsedSurfaceVariant = parseThemeColorOrNull(surfaceVariant) ?: return null
        val parsedOutline = parseThemeColorOrNull(outline) ?: return null
        val parsedReaderBackground = parseThemeColorOrNull(readerBackground) ?: return null
        val parsedReaderForeground = parseThemeColorOrNull(readerForeground) ?: return null
        val parsedSystemForeground = parseThemeColorOrNull(systemForeground) ?: return null
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return null
        }

        return CustomTheme(
            id = themeId,
            name = trimmedName,
            palette = ThemePalette(
                primary = parsedPrimary,
                secondary = parsedSecondary,
                background = parsedBackground,
                surface = parsedSurface,
                surfaceVariant = parsedSurfaceVariant,
                outline = parsedOutline,
                readerBackground = parsedReaderBackground,
                readerForeground = parsedReaderForeground,
                systemForeground = parsedSystemForeground,
            ),
            isAdvanced = isAdvanced,
        )
    }

    companion object {
        fun fromTheme(theme: CustomTheme): ThemeEditorDraft {
            return fromPalette(
                name = theme.name,
                palette = theme.palette,
                isAdvanced = theme.isAdvanced,
            )
        }

        fun fromPalette(
            name: String,
            palette: ThemePalette,
            isAdvanced: Boolean = true,
        ): ThemeEditorDraft {
            return ThemeEditorDraft(
                name = name,
                primary = formatThemeColor(palette.primary),
                secondary = formatThemeColor(palette.secondary),
                background = formatThemeColor(palette.background),
                surface = formatThemeColor(palette.surface),
                surfaceVariant = formatThemeColor(palette.surfaceVariant),
                outline = formatThemeColor(palette.outline),
                readerBackground = formatThemeColor(palette.readerBackground),
                readerForeground = formatThemeColor(palette.readerForeground),
                systemForeground = formatThemeColor(palette.systemForeground),
                isAdvanced = isAdvanced,
            )
        }
    }
}
