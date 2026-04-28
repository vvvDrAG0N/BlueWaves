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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.LightThemeId
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.core.model.generatePaletteFromGuidedInput
import com.epubreader.core.model.themePaletteSeed
import androidx.test.espresso.Espresso.pressBack

internal suspend fun SettingsThemeEditorGuidedPickerTest.resetSettings(
    theme: String = "light",
    customThemes: List<CustomTheme> = emptyList(),
) {
    settingsManager.updateGlobalSettings(
        GlobalSettings(
            fontSize = 18,
            fontType = "serif",
            theme = theme,
            customThemes = customThemes,
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

internal fun SettingsThemeEditorGuidedPickerTest.launchCurrentThemeEditor() {
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
    composeRule.onNodeWithContentDescription("Modify").performClick()
    waitUntilTagExists("custom_theme_name")
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
    val previewHex = readPreviewHex("${testTagPrefix}_picker_preview")
    tapHeaderSave(testTagPrefix)
    waitUntilPickerClosed(testTagPrefix)
    waitUntilTextContains(testTagPrefix, previewHex)
}

internal fun SettingsThemeEditorGuidedPickerTest.closeColorPicker() {
    composeRule.onNodeWithContentDescription("Save").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.tapHeaderSave(testTagPrefix: String) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_save").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.pressActivityBack(testTagPrefix: String? = null) {
    pressBack()
    composeRule.waitForIdle()
    if (testTagPrefix == null) {
        return
    }
    val exitDialogTag = "${testTagPrefix}_picker_exit_dialog"
    val pickerTag = "${testTagPrefix}_picker_hex"
    val exitDialogVisible = runCatching {
        composeRule.onNodeWithTag(exitDialogTag).fetchSemanticsNode()
        true
    }.getOrDefault(false)
    if (!exitDialogVisible) {
        val pickerStillOpen = runCatching {
            composeRule.onNodeWithTag(pickerTag).fetchSemanticsNode()
            true
        }.getOrDefault(false)
        if (pickerStillOpen) {
            pressBack()
            composeRule.waitForIdle()
        }
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.requestCloseColorPicker(testTagPrefix: String) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_close").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.tapExitDialogAction(
    testTagPrefix: String,
    action: String,
) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_exit_${action}").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.openColorPicker(swatchTag: String) {
    composeRule.onNodeWithTag(swatchTag).performScrollTo().performClick()
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

internal fun SettingsThemeEditorGuidedPickerTest.assertPreviewHex(
    tag: String,
    expectedHex: String,
) {
    val actualDescriptions = composeRule.onNodeWithTag(tag)
        .fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.ContentDescription) { emptyList<String>() }
    if (actualDescriptions.none { it.equals(expectedHex, ignoreCase = true) }) {
        throw AssertionError("Expected preview hex $expectedHex on '$tag' but found $actualDescriptions")
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.readPreviewHex(tag: String): String {
    return composeRule.onNodeWithTag(tag)
        .fetchSemanticsNode()
        .config
        .getOrElse(SemanticsProperties.ContentDescription) { emptyList<String>() }
        .firstOrNull()
        ?: throw AssertionError("Expected preview hex content description on '$tag'")
}

internal fun SettingsThemeEditorGuidedPickerTest.readNodeText(tag: String): String {
    val config = composeRule.onNodeWithTag(tag).fetchSemanticsNode().config
    return runCatching { config[SemanticsProperties.EditableText].text }.getOrNull()
        ?: runCatching { config[SemanticsProperties.Text].joinToString(separator = "") { text -> text.text } }.getOrNull()
        ?: throw AssertionError("Expected text semantics on '$tag'")
}

internal fun SettingsThemeEditorGuidedPickerTest.waitUntilTextContains(
    tag: String,
    text: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            val stableScrollTag = "${tag}_swatch"
            val scrolled = runCatching {
                composeRule.onNodeWithTag(stableScrollTag).performScrollTo()
                true
            }.getOrDefault(false)
            if (!scrolled) {
                composeRule.onNodeWithTag(tag).performScrollTo()
            }
            composeRule.onNodeWithTag(tag).assertTextContains(text)
            true
        }.getOrDefault(false)
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.waitUntilTagAbsent(
    tag: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            false
        }.getOrDefault(true)
    }
}

internal fun SettingsThemeEditorGuidedPickerTest.waitUntilPickerClosed(
    testTagPrefix: String,
    timeoutMillis: Long = 10_000,
) {
    waitUntilTagAbsent("${testTagPrefix}_picker_spectrum", timeoutMillis)
}

internal fun SettingsThemeEditorGuidedPickerTest.waitUntilTextVisible(
    text: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            composeRule.onNodeWithText(text, substring = true).assertExists()
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

internal fun SettingsThemeEditorGuidedPickerTest.replaceHexInput(
    testTagPrefix: String,
    nextHex: String,
) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_hex").performTextReplacement(nextHex)
}

internal fun SettingsThemeEditorGuidedPickerTest.replaceRgbInput(
    testTagPrefix: String,
    red: String,
    green: String,
    blue: String,
) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_red").performTextReplacement(red)
    composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_green").performTextReplacement(green)
    composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_blue").performTextReplacement(blue)
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

internal fun expectedGuidedSavedColor(
    fieldKey: String,
    attemptedPoint: ThemeColorPickerPoint,
    hue: Float,
    draft: ThemeEditorDraft,
): String {
    val rawHex = formatThemeColor(
        ThemeColorPickerHsv(
            hue = hue,
            saturation = attemptedPoint.saturation,
            value = attemptedPoint.value,
        ).toColorLong(),
    )
    return draft.applyColorEdit(
        fieldKey = fieldKey,
        rawHex = rawHex,
        guided = true,
    ).resolvedHex
}

internal fun guidedDraftAfterSavedEdits(
    baseDraft: ThemeEditorDraft,
    vararg edits: Pair<String, String>,
): ThemeEditorDraft {
    var currentDraft = baseDraft
    edits.forEach { (fieldKey, rawHex) ->
        currentDraft = currentDraft.applyColorEdit(
            fieldKey = fieldKey,
            rawHex = rawHex,
            guided = true,
        ).updatedDraft
    }
    return currentDraft
}

internal fun defaultExtendedGuidedDraft(): ThemeEditorDraft {
    return createThemeSeedDraft(
        mode = ThemeEditorMode.EXTENDED,
        readerLinked = true,
        legacyIsAdvanced = false,
    )
}

internal fun defaultBasicDraft(): ThemeEditorDraft {
    return createThemeSeedDraft(
        mode = ThemeEditorMode.BASIC,
        readerLinked = true,
        legacyIsAdvanced = false,
    )
}

private fun createThemeSeedDraft(
    mode: ThemeEditorMode,
    readerLinked: Boolean,
    legacyIsAdvanced: Boolean,
): ThemeEditorDraft {
    val seedPalette = themePaletteSeed(
        themeId = LightThemeId,
        customThemes = emptyList(),
    )
    return ThemeEditorDraft.fromPalette(
        name = "Guided Picker Test",
        palette = generatePaletteFromBase(
            primary = seedPalette.accent,
            background = seedPalette.appBackground,
        ),
        mode = mode,
        readerLinked = readerLinked,
        legacyIsAdvanced = legacyIsAdvanced,
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

internal fun basicDraft(
    palette: com.epubreader.core.model.ThemePalette = generatePaletteFromBase(
        primary = themePaletteSeed(LightThemeId, emptyList()).accent,
        background = themePaletteSeed(LightThemeId, emptyList()).appBackground,
    ),
): ThemeEditorDraft {
    return ThemeEditorDraft.fromPalette(
        name = "Guided Picker Test",
        palette = palette,
        mode = ThemeEditorMode.BASIC,
        readerLinked = true,
        legacyIsAdvanced = false,
    )
}
