package com.epubreader.feature.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.semantics.SemanticsProperties
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.generatePaletteFromBase

internal fun SettingsScreenPersistenceTestBase.waitUntilDisplayed(text: String, timeoutMillis: Long = 10_000) {
    composeRule.waitUntil(timeoutMillis) {
        try {
            composeRule.onNodeWithText(text).assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }
    }
}

internal fun SettingsScreenPersistenceTestBase.scrollToSystemBarControls() {
    composeRule.onNodeWithText("Show System Bars").performScrollTo()
}

internal fun SettingsScreenPersistenceTestBase.openAppearanceSection() {
    if (!tagExists("create_custom_theme_button")) {
        if (tagExists("settings_section_appearance")) {
            composeRule.onNodeWithTag("settings_section_appearance").performClick()
        }
    }
    waitUntilTagExists("create_custom_theme_button")
}

internal fun SettingsScreenPersistenceTestBase.openInterfaceSection() {
    if (!tagExists("show_system_bar_switch")) {
        if (tagExists("settings_section_interface")) {
            composeRule.onNodeWithTag("settings_section_interface").performClick()
        }
    }
    waitUntilTagExists("show_system_bar_switch")
}

internal fun SettingsScreenPersistenceTestBase.openLibrarySection() {
    if (!tagExists("allow_blank_covers_switch")) {
        if (tagExists("settings_section_library")) {
            composeRule.onNodeWithTag("settings_section_library").performClick()
        }
    }
    waitUntilTagExists("allow_blank_covers_switch")
}

internal fun SettingsScreenPersistenceTestBase.openThemeGallery() {
    composeRule.onNodeWithContentDescription("Gallery").performClick()
    waitUntilTagDisplayed("theme_gallery_grid")
}

internal fun SettingsScreenPersistenceTestBase.openCurrentThemeEditor() {
    composeRule.onNodeWithContentDescription("Modify").performClick()
    waitUntilTagExists("custom_theme_name")
}

internal fun SettingsScreenPersistenceTestBase.saveThemeEditor() {
    closeColorPickerIfOpen()
    composeRule.onNodeWithContentDescription("Save").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsScreenPersistenceTestBase.selectThemeEditorMode(mode: String) {
    composeRule.onNodeWithTag("theme_editor_mode_${mode.lowercase()}").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsScreenPersistenceTestBase.closeThemeGallery() {
    composeRule.onNodeWithContentDescription("Done").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsScreenPersistenceTestBase.closeColorPicker() {
    composeRule.onNodeWithText("Done").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsScreenPersistenceTestBase.replacePickerHexInput(
    testTagPrefix: String,
    value: String,
) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_hex").performTextClearance()
    composeRule.onNodeWithTag("${testTagPrefix}_picker_hex").performTextInput(value)
}

internal fun SettingsScreenPersistenceTestBase.waitUntilPickerSaveEnabled(
    testTagPrefix: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            composeRule.onNodeWithTag("${testTagPrefix}_picker_save").assertIsEnabled()
            true
        }.getOrDefault(false)
    }
}

internal fun SettingsScreenPersistenceTestBase.waitUntilPickerHexValue(
    testTagPrefix: String,
    value: String,
    timeoutMillis: Long = 10_000,
) {
    waitUntilTextContains("${testTagPrefix}_picker_hex", value, timeoutMillis)
}

internal fun SettingsScreenPersistenceTestBase.waitUntilPickerHexDiffersFrom(
    testTagPrefix: String,
    value: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        runCatching {
            !readPickerHexValue(testTagPrefix).equals(value, ignoreCase = true)
        }.getOrDefault(false)
    }
}

internal fun SettingsScreenPersistenceTestBase.readPickerHexValue(testTagPrefix: String): String {
    val config = composeRule.onNodeWithTag("${testTagPrefix}_picker_hex").fetchSemanticsNode().config
    return runCatching { config[SemanticsProperties.EditableText].text }.getOrNull()
        ?: runCatching { config[SemanticsProperties.Text].joinToString(separator = "") { text -> text.text } }.getOrNull()
        ?: throw AssertionError("Expected picker hex text semantics on '${testTagPrefix}_picker_hex'")
}

