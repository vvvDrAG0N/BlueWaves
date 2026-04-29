# Theme Picker Editor Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the spectrum-based theme picker into a preview-only mini editor with editable `HEX` and `RGB`, header save/cancel actions, guided typed-input correction, and protected dirty-exit behavior.

**Architecture:** Keep one local picker draft for all modes and treat the existing editor commit seam as the only persistence path. Extract numeric text-entry logic into a pure helper file and extract the top input band into a feature-local composable so `SettingsThemeColorPicker.kt` stays under the repo size guard while still owning dialog state, commit logic, and exit handling.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Compose UI tests, JVM unit tests, JUnit4, Gradle

---

## File Structure

- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt`
  - Own `HEX` and `RGB` text state, formatting, parsing, last-valid preview preservation, and conversion helpers between the text fields and the existing picker color model.
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerValueInputs.kt`
  - Own the new top band UI: editable `HEX`, editable `RGB`, compact live swatch, and the guided-adjustment cue.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
  - Keep the existing safe-zone sampling/projection and add one tiny typed-resolution helper that reuses the current preview callback instead of teaching the UI to resolve guided typed input on its own.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
  - Keep dialog ownership, add local preview-only behavior for all modes, add header `X` and check actions, add `BackHandler`, add the dirty-exit dialog, remove the bottom `Done` button, disable outside tap, and wire the new text-entry surface into the existing spectrum and hue controls.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
  - Replace the bottom-`Done` assumption with header-action helpers and add the new picker-editor coverage: `HEX`/`RGB` typing, guided typed correction cue, `Back` dirty dialog, `X` dirty dialog, and outside-tap disablement.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`
  - Add helper methods for the new text fields, preview assertions, header actions, and dirty-exit dialog buttons.
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt`
  - Cover partial-input stability and `HEX <-> RGB` synchronization as pure logic.
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
  - Add typed guided-resolution coverage so the numeric-entry path stays aligned with the real preview/commit resolver.
- Modify: `docs/agent_memory/step_history.md`
  - Append the implementation result and verification commands after the code work is complete.
- Modify: `docs/agent_memory/next_steps.md`
  - Remove or update the plan-refresh follow-up once the implementation is complete.

Guardrails:

- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt` is already `475` lines in this branch. Do not pile the new text inputs into that file without extracting helper/UI units first.
- Keep `ThemeEditorColorEditing.kt`, `SettingsThemeEditor.kt`, and the persisted `HEX` contract unchanged unless a task below explicitly calls for a surgical adjustment.
- Do not reintroduce outside-dismiss saves or live persistence during typing or dragging.

### Task 1: Add pure text-entry and typed-guidance coverage

**Files:**
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt`
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`

- [ ] **Step 1: Write the failing text-entry tests**

Create `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt` with this content:

```kotlin
package com.epubreader.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeColorPickerTextEntryTest {

    @Test
    fun hexInput_partial_keepsLastValidHexUntilSixDigits() {
        val initial = themeColorPickerTextFields("#4F46E5")
        val partial = initial.withHexInput("12AB")

        assertEquals("12AB", partial.hexText)
        assertEquals("#4F46E5", partial.lastValidHex)
        assertNull(partial.tryResolveHex())
        assertEquals("079", partial.rgbText.red)
        assertEquals("070", partial.rgbText.green)
        assertEquals("229", partial.rgbText.blue)
    }

    @Test
    fun hexInput_complete_updatesRgbAndLastValidHex() {
        val updated = themeColorPickerTextFields("#000000").withHexInput("12ab34")

        assertEquals("12AB34", updated.hexText)
        assertEquals("#12AB34", updated.lastValidHex)
        assertEquals("#12AB34", updated.tryResolveHex())
        assertEquals("018", updated.rgbText.red)
        assertEquals("171", updated.rgbText.green)
        assertEquals("052", updated.rgbText.blue)
    }

    @Test
    fun rgbInput_complete_updatesHexOnlyWhenAllChannelsAreValid() {
        val initial = themeColorPickerTextFields("#112233")
        val partial = initial.withRgbInput(red = "9", green = "", blue = "255")

        assertEquals("#112233", partial.lastValidHex)
        assertNull(partial.tryResolveHex())

        val complete = partial.withRgbInput(red = "009", green = "128", blue = "255")

        assertEquals("0980FF", complete.hexText)
        assertEquals("#0980FF", complete.lastValidHex)
        assertEquals("#0980FF", complete.tryResolveHex())
        assertEquals("009", complete.rgbText.red)
        assertEquals("128", complete.rgbText.green)
        assertEquals("255", complete.rgbText.blue)
    }
}
```

- [ ] **Step 2: Add the failing guided typed-resolution test**

Append this test to `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt`:

```kotlin
@Test
fun resolveGuidedTypedHex_matchesCommitResolution() {
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

    val expected = draft.applyColorEdit(
        fieldKey = "app_foreground",
        rawHex = "#000000",
        guided = true,
    )

    val resolution = resolveGuidedTypedHex("#000000") { rawHex ->
        draft.previewColorEdit(
            fieldKey = "app_foreground",
            rawHex = rawHex,
            guided = true,
        )
    }

    assertTrue(resolution.wasAdjusted)
    assertEquals(expected.resolvedHex, resolution.resolvedHex)
}
```

- [ ] **Step 3: Run the new tests to verify they fail**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerTextEntryTest" --tests "com.epubreader.feature.settings.ThemeColorPickerGuidanceTest.resolveGuidedTypedHex_matchesCommitResolution"
```

