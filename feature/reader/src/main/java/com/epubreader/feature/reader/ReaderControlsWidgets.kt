package com.epubreader.feature.reader.internal.ui
import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.basicMarquee
import com.epubreader.core.ui.KarlaFont
import com.epubreader.core.model.ThemePalette
import com.epubreader.feature.reader.ReaderTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderThemeMiniSpecimen(
    id: String,
    name: String,
    palette: ThemePalette,
    selected: Boolean,
    themeColors: ReaderTheme,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .testTag("reader_theme_chip_$id")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = "Theme $name"
                this.selected = selected
            }
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 76.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(12.dp))
                .background(Color(palette.readerBackground))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) themeColors.primary else Color(palette.outline).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val p = palette
                
                // 1. System Bar Mock (Top)
                val sysAlpha = 0.4f
                drawRect(
                    color = Color(p.systemForeground).copy(alpha = sysAlpha),
                    topLeft = Offset(8.dp.toPx(), 6.dp.toPx()),
                    size = Size(8.dp.toPx(), 2.dp.toPx())
                )
                drawRect(
                    color = Color(p.systemForeground).copy(alpha = sysAlpha),
                    topLeft = Offset(w - 16.dp.toPx(), 6.dp.toPx()),
                    size = Size(8.dp.toPx(), 2.dp.toPx())
                )

                // 2. Reader Content Simulation (Center)
                val contentY = 16.dp.toPx()
                val foreground = Color(p.readerForeground)
                
                // Page Header/Title bar (Name or similar)
                drawRoundRect(
                    color = foreground.copy(alpha = 0.25f),
                    topLeft = Offset(8.dp.toPx(), contentY),
                    size = Size(w * 0.45f, 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                
                // Main Text Lines
                val line1Y = contentY + 10.dp.toPx()
                drawRoundRect(
                    color = foreground.copy(alpha = 0.1f),
                    topLeft = Offset(8.dp.toPx(), line1Y),
                    size = Size(w - 16.dp.toPx(), 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                
                // Highlighted Line (reflecting global preview primary highlight)
                val line2Y = line1Y + 7.dp.toPx()
                drawRoundRect(
                    color = foreground.copy(alpha = 0.1f),
                    topLeft = Offset(8.dp.toPx(), line2Y),
                    size = Size(w * 0.3f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = Color(p.primary).copy(alpha = 0.8f),
                    topLeft = Offset(8.dp.toPx() + w * 0.35f, line2Y),
                    size = Size(w * 0.25f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )
                drawRoundRect(
                    color = foreground.copy(alpha = 0.1f),
                    topLeft = Offset(8.dp.toPx() + w * 0.65f, line2Y),
                    size = Size(w * 0.15f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(1.5.dp.toPx())
                )

                // 3. Surface UI Simulation (Bottom)
                val surfaceY = h - 26.dp.toPx()
                drawRoundRect(
                    color = Color(p.surface),
                    topLeft = Offset(6.dp.toPx(), surfaceY),
                    size = Size(w - 12.dp.toPx(), 20.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                
                // Mini Action Buttons in the simulated surface
                val btnY = surfaceY + 12.dp.toPx()
                // Secondary Button (Stroke)
                drawRect(
                    color = Color(p.secondary).copy(alpha = 0.4f),
                    topLeft = Offset(w - 24.dp.toPx(), btnY),
                    size = Size(6.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
                // Primary Button (Fill)
                drawRoundRect(
                    color = Color(p.primary),
                    topLeft = Offset(w - 14.dp.toPx(), btnY),
                    size = Size(6.dp.toPx(), 4.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) themeColors.primary else themeColors.foreground.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(
                    iterations = Int.MAX_VALUE
                )
                .padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun KeypadButton(
    text: String,
    modifier: Modifier = Modifier,
    themeColors: ReaderTheme,
    onClick: () -> Unit,
) {
    val isAction = text == "Clear" || text == "Confirm"
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        color = if (isAction) themeColors.foreground.copy(alpha = 0.15f) else themeColors.foreground.copy(alpha = 0.05f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = if (isAction) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge,
                color = themeColors.foreground,
                fontWeight = if (isAction) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
internal fun TextSelectionActionBar(
    themeColors: ReaderTheme,
    onCopy: () -> Unit,
    onDefine: () -> Unit,
    onTranslate: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .testTag("text_selection_action_bar")
            .shadow(8.dp, RoundedCornerShape(28.dp)),
        color = themeColors.background.copy(alpha = 0.97f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextSelectionActionButton(Icons.Default.ContentCopy, "Copy", themeColors, onCopy)
            TextSelectionActionButton(Icons.AutoMirrored.Filled.MenuBook, "Define", themeColors, onDefine)
            TextSelectionActionButton(Icons.Default.GTranslate, "Translate", themeColors, onTranslate)
        }
    }
}

@Composable
private fun TextSelectionActionButton(
    icon: ImageVector,
    label: String,
    themeColors: ReaderTheme,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = themeColors.foreground,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = themeColors.variantForeground,
            maxLines = 1,
        )
    }
}

@Composable
fun OverscrollIndicator(
    text: String,
    modifier: Modifier,
    themeColors: ReaderTheme,
) {
    Surface(
        modifier = modifier,
        color = themeColors.foreground.copy(alpha = 0.92f),
        shape = CircleShape,
    ) {
        Text(
            text = text,
            color = themeColors.background,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

internal fun readerFontFamily(fontType: String): FontFamily {
    return when (fontType) {
        "serif" -> FontFamily.Serif
        "sans-serif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "karla" -> KarlaFont
        else -> FontFamily.Default
    }
}

@Composable
internal fun ReaderGeneralToggleRow(
    label: String,
    checked: Boolean,
    themeColors: ReaderTheme,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label, 
            style = MaterialTheme.typography.bodyMedium, 
            modifier = Modifier.weight(1f),
            color = themeColors.foreground
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = themeColors.primary,
                uncheckedThumbColor = themeColors.foreground.copy(alpha = 0.4f),
                uncheckedTrackColor = themeColors.foreground.copy(alpha = 0.12f),
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            )
        )
    }
}
