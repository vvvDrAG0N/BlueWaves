package com.epubreader.feature.settings

internal data class ThemeEditorColorEditResult(
    val updatedDraft: ThemeEditorDraft,
    val resolvedHex: String,
    val wasAdjusted: Boolean,
)

internal data class ThemeEditorPickerSession(
    val label: String,
    val initialValue: String,
    val testTagPrefix: String?,
    val isGuided: Boolean,
    val onColorChange: (String) -> ThemeEditorColorEditResult,
    val onColorPreview: ((String) -> ThemeColorPickerPreviewResult)? = null,
)

internal fun ThemeEditorDraft.applyColorEdit(
    fieldKey: String,
    rawHex: String,
    guided: Boolean,
): ThemeEditorColorEditResult {
    return resolveColorEdit(
        fieldKey = fieldKey,
        rawHex = rawHex,
        guided = guided,
    )
}

internal fun ThemeEditorDraft.previewColorEdit(
    fieldKey: String,
    rawHex: String,
    guided: Boolean,
): ThemeColorPickerPreviewResult {
    val result = resolveColorEdit(
        fieldKey = fieldKey,
        rawHex = rawHex,
        guided = guided,
    )
    return ThemeColorPickerPreviewResult(
        resolvedHex = result.resolvedHex,
        wasAdjusted = result.wasAdjusted,
    )
}

private fun ThemeEditorDraft.resolveColorEdit(
    fieldKey: String,
    rawHex: String,
    guided: Boolean,
): ThemeEditorColorEditResult {
    val updatedDraft = withColorField(fieldKey, rawHex)
    val resolvedDraft = if (guided) {
        updatedDraft.rebalanceGuidedFields() ?: updatedDraft
    } else {
        updatedDraft
    }
    val resolvedHex = resolvedDraft.colorFieldValue(fieldKey)
    return ThemeEditorColorEditResult(
        updatedDraft = resolvedDraft,
        resolvedHex = resolvedHex,
        wasAdjusted = !resolvedHex.equals(rawHex, ignoreCase = true),
    )
}

internal fun ThemeEditorDraft.colorFieldValue(fieldKey: String): String {
    return when (fieldKey) {
        "accent" -> accent
        "chrome_accent" -> chromeAccent
        "app_background" -> appBackground
        "app_surface" -> appSurface
        "app_surface_variant" -> appSurfaceVariant
        "app_outline" -> appOutline
        "app_foreground" -> appForeground
        "app_foreground_muted" -> appForegroundMuted
        "reader_background" -> readerBackground
        "reader_foreground" -> readerForeground
        "reader_foreground_muted" -> readerForegroundMuted
        "reader_accent" -> readerAccent
        "overlay_scrim" -> overlayScrim
        "startup_background" -> startupBackground
        "startup_foreground" -> startupForeground
        "favorite_accent" -> favoriteAccent
        "cover_overlay_scrim" -> coverOverlayScrim
        else -> error("Unknown theme editor color field: $fieldKey")
    }
}

private fun ThemeEditorDraft.withColorField(
    fieldKey: String,
    value: String,
): ThemeEditorDraft {
    return when (fieldKey) {
        "accent" -> copy(accent = value)
        "chrome_accent" -> copy(chromeAccent = value)
        "app_background" -> copy(appBackground = value)
        "app_surface" -> copy(appSurface = value)
        "app_surface_variant" -> copy(appSurfaceVariant = value)
        "app_outline" -> copy(appOutline = value)
        "app_foreground" -> copy(appForeground = value)
        "app_foreground_muted" -> copy(appForegroundMuted = value)
        "reader_background" -> copy(readerBackground = value)
        "reader_foreground" -> copy(readerForeground = value)
        "reader_foreground_muted" -> copy(readerForegroundMuted = value)
        "reader_accent" -> copy(readerAccent = value)
        "overlay_scrim" -> copy(overlayScrim = value)
        "startup_background" -> copy(startupBackground = value)
        "startup_foreground" -> copy(startupForeground = value)
        "favorite_accent" -> copy(favoriteAccent = value)
        "cover_overlay_scrim" -> copy(coverOverlayScrim = value)
        else -> error("Unknown theme editor color field: $fieldKey")
    }
}
