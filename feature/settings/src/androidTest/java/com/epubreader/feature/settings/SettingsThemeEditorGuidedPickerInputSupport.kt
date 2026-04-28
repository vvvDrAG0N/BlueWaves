package com.epubreader.feature.settings

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

internal fun SettingsThemeEditorGuidedPickerTest.selectPickerInputMode(
    testTagPrefix: String,
    mode: String,
) {
    val targetTag = when (mode.lowercase()) {
        "hex" -> "${testTagPrefix}_picker_hex"
        "rgb" -> "${testTagPrefix}_picker_rgb_red"
        else -> error("Unsupported picker input mode: $mode")
    }
    val targetVisible = runCatching {
        composeRule.onNodeWithTag(targetTag).fetchSemanticsNode()
        true
    }.getOrDefault(false)
    if (!targetVisible) {
        composeRule.onNodeWithTag("${testTagPrefix}_picker_mode_toggle").performClick()
        composeRule.waitForIdle()
    }
}
