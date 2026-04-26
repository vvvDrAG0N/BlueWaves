package com.epubreader.feature.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SettingsScreenSectionPersistenceTest : SettingsScreenPersistenceTestBase() {

    @Test
    fun systemBack_fromSettingsSectionList_callsOnBack() {
        val backInvocations = AtomicInteger(0)

        launchSettingsScreen(
            onBack = { backInvocations.incrementAndGet() },
        )
        waitUntilDisplayed("Settings")

        pressBack()

        composeRule.waitUntil(5_000) { backInvocations.get() == 1 }
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
}
