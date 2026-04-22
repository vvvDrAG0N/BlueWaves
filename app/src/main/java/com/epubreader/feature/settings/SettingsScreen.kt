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
 * 3. [AI_CRITICAL] Theme Identification: Built-in theme identifiers ("light", "azure", "dark", "oled")
 *    remain stable. Custom theme identifiers must stay additive so older stored theme names keep working.
 */

package com.epubreader.feature.settings

import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.epubreader.core.model.isThemeNameUnique
import com.epubreader.core.model.parseThemeColorOrNull
import com.epubreader.core.model.themeButtonLabel
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.data.settings.SettingsManager
import com.epubreader.core.ui.getStaticWindowInsets
import com.epubreader.feature.reader.KarlaFont
import com.epubreader.feature.reader.ReaderStatusSettingsRow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/** Which top-level section the user has drilled into. Null = top-level list. */
private enum class SettingsSection(val title: String) {
    Appearance("Appearance"),
    Interface("Interface"),
    Interaction("Interaction"),
    Library("Library"),
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
        contentWindowInsets = getStaticWindowInsets(),
        topBar = {
            TopAppBar(
                windowInsets = getStaticWindowInsets(),
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
                SettingsSection.Appearance -> AppearanceTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                    onOpenCreateThemeEditor = ::openCreateThemeEditor,
                    onOpenEditThemeEditor = ::openEditThemeEditor,
                    onDeleteTheme = { themeToDelete = it },
                )
                SettingsSection.Interface -> InterfaceTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                )
                SettingsSection.Interaction -> InteractionTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                )
                SettingsSection.Library -> LibraryTab(
                    settings = settings,
                    scope = scope,
                    settingsManager = settingsManager,
                )
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
            onDelete = {
                scope.launch {
                    settingsManager.deleteCustomTheme(session.themeId)
                    editorSession = null
                }
            }
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

    // System back gesture should go up within settings sections, not exit entirely.
    BackHandler(enabled = activeSection != null) {
        activeSection = null
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
        SectionEntry(SettingsSection.Appearance, Icons.Outlined.Palette, "Themes · ${settings.fontSize}sp · ${settings.fontType}"),
        SectionEntry(SettingsSection.Interface, Icons.Outlined.Settings, "Scrubber · System Bars · Status"),
        SectionEntry(SettingsSection.Interaction, Icons.Default.TouchApp, "Selection · Haptics · Translation"),
        SectionEntry(SettingsSection.Library, Icons.Default.MenuBook, "Book covers & management"),
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
                    .clickable { onNavigate(entry.section) }
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
private fun AppearanceTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    onOpenCreateThemeEditor: () -> Unit,
    onOpenEditThemeEditor: (CustomTheme) -> Unit,
    onDeleteTheme: (CustomTheme) -> Unit,
) {
    val context = LocalContext.current
    var themeToExport by remember { mutableStateOf<CustomTheme?>(null) }

    // --- REFACTORED LOGIC HELPERS ---
    fun normalizeThemeName(name: String): String = name.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

    suspend fun importSingleThemeJson(
        json: JSONObject,
        seenPalettes: MutableSet<ThemePalette>,
        seenNames: MutableSet<String>,
    ): String? {
        // [PHASE 4: VALIDATION STRICTNESS] - Require at least 3 color keys to prevent empty imports
        val colorKeys = listOf("primary", "secondary", "background", "surface", "surfaceVariant", "outline", "readerBackground", "readerForeground", "readerText")
        val presentKeys = colorKeys.count { json.has(it) }
        if (presentKeys < 3) return null

        // [PHASE 2: SCHEMA HARDENING] - Handle readerForeground with readerText as fallback
        val readerBg = parseThemeColorOrNull(json.optString("readerBackground")) ?: 0xFFFFFFFF
        val readerFg = parseThemeColorOrNull(json.optString("readerForeground")) 
            ?: parseThemeColorOrNull(json.optString("readerText")) 
            ?: 0xFF000000

        val bg = parseThemeColorOrNull(json.optString("background")) ?: 0xFFFFFFFF
        val pri = parseThemeColorOrNull(json.optString("primary")) ?: 0xFF6200EE
        
        // [PHASE 2: INTELLIGENT INHERITANCE] - Fallback to background/primary if specific UI colors are missing
        val palette = ThemePalette(
            primary = pri,
            secondary = parseThemeColorOrNull(json.optString("secondary")) ?: pri,
            background = bg,
            surface = parseThemeColorOrNull(json.optString("surface")) ?: bg,
            surfaceVariant = parseThemeColorOrNull(json.optString("surfaceVariant")) ?: bg,
            outline = parseThemeColorOrNull(json.optString("outline")) ?: 0xFF757575,
            readerBackground = readerBg,
            readerForeground = readerFg,
            systemForeground = parseThemeColorOrNull(json.optString("systemForeground")) ?: 0xFF000000,
        )

        if (seenPalettes.contains(palette)) return null

        val baseName = json.optString("name", "Imported Theme").trim()
        var finalName = baseName
        if (seenNames.contains(normalizeThemeName(finalName))) {
            var counter = 1
            while (seenNames.contains(normalizeThemeName("$baseName ($counter)"))) counter++
            finalName = "$baseName ($counter)"
        }

        val themeId = "${CustomThemeIdPrefix}${UUID.randomUUID()}"
        val newTheme = CustomTheme(id = themeId, name = finalName, palette = palette, isAdvanced = true)
        
        // [PHASE 3: ATOMIC VERIFICATION] - Only return name if save actually succeeds
        return try {
            settingsManager.saveCustomTheme(newTheme, activate = false)
            seenPalettes.add(palette)
            seenNames.add(normalizeThemeName(finalName))
            finalName
        } catch (e: Exception) {
            null
        }
    }

    val bulkImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            // [PHASE 1: PERFORMANCE] - Move heavy IO and decompression to Dispatchers.IO
            withContext(Dispatchers.IO) {
                var importedCount = 0
                var skippedCount = 0
                val seenPalettes: MutableSet<ThemePalette> = settings.customThemes.map { it.palette }.toMutableSet()
                val seenNames: MutableSet<String> = (BuiltInThemeOptions.map { it.name } + settings.customThemes.map { it.name })
                    .map { normalizeThemeName(it) }.toMutableSet()
                
                for (uri in uris) {
                    try {
                        // [PHASE 1: OOM GUARD] - Skip standalone files > 1MB; ZIPs > 5MB
                        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0
                        
                        // Detect if it's a ZIP by extension (simple and common for picked files)
                        val isZip = context.contentResolver.getType(uri)?.contains("zip") == true || 
                                    uri.toString().lowercase().endsWith(".zip")

                        if (isZip) {
                            if (size > 5 * 1024 * 1024) { skippedCount++; continue }
                            
                            // [PHASE 2: ZIP EXTRACTION ENGINE]
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                ZipInputStream(input).use { zis ->
                                    var entry: ZipEntry? = zis.nextEntry
                                    while (entry != null) {
                                        if (!entry.isDirectory && entry.name.lowercase().endsWith(".json") && !entry.name.contains("__MACOSX")) {
                                            // Read entry content safely
                                            val content = zis.bufferedReader().readText()
                                            val json = try { JSONObject(content) } catch (_: Exception) { null }
                                            if (json != null) {
                                                val name = importSingleThemeJson(json, seenPalettes, seenNames)
                                                if (name != null) importedCount++ else skippedCount++
                                            } else {
                                                skippedCount++
                                            }
                                        }
                                        zis.closeEntry()
                                        entry = zis.nextEntry
                                    }
                                }
                            }
                        } else {
                            if (size > 1024 * 1024) { skippedCount++; continue }
                            
                            // SINGLE JSON PROCESSING
                            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            if (!content.isNullOrBlank()) {
                                val json = try { JSONObject(content) } catch (_: Exception) { null }
                                if (json != null) {
                                    val name = importSingleThemeJson(json, seenPalettes, seenNames)
                                    if (name != null) importedCount++ else skippedCount++
                                } else {
                                    skippedCount++
                                }
                            }
                        }
                    } catch (_: Exception) {
                        skippedCount++
                    }
                }
                
                // [PHASE 3: CONSOLIDATED REPORTING]
                withContext(Dispatchers.Main) {
                    val msg = when {
                        importedCount > 0 && skippedCount > 0 -> "Imported $importedCount themes ($skippedCount skipped/duplicate)"
                        importedCount > 0 -> "Imported $importedCount themes successfully"
                        skippedCount > 0 -> "No new themes found ($skippedCount skipped/invalid)"
                        else -> "No valid theme files found"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportUri ->
            val theme = themeToExport ?: return@let
            scope.launch {
                // [PHASE 1: PERFORMANCE] - Move file writing to Dispatchers.IO
                withContext(Dispatchers.IO) {
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
                            // [PHASE 2: SCHEMA ALIGNMENT] - Use readerForeground as the standardized key
                            put("readerForeground", formatThemeColor(theme.palette.readerForeground))
                            put("systemForeground", formatThemeColor(theme.palette.systemForeground))
                        }
                        context.contentResolver.openOutputStream(exportUri)?.use { it.write(json.toString(4).toByteArray()) }
                        
                        // [PHASE 3: FEEDBACK] - Show success confirmation
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Theme exported successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (_: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to export theme", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            themeToExport = null
                        }
                    }
                }
            }
        }
    }

    // --- DASHBOARD UI ---
    val allThemes = remember(settings.customThemes) {
        BuiltInThemeOptions.map { CustomTheme(it.id, it.name, it.palette) } + settings.customThemes
    }
    
    val initialPage = remember(allThemes) {
        allThemes.indexOfFirst { it.id == settings.theme }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { allThemes.size }

    // Fix for regression: Ensure the pager starts at the selected theme
    LaunchedEffect(settings.theme) {
        val targetPage = allThemes.indexOfFirst { it.id == settings.theme }
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage, allThemes) {
        val safeIndex = pagerState.currentPage.coerceIn(allThemes.indices)
        val themeId = allThemes[safeIndex].id
        if (themeId != settings.theme) {
            settingsManager.setActiveTheme(themeId)
        }
    }

    // --- LOCAL SETTINGS STATE (For Buttery Smooth Performance) ---
    // We use local state to drive the sliders and live preview at 60fps,
    // only committing to DataStore (disk I/O) once the drag is finished.
    var localFontSize by remember(settings.fontSize) { mutableIntStateOf(settings.fontSize) }
    var localLineHeight by remember(settings.lineHeight) { mutableFloatStateOf(settings.lineHeight) }
    var localPadding by remember(settings.horizontalPadding) { mutableIntStateOf(settings.horizontalPadding) }

    // THEME ANIMATION: Gradual color transition for the dashboard
    val currentTheme = allThemes[pagerState.currentPage.coerceIn(allThemes.indices)]
    val animBg by animateColorAsState(Color(currentTheme.palette.background), tween(600), label = "bg")
    val animSysFg by animateColorAsState(Color(currentTheme.palette.systemForeground), tween(600), label = "sys")
    val animPrimary by animateColorAsState(Color(currentTheme.palette.primary), tween(600), label = "pri")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(animBg)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. Landscape Gallery
        Spacer(Modifier.height(16.dp))
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 16.dp
        ) { page ->
            LandscapeSpecimenCard(
                theme = allThemes[page],
                fontFamily = getFontFamily(settings.fontType),
                fontSize = localFontSize,
                lineHeight = localLineHeight,
                horizontalPadding = localPadding
            )
        }

        // 2. The Counter & Hub
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${allThemes.size}",
                style = MaterialTheme.typography.labelMedium,
                color = animSysFg.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            
            Spacer(Modifier.height(12.dp))
            
            ThemeControlHub(
                currentTheme = currentTheme,
                animSysFg = animSysFg,
                animPrimary = animPrimary,
                onCreate = onOpenCreateThemeEditor,
                onData = { bulkImportLauncher.launch("*/*") },
                onModify = { onOpenEditThemeEditor(currentTheme) },
                onDelete = { onDeleteTheme(currentTheme) },
                onExport = { 
                    themeToExport = currentTheme
                    exportLauncher.launch("theme_${currentTheme.name}.json")
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = animSysFg.copy(alpha = 0.1f))

        // 3. Typography & Rhythm
        TypographySettingsPanel(
            settings = settings,
            currentFontSize = localFontSize,
            currentLineHeight = localLineHeight,
            currentPadding = localPadding,
            onFontSizeChange = { localFontSize = it.toInt() },
            onLineHeightChange = { localLineHeight = it },
            onPaddingChange = { localPadding = it.toInt() },
            onCommitFontSize = { scope.launch { settingsManager.updateGlobalSettings { it.copy(fontSize = localFontSize) } } },
            onCommitLineHeight = { scope.launch { settingsManager.updateGlobalSettings { it.copy(lineHeight = localLineHeight) } } },
            onCommitPadding = { scope.launch { settingsManager.updateGlobalSettings { it.copy(horizontalPadding = localPadding) } } },
            settingsManager = settingsManager,
            scope = scope,
            animSysFg = animSysFg,
            animPrimary = animPrimary
        )
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ThemeControlHub(
    currentTheme: CustomTheme,
    animSysFg: Color,
    animPrimary: Color,
    onCreate: () -> Unit,
    onData: () -> Unit,
    onModify: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val isCustom = currentTheme.id.startsWith(CustomThemeIdPrefix)
    var showDataMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // CREATE
        HubButton(Icons.Default.Add, "Create", animSysFg, animPrimary, onCreate)

        // DATA (Import/Export)
        Box {
            HubButton(Icons.Default.DataUsage, "Data", animSysFg, animPrimary) { showDataMenu = true }
            DropdownMenu(expanded = showDataMenu, onDismissRequest = { showDataMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Import Themes") },
                    onClick = { showDataMenu = false; onData() },
                    leadingIcon = { Icon(Icons.Default.FileDownload, null) }
                )
                if (isCustom) {
                    DropdownMenuItem(
                        text = { Text("Export This Theme") },
                        onClick = { showDataMenu = false; onExport() },
                        leadingIcon = { Icon(Icons.Default.FileUpload, null) }
                    )
                }
            }
        }

        // MODIFY (Edit/Delete)
        HubButton(Icons.Default.Edit, "Modify", animSysFg, animPrimary) { 
            onModify()
        }
    }
}

