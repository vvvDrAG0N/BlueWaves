package com.epubreader.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenThemeGalleryPersistenceTest : SettingsScreenPersistenceTestBase() {

    @Test
    fun themeGallery_afterSwipe_usesLiveAppearanceThemeForSelectionAndChrome() {
        launchSettingsScreen()
        waitUntilDisplayed("Settings")
        openAppearanceSection()

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("appearance_theme_pager").performTouchInput {
                swipe(
                    start = androidx.compose.ui.geometry.Offset(width * 0.85f, height / 2f),
                    end = androidx.compose.ui.geometry.Offset(width * 0.1f, height / 2f),
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
}
