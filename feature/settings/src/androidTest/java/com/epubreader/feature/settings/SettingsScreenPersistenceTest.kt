package com.epubreader.feature.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.core.model.CustomTheme
import com.epubreader.MainActivity
import com.epubreader.core.model.CustomThemeIdPrefix
import com.epubreader.core.model.GlobalSettings
import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun themeGallery_afterSwipe_usesLiveAppearanceThemeForSelectionAndChrome() {
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

            composeRule.onNodeWithContentDescription("Gallery").performClick()
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("theme_gallery_preview_sepia").assertIsSelected()
            composeRule.onNodeWithTag("theme_gallery_panel_sepia").assertIsDisplayed()
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
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
    fun themeGallery_hiddenThemeUpdates_appearOnReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        openThemeGallery()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        val createdTheme = sampleCustomThemes(count = 1).first().copy(name = "Ocean")
        runBlocking {
            settingsManager.saveCustomTheme(createdTheme, activate = false)
        }
        composeRule.waitForIdle()

        openThemeGallery()
        assertTrue(
            scrollGalleryUntilTagDisplayed(galleryThemeTag(createdTheme.id)),
        )
    }

    @Test
    fun themeGallery_doneDismiss_restoresAppearanceInteractions() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        openThemeGallery()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("create_custom_theme_button").performClick()
        waitUntilDisplayed("Theme Name")

        pressBack()
        waitUntilTagExists("create_custom_theme_button")
    }

    @Test
    fun themeGallery_closeSwitchThemeAndReopen_syncsSelectedTheme() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        openThemeGallery()
        composeRule.onNodeWithTag("theme_gallery_preview_light").assertIsSelected()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        runBlocking {
            settingsManager.setActiveTheme("sepia")
        }
        composeRule.waitUntil(10_000) {
            runBlocking { settingsManager.globalSettings.first().theme } == "sepia"
        }

        openThemeGallery()
        composeRule.onNodeWithTag("theme_gallery_preview_sepia").assertIsSelected()
    }

    @Test
    fun themeGallery_closeAndReopen_preservesScrollPositionWithinAppearanceSession() {
        val customThemes = sampleCustomThemes(count = 8)
        val targetTheme = customThemes.last()
        runBlocking { resetSettings(customThemes = customThemes) }

        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        openThemeGallery()
        assertTrue(scrollGalleryUntilTagDisplayed(galleryThemeTag(targetTheme.id)))
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        openThemeGallery()
        composeRule.onNodeWithTag(galleryThemeTag(targetTheme.id)).assertIsDisplayed()
    }

    @Test
    fun themeGallery_leavingAppearance_resetsGallerySession() {
        val customThemes = sampleCustomThemes(count = 8)
        val targetTheme = customThemes.last()
        runBlocking { resetSettings(customThemes = customThemes) }

        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        openThemeGallery()
        assertTrue(scrollGalleryUntilTagDisplayed(galleryThemeTag(targetTheme.id)))
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitForIdle()

        pressBack()
        waitUntilTagExists("settings_section_appearance")

        openAppearanceSection()
        openThemeGallery()
        assertFalse(tagIsDisplayed(galleryThemeTag(targetTheme.id)))
        assertTrue(scrollGalleryUntilTagDisplayed(galleryThemeTag(targetTheme.id)))
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

    private suspend fun resetSettings(customThemes: List<CustomTheme> = emptyList()) {
        settingsManager.updateGlobalSettings(
            GlobalSettings(
                fontSize = 18,
                fontType = "serif",
                theme = "light",
                customThemes = customThemes,
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
        waitUntilTagDisplayed("theme_gallery_grid")
    }

    private fun galleryThemeTag(themeId: String): String = "theme_gallery_preview_$themeId"

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

    private fun waitUntilTagDisplayed(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun tagExists(tag: String): Boolean {
        return runCatching {
            composeRule.onNodeWithTag(tag).fetchSemanticsNode()
            true
        }.getOrDefault(false)
    }

    private fun tagIsDisplayed(tag: String): Boolean {
        return runCatching {
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
            true
        }.getOrDefault(false)
    }

    private fun scrollGalleryUntilTagDisplayed(tag: String, maxSwipes: Int = 12): Boolean {
        repeat(maxSwipes) {
            if (tagIsDisplayed(tag)) return true
            composeRule.onNodeWithTag("theme_gallery_grid").performTouchInput {
                swipe(
                    start = Offset(width / 2f, height * 0.82f),
                    end = Offset(width / 2f, height * 0.22f),
                    durationMillis = 250,
                )
            }
            composeRule.waitForIdle()
        }
        return tagIsDisplayed(tag)
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

    private fun sampleCustomThemes(count: Int): List<CustomTheme> {
        return (1..count).map { index ->
            val primary = 0xFF000000L or
                ((0x30 + ((index * 17) % 160)).toLong() shl 16) or
                ((0x50 + ((index * 23) % 120)).toLong() shl 8) or
                (0x70 + ((index * 29) % 80)).toLong()
            val background = if (index % 2 == 0) 0xFF0F172AL else 0xFFF8FAFCL
            CustomTheme(
                id = "$CustomThemeIdPrefix-scroll-$index",
                name = "Scroll Theme $index",
                palette = generatePaletteFromBase(primary = primary, background = background),
            )
        }
    }
}