@Composable
private fun HubButton(icon: ImageVector, label: String, animSysFg: Color, animPrimary: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(animSysFg.copy(alpha = 0.05f))
        ) {
            Icon(icon, contentDescription = label, tint = animSysFg.copy(alpha = 0.8f))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = animSysFg.copy(alpha = 0.4f), fontSize = 10.sp)
    }
}

@Composable
private fun LandscapeSpecimenCard(
    theme: CustomTheme,
    fontFamily: FontFamily,
    fontSize: Int,
    lineHeight: Float,
    horizontalPadding: Int
) {
    val p = theme.palette
    
    // Scale settings for the preview card context
    val baseLineHeight = 4.dp * (fontSize.toFloat() / 18f)
    val spacingBetweenLines = baseLineHeight * (lineHeight - 1f) + 4.dp
    val internalPadding = 16.dp * (horizontalPadding.toFloat() / 16f)
    val constrainedPadding = if (internalPadding > 32.dp) 32.dp else internalPadding

    Card(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val pageOffset = 0f
                alpha = 1f
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        // Inner App Simulation (Reader Background)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(p.readerBackground))
                .border(1.dp, Color(p.outline).copy(alpha = 0.05f), RoundedCornerShape(16.dp))
        ) {
            // 1. System Bar Representation (Stable UI Zone)
            SystemBarMock(p)

            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Side: Reader Preview Zone (Affected by reader settings)
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .padding(start = constrainedPadding) // Dynamic Padding
                ) {
                    // Stable Title (Interface Element)
                    Text(
                        text = theme.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(p.readerForeground),
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee()
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Reactive Reader Content
                    Column(verticalArrangement = Arrangement.spacedBy(spacingBetweenLines)) {
                        SkeletonLine(
                            color = Color(p.readerForeground), 
                            widthPercent = 0.9f,
                            height = baseLineHeight
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SkeletonLine(
                                color = Color(p.readerForeground).copy(alpha = 0.5f), 
                                widthPercent = 0.3f,
                                height = baseLineHeight
                            )
                            SkeletonLine(
                                color = Color(p.primary), 
                                widthPercent = 0.4f,
                                height = baseLineHeight
                            )
                        }
                        SkeletonLine(
                            color = Color(p.readerForeground).copy(alpha = 0.5f), 
                            widthPercent = 0.8f,
                            height = baseLineHeight
                        )
                    }
                }

                // Right Side: UI Surface Mock (Stable UI Zone)
                Box(modifier = Modifier.weight(1f).align(Alignment.Bottom)) {
                    SurfaceMock(p)
                }
            }
        }
    }
}

