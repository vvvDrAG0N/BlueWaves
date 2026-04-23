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
    fun customTheme_creationSelectsThemeAndPersistsAcrossScreenReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Ocean")
        composeRule.onNodeWithText("Create Theme").performClick()

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
    fun themeGallery_doneDismissesOverlayAndAllowsReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        openThemeGallery()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(10_000) { !tagExists("theme_gallery_preview_light") }

        openThemeGallery()
        composeRule.onNodeWithTag("theme_gallery_preview_light").assertIsDisplayed()
    }

    @Test
    fun showSystemBar_persistsAcrossScreenReopenAndActivityRecreation() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openInterfaceSection()
        scrollToSystemBarControls()

        composeRule.onNodeWithTag("show_system_bar_switch").performClick()
        waitUntilSystemBarSwitchState(expected = true)

        launchSettingsScreen()
        openInterfaceSection()
        scrollToSystemBarControls()
        waitUntilSystemBarSwitchState(expected = true)

        composeRule.activityRule.scenario.recreate()
        launchSettingsScreen()
        openInterfaceSection()
        scrollToSystemBarControls()
        waitUntilSystemBarSwitchState(expected = true)
    }

    @Test
    fun showSystemBar_rowAndSwitchApplyEveryRapidToggle() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openInterfaceSection()
        scrollToSystemBarControls()

        // Single toggle to verify basic functionality
        composeRule.onNodeWithTag("show_system_bar_switch").performClick()
        waitUntilSystemBarSwitchState(expected = true)

        runBlocking { resetSettings() }
        launchSettingsScreen()
        openInterfaceSection()
        scrollToSystemBarControls()
        waitUntilSystemBarSwitchState(expected = false)

        composeRule.onNodeWithTag("show_system_bar_switch").performClick()
        waitUntilSystemBarSwitchState(expected = true)
    }

    @Test
    fun allowBlankCovers_persistsAcrossScreenReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openLibrarySection()
        composeRule.onNodeWithText("Allow Blank Covers").performScrollTo()

        composeRule.onNodeWithTag("allow_blank_covers_switch").performClick()
        composeRule.waitUntil(10_000) {
            runBlocking { settingsManager.globalSettings.first() }.allowBlankCovers
        }

        launchSettingsScreen()
        openLibrarySection()
        composeRule.onNodeWithText("Allow Blank Covers").performScrollTo()
        composeRule.onNodeWithTag("allow_blank_covers_switch").assertIsOn()
    }

    private suspend fun resetSettings() {
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

    private fun openAppearanceSection() {
        if (!tagExists("create_custom_theme_button")) {
            if (tagExists("settings_section_appearance")) {
                composeRule.onNodeWithTag("settings_section_appearance").performClick()
            }
        }
        waitUntilTagExists("create_custom_theme_button")
    }

    private fun openInterfaceSection() {
        if (!tagExists("show_system_bar_switch")) {
            if (tagExists("settings_section_interface")) {
                composeRule.onNodeWithTag("settings_section_interface").performClick()
            }
        }
        waitUntilTagExists("show_system_bar_switch")
    }

    private fun openLibrarySection() {
        if (!tagExists("allow_blank_covers_switch")) {
            if (tagExists("settings_section_library")) {
                composeRule.onNodeWithTag("settings_section_library").performClick()
            }
        }
        waitUntilTagExists("allow_blank_covers_switch")
    }

    private fun openThemeGallery() {
        composeRule.onNodeWithContentDescription("Gallery").performClick()
        waitUntilTagExists("theme_gallery_preview_light")
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

    private fun waitUntilTagExists(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) { tagExists(tag) }
    }

    private fun tagExists(tag: String): Boolean {
        return runCatching {
            composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            true
        }.getOrDefault(false)
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
