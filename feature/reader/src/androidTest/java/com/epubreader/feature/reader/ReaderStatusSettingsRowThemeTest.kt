package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.GlobalSettings
import com.epubreader.feature.reader.internal.ui.ReaderReadingControlsSection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ReaderStatusSettingsRowThemeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun readingInfoRow_usesReaderThemeColors() {
        val expectedPrimary = Color(0xFFD13B00)
        val expectedForeground = Color(0xFF0E8A3A)

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ReaderReadingControlsSection(
                        settings = GlobalSettings(),
                        onSettingsChange = {},
                        themeColors = ReaderTheme(
                            background = Color(0xFF101010),
                            foreground = expectedForeground,
                            variantForeground = Color(0xFF6B6B6B),
                            primary = expectedPrimary,
                        ),
                    )
                }
            }
        }

        composeRule.waitForIdle()

        assertNodeContainsColor("Reading Info", expectedForeground)
        assertNodeContainsColor("Time", expectedPrimary)
    }

    private fun assertNodeContainsColor(
        text: String,
        expected: Color,
        tolerance: Float = 0.08f,
    ) {
        val pixelMap = composeRule.onNodeWithText(text)
            .captureToImage()
            .toPixelMap()

        val hasExpectedColor = (0 until pixelMap.width).any { x ->
            (0 until pixelMap.height).any { y ->
                val pixel = pixelMap[x, y]
                pixel.alpha > 0.3f && pixel.closeTo(expected, tolerance)
            }
        }

        assertTrue(
            "Expected \"$text\" to contain color $expected",
            hasExpectedColor,
        )
    }

    private fun Color.closeTo(
        other: Color,
        tolerance: Float,
    ): Boolean {
        return abs(red - other.red) <= tolerance &&
            abs(green - other.green) <= tolerance &&
            abs(blue - other.blue) <= tolerance
    }
}