internal fun SettingsScreenPersistenceTestBase.saveColorPicker(testTagPrefix: String) {
    waitUntilPickerSaveEnabled(testTagPrefix)
    composeRule.onNodeWithTag("${testTagPrefix}_picker_save").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsScreenPersistenceTestBase.galleryThemeTag(themeId: String): String = "theme_gallery_preview_$themeId"

internal fun SettingsScreenPersistenceTestBase.setSliderProgress(tag: String, value: Float) {
    composeRule.onNodeWithTag(tag)
        .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(value)
        }
}

internal fun SettingsScreenPersistenceTestBase.waitUntilTextContains(
    tag: String,
    text: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        try {
            composeRule.onNodeWithTag(tag).assertTextContains(text)
            true
        } catch (_: AssertionError) {
            false
        }
    }
}

internal fun SettingsScreenPersistenceTestBase.waitUntilTagExists(tag: String, timeoutMillis: Long = 10_000) {
    composeRule.waitUntil(timeoutMillis) { tagExists(tag) }
}

internal fun SettingsScreenPersistenceTestBase.waitUntilTagGone(tag: String, timeoutMillis: Long = 10_000) {
    composeRule.waitUntil(timeoutMillis) { !tagExists(tag) }
}

internal fun SettingsScreenPersistenceTestBase.waitUntilTagDisplayed(tag: String, timeoutMillis: Long = 10_000) {
    composeRule.waitUntil(timeoutMillis) {
        try {
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }
    }
}

internal fun SettingsScreenPersistenceTestBase.tagExists(tag: String): Boolean {
    return runCatching {
        composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        true
    }.getOrDefault(false)
}

internal fun SettingsScreenPersistenceTestBase.textExists(text: String): Boolean {
    return runCatching {
        composeRule.onNodeWithText(text).fetchSemanticsNode()
        true
    }.getOrDefault(false)
}

private fun SettingsScreenPersistenceTestBase.closeColorPickerIfOpen() {
    if (textExists("Done")) {
        closeColorPicker()
    }
}

internal fun SettingsScreenPersistenceTestBase.tagIsDisplayed(tag: String): Boolean {
    return runCatching {
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        true
    }.getOrDefault(false)
}

internal fun SettingsScreenPersistenceTestBase.scrollGalleryUntilTagDisplayed(tag: String, maxSwipes: Int = 12): Boolean {
    repeat(maxSwipes) {
        if (tagIsDisplayed(tag)) return true
        composeRule.onNodeWithTag("theme_gallery_grid").performTouchInput {
            swipe(
                start = Offset(width / 2f, height * 0.82f),
                end = Offset(width / 2f, height * 0.22f),
                durationMillis = 250,
            )
        }
        composeRule.waitForIdle()
    }
    return tagIsDisplayed(tag)
}

internal fun SettingsScreenPersistenceTestBase.waitUntilSystemBarSwitchState(
    expected: Boolean,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) {
        try {
            val node = composeRule.onNodeWithTag("show_system_bar_switch")
            if (expected) {
                node.assertIsOn()
            } else {
                node.assertIsOff()
            }
            true
        } catch (_: AssertionError) {
            false
        }
    }
}

internal fun SettingsScreenPersistenceTestBase.sampleCustomThemes(count: Int): List<CustomTheme> {
    return (1..count).map { index ->
        val primary = 0xFF000000L or
            ((0x30 + ((index * 17) % 160)).toLong() shl 16) or
            ((0x50 + ((index * 23) % 120)).toLong() shl 8) or
            (0x70 + ((index * 29) % 80)).toLong()
        val background = if (index % 2 == 0) 0xFF0F172AL else 0xFFF8FAFCL
        CustomTheme(
            id = "$CustomThemeIdPrefix-scroll-$index",
            name = "Scroll Theme $index",
            palette = generatePaletteFromBase(primary = primary, background = background),
        )
    }
}
