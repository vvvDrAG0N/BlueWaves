# Theme Spectrum Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the slider-only theme color picker with a 2D saturation/value spectrum plus a hue slider, while keeping guided mode constrained to the real readable safe zone and leaving Advanced mode unrestricted.

**Architecture:** Keep HEX persistence and the existing `ThemeEditorColorEditResult` commit path intact. Add a preview-only guidance seam from the theme editor into the picker so the picker can sample the real guided resolver without mutating the draft, then extract the 2D spectrum drawing and safe-zone sampling/projection into focused helper files because `SettingsThemeColorPicker.kt` is already near the size threshold.

**Tech Stack:** Kotlin, Jetpack Compose, Compose Canvas, Android instrumentation tests, JVM unit tests, JUnit4, Gradle

---

## File Structure

- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
  - Own the HSV helpers that currently live privately in `SettingsThemeColorPicker.kt`, the preview-only guided result model, the sampled safe-zone model, and nearest-valid projection.
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
  - Own the 2D spectrum composable, safe-zone veil drawing, handle drawing, and pointer-to-spectrum coordinate mapping.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`
  - Expand the picker session contract with a preview callback and add the non-mutating guided preview helper used by safe-zone sampling.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
  - Pass the new preview callback into the picker session without changing save semantics.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
  - Replace the Saturation and Brightness sliders with the new spectrum field, keep the hue slider, rebuild the safe zone when hue changes, and preserve dismiss-vs-`Done` behavior.
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
  - Cover safe-zone sampling and nearest-valid projection as pure logic.
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`
  - Add the non-mutating preview guard so the safe-zone sampler cannot accidentally rewrite the draft.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
  - Migrate picker interaction coverage from the removed sliders to the new spectrum, and add guided-safe-zone presence/absence assertions.

Workspace guard:

- Ignore the unrelated `scripts/__pycache__/` files already present in the worktree.
- Do not touch `ThemeEditorSections.kt`, `SettingsThemeStudioComponents.kt`, `SettingsThemeEditorModeInferenceTest.kt`, or `SettingsScreenPersistenceTest.kt` in this plan; the approved spec explicitly keeps editor cell display and persistence format unchanged.

### Task 1: Add the failing pure tests for guided preview sampling and nearest-valid projection

**Files:**
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`

- [ ] **Step 1: Write the failing safe-zone sampling test**

Create `ThemeColorPickerGuidanceTest.kt` with this content:

```kotlin
package com.epubreader.feature.settings

import com.epubreader.core.model.GuidedThemePaletteInput
import com.epubreader.core.model.generatePaletteFromGuidedInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorPickerGuidanceTest {

    @Test
    fun buildGuidedSafeZone_basicAccent_excludesKnownUnreadableExtreme() {
        val draft = basicDraft()

        val zone = buildGuidedSafeZone(
            hue = 240f,
            previewColor = { rawHex ->
                draft.previewColorEdit(
                    fieldKey = "accent",
                    rawHex = rawHex,
                    guided = true,
                )
            },
        )

        assertNotNull(zone)
        assertTrue(zone!!.rows.isNotEmpty())
        assertFalse(
            zone.contains(
                ThemeColorPickerPoint(
                    saturation = 1f,
                    value = 0f,
                ),
            ),
        )
    }

    @Test
    fun project_outsidePoint_snapsToNearestAllowedSpan() {
        val zone = ThemeColorPickerSafeZone(
            rows = listOf(
                ThemeColorPickerSafeZoneRow(
                    value = 0.75f,
                    spans = listOf(0.20f..0.40f),
                ),
                ThemeColorPickerSafeZoneRow(
                    value = 0.50f,
                    spans = listOf(0.30f..0.60f),
                ),
            ),
        )

        val projected = zone.project(
            ThemeColorPickerPoint(
                saturation = 0.95f,
                value = 0.52f,
            ),
        )

        assertEquals(0.60f, projected.saturation, 0.0001f)
        assertEquals(0.50f, projected.value, 0.0001f)
    }

    private fun basicDraft(): ThemeEditorDraft {
        return ThemeEditorDraft.fromPalette(
            name = "Basic",
            palette = generatePaletteFromGuidedInput(
                GuidedThemePaletteInput(
                    accent = 0xFF4F46E5,
                    appBackground = 0xFFFFFFFF,
                    readerLinked = true,
                ),
            ),
            mode = ThemeEditorMode.BASIC,
            readerLinked = true,
            legacyIsAdvanced = true,
        )
    }
}
```

- [ ] **Step 2: Add the non-mutating preview guard**

Append this test to `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`:

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

- [ ] **Step 3: Run the pure tests to verify they fail**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerGuidanceTest" --tests "com.epubreader.feature.settings.ThemeEditorGuidedColorEditTest.previewColorEdit_doesNotMutateDraftBeforeCommit"
```