Expected: `FAIL` because `themeColorPickerTextFields(...)`, `ThemeColorPickerTextFields`, and `resolveGuidedTypedHex(...)` do not exist yet.

- [ ] **Step 4: Implement the text-entry helper and typed-guidance bridge**

Create `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt` with this content:

```kotlin
package com.epubreader.feature.settings

import com.epubreader.core.model.formatThemeColor
import com.epubreader.core.model.parseThemeColorOrNull

internal data class ThemeColorPickerRgbText(
    val red: String,
    val green: String,
    val blue: String,
)

internal data class ThemeColorPickerTextFields(
    val hexText: String,
    val rgbText: ThemeColorPickerRgbText,
    val lastValidHex: String,
) {
    fun withHexInput(nextHex: String): ThemeColorPickerTextFields {
        val sanitized = nextHex
            .uppercase()
            .filter { it.isDigit() || it in 'A'..'F' }
            .take(6)
        val resolved = sanitized.toResolvedHexOrNull()
        return if (resolved != null) {
            themeColorPickerTextFields(resolved)
        } else {
            copy(hexText = sanitized)
        }
    }

    fun withRgbInput(
        red: String = rgbText.red,
        green: String = rgbText.green,
        blue: String = rgbText.blue,
    ): ThemeColorPickerTextFields {
        val nextRgb = ThemeColorPickerRgbText(
            red = red.filter(Char::isDigit).take(3),
            green = green.filter(Char::isDigit).take(3),
            blue = blue.filter(Char::isDigit).take(3),
        )
        val resolved = nextRgb.toResolvedHexOrNull()
        return if (resolved != null) {
            themeColorPickerTextFields(resolved, nextRgb)
        } else {
            copy(rgbText = nextRgb)
        }
    }

    fun tryResolveHex(): String? {
        return hexText.toResolvedHexOrNull() ?: rgbText.toResolvedHexOrNull()
    }
}

internal fun themeColorPickerTextFields(
    hex: String,
    rgbOverride: ThemeColorPickerRgbText? = null,
): ThemeColorPickerTextFields {
    val normalizedHex = formatThemeColor(parseThemeColorOrNull(hex) ?: error("Expected valid hex: $hex"))
    val color = parseThemeColorOrNull(normalizedHex) ?: error("Expected valid hex: $normalizedHex")
    val computedRgb = ThemeColorPickerRgbText(
        red = ((color shr 16) and 0xFF).toString().padStart(3, '0'),
        green = ((color shr 8) and 0xFF).toString().padStart(3, '0'),
        blue = (color and 0xFF).toString().padStart(3, '0'),
    )
    return ThemeColorPickerTextFields(
        hexText = normalizedHex.removePrefix("#"),
        rgbText = rgbOverride ?: computedRgb,
        lastValidHex = normalizedHex,
    )
}

private fun String.toResolvedHexOrNull(): String? {
    if (length != 6) return null
    return parseThemeColorOrNull("#$this")?.let(::formatThemeColor)
}

private fun ThemeColorPickerRgbText.toResolvedHexOrNull(): String? {
    if (red.length != 3 || green.length != 3 || blue.length != 3) return null
    val resolvedRed = red.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    val resolvedGreen = green.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    val resolvedBlue = blue.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    val color = 0xFF000000L or
        (resolvedRed.toLong() shl 16) or
        (resolvedGreen.toLong() shl 8) or
        resolvedBlue.toLong()
    return formatThemeColor(color)
}
```

