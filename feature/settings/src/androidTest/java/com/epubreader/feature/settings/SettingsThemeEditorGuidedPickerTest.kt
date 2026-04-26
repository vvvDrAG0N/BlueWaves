package com.epubreader.feature.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromGuidedInput
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsThemeEditorGuidedPickerTest {

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
    fun extendedInvalidAppText_resolvesToReadableColor_andShowsGuidedStatus() {
        launchThemeEditor()
        selectThemeEditorMode("extended")

        setPickerColor(
            swatchTag = "custom_theme_background_swatch",
            testTagPrefix = "custom_theme_background",
            brightness = 0f,
        )
        setPickerColor(
            swatchTag = "custom_theme_surface_swatch",
            testTagPrefix = "custom_theme_surface",
            brightness = 0f,
        )
        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status")
            .assertTextContains("Guided mode keeps colors readable")
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        setSliderProgress("custom_theme_system_text_picker_saturation", 1f)
        setSliderProgress("custom_theme_system_text_picker_value", 0f)
        closeColorPicker()

        val expectedText = formatThemeColor(
            generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFF000000,
                    appSurface = 0xFF000000,
                    appForeground = 0xFF000000,
                    readerLinked = true,
                ),
            ).appForeground,
        )

        waitUntilTextContains("custom_theme_system_text", expectedText)
    }

    @Test
    fun extendedValidAppText_keepsExactChoice() {
        launchThemeEditor()
        selectThemeEditorMode("extended")

        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status")
            .assertTextContains("Guided mode keeps colors readable")
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        setSliderProgress("custom_theme_system_text_picker_saturation", 0f)
        setSliderProgress("custom_theme_system_text_picker_value", 0f)
        closeColorPicker()

        waitUntilTextContains("custom_theme_system_text", "#000000")
    }

    @Test
    fun advancedInvalidAppText_staysLiteral() {
        launchThemeEditor()
        selectThemeEditorMode("advanced")

        setPickerColor(
            swatchTag = "custom_theme_background_swatch",
            testTagPrefix = "custom_theme_background",
            brightness = 0f,
        )
        setPickerColor(
            swatchTag = "custom_theme_surface_swatch",
            testTagPrefix = "custom_theme_surface",
            brightness = 0f,
        )
        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        setSliderProgress("custom_theme_system_text_picker_saturation", 1f)
        setSliderProgress("custom_theme_system_text_picker_value", 0f)

        waitUntilTextContains("custom_theme_system_text", "#000000")
        assertTagDoesNotExist("custom_theme_system_text_picker_guided_status")
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        closeColorPicker()
    }

    @Test
    fun basicAccent_backDismiss_discardsPendingGuidedChoice() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSliderProgress("custom_theme_primary_picker_saturation", 1f)
        setSliderProgress("custom_theme_primary_picker_value", 1f)
        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        waitUntilTagExists("custom_theme_primary_picker_hue")

        pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        assertTagDoesNotExist("custom_theme_primary_picker_hue")
    }

    @Test
    fun basicAccent_outsideDismiss_discardsPendingGuidedChoice() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSliderProgress("custom_theme_primary_picker_saturation", 1f)
        setSliderProgress("custom_theme_primary_picker_value", 1f)
        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        waitUntilTagExists("custom_theme_primary_picker_hue")

        dismissColorPickerByOutsideTap("custom_theme_primary_picker_backdrop")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        assertTagDoesNotExist("custom_theme_primary_picker_hue")
    }

    @Test
    fun basicAccent_dialogChromeTap_keepsPickerOpen() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSliderProgress("custom_theme_primary_picker_saturation", 1f)
        setSliderProgress("custom_theme_primary_picker_value", 1f)
        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")

        tapDialogChrome("custom_theme_primary_picker_dialog")
        composeRule.waitForIdle()

        waitUntilTagExists("custom_theme_primary_picker_hue")
        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        closeColorPicker()
    }

    @Test
    fun extendedInvalidAppText_doneKeepsGuidedResolvedChoice() {
        launchThemeEditor()
        selectThemeEditorMode("extended")

        setPickerColor(
            swatchTag = "custom_theme_background_swatch",
            testTagPrefix = "custom_theme_background",
            brightness = 0f,
        )
        setPickerColor(
            swatchTag = "custom_theme_surface_swatch",
            testTagPrefix = "custom_theme_surface",
            brightness = 0f,
        )
        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        setSliderProgress("custom_theme_system_text_picker_saturation", 1f)
        setSliderProgress("custom_theme_system_text_picker_value", 0f)

        closeColorPicker()

        val expectedText = formatThemeColor(
            generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFF000000,
                    appSurface = 0xFF000000,
                    appForeground = 0xFF000000,
                    readerLinked = true,
                ),
            ).appForeground,
        )

        waitUntilTextContains("custom_theme_system_text", expectedText)
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

    private fun launchThemeEditor() {
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

    private fun selectThemeEditorMode(mode: String) {
        composeRule.onNodeWithTag("theme_editor_mode_${mode.lowercase()}").performClick()
        composeRule.waitForIdle()
    }

    private fun setPickerColor(
        swatchTag: String,
        testTagPrefix: String,
        hue: Float = 0f,
        saturation: Float = 1f,
        brightness: Float,
    ) {
        composeRule.onNodeWithTag(swatchTag).performScrollTo().performClick()
        setSliderProgress("${testTagPrefix}_picker_hue", hue)
        setSliderProgress("${testTagPrefix}_picker_saturation", saturation)
        setSliderProgress("${testTagPrefix}_picker_value", brightness)
        closeColorPicker()
    }

    private fun closeColorPicker() {
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
    }

    private fun dismissColorPickerByOutsideTap(dialogTag: String) {
        composeRule.onNodeWithTag(dialogTag).performTouchInput {
            click(Offset(width * 0.1f, height * 0.1f))
        }
    }

    private fun tapDialogChrome(dialogTag: String) {
        composeRule.onNodeWithTag(dialogTag).performTouchInput {
            click(Offset(width * 0.9f, 24f))
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

    private fun assertTagDoesNotExist(tag: String) {
        val exists = runCatching {
            composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            true
        }.getOrDefault(false)
        if (exists) {
            throw AssertionError("Expected tag '$tag' to be absent")
        }
    }

    private fun assertPreviewState(tag: String, state: String) {
        composeRule.onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))
    }

    private fun waitUntilPreviewState(
        tag: String,
        state: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                assertPreviewState(tag, state)
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

    private fun setSliderProgress(tag: String, value: Float) {
        composeRule.onNodeWithTag(tag)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(value)
            }
    }
}
