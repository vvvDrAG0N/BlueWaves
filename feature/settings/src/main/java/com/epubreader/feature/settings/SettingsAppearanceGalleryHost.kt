package com.epubreader.feature.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.zIndex
import com.epubreader.core.model.CustomTheme

@Composable
internal fun AppearanceGalleryHost(
    hasGalleryBeenOpened: Boolean,
    isGalleryOpen: Boolean,
    allThemes: List<CustomTheme>,
    galleryThemeId: String,
    galleryTheme: CustomTheme?,
    isSelectionMode: Boolean,
    selectedThemeIds: Set<String>,
    fontFamily: FontFamily,
    geometry: SpecimenGeometry,
    gallerySessionKey: Long,
    galleryGridState: LazyGridState,
    onThemeSelect: (CustomTheme) -> Unit,
    onToggleSelection: (String) -> Unit,
    onEnterSelectionMode: (String?) -> Unit,
    onBulkDelete: () -> Unit,
    onBulkExport: () -> Unit,
    onCloseSelectionMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!hasGalleryBeenOpened) {
        return
    }

    val galleryAlpha by animateFloatAsState(
        targetValue = if (isGalleryOpen) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isGalleryOpen) Spring.StiffnessMediumLow else Spring.StiffnessMedium,
            visibilityThreshold = 0.001f,
        ),
        label = "galleryAlpha",
    )
    val galleryScale by animateFloatAsState(
        targetValue = if (isGalleryOpen) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = if (isGalleryOpen) 0.75f else Spring.DampingRatioNoBouncy,
            stiffness = if (isGalleryOpen) Spring.StiffnessLow else Spring.StiffnessMedium,
            visibilityThreshold = 0.001f,
        ),
        label = "galleryScale",
    )
    val galleryShouldStayOnTop = isGalleryOpen || galleryAlpha > 0.01f
    val containerColor = remember(galleryTheme) { Color(galleryTheme?.palette?.surface ?: 0xFF121212) }
    val onSurfaceColor = remember(galleryTheme) { Color(galleryTheme?.palette?.systemForeground ?: 0xFFFFFFFF) }
    val outlineColor = remember(galleryTheme) { Color(galleryTheme?.palette?.outline ?: 0xFF808080) }
    val primaryColor = remember(galleryTheme) { Color(galleryTheme?.palette?.primary ?: 0xFFFFFFFF) }
    val scrimColor = remember(galleryTheme) { Color(galleryTheme?.palette?.overlayScrim ?: 0xFF000000) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (galleryShouldStayOnTop) 1f else -1f),
        contentAlignment = Alignment.Center,
    ) {
        if (galleryShouldStayOnTop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { },
            )
        }

        ThemeGalleryOverlay(
            allThemes = allThemes,
            activeThemeId = galleryThemeId,
            chromeThemeId = galleryThemeId,
            scrimColor = scrimColor,
            containerColor = containerColor,
            onSurfaceColor = onSurfaceColor,
            outlineColor = outlineColor,
            primaryColor = primaryColor,
            isSelectionMode = isSelectionMode,
            selectedIds = selectedThemeIds,
            fontFamily = fontFamily,
            geometry = geometry,
            gallerySessionKey = gallerySessionKey,
            galleryGridState = galleryGridState,
            isGalleryOpen = isGalleryOpen,
            transitionAlpha = galleryAlpha,
            transitionScale = galleryScale,
            onThemeSelect = onThemeSelect,
            onToggleSelection = onToggleSelection,
            onEnterSelectionMode = onEnterSelectionMode,
            onBulkDelete = onBulkDelete,
            onBulkExport = onBulkExport,
            onCloseSelectionMode = onCloseSelectionMode,
            onDismiss = onDismiss,
        )
    }
}
