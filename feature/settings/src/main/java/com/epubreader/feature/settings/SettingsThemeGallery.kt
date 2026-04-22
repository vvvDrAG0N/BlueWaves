package com.epubreader.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    fontFamily: FontFamily,
    geometry: SpecimenGeometry,
    onThemeSelect: (CustomTheme) -> Unit,
    onToggleSelection: (String) -> Unit,
    onEnterSelectionMode: (String) -> Unit,
    onBulkDelete: () -> Unit,
    onBulkExport: () -> Unit,
    onCloseSelectionMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler {
        if (isSelectionMode) onCloseSelectionMode() else onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (isSelectionMode) onCloseSelectionMode() else onDismiss()
                },
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (isSelectionMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onCloseSelectionMode) {
                                Icon(Icons.Default.Close, contentDescription = "Exit selection")
                            }
                            Text(
                                text = "${selectedIds.size} Selected",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        Row {
                            IconButton(onClick = onBulkExport, enabled = selectedIds.isNotEmpty()) {
                                Icon(Icons.Default.Inventory2, contentDescription = "Export pack")
                            }
                            IconButton(onClick = onBulkDelete, enabled = selectedIds.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete selected",
                                    tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Text(
                            "Theme Gallery",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                        TextButton(onClick = onDismiss, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Done")
                        }
                    }
                }

                val gallerySessionKey = remember { System.currentTimeMillis() }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(allThemes, key = { _, theme -> theme.id }) { index, theme ->
                        val isSelected = selectedIds.contains(theme.id)
                        ThemePreviewCard(
                            theme = theme,
                            staggerIndex = index,
                            gallerySessionKey = gallerySessionKey,
                            fontFamily = fontFamily,
                            geometry = geometry,
                            isActive = theme.id == activeThemeId,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
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
private fun ThemePreviewCard(
    theme: CustomTheme,
    staggerIndex: Int,
    gallerySessionKey: Long,
    fontFamily: FontFamily,
    geometry: SpecimenGeometry,
    isActive: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
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

    val bloomAlpha by animateFloatAsState(
        targetValue = if (isRevealed) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "bloomAlpha",
    )
    val auraColor = Color(theme.palette.primary).copy(alpha = 0.15f)
    val isCustom = theme.id.startsWith(CustomThemeIdPrefix)
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSelectionMode && !isCustom) 0.38f else 1f,
        label = "contentAlpha",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isSelectionMode && isCustom) {
            if (isSelected) 0.98f else 0.94f
        } else {
            1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "cardScale",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(theme.palette.primary)
            isActive && !isSelectionMode -> Color(theme.palette.primary).copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        animationSpec = tween(400),
        label = "borderColor",
    )

    Surface(
        modifier = Modifier
            .alpha(contentAlpha)
            .scale(cardScale)
            .clip(RoundedCornerShape(12.dp))
            .testTag("theme_gallery_preview_${theme.id}")
            .semantics {
                contentDescription = "Theme ${theme.name}"
                selected = isActive
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .drawBehind {
                drawCircle(
                    color = auraColor,
                    radius = size.maxDimension * 0.8f,
                    center = center,
                    alpha = 0.6f * if (isSelectionMode && !isSelected) 0.4f else 1f,
                )
            },
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(theme.palette.background)),
                border = BorderStroke(2.dp, borderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 2.dp),
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.6f)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .graphicsLayer { alpha = bloomAlpha },
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
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
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
