# Theme Spectrum Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the slider-first theme picker with a spectrum-based picker that teaches guided balancing through a visible exact-zone overlay, keeps HEX persistence intact, and shows RGB as the user-facing color token.

**Architecture:** Keep the durable theme model HEX-backed through `ThemeEditorDraft`, `ThemeEditorColorEditResult`, and `SettingsManager`. Add a preview-only picker guidance path so Basic and Extended can render a result swatch, exact-zone overlay, and outside-zone explanation without mutating the draft until `Done`. Extract the spectrum and hue-strip drawing into focused helper files because `SettingsThemeColorPicker.kt` is already near the size threshold, and convert the editor grid to display `RGB(...)` while still passing raw HEX into the picker.

**Tech Stack:** Kotlin, Jetpack Compose, Compose Canvas, Android instrumentation tests, JVM unit tests, JUnit4, Gradle

---

## File Structure

- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
  - Hold RGB formatting, exact-zone metadata, and preview-only picker state used by the dialog.
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
  - Hold the spectrum canvas, hue strip, exact-zone overlay, and guided veil drawing to keep `SettingsThemeColorPicker.kt` focused.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`
  - Expand the picker session contract and add a preview-only color resolution path.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
  - Replace the three visible sliders with the new spectrum UI while preserving cancel-vs-commit behavior.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
  - Pass the new preview callback into the picker and keep draft writes commit-only.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorSections.kt`
  - Carry both raw HEX and display RGB values in the editor field model.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeStudioComponents.kt`
  - Render the RGB display token in the color grid cells.
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
  - Add pure tests for RGB formatting, exact-zone metadata, and preview resolution.
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`
  - Keep the existing guided rebalance contract covered after the preview helper is introduced.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
  - Add runtime coverage for the exact-zone overlay, guided veil, advanced-mode absence, and result readout behavior.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorModeInferenceTest.kt`
  - Update mode-persistence assertions from HEX text to RGB text.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`
  - Update the saved-color assertion path to expect RGB labels in the editor while HEX persists underneath.

Workspace guard:

- The current worktree already contains unrelated reader changes and agent-memory edits. Do not stage or revert anything outside the scoped `feature/settings` files and the plan/spec artifacts for this picker work.

### Task 1: Add the failing pure guidance and RGB formatting tests

**Files:**
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`

- [ ] **Step 1: Write the failing preview and formatting tests**

Create `ThemeColorPickerGuidanceTest.kt` with these focused assertions:

```kotlin
package com.epubreader.feature.settings

import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.generatePaletteFromGuidedInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorPickerGuidanceTest {

    @Test
    fun rgbLabel_formatsOpaqueColor() {
        assertEquals("RGB(79, 70, 229)", 0xFF4F46E5.toRgbLabel())
    }

    @Test
    fun guidedPreview_basicMode_exposesExactZoneMetadata() {
        val preview = basicDraft().previewColorEdit(
            fieldKey = "accent",
            rawHex = "#4F46E5",
            guided = true,
        )

        assertNotNull(preview.exactZone)
        assertTrue(preview.isInsideExactZone)
        assertEquals("RGB(79, 70, 229)", preview.resultRgbLabel)
    }

    @Test
    fun guidedPreview_basicMode_outsideZone_marksAdjustedAndPreservesChosenValue() {
        val preview = basicDraft().previewColorEdit(
            fieldKey = "accent",
            rawHex = "#FF4A00",
            guided = true,
        )

        assertFalse(preview.isInsideExactZone)
        assertTrue(preview.wasAdjusted)
        assertEquals("RGB(255, 74, 0)", preview.chosenRgbLabel)
    }

    @Test
    fun advancedPreview_hasNoExactZoneAndKeepsLiteralColor() {
        val preview = advancedDraft().previewColorEdit(
            fieldKey = "favorite_accent",
            rawHex = "#FF0000",
            guided = false,
        )

        assertNull(preview.exactZone)
        assertTrue(preview.isInsideExactZone)
        assertFalse(preview.wasAdjusted)
        assertEquals("RGB(255, 0, 0)", preview.resultRgbLabel)
    }

    private fun basicDraft(): ThemeEditorDraft {
        return ThemeEditorDraft.fromPalette(
            name = "Basic",
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFFF5F1E8,
                    readerLinked = true,
                ),
            ),
            mode = ThemeEditorMode.BASIC,
            readerLinked = true,
            legacyIsAdvanced = true,
        )
    }

    private fun advancedDraft(): ThemeEditorDraft {
        return ThemeEditorDraft.fromPalette(
            name = "Advanced",
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFFF5F1E8,
                    readerLinked = true,
                ),
            ),
            mode = ThemeEditorMode.ADVANCED,
            readerLinked = true,
            legacyIsAdvanced = true,
        )
    }
}
```

Extend `ThemeEditorGuidedColorEditTest.kt` with this guard:

```kotlin
@Test
fun previewColorEdit_doesNotMutateDraftBeforeCommit() {
    val draft = extendedDraft(
        palette = generatePaletteFromGuidedInput(
            GuidedThemePaletteInput(
                accent = 0xFF4F46E5,
                appBackground = 0xFF000000,
                appSurface = 0xFF000000,
                appForeground = 0xFFFFFFFF,
                appForegroundMuted = 0xFFAAAAAA,
                overlayScrim = 0xFF000000,
                readerLinked = true,
            ),
        ),
    )

    val preview = draft.previewColorEdit(
        fieldKey = "app_foreground",
        rawHex = "#000000",
        guided = true,
    )

    assertTrue(preview.wasAdjusted)
    assertEquals("#FFFFFF", draft.appForeground)
}
```

- [ ] **Step 2: Run the pure tests to verify they fail**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerGuidanceTest" --tests "com.epubreader.feature.settings.ThemeEditorGuidedColorEditTest.previewColorEdit_doesNotMutateDraftBeforeCommit"
```

