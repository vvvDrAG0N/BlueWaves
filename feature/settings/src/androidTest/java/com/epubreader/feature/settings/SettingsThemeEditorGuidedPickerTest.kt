package com.epubreader.feature.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromGuidedInput
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
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
    fun basicAccent_hexInput_savesWithHeaderCheck() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        val expectedHex = basicDraft()
            .previewColorEdit(
                fieldKey = "accent",
                rawHex = "#3366CC",
                guided = true,
            )
            .resolvedHex

        replaceHexInput(
            testTagPrefix = "custom_theme_primary",
            nextHex = "3366CC",
        )

        assertPreviewHex("custom_theme_primary_picker_preview", expectedHex)
        tapHeaderSave("custom_theme_primary")

        waitUntilTextContains("custom_theme_primary", expectedHex)
        assertTagDoesNotExist("custom_theme_primary_picker_spectrum")
    }

    @Test
    fun advancedFavoriteAccent_rgbInput_keepsLiteralPreviewWithoutGuidedCue() {
        val seededTheme = blackExtendedTheme()
        runBlocking {
            resetSettings(
                theme = seededTheme.id,
                customThemes = listOf(seededTheme),
            )
        }
        launchCurrentThemeEditor()
        selectThemeEditorMode("advanced")

        openColorPicker("custom_theme_favorite_accent_swatch")
        replaceRgbInput(
            testTagPrefix = "custom_theme_favorite_accent",
            red = "255",
            green = "064",
            blue = "032",
        )
        val literalHex = "#${readNodeText("custom_theme_favorite_accent_picker_hex")}"

        assertPreviewHex("custom_theme_favorite_accent_picker_preview", literalHex)
        assertTagDoesNotExist("custom_theme_favorite_accent_picker_guided_status")
        assertTagDoesNotExist("custom_theme_favorite_accent_picker_safe_zone")
        requestCloseColorPicker("custom_theme_favorite_accent")
        waitUntilTagExists("custom_theme_favorite_accent_picker_exit_dialog")
        tapExitDialogAction("custom_theme_favorite_accent", "discard")
        waitUntilPickerClosed("custom_theme_favorite_accent")
    }

    @Test
    fun extendedInvalidAppText_typedHex_adjustsAndShowsGuidedCue() {
        val seededTheme = blackExtendedTheme()
        runBlocking {
            resetSettings(
                theme = seededTheme.id,
                customThemes = listOf(seededTheme),
            )
        }
        launchCurrentThemeEditor()
        selectThemeEditorMode("extended")
        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        replaceHexInput(
            testTagPrefix = "custom_theme_system_text",
            nextHex = "000000",
        )
        val resolvedHex = "#${readNodeText("custom_theme_system_text_picker_hex")}"

        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status")
            .assertIsDisplayed()
        assertNotEquals("#000000", resolvedHex)
        assertPreviewHex("custom_theme_system_text_picker_preview", resolvedHex)
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status")
            .assertTextContains("Adjusted for readability")
        tapHeaderSave("custom_theme_system_text")
        waitUntilTextContains("custom_theme_system_text", resolvedHex)
        waitUntilPickerClosed("custom_theme_system_text")
    }

    @Test
    fun extendedInvalidAppText_resolvesToReadableColor_andShowsGuidedStatus() {
        val seededTheme = blackExtendedTheme()
        runBlocking {
            resetSettings(
                theme = seededTheme.id,
                customThemes = listOf(seededTheme),
            )
        }
        launchCurrentThemeEditor()
        selectThemeEditorMode("extended")
        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_spectrum").assertIsDisplayed()
        waitUntilTagDisplayed("custom_theme_system_text_picker_safe_zone")
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_status")
            .assertTextContains("Guided mode keeps colors readable")
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        setSpectrumPoint(
            tag = "custom_theme_system_text_picker_spectrum",
            saturation = 1f,
            value = 0f,
        )
        val previewHex = readPreviewHex("custom_theme_system_text_picker_preview")
        assertNotEquals("#000000", previewHex)
        tapHeaderSave("custom_theme_system_text")
        waitUntilTextContains("custom_theme_system_text", previewHex)
        waitUntilPickerClosed("custom_theme_system_text")
    }

    @Test
    fun extendedValidAppText_keepsExactChoice() {
        launchThemeEditor()
        selectThemeEditorMode("extended")

        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_spectrum").assertIsDisplayed()
        waitUntilTagDisplayed("custom_theme_system_text_picker_safe_zone")
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
        tapHeaderSave("custom_theme_system_text")

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
        val seededTheme = blackExtendedTheme()
        runBlocking {
            resetSettings(
                theme = seededTheme.id,
                customThemes = listOf(seededTheme),
            )
        }
        launchCurrentThemeEditor()
        selectThemeEditorMode("advanced")
        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        composeRule.onNodeWithTag("custom_theme_system_text_picker_spectrum").assertIsDisplayed()
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        val attemptedPoint = setSpectrumPoint(
            tag = "custom_theme_system_text_picker_spectrum",
            saturation = 1f,
            value = 0f,
        )

        val expectedHex = formatThemeColor(
            ThemeColorPickerHsv(
                hue = 0f,
                saturation = attemptedPoint.saturation,
                value = attemptedPoint.value,
            ).toColorLong(),
        )
        assertPreviewHex("custom_theme_system_text_picker_preview", expectedHex)
        assertTagDoesNotExist("custom_theme_system_text_picker_guided_status")
        assertTagDoesNotExist("custom_theme_system_text_picker_safe_zone")
        assertPreviewState("custom_theme_system_text_picker_preview", "default")
        tapHeaderSave("custom_theme_system_text")
        waitUntilTextContains("custom_theme_system_text", expectedHex)
    }

    @Test
    fun basicPicker_showsSafeZoneWhileAdvancedHidesIt() {
        launchThemeEditor()

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        waitUntilTagExists("custom_theme_primary_picker_spectrum")
        waitUntilTagDisplayed("custom_theme_primary_picker_safe_zone")
        tapHeaderSave("custom_theme_primary")

        selectThemeEditorMode("advanced")
        composeRule.onNodeWithTag("custom_theme_favorite_accent_swatch").performScrollTo().performClick()
        waitUntilTagExists("custom_theme_favorite_accent_picker_spectrum")
        assertTagDoesNotExist("custom_theme_favorite_accent_picker_safe_zone")
        tapHeaderSave("custom_theme_favorite_accent")
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

    @Test
    fun extendedInvalidAppText_doneKeepsGuidedResolvedChoice() {
        val seededTheme = blackExtendedTheme()
        runBlocking {
            resetSettings(
                theme = seededTheme.id,
                customThemes = listOf(seededTheme),
            )
        }
        launchCurrentThemeEditor()
        selectThemeEditorMode("extended")
        composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_system_text_picker_hue", 0f)
        setSpectrumPoint(
            tag = "custom_theme_system_text_picker_spectrum",
            saturation = 1f,
            value = 0f,
        )
        val previewHex = readPreviewHex("custom_theme_system_text_picker_preview")
        assertNotEquals("#000000", previewHex)
        tapHeaderSave("custom_theme_system_text")
        waitUntilTextContains("custom_theme_system_text", previewHex)
        waitUntilPickerClosed("custom_theme_system_text")
    }

    private fun blackExtendedTheme(): CustomTheme {
        return CustomTheme(
            id = "custom-guided-black",
            name = "Guided Black",
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
        )
    }
}
