package com.epubreader.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
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

    internal val appContext: Context = ApplicationProvider.getApplicationContext()
    internal val settingsManager = SettingsManager(appContext)

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
        composeRule.onNodeWithTag("custom_theme_system_text_picker_spectrum").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_safe_zone").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status")
            .assertTextContains("Guided mode keeps colors readable")
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        val attemptedPoint = setSpectrumPoint(
            tag = "custom_theme_system_text_picker_spectrum",
            saturation = 1f,
            value = 0f,
        )
        closeColorPicker()

        val expectedText = expectedGuidedProjectedColor(
            fieldKey = "app_foreground",
            attemptedPoint = attemptedPoint,
            hue = 0f,
            draft = extendedDraft(
                palette = generatePaletteFromGuidedInput(
                    GuidedThemePaletteInput(
                        accent = 0xFF4F46E5,
                        appBackground = 0xFF000000,
                        appSurface = 0xFF000000,
                        appForeground = 0xFFFFFFFF,
                        appForegroundMuted = 0xFFAAAAAA,
                        overlayScrim = 0xFF000000,
                        readerLinked = true,
                    ),
                ),
            ),
        )

        waitUntilTextContains("custom_theme_system_text", expectedText)
    }

    @Test
    fun extendedValidAppText_keepsExactChoice() {
        launchThemeEditor()
        selectThemeEditorMode("extended")

        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_spectrum").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_safe_zone").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status")
            .assertTextContains("Guided mode keeps colors readable")
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        val attemptedPoint = setSpectrumPoint(
            tag = "custom_theme_system_text_picker_spectrum",
            saturation = 0f,
            value = 0f,
        )
        closeColorPicker()

        val expectedText = expectedGuidedProjectedColor(
            fieldKey = "app_foreground",
            attemptedPoint = attemptedPoint,
            hue = 0f,
            draft = extendedDraft(
                palette = generatePaletteFromGuidedInput(
                    GuidedThemePaletteInput(
                        accent = 0xFF4F46E5,
                        appBackground = 0xFFFFFFFF,
                        appSurface = 0xFFFFFFFF,
                        appForeground = 0xFF18181B,
                        appForegroundMuted = 0xFF71717A,
                        overlayScrim = 0xFF0F172A,
                        readerLinked = true,
                    ),
                ),
            ),
        )

        waitUntilTextContains("custom_theme_system_text", expectedText)
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
        composeRule.onNodeWithTag("custom_theme_system_text_picker_spectrum").assertIsDisplayed()
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        val attemptedPoint = setSpectrumPoint(
            tag = "custom_theme_system_text_picker_spectrum",
            saturation = 1f,
            value = 0f,
        )

        waitUntilTextContains(
            "custom_theme_system_text",
            formatThemeColor(
                ThemeColorPickerHsv(
                    hue = 0f,
                    saturation = attemptedPoint.saturation,
                    value = attemptedPoint.value,
                ).toColorLong(),
            ),
        )
        assertTagDoesNotExist("custom_theme_system_text_picker_guided_status")
        assertTagDoesNotExist("custom_theme_system_text_picker_safe_zone")
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        closeColorPicker()
    }

    @Test
    fun basicPicker_showsSafeZoneWhileAdvancedHidesIt() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        waitUntilTagExists("custom_theme_primary_picker_spectrum")
        composeRule.onNodeWithTag("custom_theme_primary_picker_safe_zone").assertIsDisplayed()
        closeColorPicker()

        selectThemeEditorMode("advanced")
        composeRule.onNodeWithTag("custom_theme_favorite_accent_swatch").performScrollTo().performClick()
        waitUntilTagExists("custom_theme_favorite_accent_picker_spectrum")
        assertTagDoesNotExist("custom_theme_favorite_accent_picker_safe_zone")
        closeColorPicker()
    }

    @Test
    fun basicAccent_backDismiss_discardsPendingGuidedChoice() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSpectrumPoint(
            tag = "custom_theme_primary_picker_spectrum",
            saturation = 1f,
            value = 1f,
        )
        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        waitUntilTagExists("custom_theme_primary_picker_spectrum")

        pressBack()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        assertTagDoesNotExist("custom_theme_primary_picker_spectrum")
    }

    @Test
    fun basicAccent_outsideDismiss_discardsPendingGuidedChoice() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSpectrumPoint(
            tag = "custom_theme_primary_picker_spectrum",
            saturation = 1f,
            value = 1f,
        )
        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        waitUntilTagExists("custom_theme_primary_picker_spectrum")

        dismissColorPickerByOutsideTap("custom_theme_primary_picker_backdrop")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
        assertTagDoesNotExist("custom_theme_primary_picker_spectrum")
    }

    @Test
    fun basicAccent_dialogChromeTap_keepsPickerOpen() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSpectrumPoint(
            tag = "custom_theme_primary_picker_spectrum",
            saturation = 1f,
            value = 1f,
        )
        composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")

        tapDialogChrome("custom_theme_primary_picker_dialog")
        composeRule.waitForIdle()

        waitUntilTagExists("custom_theme_primary_picker_spectrum")
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
        val attemptedPoint = setSpectrumPoint(
            tag = "custom_theme_system_text_picker_spectrum",
            saturation = 1f,
            value = 0f,
        )

        closeColorPicker()

        val expectedText = expectedGuidedProjectedColor(
            fieldKey = "app_foreground",
            attemptedPoint = attemptedPoint,
            hue = 0f,
            draft = extendedDraft(
                palette = generatePaletteFromGuidedInput(
                    GuidedThemePaletteInput(
                        accent = 0xFF4F46E5,
                        appBackground = 0xFF000000,
                        appSurface = 0xFF000000,
                        appForeground = 0xFFFFFFFF,
                        appForegroundMuted = 0xFFAAAAAA,
                        overlayScrim = 0xFF000000,
                        readerLinked = true,
                    ),
                ),
            ),
        )

        waitUntilTextContains("custom_theme_system_text", expectedText)
    }
}
