package com.epubreader.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.generatePaletteFromBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsThemeEditorExitTest : SettingsScreenPersistenceTestBase() {

    @Test
    fun editTheme_backWhenDirty_showsExitDialog_andKeepEditingKeepsEditorOpen() {
        runBlocking {
            seedActiveCustomTheme()
        }
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()
        openCurrentThemeEditor()

        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Exit Guard Draft")

        requestEditorBack()

        waitUntilTagExists("theme_editor_exit_dialog")
        composeRule.onNodeWithTag("theme_editor_exit_save").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_editor_exit_discard").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_editor_exit_keep_editing").assertIsDisplayed()

        composeRule.onNodeWithTag("theme_editor_exit_keep_editing").performClick()
        waitUntilTagGone("theme_editor_exit_dialog")
        composeRule.onNodeWithTag("custom_theme_name").assertIsDisplayed()
    }

    @Test
    fun editTheme_backSave_persistsNameChange() {
        val originalTheme = runBlocking {
            seedActiveCustomTheme()
        }
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()
        openCurrentThemeEditor()

        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Saved Exit Name")

        requestEditorBack()
        waitUntilTagExists("theme_editor_exit_dialog")
        composeRule.onNodeWithTag("theme_editor_exit_save").performClick()

        waitUntilTagGone("theme_editor_sheet")
        composeRule.waitUntil(10_000) {
            runBlocking {
                settingsManager.globalSettings.first().customThemes.any { theme ->
                    theme.id == originalTheme.id && theme.name == "Saved Exit Name"
                }
            }
        }
    }

    @Test
    fun editTheme_closeWhenDirty_usesSameExitDialog() {
        runBlocking {
            seedActiveCustomTheme()
        }
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()
        openCurrentThemeEditor()

        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Close Exit Draft")

        composeRule.onNodeWithContentDescription("Close").performClick()

        waitUntilTagExists("theme_editor_exit_dialog")
        composeRule.onNodeWithTag("theme_editor_exit_discard").assertIsDisplayed()
    }

    private suspend fun seedActiveCustomTheme(): CustomTheme {
        val theme = CustomTheme(
            id = "$CustomThemeIdPrefix-exit-guard",
            name = "Exit Guard",
            palette = generatePaletteFromBase(
                primary = 0xFF4F46E5,
                background = 0xFFF8FAFC,
            ),
        )
        resetSettings(customThemes = listOf(theme))
        settingsManager.setActiveTheme(theme.id)
        return theme
    }

    private fun requestEditorBack() {
        pressBack()
        composeRule.waitForIdle()
        if (!tagExists("theme_editor_exit_dialog") && tagExists("theme_editor_sheet")) {
            pressBack()
            composeRule.waitForIdle()
        }
    }
}
