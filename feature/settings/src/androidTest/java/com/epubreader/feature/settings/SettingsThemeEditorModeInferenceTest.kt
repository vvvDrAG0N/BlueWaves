package com.epubreader.feature.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
class SettingsThemeEditorModeInferenceTest {

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
    fun createTheme_reopensInBasic_andEditingControlsStayReachable() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        assertModeSelected("basic")
        composeRule.onNodeWithTag("theme_editor_rebalance_button").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rebalance Derived Roles").assertIsDisplayed()

        selectThemeEditorMode("extended")
        composeRule.onNodeWithTag("theme_editor_rebalance_button").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rebalance Derived Roles").assertIsDisplayed()
        selectThemeEditorMode("basic")

        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Mode Inference Basic")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            runBlocking { settingsManager.globalSettings.first() }.theme.startsWith(CustomThemeIdPrefix)
        }

        openCurrentThemeEditor()
        assertModeSelected("basic")
        composeRule.onNodeWithTag("theme_editor_rebalance_button").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rebalance Derived Roles").assertIsDisplayed()
    }

    @Test
    fun extendedOnlyEdits_reopenInExtended() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Mode Inference Extended")
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
        waitUntilTagEnabled("custom_theme_reader_background_picker_save")
        setSliderProgress("custom_theme_reader_background_picker_saturation", 1f)
        setSliderProgress("custom_theme_reader_background_picker_value", 1f)
        closeColorPicker("custom_theme_reader_background")
        waitUntilTextContains("custom_theme_reader_background", "#00FF00")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId &&
                    formatThemeColor(theme.palette.readerBackground) == "#00FF00"
            }
        }

        openCurrentThemeEditor()
        assertModeSelected("extended")
        composeRule.onNodeWithTag("custom_theme_reader_background").performScrollTo()
        composeRule.onNodeWithTag("custom_theme_reader_background").assertTextContains("#00FF00")
    }

    @Test
    fun advancedOnlyEdits_reopenInAdvanced() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Mode Inference Advanced")
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
        closeColorPicker("custom_theme_favorite_accent")
        waitUntilTextContains("custom_theme_favorite_accent", "#FF0000")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.id == activeThemeId &&
                    formatThemeColor(theme.palette.favoriteAccent) == "#FF0000"
            }
        }

        openCurrentThemeEditor()
        assertModeSelected("advanced")
        composeRule.onNodeWithTag("theme_editor_rebalance_button").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rebalance unavailable in Advanced mode").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_favorite_accent").performScrollTo()
        composeRule.onNodeWithTag("custom_theme_favorite_accent").assertTextContains("#FF0000")
    }

    @Test
    fun advancedRebalanceTap_doesNotMutatePaletteBeforeSave() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Advanced Rebalance Guard")
        saveThemeEditor()

        composeRule.waitUntil(10_000) {
            runBlocking { settingsManager.globalSettings.first() }.theme.startsWith(CustomThemeIdPrefix)
        }

        openCurrentThemeEditor()
        selectThemeEditorMode("advanced")
        composeRule.onNodeWithTag("custom_theme_favorite_accent_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_favorite_accent_picker_hue", 0f)
        setSliderProgress("custom_theme_favorite_accent_picker_saturation", 1f)
        setSliderProgress("custom_theme_favorite_accent_picker_value", 1f)
        closeColorPicker("custom_theme_favorite_accent")
        waitUntilTextContains("custom_theme_favorite_accent", "#FF0000")

        scrollThemeEditorToTop()
        composeRule.onNodeWithTag("theme_editor_rebalance_button").performClick()
        revealThemeEditorField("custom_theme_favorite_accent")
        composeRule.onNodeWithTag("custom_theme_favorite_accent").performScrollTo()
        composeRule.onNodeWithTag("custom_theme_favorite_accent").assertTextContains("#FF0000")
        composeRule.onNodeWithContentDescription("Close").performClick()
        waitUntilTagExists("theme_editor_exit_dialog")
        composeRule.onNodeWithTag("theme_editor_exit_discard").performClick()
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
            runCatching {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTagExists(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTextContains(
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

    private fun openAppearanceSection() {
        composeRule.onNodeWithTag("settings_section_appearance").performClick()
        waitUntilTagExists("create_custom_theme_button")
    }

    private fun openCurrentThemeEditor() {
        composeRule.onNodeWithContentDescription("Modify").performClick()
        waitUntilTagExists("custom_theme_name")
    }

    private fun revealThemeEditorField(tag: String, maxSwipes: Int = 4) {
        repeat(maxSwipes) {
            val isVisible = runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
                true
            }.getOrDefault(false)
            if (isVisible) return
            composeRule.onNodeWithTag("theme_editor_scroll_content").performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        waitUntilTagExists(tag)
    }

    private fun scrollThemeEditorToTop(maxSwipes: Int = 4) {
        repeat(maxSwipes) {
            composeRule.onNodeWithTag("theme_editor_scroll_content", useUnmergedTree = true)
                .performTouchInput { swipeDown() }
            composeRule.waitForIdle()
        }
    }

    private fun saveThemeEditor() {
        closeColorPickerIfOpen()
        composeRule.onNodeWithContentDescription("Save").performClick()
        composeRule.waitForIdle()
    }

    private fun selectThemeEditorMode(mode: String) {
        composeRule.onNodeWithTag("theme_editor_mode_${mode.lowercase()}").performClick()
        composeRule.waitForIdle()
    }

    private fun assertModeSelected(mode: String) {
        composeRule.onNodeWithTag("theme_editor_mode_${mode.lowercase()}").assertIsSelected()
    }

    private fun setSliderProgress(tag: String, value: Float) {
        composeRule.onNodeWithTag(tag)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(value)
            }
    }

    private fun waitUntilTagEnabled(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTagAbsent(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithTag(tag).fetchSemanticsNode()
                false
            }.getOrDefault(true)
        }
    }

    private fun closeColorPicker(testTagPrefix: String) {
        waitUntilTagEnabled("${testTagPrefix}_picker_save")
        composeRule.onNodeWithTag("${testTagPrefix}_picker_save").performClick()
        composeRule.waitForIdle()
        waitUntilTagAbsent("${testTagPrefix}_picker_spectrum")
    }

    private fun closeColorPickerIfOpen() {
        val pickerVisible = runCatching {
            composeRule.onNodeWithText("Hue").fetchSemanticsNode()
            true
        }.getOrDefault(false)
        if (pickerVisible) {
            val knownPickerPrefixes = listOf(
                "custom_theme_reader_background",
                "custom_theme_favorite_accent",
            )
            val activePrefix = knownPickerPrefixes.firstOrNull { prefix ->
                runCatching {
                    composeRule.onNodeWithTag("${prefix}_picker_spectrum").fetchSemanticsNode()
                    true
                }.getOrDefault(false)
            }
            if (activePrefix != null) {
                closeColorPicker(activePrefix)
            }
        }
    }
}
