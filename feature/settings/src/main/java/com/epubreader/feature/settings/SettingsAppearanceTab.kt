package com.epubreader.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun AppearanceTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    onOpenCreateThemeEditor: () -> Unit,
    onOpenEditThemeEditor: (CustomTheme) -> Unit,
    onDeleteTheme: (CustomTheme) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var themeToExport by remember { mutableStateOf<CustomTheme?>(null) }
    var isGalleryOpen by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedThemeIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val bulkImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val message = importThemesFromUris(
                context = context,
                uris = uris,
                existingThemes = settings.customThemes,
                settingsManager = settingsManager,
            )
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportUri ->
            val theme = themeToExport ?: return@let
            scope.launch {
                val exported = exportThemeToUri(context, exportUri, theme)
                Toast.makeText(
                    context,
                    if (exported) "Theme exported successfully" else "Failed to export theme",
                    Toast.LENGTH_SHORT,
                ).show()
                themeToExport = null
            }
        }
    }

    val bulkExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { exportUri ->
            val themesToPack = settings.customThemes.filter { it.id in selectedThemeIds }
            if (themesToPack.isEmpty()) return@let
            scope.launch {
                val exported = exportThemesPackToUri(context, exportUri, themesToPack)
                Toast.makeText(
                    context,
                    if (exported) "Exported ${themesToPack.size} themes" else "Export failed",
                    Toast.LENGTH_SHORT,
                ).show()
                if (exported) {
                    isSelectionMode = false
                    selectedThemeIds = emptySet()
                }
            }
        }
    }

    val allThemes = remember(settings.customThemes) {
        BuiltInThemeOptions.map { CustomTheme(it.id, it.name, it.palette) } + settings.customThemes
    }
    val initialPage = remember {
        allThemes.indexOfFirst { it.id == settings.theme }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { allThemes.size }

    LaunchedEffect(settings.theme) {
        val targetPage = allThemes.indexOfFirst { it.id == settings.theme }
        if (targetPage >= 0 && pagerState.settledPage != targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.settledPage, allThemes) {
        val safeIndex = pagerState.settledPage.coerceIn(allThemes.indices)
        val themeId = allThemes[safeIndex].id
        if (themeId != settings.theme) {
            settingsManager.setActiveTheme(themeId)
        }
    }

    var localFontSize by remember(settings.fontSize) { mutableIntStateOf(settings.fontSize) }
    var localLineHeight by remember(settings.lineHeight) { mutableFloatStateOf(settings.lineHeight) }
    var localPadding by remember(settings.horizontalPadding) { mutableIntStateOf(settings.horizontalPadding) }
    val readerFontFamily = remember(settings.fontType) { getFontFamily(settings.fontType) }

    val carouselGeometry = remember(localFontSize, localLineHeight, localPadding) {
        val scale = 1f
        val baseLineHeight = (4.dp * (localFontSize.toFloat() / 18f)) * scale
        val spacingBetweenLines = (baseLineHeight * (localLineHeight - 1f) + 4.dp) * scale
        val internalPadding = (16.dp * (localPadding.toFloat() / 16f)) * scale
        val constrainedPadding = if (internalPadding > 32.dp * scale) 32.dp * scale else internalPadding
        SpecimenGeometry(
            lineHeight = baseLineHeight,
            spacing = spacingBetweenLines,
            padding = constrainedPadding,
            fontSize = localFontSize.sp,
            scale = scale,
        )
    }
    val galleryGeometry = remember(localFontSize, localLineHeight, localPadding) {
        val scale = 0.65f
        val baseLineHeight = (4.dp * (localFontSize.toFloat() / 18f)) * scale
        val spacingBetweenLines = (baseLineHeight * (localLineHeight - 1f) + 4.dp) * scale
        val internalPadding = (16.dp * (localPadding.toFloat() / 16f)) * scale
        val constrainedPadding = if (internalPadding > 32.dp * scale) 32.dp * scale else internalPadding
        SpecimenGeometry(
            lineHeight = baseLineHeight,
            spacing = spacingBetweenLines,
            padding = constrainedPadding,
            fontSize = localFontSize.sp,
            scale = scale,
        )
    }

    val dashboardBgModifier = Modifier.drawBehind {
        val total = allThemes.size
        if (total == 0) return@drawBehind
        val rawPage = pagerState.currentPage
        val floor = rawPage.toInt().coerceIn(0, total - 1)
        val ceil = (floor + 1).coerceIn(0, total - 1)
        val fraction = (rawPage - floor).toFloat()
        drawRect(
            lerp(
                Color(allThemes[floor].palette.background),
                Color(allThemes[ceil].palette.background),
                fraction,
            ),
        )
    }
    val getSysFg = remember(allThemes, pagerState) {
        {
            val total = allThemes.size
            if (total == 0) {
                Color.Transparent
            } else {
                val rawPage = pagerState.currentPage
                val floor = rawPage.toInt().coerceIn(0, total - 1)
                val ceil = (floor + 1).coerceIn(0, total - 1)
                val fraction = (rawPage - floor).toFloat()
                lerp(
                    Color(allThemes[floor].palette.systemForeground),
                    Color(allThemes[ceil].palette.systemForeground),
                    fraction,
                )
            }
        }
    }
    val getPrimary = remember(allThemes, pagerState) {
        {
            val total = allThemes.size
            if (total == 0) {
                Color.Transparent
            } else {
                val rawPage = pagerState.currentPage
                val floor = rawPage.toInt().coerceIn(0, total - 1)
                val ceil = (floor + 1).coerceIn(0, total - 1)
                val fraction = (rawPage - floor).toFloat()
                lerp(
                    Color(allThemes[floor].palette.primary),
                    Color(allThemes[ceil].palette.primary),
                    fraction,
                )
            }
        }
    }

    LaunchedEffect(pagerState.settledPage, allThemes) {
        delay(600)
        val safeIndex = pagerState.settledPage.coerceIn(allThemes.indices)
        val themeId = allThemes[safeIndex].id
        if (themeId != settings.theme) {
            settingsManager.setActiveTheme(themeId)
        }
    }

    SideEffect {
        val window = (context as? android.app.Activity)?.window ?: return@SideEffect
        if (allThemes.isNotEmpty()) {
            val rawPage = pagerState.currentPage
            val floor = rawPage.toInt().coerceIn(0, allThemes.lastIndex)
            val ceil = (floor + 1).coerceIn(0, allThemes.lastIndex)
            val dominantTheme = if ((rawPage - floor) < 0.5f) allThemes[floor] else allThemes[ceil]
            val isDark = Color(dominantTheme.palette.background).luminance() < 0.5f
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val currentTheme = remember(pagerState.settledPage, allThemes) {
        if (allThemes.isEmpty()) null else allThemes[pagerState.settledPage.coerceIn(allThemes.indices)]
    }

    Box(modifier = Modifier.fillMaxSize().then(dashboardBgModifier)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = getSysFg(),
                    )
                }
                androidx.compose.material3.Text(
                    text = "Appearance",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp),
                    color = getSysFg(),
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentPadding = PaddingValues(horizontal = 48.dp),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
                key = { index -> if (index < allThemes.size) allThemes[index].id else index },
            ) { page ->
                val isFocused = pagerState.settledPage == page && !pagerState.isScrollInProgress
                Box(modifier = Modifier.graphicsLayer { clip = false }) {
                    LandscapeSpecimenCard(
                        theme = allThemes[page],
                        fontFamily = readerFontFamily,
                        geometry = carouselGeometry,
                        isMarqueeActive = { isFocused },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .graphicsLayer { clip = false },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.Text(
                    text = "${pagerState.settledPage + 1} / ${allThemes.size}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = getSysFg().copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(12.dp))
                ThemeControlHub(
                    currentTheme = currentTheme,
                    getSysFg = getSysFg,
                    getPrimary = getPrimary,
                    onCreate = onOpenCreateThemeEditor,
                    onData = { bulkImportLauncher.launch("*/*") },
                    onModify = { currentTheme?.let(onOpenEditThemeEditor) },
                    onDelete = { currentTheme?.let(onDeleteTheme) },
                    onExport = {
                        currentTheme?.let {
                            themeToExport = it
                            exportLauncher.launch("theme_${it.name}.json")
                        }
                    },
                    onGallery = { isGalleryOpen = true },
                )
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(1.dp)
                    .drawBehind { drawRect(getSysFg().copy(alpha = 0.1f)) },
            )

            Box(modifier = Modifier.graphicsLayer { clip = false }) {
                TypographySettingsPanel(
                    settings = settings,
                    currentFontSize = localFontSize,
                    currentLineHeight = localLineHeight,
                    currentPadding = localPadding,
                    onFontSizeChange = { localFontSize = it.toInt() },
                    onLineHeightChange = { localLineHeight = it },
                    onPaddingChange = { localPadding = it.toInt() },
                    onCommitFontSize = {
                        scope.launch { settingsManager.updateGlobalSettings { it.copy(fontSize = localFontSize) } }
                    },
                    onCommitLineHeight = {
                        scope.launch { settingsManager.updateGlobalSettings { it.copy(lineHeight = localLineHeight) } }
                    },
                    onCommitPadding = {
                        scope.launch { settingsManager.updateGlobalSettings { it.copy(horizontalPadding = localPadding) } }
                    },
                    settingsManager = settingsManager,
                    scope = scope,
                    getSysFg = getSysFg,
                    getPrimary = getPrimary,
                )
            }

            Spacer(Modifier.height(32.dp))
        }

        AnimatedVisibility(
            visible = isGalleryOpen,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            ThemeGalleryOverlay(
                allThemes = allThemes,
                activeThemeId = settings.theme,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedThemeIds,
                fontFamily = readerFontFamily,
                geometry = galleryGeometry,
                onThemeSelect = { theme ->
                    scope.launch { settingsManager.setActiveTheme(theme.id) }
                    isGalleryOpen = false
                },
                onToggleSelection = { id ->
                    selectedThemeIds = if (selectedThemeIds.contains(id)) selectedThemeIds - id else selectedThemeIds + id
                },
                onEnterSelectionMode = { id ->
                    isSelectionMode = true
                    selectedThemeIds = setOf(id)
                },
                onBulkDelete = { showBulkDeleteConfirm = true },
                onBulkExport = { bulkExportLauncher.launch("themes_pack_${System.currentTimeMillis()}.zip") },
                onCloseSelectionMode = {
                    isSelectionMode = false
                    selectedThemeIds = emptySet()
                },
                onDismiss = {
                    isGalleryOpen = false
                    isSelectionMode = false
                    selectedThemeIds = emptySet()
                },
            )
        }

        if (showBulkDeleteConfirm) {
            val customTargets = selectedThemeIds.filter { it.startsWith(CustomThemeIdPrefix) }
            BulkDeleteConfirmationDialog(
                count = customTargets.size,
                onConfirm = {
                    scope.launch { settingsManager.deleteCustomThemes(customTargets.toSet()) }
                    showBulkDeleteConfirm = false
                    isSelectionMode = false
                    selectedThemeIds = emptySet()
                },
                onDismiss = { showBulkDeleteConfirm = false },
            )
        }
    }
}
