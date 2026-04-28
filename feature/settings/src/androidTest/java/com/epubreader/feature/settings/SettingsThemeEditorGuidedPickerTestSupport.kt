package com.epubreader.feature.settings

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromGuidedInput

internal suspend fun SettingsThemeEditorGuidedPickerTest.resetSettings() {
    settingsManager.updateGlobalSettings(
        GlobalSettings(
            fontSize = 18,
            fontType = "serif",
            theme = "light",
            customThemes = emptyList(),
            lineHeight = 1.6f,
            horizontalPadding = 16,
            showScrubber = false,
            showSystemBar = false,
            allowBlankCovers = false,
        ),
    )
}

internal fun SettingsThemeEditorGuidedPickerTest.launchThemeEditor() {
    composeRule.runOnUiThread {
        composeRule.activity.setContent {
            MaterialTheme {
                SettingsScreen(
                    settingsManager = settingsManager,
                    onBack = {},
                )
            }
        }
    }
    waitUntilDisplayed("Settings")
    composeRule.onNodeWithTag("settings_section_appearance").performClick()
    waitUntilTagExists("create_custom_theme_button")
    composeRule.onNodeWithTag("create_custom_theme_button").performClick()
    waitUntilTagExists("custom_theme_name")
    composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
    composeRule.onNodeWithTag("custom_theme_name").performTextInput("Guided Picker Test")
}

internal fun SettingsThemeEditorGuidedPickerTest.selectThemeEditorMode(mode: String) {
    composeRule.onNodeWithTag("theme_editor_mode_${mode.lowercase()}").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.setPickerColor(
    swatchTag: String,
    testTagPrefix: String,
    hue: Float = 0f,
    saturation: Float = 1f,
    brightness: Float,
) {
    composeRule.onNodeWithTag(swatchTag).performScrollTo().performClick()
    setSliderProgress("${testTagPrefix}_picker_hue", hue)
    setSpectrumPoint(
        tag = "${testTagPrefix}_picker_spectrum",
        saturation = saturation,
        value = brightness,
    )
    closeColorPicker()
}

internal fun SettingsThemeEditorGuidedPickerTest.closeColorPicker() {
    composeRule.onNodeWithText("Done").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.dismissColorPickerByOutsideTap(backdropTag: String) {
    composeRule.onNodeWithTag(backdropTag).performTouchInput {
        click(Offset(24f, 24f))
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.tapDialogChrome(dialogTag: String) {
    composeRule.onNodeWithTag(dialogTag).performTouchInput {
        click(Offset(width * 0.9f, 24f))
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.waitUntilDisplayed(
    text: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            composeRule.onNodeWithText(text).assertIsDisplayed()
            true
        }.getOrDefault(false)
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.waitUntilTagExists(
    tag: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            true
        }.getOrDefault(false)
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.waitUntilTagDisplayed(
    tag: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            true
        }.getOrDefault(false)
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.assertTagDoesNotExist(tag: String) {
    val exists = runCatching {
        composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        true
    }.getOrDefault(false)
    if (exists) {
        throw AssertionError("Expected tag '$tag' to be absent")
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.assertPreviewState(tag: String, state: String) {
    composeRule.onNodeWithTag(tag)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
}

internal fun SettingsThemeEditorGuidedPickerTest.waitUntilTextContains(
    tag: String,
    text: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            composeRule.onNodeWithTag(tag).assertTextContains(text)
            true
        }.getOrDefault(false)
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.setSliderProgress(tag: String, value: Float) {
    composeRule.onNodeWithTag(tag)
        .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(value)
        }
}

internal fun SettingsThemeEditorGuidedPickerTest.setSpectrumPoint(
    tag: String,
    saturation: Float,
    value: Float,
): ThemeColorPickerPoint {
    val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    val x = touchCoordinate(bounds.width, saturation)
    val y = touchCoordinate(bounds.height, 1f - value)
    composeRule.onNodeWithTag(tag).performTouchInput {
        click(Offset(x, y))
    }
    return ThemeColorPickerPoint(
        saturation = if (bounds.width > 0f) x / bounds.width else saturation,
        value = if (bounds.height > 0f) 1f - (y / bounds.height) else value,
    )
}

internal fun touchCoordinate(size: Float, fraction: Float): Float {
    if (size <= 1f) {
        return 0f
    }
    return (size * fraction).coerceIn(0.5f, size - 0.5f)
}

internal fun expectedGuidedProjectedColor(
    fieldKey: String,
    attemptedPoint: ThemeColorPickerPoint,
    hue: Float,
    draft: ThemeEditorDraft,
): String {
    val safeZone = buildGuidedSafeZone(
        hue = hue,
        previewColor = { rawHex ->
            draft.previewColorEdit(
                fieldKey = fieldKey,
                rawHex = rawHex,
                guided = true,
            )
        },
    )
    val projectedPoint = safeZone.project(attemptedPoint)
    return formatThemeColor(
        ThemeColorPickerHsv(
            hue = hue,
            saturation = projectedPoint.saturation,
            value = projectedPoint.value,
        ).toColorLong(),
    )
}

internal fun extendedDraft(
    palette: com.epubreader.core.model.ThemePalette,
    readerLinked: Boolean = true,
): ThemeEditorDraft {
    return ThemeEditorDraft.fromPalette(
        name = "Guided Picker Test",
        palette = palette,
        mode = ThemeEditorMode.EXTENDED,
        readerLinked = readerLinked,
        legacyIsAdvanced = true,
    )
}