Expected: `FAIL` because `toRgbLabel()`, `previewColorEdit(...)`, and the new exact-zone metadata types do not exist yet.

### Task 2: Implement the preview-only guidance model and RGB display helpers

**Files:**
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`

- [ ] **Step 1: Add the new guidance models and preview path**

Create `ThemeColorPickerGuidance.kt` with this initial implementation:

```kotlin
package com.epubreader.feature.settings

import com.epubreader.core.model.parseThemeColorOrNull

internal data class ThemeColorPickerExactZone(
    val centerSaturation: Float,
    val centerValue: Float,
    val radius: Float,
    val feather: Float,
)

internal data class ThemeColorPickerPreview(
    val chosenHex: String,
    val resultHex: String,
    val chosenRgbLabel: String,
    val resultRgbLabel: String,
    val wasAdjusted: Boolean,
    val exactZone: ThemeColorPickerExactZone?,
    val isInsideExactZone: Boolean,
)

internal fun Long.toRgbLabel(): String {
    val color = this.toInt()
    val red = android.graphics.Color.red(color)
    val green = android.graphics.Color.green(color)
    val blue = android.graphics.Color.blue(color)
    return "RGB($red, $green, $blue)"
}

internal fun String.toRgbLabel(): String {
    val parsed = parseThemeColorOrNull(this) ?: return uppercase()
    return parsed.toRgbLabel()
}

internal fun themeColorPickerExactZone(
    mode: ThemeEditorMode,
    guided: Boolean,
): ThemeColorPickerExactZone? {
    if (!guided) return null
    return when (mode) {
        ThemeEditorMode.BASIC -> ThemeColorPickerExactZone(
            centerSaturation = 0.52f,
            centerValue = 0.58f,
            radius = 0.25f,
            feather = 0.08f,
        )
        ThemeEditorMode.EXTENDED -> ThemeColorPickerExactZone(
            centerSaturation = 0.52f,
            centerValue = 0.58f,
            radius = 0.33f,
            feather = 0.08f,
        )
        ThemeEditorMode.ADVANCED -> null
    }
}
```

Then expand `ThemeEditorColorEditing.kt` like this:

```kotlin
internal data class ThemeEditorPickerSession(
    val label: String,
    val initialValue: String,
    val testTagPrefix: String?,
    val isGuided: Boolean,
    val onPreviewColorChange: (String) -> ThemeColorPickerPreview,
    val onColorChange: (String) -> ThemeEditorColorEditResult,
)

