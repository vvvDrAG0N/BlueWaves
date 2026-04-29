package com.epubreader.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsThemeEditorGuidedPickerDismissalTest : SettingsThemeEditorGuidedPickerTestBase() {

    @Test
    fun basicAppBackground_longTitleKeepsSaveVisible() {
        launchThemeEditor()

        openColorPicker("custom_theme_background_swatch")
        composeRule.onNodeWithTag("custom_theme_background_picker_close").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_background_picker_save").assertIsDisplayed()
        requestCloseColorPicker("custom_theme_background")
        waitUntilPickerClosed("custom_theme_background")
    }

    @Test
    fun basicAccent_backWhileDirty_showsSaveDiscardKeepEditing() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        replaceHexInput("custom_theme_primary", "12AB34")

        pressActivityBack("custom_theme_primary")
        waitUntilTagExists("custom_theme_primary_picker_exit_dialog")
        waitUntilTagExists("custom_theme_primary_picker_exit_save")
        waitUntilTagExists("custom_theme_primary_picker_exit_discard")
        waitUntilTagExists("custom_theme_primary_picker_exit_keep_editing")

        tapExitDialogAction("custom_theme_primary", "keep_editing")
        waitUntilTagDisplayed("custom_theme_primary_picker_hex")
    }

    @Test
    fun basicAccent_backSave_commitsPendingGuidedChoice() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        replaceHexInput("custom_theme_primary", "12AB34")
        val committedHex = "#${readNodeText("custom_theme_primary_picker_hex")}"

        pressActivityBack("custom_theme_primary")
        waitUntilTagExists("custom_theme_primary_picker_exit_dialog")
        tapExitDialogAction("custom_theme_primary", "save")

        waitUntilTextContains("custom_theme_primary", committedHex)
        waitUntilPickerClosed("custom_theme_primary")
    }

    @Test
    fun basicAccent_closeIconDiscard_closesWithoutSaving() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        replaceHexInput("custom_theme_primary", "12AB34")

        requestCloseColorPicker("custom_theme_primary")
        waitUntilTagExists("custom_theme_primary_picker_exit_dialog")
        tapExitDialogAction("custom_theme_primary", "discard")

        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        waitUntilPickerClosed("custom_theme_primary")
    }

    @Test
    fun basicAccent_closeIconWhenClean_closesImmediately() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        requestCloseColorPicker("custom_theme_primary")

        waitUntilPickerClosed("custom_theme_primary")
    }

    @Test
    fun basicAccent_outsideTapDoesNothingWhenDirty() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        replaceHexInput("custom_theme_primary", "12AB34")

        dismissColorPickerByOutsideTap("custom_theme_primary_picker_backdrop")
        waitUntilTagDisplayed("custom_theme_primary_picker_hex")
    }

    @Test
    fun pickerOutsideTap_whenClean_stillDoesNothing() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()

        dismissColorPickerByOutsideTap("custom_theme_primary_picker_backdrop")
        waitUntilTagDisplayed("custom_theme_primary_picker_hex")
    }

    @Test
    fun basicAccent_dialogChromeTap_keepsPickerOpen() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        replaceHexInput("custom_theme_primary", "12AB34")

        tapDialogChrome("custom_theme_primary_picker_dialog")
        composeRule.waitForIdle()

        waitUntilTagDisplayed("custom_theme_primary_picker_hex")
        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        tapHeaderSave("custom_theme_primary")
    }
}
