package com.epubreader.feature.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun AppearanceTab(
    settings: GlobalSettings,
    scope: CoroutineScope,
    settingsManager: SettingsManager,
    onOpenCreateThemeEditor: (String) -> Unit,
    onOpenEditThemeEditor: (CustomTheme, String) -> Unit,
    onDeleteTheme: (CustomTheme) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var themeToExport by remember { mutableStateOf<CustomTheme?>(null) }
    var isGalleryOpen by remember { mutableStateOf(false) }
    var hasGalleryBeenOpened by remember { mutableStateOf(false) }
    LaunchedEffect(isGalleryOpen) {
        if (isGalleryOpen) {
            hasGalleryBeenOpened = true
        }
    }

    val gallerySessionKey = remember { System.currentTimeMillis() }
    val galleryGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

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
    var selectedThemeId by remember(settings.theme) { mutableStateOf(settings.theme) }

    var isProgrammaticScroll by remember { mutableStateOf(false) }
    var isClosingAppearance by remember { mutableStateOf(false) }

    LaunchedEffect(settings.theme) {
        selectedThemeId = settings.theme
    }

    LaunchedEffect(
        pagerState.currentPage,
        pagerState.settledPage,
        pagerState.isScrollInProgress,
        isProgrammaticScroll,
        allThemes,
    ) {
        if (allThemes.isEmpty() || isProgrammaticScroll) return@LaunchedEffect
        val isUserDrivenThemeTransition = pagerState.isScrollInProgress || pagerState.currentPage != pagerState.settledPage
        if (isUserDrivenThemeTransition) {
            val safeIndex = pagerState.currentPage.coerceIn(allThemes.indices)
            selectedThemeId = allThemes[safeIndex].id
        }
    }

    LaunchedEffect(settings.theme, allThemes) {
        val targetPage = allThemes.indexOfFirst { it.id == settings.theme }
        if (targetPage >= 0 && pagerState.settledPage != targetPage) {
            isProgrammaticScroll = true
            try {
                // Wait for any existing scroll to finish, then animate to target
                androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                pagerState.animateScrollToPage(targetPage)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val pendingThemeId = if (allThemes.isEmpty()) {
        settings.theme
    } else if (isProgrammaticScroll) {
        selectedThemeId
    } else {
        allThemes[pagerState.currentPage.coerceIn(allThemes.indices)].id
    }

    fun closeAppearanceTab() {
        if (isClosingAppearance) return
        if (pendingThemeId == settings.theme) {
            onBack()
            return
        }
        isClosingAppearance = true
        scope.launch {
            settingsManager.setActiveTheme(pendingThemeId)
            settingsManager.globalSettings.first { it.theme == pendingThemeId }
            onBack()
        }
    }

    BackHandler(enabled = !isGalleryOpen && !isClosingAppearance) {
        closeAppearanceTab()
    }

    // Sync global theme when pager settles
    LaunchedEffect(pagerState.settledPage) {
        if (allThemes.isEmpty()) return@LaunchedEffect
        // Only sync if the pager has actually settled and we aren't mid-scroll
        if (!pagerState.isScrollInProgress) {
            val safeIndex = pagerState.settledPage.coerceIn(allThemes.indices)
            val themeId = allThemes[safeIndex].id
            selectedThemeId = themeId
            if (themeId != settings.theme) {
                settingsManager.setActiveTheme(themeId)
            }
        }
    }

    var localFontSize by remember(settings.fontSize) { mutableIntStateOf(settings.fontSize) }
    var localLineHeight by remember(settings.lineHeight) { mutableFloatStateOf(settings.lineHeight) }
    var localPadding by remember(settings.horizontalPadding) { mutableIntStateOf(settings.horizontalPadding) }
    val readerFontFamily = remember(settings.fontType) { getFontFamily(settings.fontType) }

    val carouselGeometry = remember(localFontSize, localLineHeight, localPadding) {
        buildSpecimenGeometry(
            fontSize = localFontSize,
            lineHeight = localLineHeight,
            horizontalPadding = localPadding,
            scale = 1f,
        )
    }
    val galleryGeometry = remember(localFontSize, localLineHeight, localPadding) {
        buildSpecimenGeometry(
            fontSize = localFontSize,
            lineHeight = localLineHeight,
            horizontalPadding = localPadding,
            scale = 0.65f,
        )
    }
    val settingsThemeIndex = remember(settings.theme, allThemes) {
        allThemes.indexOfFirst { it.id == settings.theme }.coerceIn(allThemes.indices)
    }

    // Performance-first visual index: Follows currentPage during drag, jumps to target during programmatic scroll
    val visualIndex by remember(pagerState, isProgrammaticScroll, settingsThemeIndex) {
        derivedStateOf {
            if (isProgrammaticScroll) {
                settingsThemeIndex
            } else {
                pagerState.currentPage
            }
        }
    }

    val dashboardBgModifier = Modifier.drawBehind {
        val safeIndex = visualIndex.coerceIn(allThemes.indices)
        drawRect(Color(allThemes[safeIndex].palette.background))
    }
    val getSysFg = remember(allThemes, visualIndex) {
        {
            val safeIndex = visualIndex.coerceIn(allThemes.indices)
            Color(allThemes[safeIndex].palette.systemForeground)
        }
    }
    val getPrimary = remember(allThemes, visualIndex) {
        {
            val safeIndex = visualIndex.coerceIn(allThemes.indices)
            Color(allThemes[safeIndex].palette.primary)
        }
    }
    val getBackground = remember(allThemes, visualIndex) {
        {
            val safeIndex = visualIndex.coerceIn(allThemes.indices)
            Color(allThemes[safeIndex].palette.background)
        }
    }

    // High-performance system bar management
    LaunchedEffect(visualIndex, allThemes) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        if (allThemes.isNotEmpty()) {
            val safeIndex = visualIndex.coerceIn(allThemes.indices)
            val theme = allThemes[safeIndex]
            val isDark = Color(theme.palette.background).luminance() < 0.5f
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val currentTheme = remember(visualIndex, allThemes) {
        if (allThemes.isEmpty()) null else allThemes[visualIndex.coerceIn(allThemes.indices)]
    }
    val galleryTheme = currentTheme ?: allThemes.firstOrNull()
    val galleryThemeId = galleryTheme?.id ?: pendingThemeId

    Box(modifier = Modifier.fillMaxSize().then(dashboardBgModifier)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(64.dp))
            Spacer(Modifier.height(16.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .testTag("appearance_theme_pager"),
                contentPadding = PaddingValues(horizontal = 48.dp),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 1,
                key = { index -> if (index < allThemes.size) allThemes[index].id else index },
            ) { page ->
                // Marquee remains tied to settled state for stability
                val isFocused = pagerState.settledPage == page && !pagerState.isScrollInProgress
                Box(modifier = Modifier.graphicsLayer { clip = false }) {
                    LandscapeSpecimenCard(
                        theme = allThemes[page],
                        fontFamily = readerFontFamily,
                        geometry = carouselGeometry,
                        isActive = page == visualIndex,
                        isMarqueeActive = { isFocused },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .graphicsLayer { clip = false },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.Text(
                    text = "${visualIndex + 1} / ${allThemes.size}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = getSysFg().copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(12.dp))
                ThemeControlHub(
                    currentTheme = currentTheme,
                    getSysFg = getSysFg,
                    getPrimary = getPrimary,
                    onCreate = { onOpenCreateThemeEditor(pendingThemeId) },
                    onData = { bulkImportLauncher.launch("*/*") },
                    onModify = { currentTheme?.let { onOpenEditThemeEditor(it, pendingThemeId) } },
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

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(getBackground())
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(
                onClick = ::closeAppearanceTab,
                enabled = !isClosingAppearance,
            ) {
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

        AppearanceGalleryHost(
            hasGalleryBeenOpened = hasGalleryBeenOpened,
            isGalleryOpen = isGalleryOpen,
            allThemes = allThemes,
            galleryThemeId = galleryThemeId,
            galleryTheme = galleryTheme,
            isSelectionMode = isSelectionMode,
            selectedThemeIds = selectedThemeIds,
            fontFamily = readerFontFamily,
            geometry = galleryGeometry,
            gallerySessionKey = gallerySessionKey,
            galleryGridState = galleryGridState,
            onThemeSelect = { theme ->
                selectedThemeId = theme.id
                scope.launch { settingsManager.setActiveTheme(theme.id) }
                isGalleryOpen = false
            },
            onToggleSelection = { id ->
                selectedThemeIds = if (selectedThemeIds.contains(id)) selectedThemeIds - id else selectedThemeIds + id
            },
            onEnterSelectionMode = { id ->
                isSelectionMode = true
                selectedThemeIds = if (id == null) emptySet() else setOf(id)
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