Expected: `FAIL` because `buildGuidedSafeZone(...)`, `ThemeColorPickerSafeZone`, `ThemeColorPickerSafeZoneRow`, `ThemeColorPickerPoint`, and `previewColorEdit(...)` do not exist yet.

### Task 2: Implement the preview-only guidance seam and safe-zone projection core

**Files:**
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`

- [ ] **Step 1: Add the guidance and projection models**

Create `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt` with this implementation:

```kotlin
package com.epubreader.feature.settings

import com.epubreader.core.model.formatThemeColor

internal data class ThemeColorPickerPoint(
    val saturation: Float,
    val value: Float,
)

internal data class ThemeColorPickerSafeZoneRow(
    val value: Float,
    val spans: List<ClosedFloatingPointRange<Float>>,
)

internal data class ThemeColorPickerSafeZone(
    val rows: List<ThemeColorPickerSafeZoneRow>,
) {
    fun contains(point: ThemeColorPickerPoint): Boolean {
        val nearestRow = rows.minByOrNull { row -> kotlin.math.abs(row.value - point.value) } ?: return false
        return nearestRow.spans.any { span ->
            point.saturation >= span.start && point.saturation <= span.endInclusive
        }
    }

    fun project(point: ThemeColorPickerPoint): ThemeColorPickerPoint {
        if (contains(point)) return point
        return rows
            .flatMap { row ->
                row.spans.map { span ->
                    ThemeColorPickerPoint(
                        saturation = point.saturation.coerceIn(span.start, span.endInclusive),
                        value = row.value,
                    )
                }
            }
            .minByOrNull { candidate -> candidate.distanceSquaredTo(point) }
            ?: point
    }
}

internal data class ThemeColorPickerPreviewResult(
    val resolvedHex: String,
    val wasAdjusted: Boolean,
)

internal fun buildGuidedSafeZone(
    hue: Float,
    previewColor: (String) -> ThemeColorPickerPreviewResult,
    rows: Int = 36,
    columns: Int = 48,
): ThemeColorPickerSafeZone {
    val maxRow = (rows - 1).coerceAtLeast(1)
    val maxColumn = (columns - 1).coerceAtLeast(1)
    val sampledRows = buildList {
        repeat(rows) { rowIndex ->
            val value = 1f - (rowIndex.toFloat() / maxRow.toFloat())
            val exactColumns = buildList {
                repeat(columns) { columnIndex ->
                    val saturation = columnIndex.toFloat() / maxColumn.toFloat()
                    val rawHex = formatThemeColor(HsvColor(hue, saturation, value).toColorLong())
                    val preview = previewColor(rawHex)
                    if (!preview.wasAdjusted) add(saturation)
                }
            }
            val spans = exactColumns.toSafeZoneSpans(step = 1f / maxColumn.toFloat())
            if (spans.isNotEmpty()) {
                add(
                    ThemeColorPickerSafeZoneRow(
                        value = value,
                        spans = spans,
                    ),
                )
            }
        }
    }
    return ThemeColorPickerSafeZone(rows = sampledRows)
}

internal data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun toColorLong(): Long {
        val hsv = floatArrayOf(hue, saturation, value)
        return android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL
    }
}

internal fun Long.toHsvColor(): HsvColor {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toInt(), hsv)
    return HsvColor(hsv[0], hsv[1], hsv[2])
}

internal fun Float.isApproximately(other: Float, epsilon: Float = 0.001f): Boolean {
    return kotlin.math.abs(this - other) <= epsilon
}

