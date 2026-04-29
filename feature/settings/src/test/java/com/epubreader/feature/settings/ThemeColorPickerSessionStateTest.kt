package com.epubreader.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorPickerSessionStateTest {

    @Test
    fun incompleteHex_withoutPreviewChange_isTransientButNotPersistable() {
        val initialFields = themeColorPickerTextFields("#4F46E5")
        val currentFields = initialFields.withHexInput("12AB")

        val state = ThemeColorPickerSessionState(
            initialHex = "#4F46E5",
            previewHex = "#4F46E5",
            initialTextFields = initialFields,
            currentTextFields = currentFields,
            isGuided = false,
            isGuidedSafeZoneReady = true,
        )

        assertTrue(state.hasTransientTextDraft)
        assertFalse(state.hasPersistableChange)
        assertFalse(state.shouldPromptOnExit)
        assertFalse(state.canCommit)
    }

    @Test
    fun previewChange_withSyncedFields_isPersistableAndCommitEnabled() {
        val initialFields = themeColorPickerTextFields("#4F46E5")
        val currentFields = themeColorPickerTextFields("#12AB34")

        val state = ThemeColorPickerSessionState(
            initialHex = "#4F46E5",
            previewHex = "#12AB34",
            initialTextFields = initialFields,
            currentTextFields = currentFields,
            isGuided = false,
            isGuidedSafeZoneReady = true,
        )

        assertFalse(state.hasTransientTextDraft)
        assertTrue(state.hasPersistableChange)
        assertTrue(state.shouldPromptOnExit)
        assertTrue(state.canCommit)
    }

    @Test
    fun guidedLoading_blocksCommitEvenWhenPreviewWouldDiffer() {
        val initialFields = themeColorPickerTextFields("#4F46E5")
        val currentFields = themeColorPickerTextFields("#12AB34")

        val state = ThemeColorPickerSessionState(
            initialHex = "#4F46E5",
            previewHex = "#12AB34",
            initialTextFields = initialFields,
            currentTextFields = currentFields,
            isGuided = true,
            isGuidedSafeZoneReady = false,
        )

        assertTrue(state.hasPersistableChange)
        assertTrue(state.shouldPromptOnExit)
        assertFalse(state.canCommit)
    }
}