Append this small bridge to `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`:

```kotlin
internal data class ThemeColorPickerTypedResolution(
    val resolvedHex: String,
    val wasAdjusted: Boolean,
)

internal fun resolveGuidedTypedHex(
    rawHex: String,
    previewColor: (String) -> ThemeColorPickerPreviewResult,
): ThemeColorPickerTypedResolution {
    val preview = previewColor(rawHex)
    return ThemeColorPickerTypedResolution(
        resolvedHex = preview.resolvedHex,
        wasAdjusted = preview.wasAdjusted,
    )
}
```

- [ ] **Step 5: Run the JVM tests to verify they pass**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerTextEntryTest" --tests "com.epubreader.feature.settings.ThemeColorPickerGuidanceTest"
```

Expected: `BUILD SUCCESSFUL` with the new text-entry tests and guidance tests passing.

- [ ] **Step 6: Commit the pure helper slice**

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerGuidanceTest.kt
git commit -m "Add theme picker text entry helpers"
```

### Task 2: Add the new input-band UI and save-only local picker state

**Files:**
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerValueInputs.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`

- [ ] **Step 1: Add the failing typed-input instrumentation coverage**

Append these helpers to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`:

```kotlin
internal fun SettingsThemeEditorGuidedPickerTest.saveColorPicker(testTagPrefix: String) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_save").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.replaceHexInput(
    testTagPrefix: String,
    value: String,
) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_hex_input")
        .performTextClearance()
    composeRule.onNodeWithTag("${testTagPrefix}_picker_hex_input")
        .performTextInput(value)
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.replaceRgbInput(
    testTagPrefix: String,
    red: String? = null,
    green: String? = null,
    blue: String? = null,
) {
    red?.let {
        composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_red_input").performTextClearance()
        composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_red_input").performTextInput(it)
    }
    green?.let {
        composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_green_input").performTextClearance()
        composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_green_input").performTextInput(it)
    }
    blue?.let {
        composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_blue_input").performTextClearance()
        composeRule.onNodeWithTag("${testTagPrefix}_picker_rgb_blue_input").performTextInput(it)
    }
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.assertPreviewHex(
    testTagPrefix: String,
    hex: String,
) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_preview")
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(hex),
            ),
        )
}
```

Append these tests to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`:

```kotlin
@Test
fun basicAccent_hexInput_savesWithHeaderCheck() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
    replaceHexInput("custom_theme_primary", "12AB34")
    assertPreviewHex("custom_theme_primary", "#12AB34")
    saveColorPicker("custom_theme_primary")

    waitUntilTextContains("custom_theme_primary", "#12AB34")
}

@Test
fun advancedFavoriteAccent_rgbInput_savesLiteralChoice() {
    launchThemeEditor()
    selectThemeEditorMode("advanced")

    composeRule.onNodeWithTag("custom_theme_favorite_accent_swatch").performScrollTo().performClick()
    replaceRgbInput(
        testTagPrefix = "custom_theme_favorite_accent",
        red = "009",
        green = "128",
        blue = "255",
    )
    assertPreviewHex("custom_theme_favorite_accent", "#0980FF")
    saveColorPicker("custom_theme_favorite_accent")

    waitUntilTextContains("custom_theme_favorite_accent", "#0980FF")
}

