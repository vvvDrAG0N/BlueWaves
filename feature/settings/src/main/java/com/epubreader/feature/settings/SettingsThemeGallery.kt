package com.epubreader.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import kotlinx.coroutines.delay

@Composable
internal fun ThemeGalleryOverlay(
    allThemes: List<CustomTheme>,
    activeThemeId: String,
    chromeThemeId: String,
    scrimColor: Color,
    containerColor: Color,
    onSurfaceColor: Color,
    outlineColor: Color,
    primaryColor: Color,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    fontFamily: FontFamily,
    geometry: SpecimenGeometry,
    gallerySessionKey: Long,
    galleryGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    isGalleryOpen: Boolean,
    transitionAlpha: Float,
    transitionScale: Float,
    onThemeSelect: (CustomTheme) -> Unit,
    onToggleSelection: (String) -> Unit,
    onEnterSelectionMode: (String?) -> Unit,
    onBulkDelete: () -> Unit,
    onBulkExport: () -> Unit,
    onCloseSelectionMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val dismissBackdropModifier = if (isGalleryOpen) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            if (isSelectionMode) onCloseSelectionMode() else onDismiss()
        }
    } else {
        Modifier
    }

    BackHandler(enabled = isGalleryOpen) {
        if (isSelectionMode) onCloseSelectionMode() else onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Static Backdrop (Only fades)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = transitionAlpha }
                .background(scrimColor.copy(alpha = 0.45f))
                .then(dismissBackdropModifier),
        )

        // 2. Scaling Panel (Fades and scales)
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .testTag("theme_gallery_panel_$chromeThemeId")
                .graphicsLayer {
                    alpha = transitionAlpha
                    scaleX = transitionScale
                    scaleY = transitionScale
                    shadowElevation = if (isGalleryOpen) with(density) { 32.dp.toPx() } else 0f
                    shape = RoundedCornerShape(32.dp)
                    clip = true
                },
            color = containerColor,
            contentColor = onSurfaceColor,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Antigravity Floating Header with Optimized Drag Support
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .drawBehind {
                            // Bottom separator line
                            val strokeWidth = 1.dp.toPx()
                            drawLine(
                                color = outlineColor.copy(alpha = 0.2f),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = strokeWidth
                            )
                        }
                ) {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "headerContent"
                    ) { selectionActive ->
                        if (selectionActive) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = onCloseSelectionMode,
                                        enabled = isGalleryOpen,
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Exit selection")
                                    }
                                    Text(
                                        text = "${selectedIds.size} Selected",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(start = 4.dp),
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = onBulkExport,
                                        enabled = isGalleryOpen && selectedIds.isNotEmpty(),
                                    ) {
                                        Icon(Icons.Default.Inventory2, contentDescription = "Export pack")
                                    }
                                    IconButton(
                                        onClick = onBulkDelete,
                                        enabled = isGalleryOpen && selectedIds.isNotEmpty(),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete selected",
                                            tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else onSurfaceColor.copy(alpha = 0.45f),
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Theme Gallery",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onEnterSelectionMode(null) },
                                        enabled = isGalleryOpen
                                    ) {
                                        Icon(Icons.Default.Checklist, contentDescription = "Selection mode")
                                    }
                                    
                                    IconButton(
                                        onClick = onDismiss,
                                        enabled = isGalleryOpen
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Done")
                                    }
                                }
                            }
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = galleryGridState,
                    userScrollEnabled = isGalleryOpen,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("theme_gallery_grid"),
                ) {
                    itemsIndexed(allThemes, key = { _, theme -> theme.id }) { index, theme ->
                        val isSelected = selectedIds.contains(theme.id)
                        ThemePreviewCard(
                            theme = theme,
                            staggerIndex = index,
                            gallerySessionKey = gallerySessionKey,
                            fontFamily = fontFamily,
                            geometry = geometry,
                            containerColor = containerColor,
                            outlineColor = outlineColor,
                            primaryColor = primaryColor,
                            isActive = theme.id == activeThemeId,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            isGalleryOpen = isGalleryOpen,
                            onClick = {
                                if (isSelectionMode) {
                                    if (theme.id.startsWith(CustomThemeIdPrefix)) onToggleSelection(theme.id)
                                } else {
                                    onThemeSelect(theme)
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode && theme.id.startsWith(CustomThemeIdPrefix)) {
                                    onEnterSelectionMode(theme.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ThemePreviewCard(
    theme: CustomTheme,
    staggerIndex: Int,
    gallerySessionKey: Long,
    fontFamily: FontFamily,
    geometry: SpecimenGeometry,
    containerColor: Color,
    outlineColor: Color,
    primaryColor: Color,
    isActive: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isGalleryOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var isRevealed by rememberSaveable(theme.id, gallerySessionKey) { mutableStateOf(false) }

    LaunchedEffect(theme.id, gallerySessionKey) {
        if (!isRevealed) {
            val timeSinceOpen = System.currentTimeMillis() - gallerySessionKey
            val delayTime = if (timeSinceOpen < 500) (staggerIndex * 40L).coerceAtMost(300L) else 0L
            delay(delayTime)
            isRevealed = true
        }
    }

    val entryProgress by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "entryProgress",
    )

    val isCustom = theme.id.startsWith(CustomThemeIdPrefix)

    val contentAlpha by animateFloatAsState(
        targetValue = if (isSelectionMode && !isCustom) 0.38f else 1f,
        label = "contentAlpha",
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isSelectionMode && isCustom) {
            if (isSelected) 1.0f else 0.92f
        } else if (isActive && !isSelectionMode) {
            1.02f
        } else {
            1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cardScale",
    )

    val density = LocalDensity.current
    val elevation by animateFloatAsState(
        targetValue = with(density) {
            when {
                isSelected -> 12.dp.toPx()
                isActive && !isSelectionMode -> 8.dp.toPx()
                else -> 2.dp.toPx()
            }
        },
        label = "elevation",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> primaryColor.copy(alpha = 0.72f)
            isActive && !isSelectionMode -> outlineColor.copy(alpha = 0.55f)
            else -> Color.Transparent
        },
        animationSpec = tween(400),
        label = "borderColor",
    )
    val interactionModifier = if (isGalleryOpen) {
        Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier
    }

    Surface(
        modifier = Modifier
            .graphicsLayer {
                alpha = entryProgress * contentAlpha
                scaleX = cardScale * (0.95f + 0.05f * entryProgress)
                scaleY = cardScale * (0.95f + 0.05f * entryProgress)
                translationY = with(density) { (1f - entryProgress) * 64.dp.toPx() }
                rotationX = if (isGalleryOpen) (1f - entryProgress) * 10f else 0f
                shadowElevation = if (isGalleryOpen) elevation else 0f
                shape = RoundedCornerShape(16.dp)
                clip = true
            }
            .testTag("theme_gallery_preview_${theme.id}")
            .semantics {
                contentDescription = "Theme ${theme.name}"
                selected = isActive
            }
            .then(interactionModifier),
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(theme.palette.background)),
                border = BorderStroke(1.dp, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    ) {
                        ThemeSpecimenContent(
                            theme = theme,
                            fontFamily = fontFamily,
                            geometry = geometry,
                            isMini = true,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(theme.palette.primary)),
                        )
                    }
                }
            }

            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(containerColor.copy(alpha = 0.8f))
                        .border(1.dp, outlineColor.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BulkDeleteConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Themes?") },
        text = { Text("Are you sure you want to delete $count custom themes? This action cannot be undone.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
