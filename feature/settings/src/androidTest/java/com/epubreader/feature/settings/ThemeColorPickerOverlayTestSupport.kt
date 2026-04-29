package com.epubreader.feature.settings

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.epubreader.core.model.formatThemeColor
import com.epubreader.MainActivity
import com.epubreader.core.model.generatePaletteFromBase
import com.epubreader.core.model.parseThemeColorOrNull
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule

abstract class ThemeColorPickerOverlayTestSupport {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private val committedHexes = mutableListOf<String>()

    protected fun launchGuidedOverlay(
        initialHex: String,
        safeZoneGate: CompletableDeferred<Unit>,
    ) {
        launchGuidedOverlay(
            initialHex = initialHex,
            safeZoneGatesByHueBucket = mapOf(initialHex.toOverlayHueBucket() to safeZoneGate),
        )
    }

    protected fun launchGuidedOverlay(
        initialHex: String,
        safeZoneGatesByHueBucket: Map<Int, CompletableDeferred<Unit>>,
    ) {
        committedHexes.clear()
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ThemeColorPickerOverlay(
                        label = "Overlay",
                        initialValue = initialHex,
                        testTagPrefix = "overlay",
                        isGuided = true,
                        onDismiss = {},
                        onPreviewValueChange = { rawHex ->
                            ThemeColorPickerPreviewResult(
                                resolvedHex = rawHex,
                                wasAdjusted = false,
                            )
                        },
                        onValueChange = { rawHex ->
                            committedHexes += rawHex
                            ThemeEditorColorEditResult(
                                updatedDraft = ThemeEditorDraft.fromPalette(
                                    name = "Overlay",
                                    palette = generatePaletteFromBase(
                                        primary = parseThemeColorOrNull(rawHex) ?: 0xFFFF0000,
                                        background = 0xFF000000,
                                    ),
                                    mode = ThemeEditorMode.BASIC,
                                ),
                                resolvedHex = rawHex,
                                wasAdjusted = false,
                            )
                        },
                        safeZoneResolverOverride = ThemeColorPickerSafeZoneResolver { hue, preview ->
                            safeZoneGatesByHueBucket[hue.toSafeZoneHueBucket()]?.await()
                            buildGuidedSafeZone(
                                hue = hue,
                                previewColor = preview,
                            )
                        },
                    )
                }
            }
        }
    }

    protected fun launchUnguidedOverlay(initialHex: String) {
        committedHexes.clear()
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    ThemeColorPickerOverlay(
                        label = "Overlay",
                        initialValue = initialHex,
                        testTagPrefix = "overlay",
                        isGuided = false,
                        onDismiss = {},
                        onValueChange = { rawHex ->
                            committedHexes += rawHex
                            ThemeEditorColorEditResult(
                                updatedDraft = ThemeEditorDraft.fromPalette(
                                    name = "Overlay",
                                    palette = generatePaletteFromBase(
                                        primary = parseThemeColorOrNull(rawHex) ?: 0xFF4F46E5,
                                        background = 0xFF000000,
                                    ),
                                    mode = ThemeEditorMode.BASIC,
                                ),
                                resolvedHex = rawHex,
                                wasAdjusted = false,
                            )
                        },
                    )
                }
            }
        }
    }

    protected fun replaceHexInput(testTagPrefix: String, value: String) {
        composeRule.onNodeWithTag("${testTagPrefix}_picker_hex").performTextClearance()
        composeRule.onNodeWithTag("${testTagPrefix}_picker_hex").performTextInput(value)
        composeRule.waitForIdle()
    }

    protected fun switchPickerInputMode(testTagPrefix: String) {
        composeRule.onNodeWithTag("${testTagPrefix}_picker_mode_toggle").performClick()
        composeRule.waitForIdle()
    }

    protected fun waitUntilTagExists(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun waitUntilTagGone(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    protected fun assertPreviewHex(previewTag: String, hex: String) {
        composeRule.onNodeWithTag(previewTag).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(hex),
            ),
        )
    }

    protected fun waitUntilPreviewHex(
        previewTag: String,
        hex: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                assertPreviewHex(previewTag, hex)
                true
            }.getOrDefault(false)
        }
    }

    protected fun assertSpectrumState(tag: String, expectedState: String) {
        composeRule.onNodeWithTag(tag).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                expectedState,
            ),
        )
    }

    protected fun waitUntilSpectrumState(
        tag: String,
        expectedState: String,
        timeoutMillis: Long = 10_000,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            runCatching {
                assertSpectrumState(tag, expectedState)
                true
            }.getOrDefault(false)
        }
    }

    protected fun assertNoCommitRecorded() {
        composeRule.runOnIdle {
            check(committedHexes.isEmpty()) {
                "Expected no picker commit, but recorded: $committedHexes"
            }
        }
    }

    protected fun assertLastCommittedHex(expectedHex: String) {
        composeRule.runOnIdle {
            check(committedHexes.lastOrNull() == expectedHex) {
                "Expected last committed hex $expectedHex but recorded $committedHexes"
            }
        }
    }

    protected fun setSpectrumPoint(
        tag: String,
        saturation: Float,
        value: Float,
    ) {
        val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val x = overlayTouchCoordinate(bounds.width, saturation)
        val y = overlayTouchCoordinate(bounds.height, 1f - value)
        composeRule.onNodeWithTag(tag).performTouchInput {
            click(Offset(x, y))
        }
        composeRule.waitForIdle()
    }

    protected fun setSliderProgress(tag: String, value: Float) {
        composeRule.onNodeWithTag(tag)
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(value)
            }
    }

    protected fun completedSafeZoneGate(): CompletableDeferred<Unit> {
        return CompletableDeferred<Unit>().apply { complete(Unit) }
    }

    protected fun expectedGuidedPreviewHex(
        hue: Float,
        saturation: Float,
        value: Float,
    ): String {
        val projected = buildGuidedSafeZone(
            hue = hue,
            previewColor = { rawHex ->
                ThemeColorPickerPreviewResult(
                    resolvedHex = rawHex,
                    wasAdjusted = false,
                )
            },
        ).project(
            ThemeColorPickerPoint(
                saturation = saturation.coerceIn(0f, 1f),
                value = value.coerceIn(0f, 1f),
            ),
        )
        return formatThemeColor(
            ThemeColorPickerHsv(
                hue = hue,
                saturation = projected.saturation,
                value = projected.value,
            ).toColorLong(),
        )
    }
}

private fun overlayTouchCoordinate(size: Float, fraction: Float): Float {
    if (size <= 1f) {
        return 0f
    }
    return (fraction.coerceIn(0f, 1f) * (size - 1f)).coerceIn(0f, size - 1f)
}

private fun String.toOverlayHueBucket(): Int {
    val color = parseThemeColorOrNull(this) ?: return 0
    return color.toThemeColorPickerHsv().hue.toSafeZoneHueBucket()
}
