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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    var renderGalleryOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(isGalleryOpen) {
        if (isGalleryOpen) {
            renderGalleryOverlay = true
        } else if (renderGalleryOverlay) {
            // Drop the heavy layered grid after the close animation settles.
            delay(220)
            renderGalleryOverlay = false
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

    var isProgrammaticScroll by remember { mutableStateOf(false) }

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

    // Sync global theme when pager settles
    LaunchedEffect(pagerState.settledPage) {
        if (allThemes.isEmpty()) return@LaunchedEffect
        // Only sync if the pager has actually settled and we aren't mid-scroll
        if (!pagerState.isScrollInProgress) {
            val safeIndex = pagerState.settledPage.coerceIn(allThemes.indices)
            val themeId = allThemes[safeIndex].id
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
                    .padding(horizontal = 24.dp, vertical = 16.dp)
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

        if (renderGalleryOverlay) {
            val galleryAlpha by animateFloatAsState(
                targetValue = if (isGalleryOpen) 1f else 0f,
                animationSpec = spring(
                    stiffness = if (isGalleryOpen) Spring.StiffnessLow else Spring.StiffnessMedium,
                    visibilityThreshold = 0.001f
                ),
                label = "galleryAlpha"
            )
            val galleryScale by animateFloatAsState(
                targetValue = if (isGalleryOpen) 1f else 0.92f,
                animationSpec = spring(
                    stiffness = if (isGalleryOpen) Spring.StiffnessLow else Spring.StiffnessMedium,
                    visibilityThreshold = 0.001f
                ),
                label = "galleryScale"
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Interaction Shield: Blocks dashboard touches while gallery is visible or animating
                if (galleryAlpha > 0.01f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { /* Block touches during animation */ }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = galleryAlpha
                            scaleX = galleryScale
                            scaleY = galleryScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ThemeGalleryOverlay(
                        allThemes = allThemes,
                        activeThemeId = settings.theme,
                        isSelectionMode = isSelectionMode,
                        selectedIds = selectedThemeIds,
                        fontFamily = readerFontFamily,
                        geometry = galleryGeometry,
                        gallerySessionKey = gallerySessionKey,
                        galleryGridState = galleryGridState,
                        isGalleryOpen = isGalleryOpen,
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
            }
        }
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