private fun ThemeColorPickerPoint.distanceSquaredTo(other: ThemeColorPickerPoint): Float {
    val dx = saturation - other.saturation
    val dy = value - other.value
    return dx * dx + dy * dy
}

private fun List<Float>.toSafeZoneSpans(step: Float): List<ClosedFloatingPointRange<Float>> {
    if (isEmpty()) return emptyList()
    val sorted = sorted()
    val spans = mutableListOf<ClosedFloatingPointRange<Float>>()
    var start = sorted.first()
    var end = sorted.first()
    for (index in 1 until sorted.size) {
        val value = sorted[index]
        if ((value - end) <= step + 0.0001f) {
            end = value
        } else {
            spans += start..end
            start = value
            end = value
        }
    }
    spans += start..end
    return spans
}
```

- [ ] **Step 2: Add the preview-only picker callback**

Update `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt` to this shape:

```kotlin
internal data class ThemeEditorPickerSession(
    val label: String,
    val initialValue: String,
    val testTagPrefix: String?,
    val isGuided: Boolean,
    val onPreviewColorChange: (String) -> ThemeColorPickerPreviewResult,
    val onColorChange: (String) -> ThemeEditorColorEditResult,
)

internal fun ThemeEditorDraft.previewColorEdit(
    fieldKey: String,
    rawHex: String,
    guided: Boolean,
): ThemeColorPickerPreviewResult {
    val result = applyColorEdit(
        fieldKey = fieldKey,
        rawHex = rawHex,
        guided = guided,
    )
    return ThemeColorPickerPreviewResult(
        resolvedHex = result.resolvedHex,
        wasAdjusted = result.wasAdjusted,
    )
}
```

Keep `applyColorEdit(...)`, `colorFieldValue(...)`, and `withColorField(...)` unchanged below that addition.

- [ ] **Step 3: Re-run the pure tests to verify they pass**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerGuidanceTest" --tests "com.epubreader.feature.settings.ThemeEditorGuidedColorEditTest"
```

Expected: `PASS`

- [ ] **Step 4: Commit the pure guidance layer**

Run:

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt
git commit -m "settings: add theme picker safe zone core"
```

Expected: one commit containing only the pure guidance seam and its tests.

### Task 3: Add the failing runtime tests for the spectrum surface and guided safe-zone visibility

**Files:**
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`

- [ ] **Step 1: Add the safe-zone visibility test**

Append this test to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`:

```kotlin
@Test
fun basicPicker_showsSafeZoneWhileAdvancedHidesIt() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
    waitUntilTagExists("custom_theme_primary_picker_spectrum")
    composeRule.onNodeWithTag("custom_theme_primary_picker_safe_zone").assertIsDisplayed()
    closeColorPicker()

    selectThemeEditorMode("advanced")
    composeRule.onNodeWithTag("custom_theme_favorite_accent_swatch").performScrollTo().performClick()
    waitUntilTagExists("custom_theme_favorite_accent_picker_spectrum")
    assertTagDoesNotExist("custom_theme_favorite_accent_picker_safe_zone")
    closeColorPicker()
}
```

- [ ] **Step 2: Replace slider-only picker interactions with spectrum interactions**

Add this helper near the existing picker helpers in `SettingsThemeEditorGuidedPickerTest.kt`:

```kotlin
private fun tapSpectrum(tag: String, xFraction: Float, yFraction: Float) {
    composeRule.onNodeWithTag(tag).performTouchInput {
        click(
            Offset(
                x = width * xFraction,
                y = height * yFraction,
            ),
        )
    }
}
```

Then update the existing dismiss tests so they no longer depend on the removed Saturation and Brightness sliders:

```kotlin
composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
setSliderProgress("custom_theme_primary_picker_hue", 0f)
tapSpectrum("custom_theme_primary_picker_spectrum", xFraction = 0.95f, yFraction = 0.95f)
composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
waitUntilTagExists("custom_theme_primary_picker_spectrum")
```

Make that replacement in:

- `basicAccent_backDismiss_discardsPendingGuidedChoice()`
- `basicAccent_outsideDismiss_discardsPendingGuidedChoice()`
- `basicAccent_dialogChromeTap_keepsPickerOpen()`

- [ ] **Step 3: Run the instrumentation class to verify it fails**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `FAIL` because `_picker_spectrum` and `_picker_safe_zone` do not exist yet, and the updated picker tests still point at the old UI.

### Task 4: Implement the spectrum surface, guided safe-zone overlay, and picker wiring

**Files:**
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`

