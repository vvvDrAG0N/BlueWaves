package com.epubreader.feature.settings

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.epubreader.MainActivity
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule

abstract class SettingsThemeEditorGuidedPickerTestBase {

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
}