internal fun ThemeEditorDraft.previewColorEdit(
    fieldKey: String,
    rawHex: String,
    guided: Boolean,
): ThemeColorPickerPreview {
    val result = applyColorEdit(
        fieldKey = fieldKey,
        rawHex = rawHex,
        guided = guided,
    )
    val zone = themeColorPickerExactZone(mode = mode, guided = guided)
    return ThemeColorPickerPreview(
        chosenHex = rawHex,
        resultHex = result.resolvedHex,
        chosenRgbLabel = rawHex.toRgbLabel(),
        resultRgbLabel = result.resolvedHex.toRgbLabel(),
        wasAdjusted = result.wasAdjusted,
        exactZone = zone,
        isInsideExactZone = !result.wasAdjusted,
    )
}
```

- [ ] **Step 2: Re-run the pure tests to verify they pass**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerGuidanceTest" --tests "com.epubreader.feature.settings.ThemeEditorGuidedColorEditTest"
```

Expected: `PASS`

### Task 3: Add the failing runtime tests for the exact-zone overlay and RGB editor display

**Files:**
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorModeInferenceTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorModeInferenceTest.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`

- [ ] **Step 1: Add the new guided overlay and RGB display assertions**

Add these tests to `SettingsThemeEditorGuidedPickerTest.kt`:

```kotlin
@Test
fun basicPicker_showsExactZoneWhileAdvancedHidesIt() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
    waitUntilTagExists("custom_theme_primary_picker_spectrum")
    composeRule.onNodeWithTag("custom_theme_primary_picker_guidance_zone").assertIsDisplayed()
    composeRule.onNodeWithTag("custom_theme_primary_picker_guidance_caption")
        .assertTextContains("Exact zone")
    closeColorPicker()

    selectThemeEditorMode("advanced")
    composeRule.onNodeWithTag("custom_theme_favorite_accent_swatch").performScrollTo().performClick()
    waitUntilTagExists("custom_theme_favorite_accent_picker_spectrum")
    assertTagDoesNotExist("custom_theme_favorite_accent_picker_guidance_zone")
    closeColorPicker()
}

@Test
fun basicPicker_outsideZone_updatesResultReadoutWithoutMutatingFieldUntilDone() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
    tapSpectrum("custom_theme_primary_picker_spectrum", xFraction = 0.88f, yFraction = 0.12f)

    composeRule.onNodeWithTag("custom_theme_primary_picker_result")
        .assertTextContains("RGB(")
    composeRule.onNodeWithTag("custom_theme_primary_picker_guidance_caption")
        .assertTextContains("Outside the zone")
    composeRule.onNodeWithTag("custom_theme_primary")
        .assertTextContains("RGB(79, 70, 229)")

    closeColorPicker()
}
```

Add this helper:

```kotlin
private fun tapSpectrum(tag: String, xFraction: Float, yFraction: Float) {
    composeRule.onNodeWithTag(tag).performTouchInput {
        click(Offset(width * xFraction, height * yFraction))
    }
}
```

Update the RGB text expectations in `SettingsThemeEditorModeInferenceTest.kt` and `SettingsScreenPersistenceTest.kt`:

```kotlin
composeRule.onNodeWithTag("custom_theme_reader_background").assertTextContains("RGB(")
composeRule.onNodeWithTag("custom_theme_favorite_accent").assertTextContains("RGB(")
waitUntilTextContains("custom_theme_primary", "RGB(")
```

- [ ] **Step 2: Run the targeted instrumentation slice to verify it fails**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest,com.epubreader.feature.settings.SettingsThemeEditorModeInferenceTest,com.epubreader.feature.settings.SettingsScreenPersistenceTest
```

Expected: `FAIL` because the picker still exposes slider tags instead of spectrum tags, the guided zone overlay does not exist yet, and the editor cells still render `#HEX` text.

### Task 4: Implement the spectrum UI, exact-zone overlay, and RGB editor display