@Test
fun extendedInvalidAppText_typedHex_adjustsAndShowsGuidedCue() {
    launchThemeEditor()
    selectThemeEditorMode("extended")

    setPickerColor(
        swatchTag = "custom_theme_background_swatch",
        testTagPrefix = "custom_theme_background",
        brightness = 0f,
    )
    setPickerColor(
        swatchTag = "custom_theme_surface_swatch",
        testTagPrefix = "custom_theme_surface",
        brightness = 0f,
    )
    composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
    replaceHexInput("custom_theme_system_text", "000000")

    composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_adjustment").assertIsDisplayed()
    composeRule.onNodeWithTag("custom_theme_system_text_picker_guided_adjustment")
        .assertTextContains("Adjusted for readability")
}
```

- [ ] **Step 2: Run the connected picker test to verify it fails**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `FAIL` because the picker does not yet expose the new `HEX`/`RGB` field tags or the header save action.

- [ ] **Step 3: Create the new top input-band composable**

Create `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerValueInputs.kt` with this content:

```kotlin
package com.epubreader.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.epubreader.core.model.formatThemeColor

@Composable
internal fun ThemeColorPickerValueInputs(
    textFields: ThemeColorPickerTextFields,
    swatchColor: Long,
    testTagPrefix: String?,
    adjustmentCue: String?,
    onHexChange: (String) -> Unit,
    onRgbChange: (red: String?, green: String?, blue: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = textFields.hexText,
                onValueChange = onHexChange,
                label = { Text("HEX") },
                singleLine = true,
                modifier = if (testTagPrefix != null) {
                    Modifier
                        .fillMaxWidth()
                        .testTag("${testTagPrefix}_picker_hex_input")
                } else {
                    Modifier.fillMaxWidth()
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = textFields.rgbText.red,
                    onValueChange = { onRgbChange(it, null, null) },
                    label = { Text("R") },
                    singleLine = true,
                    modifier = rgbFieldModifier(testTagPrefix, "red"),
                )
                OutlinedTextField(
                    value = textFields.rgbText.green,
                    onValueChange = { onRgbChange(null, it, null) },
                    label = { Text("G") },
                    singleLine = true,
                    modifier = rgbFieldModifier(testTagPrefix, "green"),
                )
                OutlinedTextField(
                    value = textFields.rgbText.blue,
                    onValueChange = { onRgbChange(null, null, it) },
                    label = { Text("B") },
                    singleLine = true,
                    modifier = rgbFieldModifier(testTagPrefix, "blue"),
                )
            }
            if (adjustmentCue != null) {
                Text(
                    text = adjustmentCue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = if (testTagPrefix != null) {
                        Modifier.testTag("${testTagPrefix}_picker_guided_adjustment")
                    } else {
                        Modifier
                    },
                )
            }
        }

        Box(
            modifier = Modifier
                .width(88.dp)
                .height(88.dp)
                .background(Color(swatchColor), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .semantics {
                    contentDescription = formatThemeColor(swatchColor)
                }
                .then(
                    if (testTagPrefix != null) Modifier.testTag("${testTagPrefix}_picker_preview")
                    else Modifier,
                ),
        )
    }
}

private fun rgbFieldModifier(
    testTagPrefix: String?,
    channel: String,
): Modifier {
    return if (testTagPrefix != null) {
        Modifier
            .weight(1f)
            .testTag("${testTagPrefix}_picker_rgb_${channel}_input")
    } else {
        Modifier.weight(1f)
    }
}
```

- [ ] **Step 4: Refactor the picker to use local save-only state and the new top band**

In `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`, make these focused changes:

1. Add the local text-entry and dirty-state fields near the existing HSV state:

```kotlin
    val initialHex = remember(parsedColor) { formatThemeColor(parsedColor ?: DefaultPickerColor) }
    var textFields by remember(initialHex) { mutableStateOf(themeColorPickerTextFields(initialHex)) }
    val isDirty = textFields.lastValidHex != initialHex
    val adjustmentCue = if (wasAdjusted) "Adjusted for readability" else null
```

2. Add one helper that applies a resolved `HEX` value to every local representation:

```kotlin
    fun applyResolvedHexToLocalState(
        resolvedHex: String,
        adjusted: Boolean,
    ) {
        val resolvedColor = parseThemeColorOrNull(resolvedHex) ?: return
        val resolvedHsv = resolvedColor.toThemeColorPickerHsv()
        pickerHue = resolvedHsv.hue
        pickerSaturation = resolvedHsv.saturation
        pickerValue = resolvedHsv.value
        textFields = themeColorPickerTextFields(resolvedHex)
        wasAdjusted = adjusted
        if (adjusted) {
            snapPulseToken += 1
        }
    }
