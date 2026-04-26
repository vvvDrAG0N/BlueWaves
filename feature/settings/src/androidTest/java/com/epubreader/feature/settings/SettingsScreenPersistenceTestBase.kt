package com.epubreader.feature.settings

import android.content.Context
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.epubreader.MainActivity
import com.epubreader.core.model.CustomTheme
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule

abstract class SettingsScreenPersistenceTestBase {

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

    internal suspend fun resetSettings(customThemes: List<CustomTheme> = emptyList()) {
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

    internal fun launchSettingsScreen(
        onBack: () -> Unit = {},
    ) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        onBack = onBack,
                    )
                }
            }
        }
    }
}
