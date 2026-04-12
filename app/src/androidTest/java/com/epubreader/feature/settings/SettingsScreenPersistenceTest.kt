package com.epubreader.feature.settings

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
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

    private val settingsManager by lazy { SettingsManager(composeRule.activity) }

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
        runBlocking {
            launchSettingsScreen()
            waitUntilDisplayed("Settings")

            composeRule.onNodeWithContentDescription("Font Size Slider")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    setProgress(26f)
                }
            composeRule.onNodeWithText("Karla").performClick()
            composeRule.onNodeWithContentDescription("Theme oled").performClick()

            composeRule.waitUntil(10_000) {
                val settings = runBlocking { settingsManager.globalSettings.first() }
                settings.fontSize == 26 &&
                    settings.fontType == "karla" &&
                    settings.theme == "oled"
            }

            launchSettingsScreen()

            waitUntilDisplayed("Font Size: 26sp")
            composeRule.onNodeWithText("Karla").assertIsSelected()
            composeRule.onNodeWithContentDescription("Theme oled").assertIsSelected()
            composeRule.onNodeWithText("Font Family").assertIsDisplayed()
        }
    }

    private suspend fun resetSettings() {
        settingsManager.updateGlobalSettings(
            GlobalSettings(
                fontSize = 18,
                fontType = "serif",
                theme = "light",
                lineHeight = 1.6f,
                horizontalPadding = 16,
                showScrubber = false,
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
}