```

3. Replace the old immediate `onValueChange(...)` calls in `updatePickerColor(...)` with a local-only path:

```kotlin
    fun applyRawHexLocally(rawHex: String) {
        val resolved = if (isGuided && guidedPreviewValueChange != null) {
            resolveGuidedTypedHex(rawHex, guidedPreviewValueChange)
        } else {
            ThemeColorPickerTypedResolution(
                resolvedHex = rawHex,
                wasAdjusted = false,
            )
        }
        applyResolvedHexToLocalState(
            resolvedHex = resolved.resolvedHex,
            adjusted = resolved.wasAdjusted,
        )
    }

    fun updatePickerColor(
        hue: Float = pickerHue,
        saturation: Float = pickerSaturation,
        value: Float = pickerValue,
    ) {
        val projectedPoint = if (isGuided && !hue.isApproximately(pickerHue)) {
            ThemeColorPickerPoint(
                saturation = saturation.coerceIn(0f, 1f),
                value = value.coerceIn(0f, 1f),
            )
        } else {
            projectPoint(saturation = saturation, value = value)
        }
        val rawColor = ThemeColorPickerHsv(
            hue = hue,
            saturation = projectedPoint.saturation,
            value = projectedPoint.value,
        ).toColorLong()
        applyRawHexLocally(formatThemeColor(rawColor))
    }
```

4. Replace the large preview block with the new value-input band:

```kotlin
                    ThemeColorPickerValueInputs(
                        textFields = textFields,
                        swatchColor = parseThemeColorOrNull(textFields.lastValidHex) ?: DefaultPickerColor,
                        testTagPrefix = testTagPrefix,
                        adjustmentCue = adjustmentCue,
                        onHexChange = { nextHex ->
                            val nextFields = textFields.withHexInput(nextHex)
                            textFields = nextFields
                            nextFields.tryResolveHex()?.let(::applyRawHexLocally)
                        },
                        onRgbChange = { red, green, blue ->
                            val nextFields = textFields.withRgbInput(
                                red = red ?: textFields.rgbText.red,
                                green = green ?: textFields.rgbText.green,
                                blue = blue ?: textFields.rgbText.blue,
                            )
                            textFields = nextFields
                            nextFields.tryResolveHex()?.let(::applyRawHexLocally)
                        },
                        modifier = Modifier.padding(top = 24.dp),
                    )
```

5. Replace the bottom `Done` row with a header save action and commit helper:

```kotlin
    fun commitCurrentColor() {
        val result = onValueChange(textFields.lastValidHex)
        applyResolvedHexToLocalState(
            resolvedHex = result.resolvedHex,
            adjusted = result.wasAdjusted,
        )
        onDismiss()
    }
```

Then add a header row:

```kotlin
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = if (testTagPrefix != null) {
                                Modifier.testTag("${testTagPrefix}_picker_close")
                            } else {
                                Modifier
                            },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                        Text(
                            text = "$label Color",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(
                            onClick = ::commitCurrentColor,
                            modifier = if (testTagPrefix != null) {
                                Modifier.testTag("${testTagPrefix}_picker_save")
                            } else {
                                Modifier
                            },
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
```

- [ ] **Step 5: Run the connected picker test to verify the typed-input flows pass**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: the new `HEX`/`RGB` tests pass, while the old `closeColorPicker()`/`Done` assumptions will still fail until the exit-flow task updates them.

- [ ] **Step 6: Commit the input-band and local-state slice**

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerValueInputs.kt feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt
git commit -m "Refactor theme picker into preview-only editor"
```

### Task 3: Add protected exit behavior and update the picker runtime tests

**Files:**
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`

- [ ] **Step 1: Add the failing dirty-exit and outside-tap tests**

Append these helpers to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`:

```kotlin
internal fun SettingsThemeEditorGuidedPickerTest.requestCloseColorPicker(testTagPrefix: String) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_close").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsThemeEditorGuidedPickerTest.tapExitDialogAction(
    testTagPrefix: String,
    action: String,
) {
    composeRule.onNodeWithTag("${testTagPrefix}_picker_exit_${action}").performClick()
    composeRule.waitForIdle()
}
```

Append these tests to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`:

```kotlin
@Test
fun basicAccent_backWhileDirty_showsSaveDiscardKeepEditing() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
    replaceHexInput("custom_theme_primary", "12AB34")

    pressBack()
    waitUntilTagDisplayed("custom_theme_primary_picker_exit_dialog")
    composeRule.onNodeWithTag("custom_theme_primary_picker_exit_save").assertIsDisplayed()
    composeRule.onNodeWithTag("custom_theme_primary_picker_exit_discard").assertIsDisplayed()
    composeRule.onNodeWithTag("custom_theme_primary_picker_exit_keep_editing").assertIsDisplayed()

    tapExitDialogAction("custom_theme_primary", "keep_editing")
    waitUntilTagExists("custom_theme_primary_picker_hex_input")
}

@Test
fun basicAccent_closeIconDiscard_closesWithoutSaving() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
    replaceHexInput("custom_theme_primary", "12AB34")

    requestCloseColorPicker("custom_theme_primary")
    waitUntilTagDisplayed("custom_theme_primary_picker_exit_dialog")
    tapExitDialogAction("custom_theme_primary", "discard")

    assertTagDoesNotExist("custom_theme_primary_picker_hex_input")
    composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
}

