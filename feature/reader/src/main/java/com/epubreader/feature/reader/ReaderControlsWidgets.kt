package com.epubreader.feature.reader

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.epubreader.core.ui.KarlaFont

@Composable
fun ReaderThemeButton(
    name: String,
    bg: Color,
    fg: Color,
    selected: Boolean,
    label: String = "A",
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = "Theme $name"
                this.selected = selected
            }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(bg)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = fg, fontWeight = FontWeight.Bold)
        }
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
            color = themeColors.foreground.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

@Composable
fun OverscrollIndicator(text: String, modifier: Modifier, color: Color) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape,
    ) {
        Text(
            text = text,
            color = Color.White,
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
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
