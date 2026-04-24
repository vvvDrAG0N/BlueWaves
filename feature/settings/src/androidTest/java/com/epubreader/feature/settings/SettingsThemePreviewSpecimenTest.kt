package com.epubreader.feature.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.BuiltInThemeOptions
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.core.model.themePaletteSeed
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsThemePreviewSpecimenTest {

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
    fun createTheme_afterPagerSwipe_seedsFromVisibleTheme() {
        runBlocking { resetSettings(theme = "sepia") }

        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("appearance_theme_pager").performTouchInput {
                swipe(
                    start = Offset(width * 0.15f, height / 2f),
                    end = Offset(width * 0.85f, height / 2f),
                    durationMillis = 250,
                )
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("appearance_theme_card_light").assertIsSelected()
            composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        waitUntilTagExists("custom_theme_name")
        waitUntilStateDescriptionContains("theme_editor_sheet", "chromeTheme=light")
        val lightSeed = themePaletteSeed("light", emptyList())
        val expectedPalette = generatePaletteFromBase(
            primary = lightSeed.accent,
            background = lightSeed.appBackground,
        )

        waitUntilTextContains("custom_theme_primary", formatThemeColor(expectedPalette.accent))
        waitUntilTextContains("custom_theme_background", formatThemeColor(expectedPalette.appBackground))
    }

    @Test
    fun appearanceReaderPreview_reactsToTypographyControls() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        val previewTag = "appearance_theme_card_light"
        waitUntilTagExists(previewTag)
        val initial = captureBitmapByTag(previewTag)

        setSliderProgressByContentDescription("Font Size Slider", 26f)
        val afterFontSize = captureBitmapByTag(previewTag)
        assertBitmapsDifferent(initial, afterFontSize, "font size change")

        setSliderProgressByContentDescription("Line Height Slider", 2f)
        val afterLineHeight = captureBitmapByTag(previewTag)
        assertBitmapsDifferent(afterFontSize, afterLineHeight, "line height change")

        setSliderProgressByContentDescription("Padding Slider", 48f)
        val afterPadding = captureBitmapByTag(previewTag)
        assertBitmapsDifferent(afterLineHeight, afterPadding, "padding change")
    }

    @Test
    fun galleryReaderPreview_preservesSelectionAndLongPress() {
        val customTheme = sampleCustomTheme()
        runBlocking {
            resetSettings(customThemes = listOf(customTheme))
        }

        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        openThemeGallery()
        val builtInTarget = BuiltInThemeOptions.first { it.id != "light" }
        composeRule.onNodeWithTag(galleryThemeTag(builtInTarget.id)).performClick()
        waitUntilActiveThemeIs(builtInTarget.id)

        openThemeGallery()
        val customTag = galleryThemeTag(customTheme.id)
        assertTrue(scrollGalleryUntilTagDisplayed(customTag))
        composeRule.onNodeWithTag(customTag).performTouchInput { longClick() }
        waitUntilDisplayed("1 Selected")
    }

    @Test
    fun editorReaderPreview_updatesAfterAccentEdit() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()
        openCreateThemeEditor("Preview Render")

        waitUntilTagExists("theme_editor_preview_static", useUnmergedTree = true)
        composeRule.mainClock.autoAdvance = false
        val initial = try {
            captureBitmapByTag("theme_editor_preview_static", useUnmergedTree = true)
        } finally {
            composeRule.mainClock.advanceTimeBy(600)
        }
        val delayed = try {
            captureBitmapByTag("theme_editor_preview_static", useUnmergedTree = true)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
        assertBitmapsEqual(initial, delayed, "static editor preview")

        composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
        setSliderProgress("custom_theme_primary_picker_hue", 180f)
        setSliderProgress("custom_theme_primary_picker_saturation", 1f)
        setSliderProgress("custom_theme_primary_picker_value", 1f)
        closeColorPicker()

        val updated = captureBitmapByTag("theme_editor_preview_static", useUnmergedTree = true)
        assertBitmapsDifferent(initial, updated, "editor accent change")
    }

    @Test
    fun editTheme_afterSwipe_usesVisibleThemeChromeInsteadOfStaleActiveTheme() {
        val customTheme = sampleCustomTheme(
            id = "$CustomThemeIdPrefix-editor-visible",
            name = "Crimson Custom",
            primary = 0xFF9F0712,
            background = 0xFF0B0A10,
        )
        runBlocking {
            resetSettings(theme = "forest", customThemes = listOf(customTheme))
        }

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
            composeRule.onNodeWithTag("appearance_theme_card_${customTheme.id}").assertIsSelected()
            composeRule.onNodeWithContentDescription("Modify").performClick()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        waitUntilTagExists("theme_editor_sheet")
        waitUntilStateDescriptionContains("theme_editor_sheet", "chromeTheme=${customTheme.id}")
    }

    private suspend fun resetSettings(
        theme: String = "light",
        customThemes: List<CustomTheme> = emptyList(),
    ) {
        settingsManager.updateGlobalSettings(
            GlobalSettings(
                fontSize = 18,
                fontType = "serif",
                theme = theme,
                customThemes = customThemes,
                lineHeight = 1.6f,
                horizontalPadding = 16,
                showScrubber = false,
                showSystemBar = false,
                allowBlankCovers = false,
            ),
        )
    }

    private fun sampleCustomTheme(
        id: String = "$CustomThemeIdPrefix-preview-specimen",
        name: String = "Preview Specimen Custom",
        primary: Long = 0xFFEF4444,
        background: Long = 0xFF0F172A,
    ): CustomTheme {
        return CustomTheme(
            id = id,
            name = name,
            palette = generatePaletteFromBase(
                primary = primary,
                background = background,
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

    private fun openAppearanceSection() {
        composeRule.onNodeWithTag("settings_section_appearance").performClick()
        waitUntilTagExists("create_custom_theme_button")
    }

    private fun openThemeGallery() {
        composeRule.onNodeWithContentDescription("Gallery").performClick()
        waitUntilTagExists("theme_gallery_grid")
    }

    private fun openCreateThemeEditor(name: String) {
        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput(name)
    }

    private fun closeColorPicker() {
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()
    }

    private fun galleryThemeTag(themeId: String): String = "theme_gallery_preview_$themeId"

    private fun setSliderProgress(tag: String, value: Float) {
        composeRule.onNodeWithTag(tag)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { setProgress ->
                setProgress(value)
            }
        composeRule.waitForIdle()
    }

    private fun setSliderProgressByContentDescription(description: String, value: Float) {
        composeRule.onNodeWithContentDescription(description)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { setProgress ->
                setProgress(value)
            }
        composeRule.waitForIdle()
    }

    private fun waitUntilDisplayed(text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTagExists(
        tag: String,
        timeoutMillis: Long = 10_000,
        useUnmergedTree: Boolean = false,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            tagCount(tag, useUnmergedTree = useUnmergedTree) > 0
        }
    }

    private fun waitUntilActiveThemeIs(themeId: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                runBlocking { settingsManager.globalSettings.first().theme == themeId }
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

    private fun waitUntilStateDescriptionContains(
        tag: String,
        text: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            stateDescriptionForTag(tag).contains(text)
        }
    }

    private fun captureBitmapByTag(tag: String, useUnmergedTree: Boolean = false): Bitmap {
        return composeRule.onNodeWithTag(tag, useUnmergedTree = useUnmergedTree)
            .captureToImage()
            .asAndroidBitmap()
            .copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun assertBitmapsDifferent(before: Bitmap, after: Bitmap, reason: String) {
        assertFalse("Expected preview bitmap to change after $reason", before.sameAs(after))
    }

    private fun assertBitmapsEqual(before: Bitmap, after: Bitmap, reason: String) {
        assertTrue("Expected preview bitmap to stay stable for $reason", before.sameAs(after))
    }

    private fun stateDescriptionForTag(tag: String): String {
        val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        return node.config.getOrElse(SemanticsProperties.StateDescription) { "" }
    }

    private fun tagCount(tag: String, useUnmergedTree: Boolean = false): Int {
        return runCatching {
            composeRule.onNodeWithTag(tag, useUnmergedTree = useUnmergedTree).fetchSemanticsNode()
            1
        }.getOrDefault(0)
    }

    private fun scrollGalleryUntilTagDisplayed(tag: String, maxSwipes: Int = 12): Boolean {
        repeat(maxSwipes) {
            val visible = runCatching {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                true
            }.getOrDefault(false)
            if (visible) return true
            composeRule.onNodeWithTag("theme_gallery_grid").performTouchInput {
                swipe(
                    start = Offset(width / 2f, height * 0.82f),
                    end = Offset(width / 2f, height * 0.22f),
                    durationMillis = 250,
                )
            }
            composeRule.waitForIdle()
        }
        return runCatching {
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            true
        }.getOrDefault(false)
    }
}