@Test
fun basicAccent_closeIconWhenClean_closesImmediately() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
    requestCloseColorPicker("custom_theme_primary")

    assertTagDoesNotExist("custom_theme_primary_picker_hex_input")
}

@Test
fun basicAccent_outsideTapDoesNothingWhenDirty() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()
    replaceHexInput("custom_theme_primary", "12AB34")

    dismissColorPickerByOutsideTap("custom_theme_primary_picker_backdrop")
    waitUntilTagExists("custom_theme_primary_picker_hex_input")
}
```

- [ ] **Step 2: Run the connected picker test to verify it fails**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `FAIL` because the picker still closes on `Back`, still assumes the bottom `Done` button in the old helpers, and the backdrop is still interactive.

- [ ] **Step 3: Implement the dirty-exit contract in the picker owner**

In `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`, make these changes:

1. Add the dirty-exit state and request-close helper near the existing picker state:

```kotlin
    var showExitDialog by remember { mutableStateOf(false) }

    fun requestDismiss() {
        if (isDirty) {
            showExitDialog = true
        } else {
            onDismiss()
        }
    }
```

2. Add `BackHandler` and stop the dialog/backdrop from dismissing automatically:

```kotlin
    BackHandler {
        requestDismiss()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
```

3. Remove the backdrop click handler entirely, but keep the node for test targeting:

```kotlin
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (testTagPrefix != null) {
                            Modifier.testTag("${testTagPrefix}_picker_backdrop")
                        } else {
                            Modifier
                        },
                    ),
            )
```

4. Wire the header `X` to `requestDismiss()` and add the exit dialog at the end of the `Dialog` content:

```kotlin
                        IconButton(
                            onClick = ::requestDismiss,
                            modifier = if (testTagPrefix != null) {
                                Modifier.testTag("${testTagPrefix}_picker_close")
                            } else {
                                Modifier
                            },
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
```

```kotlin
            if (showExitDialog) {
                AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("Save changes?") },
                    confirmButton = {
                        TextButton(
                            onClick = ::commitCurrentColor,
                            modifier = if (testTagPrefix != null) {
                                Modifier.testTag("${testTagPrefix}_picker_exit_save")
                            } else {
                                Modifier
                            },
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    showExitDialog = false
                                    onDismiss()
                                },
                                modifier = if (testTagPrefix != null) {
                                    Modifier.testTag("${testTagPrefix}_picker_exit_discard")
                                } else {
                                    Modifier
                                },
                            ) {
                                Text("Discard")
                            }
                            TextButton(
                                onClick = { showExitDialog = false },
                                modifier = if (testTagPrefix != null) {
                                    Modifier.testTag("${testTagPrefix}_picker_exit_keep_editing")
                                } else {
                                    Modifier
                                },
                            ) {
                                Text("Keep editing")
                            }
                        }
                    },
                    modifier = if (testTagPrefix != null) {
                        Modifier.testTag("${testTagPrefix}_picker_exit_dialog")
                    } else {
                        Modifier
                    },
                )
            }