**Files:**
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorSections.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeStudioComponents.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorModeInferenceTest.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`

- [ ] **Step 1: Extract the spectrum and hue-strip canvases**

Create `ThemeColorPickerCanvas.kt` with the focused visual building blocks:

```kotlin
package com.epubreader.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeColorSpectrumField(
    hue: Float,
    saturation: Float,
    value: Float,
    exactZone: ThemeColorPickerExactZone?,
    testTagPrefix: String?,
    onChange: (Float, Float) -> Unit,
) {
    Box {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp)
                .then(
                    if (testTagPrefix != null) Modifier.testTag("${testTagPrefix}_picker_spectrum") else Modifier
                )
                .pointerInput(hue) {
                    detectDragGestures(
                        onDragStart = { offset -> updateSpectrumColor(offset, size.width, size.height, onChange) },
                        onDrag = { change, _ -> updateSpectrumColor(change.position, size.width, size.height, onChange) },
                    )
                },
        ) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color.Black),
                ),
            )
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))),
                ),
                alpha = 0.92f,
            )
            if (exactZone != null) {
                val center = Offset(
                    size.width * exactZone.centerSaturation,
                    size.height * (1f - exactZone.centerValue),
                )
                val radius = size.minDimension * exactZone.radius
                val featherRadius = size.minDimension * (exactZone.radius + exactZone.feather)
                val outerRadius = size.maxDimension
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            (radius / outerRadius) to Color.Transparent,
                            (featherRadius / outerRadius) to Color.White.copy(alpha = 0.12f),
                            1f to Color.White.copy(alpha = 0.22f),
                        ),
                        center = center,
                        radius = outerRadius,
                    ),
                    radius = outerRadius,
                    center = center,
                )
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    ),
                )
            }
        }
    }
}

private fun updateSpectrumColor(
    offset: Offset,
    width: Float,
    height: Float,
    onChange: (Float, Float) -> Unit,
) {
    val saturation = (offset.x / width).coerceIn(0f, 1f)
    val value = 1f - (offset.y / height).coerceIn(0f, 1f)
    onChange(saturation, value)
}

@Composable
internal fun ThemeHueStrip(
    hue: Float,
    testTagPrefix: String?,
    onHueChange: (Float) -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .then(
                if (testTagPrefix != null) Modifier.testTag("${testTagPrefix}_picker_hue_strip") else Modifier
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val nextHue = ((offset.x / size.width) * 360f).coerceIn(0f, 360f)
                        onHueChange(nextHue)
                    },
                    onDrag = { change, _ ->
                        val nextHue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                        onHueChange(nextHue)
                    },
                )
            },
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                ),
            ),
        )
        drawCircle(
            color = Color.White,
            radius = 9.dp.toPx(),
            center = Offset((hue / 360f) * size.width, size.height / 2f),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}
