package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderControlsSettingsUpdateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun controlsApplyEachChangeToLatestPersistedSettingsState() {
        var persistedSettings by mutableStateOf(GlobalSettings())

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                val listState = rememberLazyListState()
                MaterialTheme {
                    ReaderControls(
                        settings = persistedSettings,
                        onSettingsChange = { transform ->
                            persistedSettings = transform(persistedSettings)
                        },
                        themeColors = getThemeColors(persistedSettings.theme),
                        onNavigatePrev = {},
                        onNavigateNext = {},
                        listState = listState,
                        itemCount = 8,
                        currentChapterIndex = 0,
                        totalChapters = 3,
                        sectionLabel = "Chapter",
                    )
                }
            }
        }

        composeRule.onNodeWithTag("reader_controls_tab_font").performClick()
        composeRule.onNodeWithText("Karla").performScrollTo().performClick()

        composeRule.onNodeWithTag("reader_controls_tab_theme").performClick()
        composeRule.onNodeWithContentDescription("Theme OLED").performScrollTo().performClick()

        composeRule.waitForIdle()

        assertEquals("karla", persistedSettings.fontType)
        assertEquals("oled", persistedSettings.theme)
    }
}
