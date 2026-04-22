package com.epubreader.feature.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.core.model.isThemeNameUnique
import com.epubreader.core.model.parseThemeColorOrNull
import com.epubreader.core.ui.KarlaFont

@Composable
internal fun CustomThemeEditorDialog(
    session: ThemeEditorSession,
    activeThemeId: String,
    existingThemes: List<CustomTheme>,
    onDismiss: () -> Unit,
    onSave: (CustomTheme, Boolean) -> Unit,
    onDelete: () -> Unit = {},
) {
    var draft by remember(session) { mutableStateOf(session.draft) }

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

    val parsedTheme = remember(draft, session.themeId) { draft.toCustomTheme(session.themeId) }
    val nameConflict = !isThemeNameUnique(draft.name, session.themeId, existingThemes)
    val isValid = parsedTheme != null && draft.name.trim().isNotEmpty() && !nameConflict
    val shouldActivate = session.isNew || activeThemeId == session.themeId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session.isNew) "Create Custom Theme" else "Edit Custom Theme") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
                    )
                }

                ThemeColorField("Primary", draft.primary, { draft = draft.copy(primary = it) }, "custom_theme_primary")
                ThemeColorField("Background", draft.background, { draft = draft.copy(background = it) }, "custom_theme_background")

                if (draft.isAdvanced) {
                    ThemeColorField("Secondary", draft.secondary, { draft = draft.copy(secondary = it) }, "custom_theme_secondary")
                    ThemeColorField("Surface", draft.surface, { draft = draft.copy(surface = it) }, "custom_theme_surface")
                    ThemeColorField("Surface Variant", draft.surfaceVariant, { draft = draft.copy(surfaceVariant = it) }, "custom_theme_surface_variant")
                    ThemeColorField("Outline", draft.outline, { draft = draft.copy(outline = it) }, "custom_theme_outline")
                    ThemeColorField("Reader Background", draft.readerBackground, { draft = draft.copy(readerBackground = it) }, "custom_theme_reader_background")
                    ThemeColorField("Reader Text", draft.readerForeground, { draft = draft.copy(readerForeground = it) }, "custom_theme_reader_foreground")
                    ThemeColorField("System Text", draft.systemForeground, { draft = draft.copy(systemForeground = it) }, "custom_theme_system_foreground")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { parsedTheme?.let { onSave(it, shouldActivate) } }) {
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
        onValueChange(formatThemeColor(HsvColor(pickerHue, pickerSaturation, pickerValue).toColorLong()))
    }

    val previewColor = Color(HsvColor(pickerHue, pickerSaturation, pickerValue).toColorLong())

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
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("$label Color", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = formatThemeColor(HsvColor(pickerHue, pickerSaturation, pickerValue).toColorLong()),
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
            ),
        ).toLong() and 0xFFFFFFFFL
    }
}

private fun Long.toHsvColor(): HsvColor {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(this.toInt(), hsv)
    return HsvColor(hue = hsv[0], saturation = hsv[1], value = hsv[2])
}

internal data class ThemeEditorSession(
    val themeId: String,
    val isNew: Boolean,
    val draft: ThemeEditorDraft,
)

internal data class ThemeEditorDraft(
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
        if (trimmedName.isEmpty()) return null

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
            return fromPalette(theme.name, theme.palette, theme.isAdvanced)
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

internal fun getFontFamily(fontType: String): FontFamily {
    return when (fontType.lowercase()) {
        "serif" -> FontFamily.Serif
        "sans-serif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "karla" -> KarlaFont
        else -> FontFamily.Default
    }
}
