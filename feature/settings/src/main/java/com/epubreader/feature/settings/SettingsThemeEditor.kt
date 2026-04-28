package com.epubreader.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.isThemeNameUnique
import com.epubreader.core.model.themePaletteSeed
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
    var activePicker by remember { mutableStateOf<ThemeEditorPickerSession?>(null) }
    val parsedTheme = remember(draft, session.themeId) { draft.toCustomTheme(session.themeId) }
    val nameConflict = remember(draft.name, session.themeId, existingThemes) {
        !isThemeNameUnique(draft.name, session.themeId, existingThemes)
    }
    val isValid = parsedTheme != null && draft.name.trim().isNotEmpty() && !nameConflict
    val shouldActivate = session.isNew || activeThemeId == session.themeId || session.chromeThemeId == session.themeId
    val chromePalette = remember(session.chromeThemeId, existingThemes) {
        themeEditorChromePalette(session.chromeThemeId, existingThemes)
    }
    val chromeColorScheme = remember(session.chromeThemeId, existingThemes) {
        themeEditorColorScheme(session.chromeThemeId, existingThemes)
    }
    val previewTheme = remember(draft, parsedTheme, session.themeId, activeThemeId, existingThemes) {
        parsedTheme ?: CustomTheme(
            id = session.themeId,
            name = draft.name,
            palette = themePaletteSeed(session.chromeThemeId, existingThemes),
            isAdvanced = draft.legacyIsAdvanced,
        )
    }
    val previewGeometry = remember(
        session.settings.fontSize,
        session.settings.lineHeight,
        session.settings.horizontalPadding,
    ) {
        buildSpecimenGeometry(
            fontSize = session.settings.fontSize,
            lineHeight = session.settings.lineHeight,
            horizontalPadding = session.settings.horizontalPadding,
            scale = 1f,
        )
    }

    fun updateGuidedDraft(transform: (ThemeEditorDraft) -> ThemeEditorDraft) {
        val updated = transform(draft)
        draft = updated.rebalanceGuidedFields() ?: updated
    }

    fun colorField(
        key: String,
        label: String,
        value: String,
        guided: Boolean,
        setter: (String) -> ThemeEditorDraft,
    ): ThemeEditorColorField {
                return ThemeEditorColorField(
                    label = label,
                    value = value,
                    testTagPrefix = themeEditorColorTestTag(key),
                    onClick = {
                        activePicker = ThemeEditorPickerSession(
                            label = label,
                            initialValue = value,
                            testTagPrefix = themeEditorColorTestTag(key),
                            isGuided = guided,
                            onColorPreview = { nextValue ->
                                draft.previewColorEdit(
                                    fieldKey = key,
                                    rawHex = nextValue,
                                    guided = guided,
                                )
                            },
                            onColorChange = { nextValue ->
                                val result = draft.applyColorEdit(
                                    fieldKey = key,
                                    rawHex = nextValue,
                                    guided = guided,
                                )
                                draft = result.updatedDraft
                                result
                            },
                        )
                    },
                )
            }

    val basicFields = listOf(
        colorField("accent", "Accent", draft.accent, guided = true) { draft.copy(accent = it) },
        colorField("app_background", "App Background", draft.appBackground, guided = true) {
            draft.copy(appBackground = it)
        },
    )
    val extendedAppFields = listOf(
        colorField("accent", "Accent", draft.accent, guided = true) { draft.copy(accent = it) },
        colorField("chrome_accent", "Chrome Accent", draft.chromeAccent, guided = true) {
            draft.copy(chromeAccent = it)
        },
        colorField("app_background", "App Background", draft.appBackground, guided = true) {
            draft.copy(appBackground = it)
        },
        colorField("app_surface", "Card / Surface", draft.appSurface, guided = true) {
            draft.copy(appSurface = it)
        },
        colorField("app_foreground", "App Text", draft.appForeground, guided = true) {
            draft.copy(appForeground = it)
        },
        colorField("app_foreground_muted", "Muted Text", draft.appForegroundMuted, guided = true) {
            draft.copy(appForegroundMuted = it)
        },
    )
    val extendedReaderFields = listOf(
        colorField("reader_background", "Reader Page", draft.readerBackground, guided = true) {
            draft.copy(readerBackground = it)
        },
        colorField("reader_foreground", "Reader Text", draft.readerForeground, guided = true) {
            draft.copy(readerForeground = it)
        },
    )
    val advancedAppFields = listOf(
        colorField("accent", "Accent", draft.accent, guided = false) { draft.copy(accent = it) },
        colorField("chrome_accent", "Chrome Accent", draft.chromeAccent, guided = false) {
            draft.copy(chromeAccent = it)
        },
        colorField("app_background", "App Background", draft.appBackground, guided = false) {
            draft.copy(appBackground = it)
        },
        colorField("app_surface", "Card / Surface", draft.appSurface, guided = false) {
            draft.copy(appSurface = it)
        },
        colorField("app_surface_variant", "Surface Depth", draft.appSurfaceVariant, guided = false) {
            draft.copy(appSurfaceVariant = it)
        },
        colorField("app_outline", "App Outline", draft.appOutline, guided = false) {
            draft.copy(appOutline = it)
        },
        colorField("app_foreground", "App Text", draft.appForeground, guided = false) {
            draft.copy(appForeground = it)
        },
        colorField("app_foreground_muted", "Muted Text", draft.appForegroundMuted, guided = false) {
            draft.copy(appForegroundMuted = it)
        },
    )
    val advancedReaderFields = listOf(
        colorField("reader_background", "Reader Page", draft.readerBackground, guided = false) {
            draft.copy(readerBackground = it)
        },
        colorField("reader_foreground", "Reader Text", draft.readerForeground, guided = false) {
            draft.copy(readerForeground = it)
        },
        colorField("reader_foreground_muted", "Reader Muted Text", draft.readerForegroundMuted, guided = false) {
            draft.copy(readerForegroundMuted = it)
        },
        colorField("reader_accent", "Reader Accent", draft.readerAccent, guided = false) {
            draft.copy(readerAccent = it)
        },
    )
    val overlayField = colorField("overlay_scrim", "Overlay Backdrop", draft.overlayScrim, guided = draft.mode != ThemeEditorMode.ADVANCED) {
        draft.copy(overlayScrim = it)
    }
    val advancedLibraryFields = listOf(
        colorField("startup_background", "Startup Background", draft.startupBackground, guided = false) {
            draft.copy(startupBackground = it)
        },
        colorField("startup_foreground", "Startup Foreground", draft.startupForeground, guided = false) {
            draft.copy(startupForeground = it)
        },
        colorField("favorite_accent", "Favorite Accent", draft.favoriteAccent, guided = false) {
            draft.copy(favoriteAccent = it)
        },
        colorField("cover_overlay_scrim", "Cover Overlay", draft.coverOverlayScrim, guided = false) {
            draft.copy(coverOverlayScrim = it)
        },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MaterialTheme(colorScheme = chromeColorScheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(chromePalette.overlayScrim).copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .testTag("theme_editor_sheet")
                        .semantics {
                            stateDescription = "chromeTheme=${session.chromeThemeId}"
                        }
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
                            showDelete = !session.isNew && session.themeId.startsWith(CustomThemeIdPrefix),
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .testTag("theme_editor_preview_static")
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .graphicsLayer {
                                        clip = true
                                        shape = RoundedCornerShape(16.dp)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                ThemeEditorPreviewCard(
                                    theme = previewTheme,
                                    fontFamily = getFontFamily(session.settings.fontType),
                                    geometry = previewGeometry,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            StudioTextField(
                                value = draft.name,
                                onValueChange = { draft = draft.copy(name = it) },
                                label = "Theme Name",
                                isError = draft.name.trim().isEmpty() || nameConflict,
                                errorText = when {
                                    draft.name.trim().isEmpty() -> "Name required"
                                    nameConflict -> "Taken"
                                    else -> null
                                },
                                testTag = "custom_theme_name",
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .testTag("theme_editor_scroll_content")
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            ThemeEditorControlsSection(
                                mode = draft.mode,
                                onModeChange = { draft = draft.copy(mode = it) },
                                onRebalance = { draft = draft.rebalanceGuidedFields() ?: draft },
                            )

                            when (draft.mode) {
                                ThemeEditorMode.BASIC -> {
                                    ThemeEditorSection(
                                        title = "Quick Theme",
                                        subtitle = "Choose an accent and a background. The rest is generated automatically.",
                                        fields = basicFields,
                                    )
                                }

                                ThemeEditorMode.EXTENDED -> {
                                    ThemeEditorSection(
                                        title = "App",
                                        subtitle = "Shape the main shell with the most visible roles.",
                                        fields = extendedAppFields,
                                    )
                                    StudioToggleCell(
                                        label = "Link Reader To App",
                                        checked = draft.readerLinked,
                                        onCheckedChange = { linked ->
                                            updateGuidedDraft { draft.copy(readerLinked = linked) }
                                        },
                                        testTag = "theme_editor_reader_link_toggle",
                                    )
                                    if (draft.readerLinked) {
                                        Text(
                                            text = "Reader colors currently follow the app theme. Turn linking off to tune the page separately.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        ThemeEditorSection(
                                            title = "Reader",
                                            subtitle = "Set the reading page and text directly.",
                                            fields = extendedReaderFields,
                                        )
                                    }
                                    ThemeEditorSection(
                                        title = "Overlays",
                                        subtitle = "Control full-screen backdrops without touching every low-level token.",
                                        fields = listOf(overlayField),
                                    )
                                }

                                ThemeEditorMode.ADVANCED -> {
                                    ThemeEditorSection(title = "App", fields = advancedAppFields)
                                    ThemeEditorSection(title = "Reader", fields = advancedReaderFields)
                                    ThemeEditorSection(title = "Overlays", fields = listOf(overlayField))
                                    ThemeEditorSection(
                                        title = "Library & Startup",
                                        subtitle = "Fine-tune launch, favorites, and cover overlays directly.",
                                        fields = advancedLibraryFields,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
            activePicker?.let { field ->
                ThemeColorPickerOverlay(
                    label = field.label,
                    initialValue = field.initialValue,
                    testTagPrefix = field.testTagPrefix,
                    isGuided = field.isGuided,
                    onDismiss = { activePicker = null },
                    onPreviewValueChange = field.onColorPreview,
                    onValueChange = field.onColorChange,
                )
            }
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