```

5. Update `closeColorPicker()` and `setPickerColor()` in `SettingsThemeEditorGuidedPickerTestSupport.kt` to use the new save action:

```kotlin
internal fun SettingsThemeEditorGuidedPickerTest.closeColorPicker(testTagPrefix: String) {
    saveColorPicker(testTagPrefix)
}
```

```kotlin
internal fun SettingsThemeEditorGuidedPickerTest.setPickerColor(
    swatchTag: String,
    testTagPrefix: String,
    hue: Float = 0f,
    saturation: Float = 1f,
    brightness: Float,
) {
    composeRule.onNodeWithTag(swatchTag).performScrollTo().performClick()
    setSliderProgress("${testTagPrefix}_picker_hue", hue)
    setSpectrumPoint(
        tag = "${testTagPrefix}_picker_spectrum",
        saturation = saturation,
        value = brightness,
    )
    closeColorPicker(testTagPrefix)
}
```

Then update every existing `closeColorPicker()` call in `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt` to pass the picker prefix that opened the dialog, for example:

```kotlin
closeColorPicker("custom_theme_system_text")
closeColorPicker("custom_theme_primary")
closeColorPicker("custom_theme_favorite_accent")
```

- [ ] **Step 4: Run the connected picker test to verify the exit contract passes**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `BUILD SUCCESSFUL` with the new `Back`, `X`, and outside-tap tests passing.

- [ ] **Step 5: Commit the exit-flow slice**

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt
git commit -m "Add guarded exit flow to theme picker"
```

### Task 4: Run full verification and update continuity docs

**Files:**
- Modify: `docs/agent_memory/step_history.md`
- Modify: `docs/agent_memory/next_steps.md`
- Test: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt`
- Test: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`

- [ ] **Step 1: Run the full settings JVM suite**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Re-run the connected picker class**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Re-run the repo size guard**

Run:

```powershell
.\gradlew.bat checkKotlinFileLineLimit
```

Expected: no line-limit violations, especially none in `SettingsThemeColorPicker.kt`.

- [ ] **Step 4: Install and manually smoke the picker on the emulator**

Run:

```powershell
.\gradlew.bat :app:installDebug
```

Then manually verify on `emulator-5554`:

```text
1. Open Settings -> Appearance -> Create custom theme
2. Open a Basic picker, type HEX, save with header check
3. Reopen, type RGB, press Back, choose Keep editing, then Save
4. Open an Extended guided field, type an invalid dark text color, confirm the cue appears and the value rewrites
5. Tap outside the dialog and confirm nothing closes
6. Use X -> Discard and confirm the cell value stays unchanged
```

- [ ] **Step 5: Record the work in continuity docs**

Append a new numbered entry to `docs/agent_memory/step_history.md` describing:

```markdown
## <next index>. 2026-04-28 00:00
- Agent model: Codex GPT-5
- Agent name: Codex
- Task goal: Implement the theme picker editor refresh with editable HEX/RGB, header save/cancel actions, guided typed adjustment, and guarded dirty exits.
- Area/files: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerValueInputs.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`, `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`, `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`, `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt`
- Action taken:
  1. Added pure text-entry helpers and tests for HEX/RGB synchronization plus partial-input stability.
  2. Extracted the top input-band composable so the picker owner stayed below the line limit.
  3. Switched the picker to preview-only local edits across all modes and moved save to the header check action.
  4. Added guarded dirty-exit behavior for Back and X, with outside tap disabled.
  5. Re-ran JVM tests, connected picker tests, the line-limit guard, and emulator manual smoke.
- Verification:
  - `.\gradlew.bat :feature:settings:testDebugUnitTest`
  - `.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest`
  - `.\gradlew.bat checkKotlinFileLineLimit`
  - `.\gradlew.bat :app:installDebug`
```

Update `docs/agent_memory/next_steps.md` by either deleting the `Theme Picker Editor Refresh` planning item or replacing it with a smaller follow-up only if something remains open after verification.

- [ ] **Step 6: Commit the verification and docs pass**

```powershell
git add docs/agent_memory/step_history.md docs/agent_memory/next_steps.md
git commit -m "Document theme picker editor refresh verification"
```
