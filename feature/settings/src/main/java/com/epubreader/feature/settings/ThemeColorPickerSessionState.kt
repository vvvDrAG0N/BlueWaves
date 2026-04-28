package com.epubreader.feature.settings

internal data class ThemeColorPickerSessionState(
    val initialHex: String,
    val previewHex: String,
    val initialTextFields: ThemeColorPickerTextFields,
    val currentTextFields: ThemeColorPickerTextFields,
    val isGuided: Boolean,
    val isGuidedSafeZoneReady: Boolean,
) {
    val hasPersistableChange: Boolean
        get() = !previewHex.equals(initialHex, ignoreCase = true)

    val hasTransientTextDraft: Boolean
        get() = !currentTextFields.matchesResolvedPreview(previewHex)

    val shouldPromptOnExit: Boolean
        get() = hasPersistableChange

    val canCommit: Boolean
        get() = !hasTransientTextDraft && (!isGuided || isGuidedSafeZoneReady)
}