```

Keep the exact-zone tag on a separate guidance helper in `SettingsThemeColorPicker.kt` so tests do not depend on Compose canvas internals:

```kotlin
Box(
    modifier = Modifier
        .testTag("${testTagPrefix}_picker_guidance_zone")
        .semantics { stateDescription = if (preview.isInsideExactZone) "exact" else "balanced" }
)
```

- [ ] **Step 2: Rebuild the picker modal around the preview model**

Update `SettingsThemeColorPicker.kt` so it accepts preview data without mutating the draft:

```kotlin
@Composable
internal fun ThemeColorPickerOverlay(
    label: String,
    initialValue: String,
    testTagPrefix: String? = null,
    isGuided: Boolean,
    onDismiss: () -> Unit,
    onPreviewValueChange: (String) -> ThemeColorPickerPreview,
    onValueChange: (String) -> ThemeEditorColorEditResult,
) {
    var preview by remember(initialValue) {
        mutableStateOf(onPreviewValueChange(initialValue))
    }

    fun refreshPreview(hue: Float = pickerHue, saturation: Float = pickerSaturation, value: Float = pickerValue) {
        setPickerColor(hue = hue, saturation = saturation, value = value)
        preview = onPreviewValueChange(formatThemeColor(HsvColor(hue, saturation, value).toColorLong()))
        hasPendingGuidedChange = isGuided
    }
```

Replace the three visible sliders with:

```kotlin
ThemeColorSpectrumField(
    hue = pickerHue,
    saturation = pickerSaturation,
    value = pickerValue,
    exactZone = preview.exactZone,
    testTagPrefix = testTagPrefix,
    onChange = { nextSaturation, nextValue ->
        refreshPreview(saturation = nextSaturation, value = nextValue)
    },
)

ThemeHueStrip(
    hue = pickerHue,
    testTagPrefix = testTagPrefix,
    onHueChange = { nextHue -> refreshPreview(hue = nextHue) },
)
```

Render the new readouts instead of the old status block:

```kotlin
Text(
    text = preview.resultRgbLabel,
    modifier = Modifier.testTag("${testTagPrefix}_picker_result"),
    style = MaterialTheme.typography.titleMedium,
)
Text(
    text = if (preview.isInsideExactZone || !isGuided) {
        "Inside the exact zone. The result stays literal."
    } else {
        "Outside the zone, guided mode may rebalance the final result."
    },
    modifier = Modifier.testTag("${testTagPrefix}_picker_guidance_caption"),
)
if (isGuided && !preview.isInsideExactZone) {
    Text(text = "Chosen ${preview.chosenRgbLabel}")
}
```

Keep `confirmPicker` unchanged in spirit: only `Done` writes through `onValueChange`, and BACK/outside tap remains cancel-only.

- [ ] **Step 3: Wire preview callbacks and RGB editor values through the editor**

Update `SettingsThemeEditor.kt` so picker sessions pass both preview and commit callbacks:

```kotlin
activePicker = ThemeEditorPickerSession(
    label = label,
    initialValue = value,
    testTagPrefix = themeEditorColorTestTag(key),
    isGuided = guided,
    onPreviewColorChange = { nextValue ->
        draft.previewColorEdit(
            fieldKey = key,
            rawHex = nextValue,
            guided = guided,
        )
    },
    onColorChange = { nextValue ->
        val result = draft.applyColorEdit(
            fieldKey = key,
            rawHex = nextValue,
            guided = guided,
        )
        draft = result.updatedDraft
        result
    },
)
```

Update `ThemeEditorSections.kt` to carry display text separately from raw HEX:

```kotlin
internal data class ThemeEditorColorField(
    val label: String,
    val rawValue: String,
    val displayValue: String,
    val testTagPrefix: String?,
    val onClick: () -> Unit,
)
```

Then feed `displayValue` into `StudioColorCell` from `SettingsThemeEditor.kt`:

```kotlin
return ThemeEditorColorField(
    label = label,
    rawValue = value,
    displayValue = value.toRgbLabel(),
    testTagPrefix = themeEditorColorTestTag(key),
    onClick = {
        activePicker = ThemeEditorPickerSession(
            label = label,
            initialValue = value,
            testTagPrefix = themeEditorColorTestTag(key),
            isGuided = guided,
            onPreviewColorChange = { nextValue ->
                draft.previewColorEdit(
                    fieldKey = key,
                    rawHex = nextValue,
                    guided = guided,
                )
            },
            onColorChange = { nextValue ->
                val result = draft.applyColorEdit(
                    fieldKey = key,
                    rawHex = nextValue,
                    guided = guided,
                )
                draft = result.updatedDraft
                result
            },
        )
    },
)
```

Finally, in `SettingsThemeStudioComponents.kt`, stop uppercasing the raw value and render the already-prepared RGB label:

```kotlin
Text(
    value,
    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    modifier = Modifier.basicMarquee(),
)
```

- [ ] **Step 4: Run the instrumentation slice again to verify it passes**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest,com.epubreader.feature.settings.SettingsThemeEditorModeInferenceTest,com.epubreader.feature.settings.SettingsScreenPersistenceTest
```

Expected: `PASS`

### Task 5: Run non-regression verification and commit

**Files:**
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorSections.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeStudioComponents.kt`
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorModeInferenceTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`

- [ ] **Step 1: Run the full scoped settings verification**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerGuidanceTest" --tests "com.epubreader.feature.settings.ThemeEditorGuidedColorEditTest"
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest,com.epubreader.feature.settings.SettingsThemeEditorModeInferenceTest,com.epubreader.feature.settings.SettingsScreenPersistenceTest
.\gradlew.bat checkKotlinFileLineLimit
```

Expected: all commands `PASS`

- [ ] **Step 2: Do one manual picker smoke on the emulator**

Manual checks:

```text
1. Open Appearance -> Create Custom Theme -> Basic.
2. Open Accent and verify the dashed exact-zone circle is visible.
3. Tap inside the circle and confirm the result text stays literal.
4. Tap outside the circle and confirm the outer-veil explanation appears without changing the grid cell until Done.
5. Switch to Advanced and confirm the circle and veil are gone.
6. Commit once and confirm the editor cell shows RGB text, not HEX.
```

Expected: the picker teaches the rule visually before the user commits anything, and cancel still discards changes.

- [ ] **Step 3: Commit the scoped implementation**

Run:

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorSections.kt feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeStudioComponents.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorModeInferenceTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt
git commit -m "settings: add spectrum theme picker guidance"
```

Expected: one commit containing only the scoped theme picker redesign and its tests.
