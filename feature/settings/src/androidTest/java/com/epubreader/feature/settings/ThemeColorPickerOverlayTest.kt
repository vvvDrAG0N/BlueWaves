package com.epubreader.feature.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeColorPickerOverlayTest : ThemeColorPickerOverlayTestSupport() {

    @Test
    fun guidedPicker_coldOpenLoading_blocksInteractionAndSave() {
        val readyGate = CompletableDeferred<Unit>()
        launchGuidedOverlay(
            initialHex = "#FF0000",
            safeZoneGate = readyGate,
        )

        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()
        assertSpectrumState("overlay_picker_spectrum", "loading")
        setSpectrumPoint("overlay_picker_spectrum", saturation = 0.15f, value = 0.20f)
        assertPreviewHex("overlay_picker_preview", "#FF0000")
        assertNoCommitRecorded()
        composeRule.onNodeWithTag("overlay_picker_hex").assertIsEnabled()
        replaceHexInput("overlay", "FF0000")
        assertPreviewHex("overlay_picker_preview", "#FF0000")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()
        switchPickerInputMode("overlay")
        composeRule.onNodeWithTag("overlay_picker_rgb_red").assertIsEnabled()

        readyGate.complete(Unit)
        waitUntilTagExists("overlay_picker_safe_zone")
        assertSpectrumState("overlay_picker_spectrum", "ready")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsEnabled()
    }

    @Test
    fun guidedPicker_saveAttemptDuringLoading_isBlocked() {
        val readyGate = CompletableDeferred<Unit>()
        launchGuidedOverlay(
            initialHex = "#FF0000",
            safeZoneGate = readyGate,
        )

        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("overlay_picker_save").performClick()
        assertNoCommitRecorded()
    }

    @Test
    fun guidedPicker_typingNewHueDuringInitialLoad_keepsSaveBlockedUntilTypedHueIsReady() {
        val initialHueGate = CompletableDeferred<Unit>()
        val typedHueGate = CompletableDeferred<Unit>()
        launchGuidedOverlay(
            initialHex = "#FF0000",
            safeZoneGatesByHueBucket = mapOf(
                0 to initialHueGate,
                120 to typedHueGate,
            ),
        )

        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()
        assertSpectrumState("overlay_picker_spectrum", "loading")

        replaceHexInput("overlay", "00FF00")
        assertPreviewHex("overlay_picker_preview", "#00FF00")
        assertSpectrumState("overlay_picker_spectrum", "loading")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()

        initialHueGate.complete(Unit)
        composeRule.waitForIdle()
        assertSpectrumState("overlay_picker_spectrum", "loading")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()

        typedHueGate.complete(Unit)
        waitUntilTagExists("overlay_picker_safe_zone")
        assertSpectrumState("overlay_picker_spectrum", "ready")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsEnabled()
        assertPreviewHex("overlay_picker_preview", "#00FF00")
    }

    @Test
    fun guidedPicker_readyToUncachedHueBucket_returnsToLoadingUntilResolvedAgain() {
        val queuedHue = 120f
        val queuedPreviewHex = expectedGuidedPreviewHex(
            hue = queuedHue,
            saturation = 1f,
            value = 1f,
        )
        val initialBucketGate = completedSafeZoneGate()
        val nextBucketGate = CompletableDeferred<Unit>()
        launchGuidedOverlay(
            initialHex = "#FF0000",
            safeZoneGatesByHueBucket = mapOf(
                0 to initialBucketGate,
                120 to nextBucketGate,
            ),
        )

        waitUntilTagExists("overlay_picker_safe_zone")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsEnabled()
        assertSpectrumState("overlay_picker_spectrum", "ready")
        assertPreviewHex("overlay_picker_preview", "#FF0000")

        setSliderProgress("overlay_picker_hue", queuedHue)
        waitUntilSpectrumState("overlay_picker_spectrum", "loading")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()
        setSpectrumPoint("overlay_picker_spectrum", saturation = 0.85f, value = 0.35f)
        assertPreviewHex("overlay_picker_preview", "#FF0000")
        assertNoCommitRecorded()

        nextBucketGate.complete(Unit)

        waitUntilPreviewHex("overlay_picker_preview", queuedPreviewHex)
        assertSpectrumState("overlay_picker_spectrum", "ready")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsEnabled()
        assertPreviewHex("overlay_picker_preview", queuedPreviewHex)
        composeRule.onNodeWithTag("overlay_picker_save").performClick()
        assertLastCommittedHex(queuedPreviewHex)
    }

    @Test
    fun guidedPicker_typingDuringQueuedHueLoad_cancelsOlderQueuedHue() {
        val queuedHueGate = CompletableDeferred<Unit>()
        val typedHueGate = CompletableDeferred<Unit>()
        launchGuidedOverlay(
            initialHex = "#FF0000",
            safeZoneGatesByHueBucket = mapOf(
                0 to completedSafeZoneGate(),
                120 to queuedHueGate,
                240 to typedHueGate,
            ),
        )

        waitUntilTagExists("overlay_picker_safe_zone")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsEnabled()

        setSliderProgress("overlay_picker_hue", 120f)
        waitUntilSpectrumState("overlay_picker_spectrum", "loading")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()
        assertPreviewHex("overlay_picker_preview", "#FF0000")

        replaceHexInput("overlay", "0000FF")
        assertPreviewHex("overlay_picker_preview", "#0000FF")
        assertSpectrumState("overlay_picker_spectrum", "loading")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()

        queuedHueGate.complete(Unit)
        composeRule.waitForIdle()
        assertPreviewHex("overlay_picker_preview", "#0000FF")
        assertSpectrumState("overlay_picker_spectrum", "loading")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()

        typedHueGate.complete(Unit)
        waitUntilTagExists("overlay_picker_safe_zone")
        assertSpectrumState("overlay_picker_spectrum", "ready")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsEnabled()
        assertPreviewHex("overlay_picker_preview", "#0000FF")
    }

    @Test
    fun guidedPicker_readySave_recordsCommit() {
        val readyGate = CompletableDeferred<Unit>()
        launchGuidedOverlay(
            initialHex = "#FF0000",
            safeZoneGate = readyGate,
        )

        readyGate.complete(Unit)
        waitUntilTagExists("overlay_picker_safe_zone")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsEnabled()
        composeRule.onNodeWithTag("overlay_picker_save").performClick()
        assertLastCommittedHex("#FF0000")
    }

    @Test
    fun pickerHexField_focused_backShowsExitDialogOnFirstPress() {
        launchUnguidedOverlay(initialHex = "#4F46E5")

        composeRule.onNodeWithTag("overlay_picker_hex").performClick()
        replaceHexInput("overlay", "12AB34")

        pressBack()
        waitUntilTagExists("overlay_picker_exit_dialog")
    }
}