@Composable
private fun SystemBarMock(p: ThemePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("12:00", fontSize = 8.sp, color = Color(p.systemForeground).copy(alpha = 0.6f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(8.dp, 4.dp).background(Color(p.systemForeground).copy(alpha = 0.2f)))
            Box(modifier = Modifier.size(8.dp, 4.dp).background(Color(p.systemForeground).copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun SurfaceMock(p: ThemePalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(p.surface))
            .border(1.dp, Color(p.outline).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(p.surfaceVariant))
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp, 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(0.5.dp, Color(p.secondary), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp, 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(p.primary))
            )
        }
    }
}

@Composable
private fun SkeletonLine(color: Color, widthPercent: Float, height: androidx.compose.ui.unit.Dp = 4.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthPercent)
            .height(height)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
    )
}

@Composable
private fun TypographySettingsPanel(
    settings: GlobalSettings,
    currentFontSize: Int,
    currentLineHeight: Float,
    currentPadding: Int,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onPaddingChange: (Float) -> Unit,
    onCommitFontSize: () -> Unit,
    onCommitLineHeight: () -> Unit,
    onCommitPadding: () -> Unit,
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    animSysFg: Color,
    animPrimary: Color
) {
    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Typography & Rhythm", style = MaterialTheme.typography.titleMedium, color = animSysFg)

        // Font Family
        Column {
            val fonts = listOf("default", "serif", "sans-serif", "monospace", "karla")
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = fonts.indexOf(settings.fontType).coerceAtLeast(0)
            )

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fonts) { font ->
                    val isSelected = settings.fontType == font
                    FilterChip(
                        selected = isSelected,
                        onClick = { scope.launch { settingsManager.updateGlobalSettings { it.copy(fontType = font) } } },
                        label = { Text(font.replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = animSysFg.copy(alpha = 0.5f),
                            selectedContainerColor = animPrimary.copy(alpha = 0.15f),
                            selectedLabelColor = animPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = animSysFg.copy(alpha = 0.1f),
                            selectedBorderColor = animPrimary.copy(alpha = 0.5f),
                            borderWidth = 1.dp,
                            selectedBorderWidth = 1.dp
                        )
                    )
                }
            }
        }

        // sliders for padding and line height
        ControlSlider(
            label = "Font Size",
            value = currentFontSize.toFloat(),
            range = 12f..32f,
            animSysFg = animSysFg,
            animPrimary = animPrimary,
            onValueChange = onFontSizeChange,
            onValueChangeFinished = onCommitFontSize
        )

        ControlSlider(
            label = "Line Height",
            value = currentLineHeight,
            range = 1.0f..2.0f,
            animSysFg = animSysFg,
            animPrimary = animPrimary,
            onValueChange = onLineHeightChange,
            onValueChangeFinished = onCommitLineHeight
        )

        ControlSlider(
            label = "Padding",
            value = currentPadding.toFloat(),
            range = 0f..48f,
            animSysFg = animSysFg,
            animPrimary = animPrimary,
            onValueChange = onPaddingChange,
            onValueChangeFinished = onCommitPadding
        )
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    animSysFg: Color,
    animPrimary: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = animSysFg.copy(alpha = 0.7f))
            Text(if (range.endInclusive > 5f) "${value.toInt()}" else String.format("%.2f", value), style = MaterialTheme.typography.labelMedium, color = animSysFg.copy(alpha = 0.5f))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = animPrimary,
                activeTrackColor = animPrimary.copy(alpha = 0.5f),
                inactiveTrackColor = animSysFg.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
