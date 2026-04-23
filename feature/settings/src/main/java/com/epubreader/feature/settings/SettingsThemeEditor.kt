package com.epubreader.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.ThemePalette
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.core.model.isThemeNameUnique
import com.epubreader.core.model.parseThemeColorOrNull
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.core.ui.KarlaFont

@Composable
internal fun CustomThemeEditorDialog(
    session: ThemeEditorSession,
    activeThemeId: String,
    existingThemes: List<CustomTheme>,
    primaryColor: Color,
    onSurfaceColor: Color,
    onDismiss: () -> Unit,
    onSave: (CustomTheme, Boolean) -> Unit,
    onDelete: () -> Unit = {},
) {
    val draftState = remember(session) { mutableStateOf(session.draft) }
    val draft = draftState.value

    LaunchedEffect(draft.primary, draft.background, draft.isAdvanced) {
        if (!draft.isAdvanced) {
            val p = parseThemeColorOrNull(draft.primary)
            val b = parseThemeColorOrNull(draft.background)
            if (p != null && b != null) {
                val generated = generatePaletteFromBase(p, b)
                draftState.value = draft.copy(
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
    val nameConflict = remember(draft.name, session.themeId, existingThemes) { 
        !isThemeNameUnique(draft.name, session.themeId, existingThemes) 
    }
    val isValid = parsedTheme != null && draft.name.trim().isNotEmpty() && !nameConflict
    val shouldActivate = session.isNew || activeThemeId == session.themeId

    // Live specimen state
    val previewTheme = remember(draft) { 
        val palette = draft.toCustomTheme(session.themeId)?.palette 
            ?: themePaletteSeed(activeThemeId, existingThemes)
            
        CustomTheme(
            id = session.themeId,
            name = draft.name.ifBlank { "" },
            palette = palette,
            isAdvanced = draft.isAdvanced
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f)
                    .clickable(enabled = false) {}
                    .graphicsLayer {
                        shadowElevation = 32.dp.toPx()
                        shape = RoundedCornerShape(32.dp)
                        clip = true
                    },
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    StudioHeader(
                        isNew = session.isNew,
                        isValid = isValid,
                        onDismiss = onDismiss,
                        onSave = { parsedTheme?.let { onSave(it, shouldActivate) } },
                        onDelete = onDelete,
                        showDelete = !session.isNew && session.themeId.startsWith(CustomThemeIdPrefix)
                    )

                    // Derive canonical geometry for a BALANCED preview (match Carousel)
                    val settings = session.settings
                    val previewGeometry = remember(settings.fontSize, settings.lineHeight, settings.horizontalPadding) {
                        val scale = 1.0f // Standard Carousel Scale
                        val baseLineHeight = (4.dp * (settings.fontSize.toFloat() / 18f)) * scale
                        val spacingBetweenLines = (baseLineHeight * (settings.lineHeight - 1f) + 4.dp) * scale
                        val internalPadding = (16.dp * (settings.horizontalPadding.toFloat() / 16f)) * scale
                        val constrainedPadding = if (internalPadding > 32.dp * scale) 32.dp * scale else internalPadding
                        SpecimenGeometry(
                            lineHeight = baseLineHeight,
                            spacing = spacingBetweenLines,
                            padding = constrainedPadding,
                            fontSize = settings.fontSize.sp,
                            scale = scale,
                        )
                    }

                    // 1. Sticky Studio Area (Balanced Preview + Identity)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // High-Fidelity Specimen
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .graphicsLayer {
                                    clip = true
                                    shape = RoundedCornerShape(16.dp)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.fillMaxWidth(0.75f)) {
                                ThemePreviewCard(
                                    theme = previewTheme,
                                    staggerIndex = 0,
                                    gallerySessionKey = 0L,
                                    fontFamily = FontFamily.Default,
                                    geometry = previewGeometry,
                                    containerColor = Color(previewTheme.palette.surface),
                                    outlineColor = Color(previewTheme.palette.outline),
                                    primaryColor = Color(previewTheme.palette.primary),
                                    isActive = true,
                                    isSelectionMode = false,
                                    isSelected = false,
                                    isGalleryOpen = true,
                                    onClick = {},
                                    onLongClick = {}
                                )
                            }
                        }

                        // Theme Name (Moved down from side-by-side)
                        StudioTextField(
                            value = draft.name,
                            onValueChange = { draftState.value = draftState.value.copy(name = it) },
                            label = "Theme Name",
                            isError = draft.name.trim().isEmpty() || nameConflict,
                            errorText = when {
                                draft.name.trim().isEmpty() -> "Name required"
                                nameConflict -> "Taken"
                                else -> null
                            }
                        )
                        
                        Divider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                    }

                    // 2. Scrollable Detail Area (Control Grid)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        var activePickerKey by remember { mutableStateOf<String?>(null) }
                        
                        // A. Core Row (Primary + BG)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                StudioColorCell("Primary", draft.primary) { activePickerKey = "Primary" }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                StudioColorCell("Background", draft.background) { activePickerKey = "Background" }
                            }
                        }

                        // B. Advanced Toggle (Full Width)
                        StudioToggleCell(
                            label = "Advanced Studio",
                            checked = draft.isAdvanced,
                            onCheckedChange = { draftState.value = draftState.value.copy(isAdvanced = it) }
                        )
                        
                        // C. Advanced Colors (Chunked Grid)
                        val allColors = remember {
                            listOf(
                                "Primary" to { draftState.value.primary } to { c: String -> draftState.value = draftState.value.copy(primary = c) },
                                "Background" to { draftState.value.background } to { c: String -> draftState.value = draftState.value.copy(background = c) },
                                "Secondary" to { draftState.value.secondary } to { c: String -> draftState.value = draftState.value.copy(secondary = c) },
                                "Surface" to { draftState.value.surface } to { c: String -> draftState.value = draftState.value.copy(surface = c) },
                                "Surface Variant" to { draftState.value.surfaceVariant } to { c: String -> draftState.value = draftState.value.copy(surfaceVariant = c) },
                                "Outline" to { draftState.value.outline } to { c: String -> draftState.value = draftState.value.copy(outline = c) },
                                "Reader Background" to { draftState.value.readerBackground } to { c: String -> draftState.value = draftState.value.copy(readerBackground = c) },
                                "Reader Text" to { draftState.value.readerForeground } to { c: String -> draftState.value = draftState.value.copy(readerForeground = c) },
                                "System Text" to { draftState.value.systemForeground } to { c: String -> draftState.value = draftState.value.copy(systemForeground = c) }
                            )
                        }

                        if (draft.isAdvanced) {
                            val secondarySet = remember(allColors) { allColors.drop(2) }
                            val rows = remember(secondarySet) { secondarySet.chunked(2) }
                            
                            rows.forEach { rowPairs ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowPairs.forEach { item ->
                                        val (pair, _) = item
                                        val (label, getter) = pair
                                        Box(modifier = Modifier.weight(1f)) {
                                            StudioColorCell(label, getter()) { activePickerKey = label }
                                        }
                                    }
                                    if (rowPairs.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // Pickers
                        allColors.forEach { (pair, setter) ->
                            val (label, value) = pair
                            if (activePickerKey == label) {
                                ColorPickerOverlay(
                                    label = label,
                                    initialValue = value(),
                                    primaryColor = primaryColor,
                                    onSurfaceColor = onSurfaceColor,
                                    onDismiss = { activePickerKey = null },
                                    onValueChange = setter
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerOverlay(
    label: String,
    initialValue: String,
    primaryColor: Color,
    onSurfaceColor: Color,
    onDismiss: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val parsedColor = remember(initialValue) { parseThemeColorOrNull(initialValue) }
    val initialHsv = remember(parsedColor) { (parsedColor ?: DefaultPickerColor).toHsvColor() }
    var pickerHue by remember { mutableFloatStateOf(initialHsv.hue) }
    var pickerSaturation by remember { mutableFloatStateOf(initialHsv.saturation) }
    var pickerValue by remember { mutableFloatStateOf(initialHsv.value) }

    // Throttled update to parent to maintain high-performance live preview
    LaunchedEffect(pickerHue, pickerSaturation, pickerValue) {
        val newColor = HsvColor(pickerHue, pickerSaturation, pickerValue).toColorLong()
        onValueChange(formatThemeColor(newColor))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("$label Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Local preview box (recomposes at 60fps)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(HsvColor(pickerHue, pickerSaturation, pickerValue).toColorLong()))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                )
                
                Text("Hue", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = pickerHue, 
                    onValueChange = { pickerHue = it }, 
                    valueRange = 0f..360f
                )
                
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = pickerSaturation, 
                    onValueChange = { pickerSaturation = it }, 
                    valueRange = 0f..1f
                )
                
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = pickerValue, 
                    onValueChange = { pickerValue = it }, 
                    valueRange = 0f..1f
                )
            }
        }
    )
}

private const val DefaultPickerColor = 0xFFFFFFFFL

private data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toHsvColor(): HsvColor = this // Identity for easier logic in picker

    fun toColorLong(): Long {
        val hsv = floatArrayOf(hue, saturation, value)
        return android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL
    }
}

private fun Long.toHsvColor(): HsvColor {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toInt(), hsv)
    return HsvColor(hsv[0], hsv[1], hsv[2])
}

internal data class ThemeEditorSession(
    val themeId: String,
    val isNew: Boolean,
    val draft: ThemeEditorDraft,
    val settings: com.epubreader.core.model.GlobalSettings
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
        // We allow empty name in this mapper to support Live Preview while typing

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
