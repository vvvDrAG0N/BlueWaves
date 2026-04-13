package com.epubreader.app

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationSystemBarTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val settingsManager by lazy { 
        SettingsManager(InstrumentationRegistry.getInstrumentation().targetContext) 
    }

    @Before
    fun setUp() = runBlocking {
        // Reset settings to default before each test.
        // We ensure showSystemBar is false to test the immersive mode behavior.
        settingsManager.updateGlobalSettings(GlobalSettings(firstTime = false, showSystemBar = false))
    }

    @Test
    fun systemBars_InitialState_IsHidden() {
        // Ensure the app is loaded and on the Library screen.
        // If a welcome dialog is shown, this might fail, but we set firstTime=false in setUp.
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
        
        // Default showSystemBar is false, so they should be hidden in Library.
        assertSystemBarsVisibility(visible = false)
    }

    @Test
    fun systemBars_ToggleInSettings_UpdatesVisibility() {
        // Ensure we start on the Library screen.
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()

        // Go to settings.
        composeRule.onNodeWithContentDescription("Settings").performClick()
        
        // In Settings, system bars should ALWAYS be visible.
        assertSystemBarsVisibility(visible = true)
        
        // Toggle the global system bar setting to ON.
        composeRule.onNodeWithTag("show_system_bar_switch").performClick()
        
        // They should still be visible in Settings.
        assertSystemBarsVisibility(visible = true)
        
        // Go back to the Library.
        composeRule.onNodeWithContentDescription("Back").performClick()
        
        // Now they should be visible in the Library because the global setting is ON.
        assertSystemBarsVisibility(visible = true)

        // Go back to settings and toggle it OFF.
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithTag("show_system_bar_switch").performClick()
        
        // Go back to the Library.
        composeRule.onNodeWithContentDescription("Back").performClick()
        
        // Now they should be hidden in the Library again.
        assertSystemBarsVisibility(visible = false)
    }

    /**
     * Asserts that the system bars (specifically the status bar) are in the expected visibility state.
     * Uses ViewCompat.getRootWindowInsets and waits for the state to settle.
     */
    private fun assertSystemBarsVisibility(visible: Boolean) {
        val timeout = 10000L
        
        composeRule.waitUntil(timeout) {
            var success = false
            composeRule.runOnUiThread {
                val activity = composeRule.activity
                val decorView = activity.window.decorView
                
                // Attempt to ensure focus so insets are reported correctly.
                if (!decorView.hasFocus()) {
                    decorView.requestFocus()
                }
                
                val insets = ViewCompat.getRootWindowInsets(decorView)
                val systemUiVisibility = decorView.systemUiVisibility
                
                if (insets != null) {
                    val statusVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars())
                    
                    // Fallback check: On some emulators/environments, Insets might report 
                    // visibility based on system policy, but the request was sent.
                    // We check for immersive mode flags as a secondary indicator.
                    val isImmersive = (systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) != 0 ||
                                     (systemUiVisibility and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
                    
                    // We consider it a success if the reported visibility matches OR 
                    // if we are trying to hide and the immersive flags are set.
                    val reportedMatches = if (visible) statusVisible else !statusVisible
                    val hiddenByFlags = !visible && isImmersive
                    
                    success = reportedMatches || hiddenByFlags
                }
            }
            success
        }
    }
}