private fun InterfaceTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Reader Interface", style = MaterialTheme.typography.titleLarge)

        SettingsToggleRow(
            title = "Show Reader Scrubber",
            subtitle = "Vertical progress handle on the right side.",
            checked = settings.showScrubber,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showScrubber = checked) } }
            }
        )

        SettingsToggleRow(
            title = "Show System Bars",
            subtitle = "Keep status and navigation bars visible while reading.",
            checked = settings.showSystemBar,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showSystemBar = checked) } }
            }
        )

        SettingsToggleRow(
            title = "Show Scroll-to-Top Button",
            subtitle = "Quickly jump to the start of the chapter.",
            checked = settings.showScrollToTop,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(showScrollToTop = checked) } }
            }
        )

        HorizontalDivider()

        Text("Status Overlay", style = MaterialTheme.typography.titleMedium)
        ReaderStatusSettingsRow(
            settings = settings,
            onUpdateSettings = { transform ->
                scope.launch { settingsManager.updateGlobalSettings(transform) }
            },
            isSystemBarVisible = settings.showSystemBar
        )
    }
}

@Composable
private fun InteractionTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Interaction", style = MaterialTheme.typography.titleLarge)

        SettingsToggleRow(
            title = "Selectable Text",
            subtitle = "Allow long-pressing to select text for copying or sharing.",
            checked = settings.selectableText,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(selectableText = checked) } }
            }
        )

        SettingsToggleRow(
            title = "Haptic Feedback",
            subtitle = "Enable vibration for gestures and clicks.",
            checked = settings.hapticFeedback,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(hapticFeedback = checked) } }
            }
        )

        HorizontalDivider()

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Translate To", style = MaterialTheme.typography.titleMedium)
            Text("Target language for text translations", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(languages) { (code, name) ->
                    FilterChip(
                        selected = settings.targetTranslationLanguage == code,
                        onClick = { scope.launch { settingsManager.updateGlobalSettings { it.copy(targetTranslationLanguage = code) } } },
                        label = { Text(name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Library", style = MaterialTheme.typography.titleLarge)

        SettingsToggleRow(
            title = "Allow Blank Covers",
            subtitle = "Removing a cover won't fall back to the original file cover.",
            checked = settings.allowBlankCovers,
            onCheckedChange = { checked ->
                scope.launch { settingsManager.updateGlobalSettings { it.copy(allowBlankCovers = checked) } }
            }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ThemePreviewCard(
    palette: ThemePalette,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
) {
    val containerColor = Color(palette.background)
    val readerColor = Color(palette.readerBackground)
    val textColor = Color(palette.readerForeground)
    val primaryColor = Color(palette.primary)
    val secondaryColor = Color(palette.secondary)
    val surfaceColor = Color(palette.surface)
    val outlineColor = Color(palette.outline)

    val errorColor = MaterialTheme.colorScheme.error

    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(130.dp)
            .height(180.dp)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 3.dp else 1.dp,
            color = if (isSelected) primaryColor else outlineColor.copy(alpha = 0.3f)
        ),
        shadowElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header simulated
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(surfaceColor)
            )

            // Content Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(readerColor)
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index == 3) 0.6f else 1f)
                                .height(2.dp)
                                .background(textColor.copy(alpha = 0.4f))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).align(Alignment.CenterHorizontally),
                        tint = primaryColor.copy(alpha = 0.6f)
                    )
                }
            }

            // Footer with Palette & Name
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PaletteDot(primaryColor, outlineColor)
                    PaletteDot(secondaryColor, outlineColor)
                    PaletteDot(outlineColor, outlineColor)
                    Spacer(Modifier.weight(1f))
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp), tint = Color(palette.systemForeground).copy(alpha = 0.8f))
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sysFg = Color(palette.systemForeground)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(),
                        color = sysFg
                    )
                    if (onDelete != null || onExport != null) {
                        ThemeCardMenu(onExport, onDelete, sysFg)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteDot(color: Color, outlineColor: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
            .border(0.5.dp, outlineColor.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
private fun ThemeCardMenu(
    onExport: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    tint: Color
) {
    var showMenu by remember { mutableStateOf(false) }
    val errorColor = MaterialTheme.colorScheme.error

    Box {
        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", modifier = Modifier.size(14.dp), tint = tint.copy(alpha = 0.8f))
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (onExport != null) {
                DropdownMenuItem(
                    text = { Text("Export") },
                    onClick = { onExport(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(18.dp)) }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete", color = errorColor) },
                    onClick = { onDelete(); showMenu = false },
                    leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = errorColor) }
                )
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
    onDelete: () -> Unit = {},
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
    val nameConflict = !isThemeNameUnique(draft.name, session.themeId, existingThemes)
    val isValid = parsedTheme != null && draft.name.trim().isNotEmpty() && !nameConflict
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
                    isError = draft.name.trim().isEmpty() || nameConflict,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!session.isNew && session.themeId.startsWith(CustomThemeIdPrefix)) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
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

private fun getFontFamily(fontType: String): FontFamily {
    return when (fontType.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "karla" -> KarlaFont
        else -> FontFamily.Default
    }
}
