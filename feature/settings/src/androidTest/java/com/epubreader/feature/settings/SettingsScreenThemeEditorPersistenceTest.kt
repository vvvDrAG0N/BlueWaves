package com.epubreader.feature.settings

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.formatThemeColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class SettingsScreenThemeEditorPersistenceTest : SettingsScreenPersistenceTestBase() {

    @Test
    fun themeEditor_switchingModesPreservesAdvancedReaderAccentUntilRebalance() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Mode Guard")
        saveThemeEditor()

        var activeThemeId = ""
        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            activeThemeId = settings.theme
            settings.theme.startsWith(CustomThemeIdPrefix)
        }

        openCurrentThemeEditor()
        selectThemeEditorMode("advanced")
        composeRule.onNodeWithTag("custom_theme_reader_accent_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_reader_accent_picker_hue", 0f)
        setSliderProgress("custom_theme_reader_accent_picker_saturation", 1f)
        setSliderProgress("custom_theme_reader_accent_picker_value", 1f)
        closeColorPicker()
        waitUntilTextContains("custom_theme_reader_accent", "#FF0000")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId && formatThemeColor(theme.palette.readerAccent) == "#FF0000"
            }
        }

        openCurrentThemeEditor()
        selectThemeEditorMode("extended")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId && formatThemeColor(theme.palette.readerAccent) == "#FF0000"
            }
        }

        openCurrentThemeEditor()
        selectThemeEditorMode("extended")
        composeRule.onNodeWithTag("theme_editor_rebalance_button").performClick()
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId && formatThemeColor(theme.palette.readerAccent) != "#FF0000"
            }
        }
    }

    @Test
    fun themeEditor_unlockingReaderLinkAllowsSeparateReaderBackground() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Split Reader")
        saveThemeEditor()

        var activeThemeId = ""
        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            activeThemeId = settings.theme
            settings.theme.startsWith(CustomThemeIdPrefix)
        }

        openCurrentThemeEditor()
        selectThemeEditorMode("extended")
        composeRule.onNodeWithTag("theme_editor_reader_link_toggle").performScrollTo().performClick()
        waitUntilTagExists("custom_theme_reader_background_swatch")
        composeRule.onNodeWithTag("custom_theme_reader_background_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_reader_background_picker_hue", 120f)
        setSliderProgress("custom_theme_reader_background_picker_saturation", 1f)
        setSliderProgress("custom_theme_reader_background_picker_value", 1f)
        closeColorPicker()
        waitUntilTextContains("custom_theme_reader_background", "#00FF00")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId &&
                    formatThemeColor(theme.palette.readerBackground) == "#00FF00" &&
                    formatThemeColor(theme.palette.background) != "#00FF00"
            }
        }
    }

    @Test
    fun themeEditor_switchingModesPreservesAdvancedFavoriteAccentUntilRebalance() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Launch Guard")
        saveThemeEditor()

        var activeThemeId = ""
        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            activeThemeId = settings.theme
            settings.theme.startsWith(CustomThemeIdPrefix)
        }

        openCurrentThemeEditor()
        selectThemeEditorMode("advanced")
        composeRule.onNodeWithTag("custom_theme_favorite_accent_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_favorite_accent_picker_hue", 0f)
        setSliderProgress("custom_theme_favorite_accent_picker_saturation", 1f)
        setSliderProgress("custom_theme_favorite_accent_picker_value", 1f)
        closeColorPicker()
        waitUntilTextContains("custom_theme_favorite_accent", "#FF0000")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId && formatThemeColor(theme.palette.favoriteAccent) == "#FF0000"
            }
        }

        openCurrentThemeEditor()
        selectThemeEditorMode("advanced")
        waitUntilTextContains("custom_theme_favorite_accent", "#FF0000")
        saveThemeEditor()

        openCurrentThemeEditor()
        selectThemeEditorMode("extended")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId && formatThemeColor(theme.palette.favoriteAccent) == "#FF0000"
            }
        }

        openCurrentThemeEditor()
        selectThemeEditorMode("extended")
        composeRule.onNodeWithTag("theme_editor_rebalance_button").performClick()
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId && formatThemeColor(theme.palette.favoriteAccent) != "#FF0000"
            }
        }
    }
}