- [ ] **Step 1: Add the 2D spectrum composable**

Create `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt` with this implementation:

```kotlin
package com.epubreader.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeColorSpectrumField(
    hue: Float,
    point: ThemeColorPickerPoint,
    safeZone: ThemeColorPickerSafeZone?,
    testTagPrefix: String?,
    onPointChange: (ThemeColorPickerPoint) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .then(
                if (testTagPrefix != null) Modifier.testTag("${testTagPrefix}_picker_spectrum") else Modifier
            )
            .pointerInput(hue, safeZone) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onPointChange(offset.toSpectrumPoint(size.width, size.height))
                    },
                    onDrag = { change, _ ->
                        onPointChange(change.position.toSpectrumPoint(size.width, size.height))
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White,
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                    ),
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                ),
            )
            if (safeZone != null) {
                drawGuidedVeil(safeZone)
            }
            drawSpectrumHandle(point)
        }
        if (safeZone != null && testTagPrefix != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .testTag("${testTagPrefix}_picker_safe_zone")
                    .semantics {
                        stateDescription = "rows=${safeZone.rows.size}"
                    },
            )
        }
    }
}

private fun DrawScope.drawGuidedVeil(zone: ThemeColorPickerSafeZone) {
    drawRect(color = Color.Black.copy(alpha = 0.18f))
    val rowHeight = size.height / zone.rows.size.coerceAtLeast(1)
    zone.rows.forEach { row ->
        val centerY = size.height * (1f - row.value)
        val top = (centerY - rowHeight / 2f).coerceAtLeast(0f)
        row.spans.forEach { span ->
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(span.start * size.width, top),
                size = Size(
                    width = (span.endInclusive - span.start).coerceAtLeast(0.01f) * size.width,
                    height = rowHeight + 1f,
                ),
                blendMode = BlendMode.Clear,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.75f),
                start = Offset(span.start * size.width, centerY),
                end = Offset(span.endInclusive * size.width, centerY),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

private fun DrawScope.drawSpectrumHandle(point: ThemeColorPickerPoint) {
    val center = Offset(
        x = point.saturation.coerceIn(0f, 1f) * size.width,
        y = (1f - point.value.coerceIn(0f, 1f)) * size.height,
    )
    drawCircle(
        color = Color.White,
        radius = 12.dp.toPx(),
        center = center,
        style = Stroke(width = 3.dp.toPx()),
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.35f),
        radius = 16.dp.toPx(),
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )
}

private fun Offset.toSpectrumPoint(width: Float, height: Float): ThemeColorPickerPoint {
    return ThemeColorPickerPoint(
        saturation = (x / width).coerceIn(0f, 1f),
        value = 1f - (y / height).coerceIn(0f, 1f),
    )
}
```

- [ ] **Step 2: Wire the preview callback from the editor**

Update `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt` so `ThemeEditorPickerSession` passes both preview and commit callbacks:

```kotlin
return ThemeEditorColorField(
    label = label,
    value = value,
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

Keep the field grid, save behavior, and the rest of the dialog unchanged.

- [ ] **Step 3: Replace the picker sliders with the spectrum field**

Update `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt` with these focused changes:

1. Change the function signature:

```kotlin
internal fun ThemeColorPickerOverlay(
    label: String,
    initialValue: String,
    testTagPrefix: String? = null,
    isGuided: Boolean,
    onDismiss: () -> Unit,
    onPreviewValueChange: (String) -> ThemeColorPickerPreviewResult,
    onValueChange: (String) -> ThemeEditorColorEditResult,
)
```

2. Add safe-zone state and guided projection:

```kotlin
var safeZone by remember { mutableStateOf<ThemeColorPickerSafeZone?>(null) }

