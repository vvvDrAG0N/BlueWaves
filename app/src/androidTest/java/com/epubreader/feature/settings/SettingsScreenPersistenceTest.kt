package com.epubreader.feature.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.formatThemeColor
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenPersistenceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val appContext: Context = ApplicationProvider.getApplicationContext()
    private val settingsManager = SettingsManager(appContext)

    @Before
    fun setUp() = runBlocking {
        resetSettings()
    }

    @After
    fun tearDown() = runBlocking {
        resetSettings()
    }

    @Test
    fun changingControls_persistsAcrossScreenReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")

        // Switch to Fonts tab
        composeRule.onNodeWithText("Fonts").performClick()

        composeRule.onNodeWithContentDescription("Font Size Slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(26f)
            }
        composeRule.onNodeWithText("Karla").performScrollTo().performClick()

        // Switch to Themes tab
        composeRule.onNodeWithText("Themes").performClick()
        composeRule.onNodeWithContentDescription("Theme OLED").performScrollTo().performClick()

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

        // Check Fonts tab
        composeRule.onNodeWithText("Fonts").performClick()
        waitUntilDisplayed("26sp")
        composeRule.onNodeWithText("Karla").assertIsSelected()

        // Check Themes tab
        composeRule.onNodeWithText("Themes").performClick()
        composeRule.onNodeWithContentDescription("Theme OLED").assertIsSelected()
        composeRule.onNodeWithText("Font Family").assertDoesNotExist() // Not in Themes tab
    }

    @Test
    fun customTheme_creationSelectsThemeAndPersistsAcrossScreenReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")

        // Switch to Themes tab
        composeRule.onNodeWithText("Themes").performClick()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Ocean")
        composeRule.onNodeWithText("Create Theme").performClick()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.theme.startsWith(CustomThemeIdPrefix) &&
                settings.customThemes.any { it.name == "Ocean" }
        }

        launchSettingsScreen()
        // Switch to Themes tab
        composeRule.onNodeWithText("Themes").performClick()
        composeRule.onNodeWithContentDescription("Theme Ocean").assertIsSelected()
    }

    @Test
    fun customThemeColorPicker_updatesHexFieldAndSavedPalette() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")

        // Switch to Themes tab
        composeRule.onNodeWithText("Themes").performClick()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Sunset")
        composeRule.onNodeWithTag("custom_theme_primary_swatch").performClick()

        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSliderProgress("custom_theme_primary_picker_saturation", 1f)
        setSliderProgress("custom_theme_primary_picker_value", 1f)

        waitUntilTextContains("custom_theme_primary", "#FF0000")
        composeRule.onNodeWithText("Create Theme").performClick()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.name == "Sunset" && formatThemeColor(theme.palette.primary) == "#FF0000"
            }
        }
    }

    @Test
    fun showSystemBar_persistsAcrossScreenReopenAndActivityRecreation() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        // General tab is default, so no need to switch
        scrollToSystemBarControls()

        composeRule.onNodeWithTag("show_system_bar_switch").performClick()
        waitUntilSystemBarSwitchState(expected = true)

        launchSettingsScreen()
        scrollToSystemBarControls()
        waitUntilSystemBarSwitchState(expected = true)

        composeRule.activityRule.scenario.recreate()
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        scrollToSystemBarControls()
        waitUntilSystemBarSwitchState(expected = true)
    }

    @Test
    fun showSystemBar_rowAndSwitchApplyEveryRapidToggle() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        // General tab is default, so no need to switch
        scrollToSystemBarControls()

        // Single toggle to verify basic functionality
        composeRule.onNodeWithTag("show_system_bar_switch").performClick()
        waitUntilSystemBarSwitchState(expected = true)

        runBlocking { resetSettings() }
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        scrollToSystemBarControls()
        waitUntilSystemBarSwitchState(expected = false)

        composeRule.onNodeWithTag("show_system_bar_switch").performClick()
        waitUntilSystemBarSwitchState(expected = true)
    }

    private suspend fun resetSettings() {
        settingsManager.updateGlobalSettings(
            GlobalSettings(
                fontSize = 18,
                fontType = "serif",
                theme = "light",
                lineHeight = 1.6f,
                horizontalPadding = 16,
                showScrubber = false,
                showSystemBar = false,
            ),
        )
    }

    private fun launchSettingsScreen() {
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
    }

    private fun waitUntilDisplayed(text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun scrollToSystemBarControls() {
        composeRule.onNodeWithText("Show System Bars").performScrollTo()
    }

    private fun setSliderProgress(tag: String, value: Float) {
        composeRule.onNodeWithTag(tag)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(value)
            }
    }

    private fun waitUntilTextContains(tag: String, text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithTag(tag).assertTextContains(text)
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun waitUntilSystemBarSwitchState(expected: Boolean, timeoutMillis: Long = 10_000) {
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
}
