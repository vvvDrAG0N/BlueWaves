package com.epubreader.feature.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
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
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.formatThemeColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenPersistenceTest : SettingsScreenPersistenceTestBase() {

    @Test
    fun changingControls_persistsAcrossScreenReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithContentDescription("Font Size Slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(26f)
            }
        composeRule.onNodeWithText("Karla").performScrollTo().performClick()

        openThemeGallery()
        composeRule.onNodeWithTag("theme_gallery_preview_oled").performScrollTo().performClick()

        var lastSettings: GlobalSettings? = null
        composeRule.waitUntil(10_000) {
            lastSettings = runBlocking { settingsManager.globalSettings.first() }
            lastSettings?.let {
                it.fontSize == 26 &&
                        it.fontType == "karla" &&
                        it.theme == "oled"
            } ?: false
        }

        launchSettingsScreen()
        openAppearanceSection()
        waitUntilDisplayed("26")
        composeRule.onNodeWithText("Karla").assertIsSelected()

        openThemeGallery()
        composeRule.onNodeWithTag("theme_gallery_preview_oled").assertIsSelected()
    }

    @Test
    fun appearanceSwipe_backOutOfSection_persistsPendingThemeSelection() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("appearance_theme_pager").performTouchInput {
                swipe(
                    start = Offset(width * 0.85f, height / 2f),
                    end = Offset(width * 0.1f, height / 2f),
                    durationMillis = 250,
                )
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("appearance_theme_card_sepia").assertIsSelected()

            pressBack()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.waitUntil(10_000) {
            runBlocking { settingsManager.globalSettings.first().theme } == "sepia"
        }
        waitUntilTagExists("settings_section_appearance")
    }

    @Test
    fun customTheme_creationSelectsThemeAndPersistsAcrossScreenReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Ocean")
        composeRule.onNodeWithContentDescription("Save").performClick()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.theme.startsWith(CustomThemeIdPrefix) &&
                settings.customThemes.any { it.name == "Ocean" }
        }
        val activeCustomThemeId = runBlocking { settingsManager.globalSettings.first().theme }

        launchSettingsScreen()
        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.theme == activeCustomThemeId &&
                settings.customThemes.any { it.name == "Ocean" }
        }
    }

    @Test
    fun customThemeColorPicker_updatesHexFieldAndSavedPalette() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Sunset")
        composeRule.onNodeWithTag("custom_theme_primary_swatch").performClick()

        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSliderProgress("custom_theme_primary_picker_saturation", 1f)
        setSliderProgress("custom_theme_primary_picker_value", 1f)

        waitUntilTextContains("custom_theme_primary", "#FF0000")
        composeRule.onNodeWithContentDescription("Save").performClick()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.name == "Sunset" && formatThemeColor(theme.palette.primary) == "#FF0000"
            }
        }
    }

}