fun rebuildSafeZone(nextHue: Float = pickerHue): ThemeColorPickerSafeZone? {
    return if (isGuided) {
        buildGuidedSafeZone(
            hue = nextHue,
            previewColor = onPreviewValueChange,
        )
    } else {
        null
    }
}

fun updatePickerColor(
    hue: Float = pickerHue,
    saturation: Float = pickerSaturation,
    value: Float = pickerValue,
) {
    val nextZone = if (isGuided && !hue.isApproximately(pickerHue)) rebuildSafeZone(hue) else safeZone
    val requestedPoint = ThemeColorPickerPoint(saturation = saturation, value = value)
    val resolvedPoint = if (isGuided) {
        nextZone?.project(requestedPoint) ?: requestedPoint
    } else {
        requestedPoint
    }

    if (isGuided) {
        setPickerColor(
            hue = hue,
            saturation = resolvedPoint.saturation,
            value = resolvedPoint.value,
        )
        safeZone = nextZone
        hasPendingGuidedChange = true
        wasAdjusted = false
        showSnapHighlight = false
        return
    }

    val rawColor = HsvColor(hue, resolvedPoint.saturation, resolvedPoint.value).toColorLong()
    val result = onValueChange(formatThemeColor(rawColor))
    val resolvedColor = parseThemeColorOrNull(result.resolvedHex) ?: rawColor
    val resolvedHsv = resolvedColor.toHsvColor()
    setPickerColor(
        hue = resolvedHsv.hue,
        saturation = resolvedHsv.saturation,
        value = resolvedHsv.value,
    )
    safeZone = null
    hasPendingGuidedChange = false
    wasAdjusted = false
    showSnapHighlight = false
}
```

3. Recompute the zone when the hue changes and replace the old sliders:

```kotlin
LaunchedEffect(isGuided, pickerHue) {
    safeZone = rebuildSafeZone()
    if (isGuided) {
        val projected = safeZone?.project(
            ThemeColorPickerPoint(
                saturation = pickerSaturation,
                value = pickerValue,
            ),
        )
        if (
            projected != null &&
            (!pickerSaturation.isApproximately(projected.saturation) ||
                !pickerValue.isApproximately(projected.value))
        ) {
            setPickerColor(
                saturation = projected.saturation,
                value = projected.value,
            )
        }
    }
}
```

Then replace the Saturation and Brightness slider block with:

```kotlin
ThemeColorSpectrumField(
    hue = pickerHue,
    point = ThemeColorPickerPoint(
        saturation = pickerSaturation,
        value = pickerValue,
    ),
    safeZone = safeZone,
    testTagPrefix = testTagPrefix,
    onPointChange = { nextPoint ->
        updatePickerColor(
            saturation = nextPoint.saturation,
            value = nextPoint.value,
        )
    },
)
```

Keep the existing Hue slider and the existing `Done` row below it.

- [ ] **Step 4: Run the picker instrumentation class again to verify it passes**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `PASS`

- [ ] **Step 5: Commit the spectrum UI slice**

Run:

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt
git commit -m "settings: add guided spectrum color picker"
```

Expected: one commit containing only the picker UI redesign and its tests.

### Task 5: Run scoped non-regression verification and manual smoke

**Files:**
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeEditorGuidedColorEditTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`

- [ ] **Step 1: Run the full scoped verification**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerGuidanceTest" --tests "com.epubreader.feature.settings.ThemeEditorGuidedColorEditTest"
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
.\gradlew.bat checkKotlinFileLineLimit
```

Expected: all commands `PASS`

- [ ] **Step 2: Do one manual emulator smoke**

Manual checks:

```text
1. Open Settings -> Appearance -> Create Custom Theme -> Basic.
2. Open Accent and verify the 2D spectrum is visible and the hue slider still appears below it.
3. Drag near a visibly dimmed outer corner and confirm the handle stays inside the readable safe zone.
4. Press BACK and confirm the field still shows the original value.
5. Reopen Accent, drag to a new allowed point, press Done, and confirm the field changes.
6. Switch to Advanced, reopen Favorite Accent, and confirm there is no safe-zone overlay and the handle can reach the full square.
```

Expected: guided mode feels constrained but stable, Advanced mode feels free, and cancel-vs-commit behavior is unchanged.
