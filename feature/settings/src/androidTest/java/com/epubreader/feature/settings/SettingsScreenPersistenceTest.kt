package com.epubreader.feature.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Ocean")
        composeRule.onNodeWithContentDescription("Save").performClick()

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
        waitUntilTagExists("custom_theme_name")
        composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
        composeRule.onNodeWithTag("custom_theme_name").performTextInput("Sunset")
        composeRule.onNodeWithTag("custom_theme_primary_swatch").performClick()

        setSliderProgress("custom_theme_primary_picker_hue", 0f)
        setSliderProgress("custom_theme_primary_picker_saturation", 1f)
        setSliderProgress("custom_theme_primary_picker_value", 1f)

        waitUntilTextContains("custom_theme_primary", "#FF0000")
        composeRule.onNodeWithContentDescription("Save").performClick()

        composeRule.waitUntil(10_000) {
            val settings = runBlocking { settingsManager.globalSettings.first() }
            settings.customThemes.any { theme ->
                theme.name == "Sunset" && formatThemeColor(theme.palette.primary) == "#FF0000"
            }
        }
    }

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

    @Test
    fun themeGallery_hiddenThemeUpdates_appearOnReopen() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        openThemeGallery()
        closeThemeGallery()

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
        closeThemeGallery()

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
        closeThemeGallery()

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
        closeThemeGallery()

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
        closeThemeGallery()

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

    @Test
    fun librarySection_doesNotShowDeprecatedReaderContentEngineSelector() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openLibrarySection()
        composeRule.onAllNodesWithText("Reader Content Engine").assertCountEquals(0)
        composeRule.onAllNodesWithTag("reader_content_engine_selector").assertCountEquals(0)
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
}
