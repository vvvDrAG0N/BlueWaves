# Theme Spectrum Picker Merge-Blocker Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the pre-merge picker/editor review findings so guided preview remains trustworthy, dirty-exit behavior is single-step and honest, and picker saves stay local until the editor explicitly saves.

**Architecture:** Keep the existing picker/editor surface, but extract the picker's save/dirty/readiness rules into small pure helpers so `SettingsThemeColorPicker.kt` drops back away from the line-limit cliff. Treat guided safe-zone readiness and incomplete numeric drafts as explicit state, not as implicit fall-through behavior, then pin the resulting contracts with focused overlay tests plus end-to-end editor persistence coverage.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Compose UI tests, JVM unit tests, JUnit4, Gradle

---

## File Structure

- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerSessionState.kt`
  - Own picker dirty-state, save-enable, transient-text, and guided-readiness decisions so those rules stop living inline in `SettingsThemeColorPicker.kt`.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt`
  - Add helpers for checking whether current text matches a resolved preview color.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
  - Consume the extracted session-state helper, block guided interaction/commit until the safe zone is ready, and stop treating raw incomplete input as a saveable change.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
  - Add the tiny safe-zone resolver seam used by focused tests and centralize the “loading vs ready” guided constraint state.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
  - Treat guided-loading as blocked interaction rather than “no boundary”.
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
  - Rebalance the draft immediately when switching from `ADVANCED` into `BASIC` or `EXTENDED`, so guided pickers do not self-dirty on open.
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerSessionStateTest.kt`
  - Cover partial input, guided-loading save gating, and persistable-change semantics as pure logic.
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt`
  - Add `matchesResolvedPreview(...)` style coverage so transient text state is pinned at the helper layer.
- Create: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTest.kt`
  - Mount the picker directly with fake callbacks/resolvers to test guided-loading races and focused-field Back behavior without going through the whole settings screen.
- Create: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTestSupport.kt`
  - Hold the direct-overlay launch helpers and fake delayed safe-zone resolver so `SettingsThemeEditorGuidedPickerTestSupport.kt` does not blow past 500 lines.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`
  - Remove the silent double-Back fallback and fail loudly if the first Back press does not surface the exit dialog.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
  - Add the editor-level guided-mode re-entry regression check and clean outside-tap coverage.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorExitTest.kt`
  - Add focused editor-name-field Back coverage and invalid-draft exit-dialog coverage.
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`
  - Add the missing “picker save -> editor discard -> no persistence” test.
- Modify: `docs/agent_memory/step_history.md`
  - Append the planning pass and, after implementation, the verification result.
- Modify: `docs/agent_memory/next_steps.md`
  - Replace the generic merge/smoke note with this merge-blocker plan as the active next step.

Guardrails:

- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt` is already `476` lines. Do not add more inline state/logic without moving code out first.
- `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt` is already `446` lines. New overlay-only helpers belong in a separate support file.
- Keep `ThemeEditorColorEditing.kt`, DataStore keys, and stored `HEX` persistence semantics unchanged.
- Do not weaken the “local picker save is still local until editor save” contract while fixing the review findings.

### Task 1: Extract picker session-state rules and fix partial-input dirty/save semantics

**Files:**
- Create: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerSessionState.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
- Create: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerSessionStateTest.kt`
- Modify: `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt`

- [ ] **Step 1: Write the failing pure-state tests**

Create `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerSessionStateTest.kt` with:

```kotlin
package com.epubreader.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorPickerSessionStateTest {

    @Test
    fun incompleteHex_withoutPreviewChange_isTransientButNotPersistable() {
        val initialFields = themeColorPickerTextFields("#4F46E5")
        val currentFields = initialFields.withHexInput("12AB")

        val state = ThemeColorPickerSessionState(
            initialHex = "#4F46E5",
            previewHex = "#4F46E5",
            initialTextFields = initialFields,
            currentTextFields = currentFields,
            isGuided = false,
            isGuidedSafeZoneReady = true,
        )

        assertTrue(state.hasTransientTextDraft)
        assertFalse(state.hasPersistableChange)
        assertFalse(state.shouldPromptOnExit)
        assertFalse(state.canCommit)
    }

    @Test
    fun previewChange_withSyncedFields_isPersistableAndCommitEnabled() {
        val initialFields = themeColorPickerTextFields("#4F46E5")
        val currentFields = themeColorPickerTextFields("#12AB34")

        val state = ThemeColorPickerSessionState(
            initialHex = "#4F46E5",
            previewHex = "#12AB34",
            initialTextFields = initialFields,
            currentTextFields = currentFields,
            isGuided = false,
            isGuidedSafeZoneReady = true,
        )

        assertFalse(state.hasTransientTextDraft)
        assertTrue(state.hasPersistableChange)
        assertTrue(state.shouldPromptOnExit)
        assertTrue(state.canCommit)
    }

    @Test
    fun guidedLoading_blocksCommitEvenWhenPreviewWouldDiffer() {
        val initialFields = themeColorPickerTextFields("#4F46E5")
        val currentFields = themeColorPickerTextFields("#12AB34")

        val state = ThemeColorPickerSessionState(
            initialHex = "#4F46E5",
            previewHex = "#12AB34",
            initialTextFields = initialFields,
            currentTextFields = currentFields,
            isGuided = true,
            isGuidedSafeZoneReady = false,
        )

        assertTrue(state.hasPersistableChange)
        assertTrue(state.shouldPromptOnExit)
        assertFalse(state.canCommit)
    }
}
```

- [ ] **Step 2: Extend the text-entry helper test with preview-match coverage**

Append to `feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt`:

```kotlin
@Test
fun matchesResolvedPreview_falseWhenRgbDraftIsIncomplete() {
    val initial = themeColorPickerTextFields("#12AB34")
    val partial = initial.withRgbInput(red = "255", green = "", blue = "100")

    assertFalse(partial.matchesResolvedPreview("#12AB34"))
}

@Test
fun matchesResolvedPreview_trueWhenFieldsReflectPreviewHex() {
    val fields = themeColorPickerTextFields(
        hex = "#12AB34",
        activeInput = ThemeColorPickerActiveInput.RGB,
    )

    assertTrue(fields.matchesResolvedPreview("#12AB34"))
}
```

- [ ] **Step 3: Run the focused JVM tests to verify they fail**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerSessionStateTest" --tests "com.epubreader.feature.settings.ThemeColorPickerTextEntryTest"
```

Expected: `FAIL` because `ThemeColorPickerSessionState` and `matchesResolvedPreview(...)` do not exist yet.

- [ ] **Step 4: Add the new pure helper and wire the picker to it**

Create `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerSessionState.kt` with:

```kotlin
package com.epubreader.feature.settings

internal data class ThemeColorPickerSessionState(
    val initialHex: String,
    val previewHex: String,
    val initialTextFields: ThemeColorPickerTextFields,
    val currentTextFields: ThemeColorPickerTextFields,
    val isGuided: Boolean,
    val isGuidedSafeZoneReady: Boolean,
) {
    val hasPersistableChange: Boolean
        get() = !previewHex.equals(initialHex, ignoreCase = true)

    val hasTransientTextDraft: Boolean
        get() = !currentTextFields.matchesResolvedPreview(previewHex)

    val shouldPromptOnExit: Boolean
        get() = hasPersistableChange

    val canCommit: Boolean
        get() = !hasTransientTextDraft && (!isGuided || isGuidedSafeZoneReady)
}
```

Append to `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt`:

```kotlin
internal fun ThemeColorPickerTextFields.matchesResolvedPreview(previewHex: String): Boolean {
    val previewFields = themeColorPickerTextFields(
        hex = previewHex,
        activeInput = preferredInput,
    )
    return hexText == previewFields.hexText && rgbText == previewFields.rgbText
}
```

In `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`, replace the inline dirty/commit rules:

```kotlin
    fun currentSessionState(): ThemeColorPickerSessionState {
        return ThemeColorPickerSessionState(
            initialHex = initialHex,
            previewHex = currentPreviewHex(),
            initialTextFields = initialTextFields,
            currentTextFields = textFields,
            isGuided = isGuided,
            isGuidedSafeZoneReady = !isGuided || guidedSafeZone != null,
        )
    }
```

Then use it in the close/save paths:

```kotlin
    fun requestDismiss() {
        val state = currentSessionState()
        if (state.shouldPromptOnExit) {
            showExitDialog = true
        } else {
            onDismiss()
        }
    }

    fun commitCurrentColor() {
        val state = currentSessionState()
        if (!state.canCommit) {
            return
        }
        showExitDialog = false
        val result = onValueChange(currentPreviewHex())
        applyPreviewHex(
            hex = result.resolvedHex,
            adjusted = result.wasAdjusted,
        )
        onDismiss()
    }
```

Also disable the header `Save` and exit-dialog `Save` button from `state.canCommit`.

- [ ] **Step 5: Re-run the focused JVM tests to verify they pass**

Run:

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest --tests "com.epubreader.feature.settings.ThemeColorPickerSessionStateTest" --tests "com.epubreader.feature.settings.ThemeColorPickerTextEntryTest"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the session-state slice**

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerSessionState.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerSessionStateTest.kt feature/settings/src/test/java/com/epubreader/feature/settings/ThemeColorPickerTextEntryTest.kt
git commit -m "Fix picker dirty and partial-input session state"
```

### Task 2: Block guided interaction until the safe zone is ready and pin the race with focused overlay tests

**Files:**
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
- Create: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTest.kt`
- Create: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTestSupport.kt`

- [ ] **Step 1: Write the failing focused overlay race tests**

Create `feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTest.kt` with:

```kotlin
package com.epubreader.feature.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeColorPickerOverlayTest : ThemeColorPickerOverlayTestSupport() {

    @Test
    fun guidedPicker_loadingSafeZone_blocksSpectrumInputAndSave() {
        val readyGate = CompletableDeferred<Unit>()
        launchGuidedOverlay(
            initialHex = "#FF0000",
            safeZoneGate = readyGate,
        )

        composeRule.onNodeWithTag("overlay_picker_save").assertIsNotEnabled()
        setSpectrumPoint("overlay_picker_spectrum", saturation = 0.15f, value = 0.20f)
        assertPreviewHex("overlay_picker_preview", "#FF0000")

        readyGate.complete(Unit)
        waitUntilTagExists("overlay_picker_safe_zone")
        composeRule.onNodeWithTag("overlay_picker_save").assertIsEnabled()
    }

    @Test
    fun guidedPicker_fastSaveDuringLoading_doesNotCommitRawPreview() {
        val readyGate = CompletableDeferred<Unit>()
        launchGuidedOverlay(
            initialHex = "#FF0000",
            safeZoneGate = readyGate,
        )

        composeRule.onNodeWithTag("overlay_picker_save").performClick()
        waitUntilTagExists("overlay_picker_hex")
    }
}
```

- [ ] **Step 2: Run the focused overlay class to verify it fails**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.ThemeColorPickerOverlayTest
```

Expected: `FAIL` because there is no delayed safe-zone seam and save is still enabled before guided readiness is guaranteed.

- [ ] **Step 3: Add a tiny safe-zone resolver seam and loading-aware interaction policy**

Append to `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`:

```kotlin
internal fun interface ThemeColorPickerSafeZoneResolver {
    suspend fun resolve(
        hue: Float,
        previewColor: (String) -> ThemeColorPickerPreviewResult,
    ): ThemeColorPickerSafeZone
}

internal fun defaultThemeColorPickerSafeZoneResolver(
    cache: ThemeColorPickerSafeZoneCache,
): ThemeColorPickerSafeZoneResolver {
    return ThemeColorPickerSafeZoneResolver { hue, previewColor ->
        cache.zoneForHue(hue) { bucketedHue ->
            buildGuidedSafeZone(
                hue = bucketedHue,
                previewColor = previewColor,
            )
        }
    }
}
```

In `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`, replace the permissive null behavior:

```kotlin
private fun acceptsInteractivePoint(
    isGuided: Boolean,
    safeZone: ThemeColorPickerSafeZone?,
    point: ThemeColorPickerPoint,
): Boolean {
    return when {
        !isGuided -> true
        safeZone == null -> false
        else -> safeZone.contains(point)
    }
}
```

In `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`:

1. Add an optional internal resolver parameter:

```kotlin
internal fun ThemeColorPickerOverlay(
    ...,
    safeZoneResolverOverride: ThemeColorPickerSafeZoneResolver? = null,
) {
```

2. Use the resolver in `produceState`:

```kotlin
    val safeZoneResolver = remember(guidedPreviewValueChange, safeZoneCache, safeZoneResolverOverride) {
        when {
            guidedPreviewValueChange == null || safeZoneCache == null -> null
            safeZoneResolverOverride != null -> safeZoneResolverOverride
            else -> defaultThemeColorPickerSafeZoneResolver(safeZoneCache)
        }
    }
```

```kotlin
        value = withContext(Dispatchers.Default) {
            val preview = requireNotNull(guidedPreviewValueChange)
            requireNotNull(safeZoneResolver).resolve(pickerHue, preview)
        }
```

3. Disable save while loading and expose a loading semantics state:

```kotlin
    val sessionState = currentSessionState()
```

```kotlin
                    ThemeColorPickerCanvas(
                        ...,
                        safeZone = guidedSafeZone,
                        isGuided = isGuided,
                        loadingStateDescription = if (isGuided && guidedSafeZone == null) "loading" else "ready",
                    )
```

- [ ] **Step 4: Add the focused overlay support harness**

Create `feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTestSupport.kt` with:

```kotlin
package com.epubreader.feature.settings

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.rules.activityScenarioRule
import com.epubreader.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Rule

abstract class ThemeColorPickerOverlayTestSupport {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    protected fun launchGuidedOverlay(
        initialHex: String,
        safeZoneGate: CompletableDeferred<Unit>,
    ) {
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
                        safeZoneResolverOverride = ThemeColorPickerSafeZoneResolver { _, preview ->
                            safeZoneGate.await()
                            buildGuidedSafeZone(
                                hue = 0f,
                                previewColor = preview,
                            )
                        },
                    )
                }
            }
        }
    }

    protected fun launchUnguidedOverlay(initialHex: String) {
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

    protected fun waitUntilTagExists(tag: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
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
}
```

- [ ] **Step 5: Re-run the focused overlay class to verify it passes**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.ThemeColorPickerOverlayTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit the guided-loading slice**

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTestSupport.kt
git commit -m "Block guided picker interaction until safe zone is ready"
```

### Task 3: Make Back single-step with focused fields and stop guided mode switches from self-dirtying pickers

**Files:**
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorExitTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTest.kt`

- [ ] **Step 1: Write the failing focused-Back and mode-switch tests**

Append to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTest.kt`:

```kotlin
@Test
fun pickerHexField_focused_backShowsExitDialogOnFirstPress() {
    launchUnguidedOverlay(initialHex = "#4F46E5")
    composeRule.onNodeWithTag("overlay_picker_hex").performClick()
    replaceHexInput("overlay", "12AB")

    pressBack()
    waitUntilTagExists("overlay_picker_exit_dialog")
}
```

Append to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorExitTest.kt`:

```kotlin
@Test
fun editorNameField_focused_backShowsExitDialogOnFirstPress() {
    val theme = seedTheme("Focused Back")
    launchThemeEditor(theme)

    composeRule.onNodeWithTag("custom_theme_name").performClick()
    composeRule.onNodeWithTag("custom_theme_name").performTextInput(" 2")

    pressBack()
    composeRule.onNodeWithTag("theme_editor_exit_dialog").assertIsDisplayed()
}
```

Append to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`:

```kotlin
@Test
fun advancedToExtended_openingGuidedPicker_startsCleanWhenDraftWasRebalanced() {
    launchThemeEditor()
    selectThemeEditorMode("advanced")

    composeRule.onNodeWithTag("custom_theme_background_swatch").performScrollTo().performClick()
    replaceHexInput("custom_theme_background", "000000")
    tapHeaderSave("custom_theme_background")
    waitUntilTextContains("custom_theme_background", "#000000")

    composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
    replaceHexInput("custom_theme_system_text", "000000")
    tapHeaderSave("custom_theme_system_text")
    waitUntilTextContains("custom_theme_system_text", "#000000")
    saveThemeEditor()

    openCurrentThemeEditor()
    selectThemeEditorMode("extended")
    composeRule.onNodeWithTag("custom_theme_system_text_swatch").performScrollTo().performClick()
    requestCloseColorPicker("custom_theme_system_text")

    assertTagDoesNotExist("custom_theme_system_text_picker_exit_dialog")
}
```

- [ ] **Step 2: Run the focused Android tests to verify they fail**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.ThemeColorPickerOverlayTest,com.epubreader.feature.settings.SettingsThemeEditorExitTest,com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `FAIL` because the existing helpers still double-press Back and mode changes do not rebalance the draft before guided picker open.

- [ ] **Step 3: Remove the hidden double-Back fallback and rebalance on guided-mode entry**

In `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt`, replace:

```kotlin
internal fun SettingsThemeEditorGuidedPickerTest.pressActivityBack(testTagPrefix: String? = null) {
    pressBack()
    composeRule.waitForIdle()
    if (testTagPrefix == null) {
        return
    }
    ...
}
```

with:

```kotlin
internal fun SettingsThemeEditorGuidedPickerTest.pressActivityBack(testTagPrefix: String? = null) {
    pressBack()
    composeRule.waitForIdle()
    if (testTagPrefix != null) {
        waitUntilTagExists("${testTagPrefix}_picker_exit_dialog")
    }
}
```

In `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`, replace the mode-change lambda:

```kotlin
onModeChange = { mode ->
    val modeDraft = draft.copy(mode = mode)
    draft = when (mode) {
        ThemeEditorMode.ADVANCED -> modeDraft
        ThemeEditorMode.BASIC,
        ThemeEditorMode.EXTENDED,
        -> modeDraft.rebalanceGuidedFields() ?: modeDraft
    }
}
```

Also add a focused-field escape path in the picker dialog container before the dirty-exit handler:

```kotlin
modifier = Modifier.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp && event.key == Key.Back) {
        requestDismiss()
        true
    } else {
        false
    }
}
```

- [ ] **Step 4: Re-run the focused Android tests to verify they pass**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.ThemeColorPickerOverlayTest,com.epubreader.feature.settings.SettingsThemeEditorExitTest,com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the Back/mode-switch slice**

```powershell
git add feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTestSupport.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorExitTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/ThemeColorPickerOverlayTest.kt
git commit -m "Fix picker back handling and guided mode rebalance"
```

### Task 4: Pin the remaining end-to-end contracts and refresh continuity docs

**Files:**
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorExitTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Modify: `docs/agent_memory/step_history.md`
- Modify: `docs/agent_memory/next_steps.md`

- [ ] **Step 1: Add the missing persistence and exit-dialog regression tests**

Append to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`:

```kotlin
@Test
fun pickerSave_thenEditorDiscard_doesNotPersistPaletteChange() {
    launchSettingsScreen()
    waitUntilDisplayed("Settings")
    openAppearanceSection()

    composeRule.onNodeWithTag("create_custom_theme_button").performClick()
    waitUntilTagExists("custom_theme_name")
    composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
    composeRule.onNodeWithTag("custom_theme_name").performTextInput("Discarded Picker Save")
    saveThemeEditor()

    openCurrentThemeEditor()
    composeRule.onNodeWithTag("custom_theme_primary_swatch").performClick()
    replaceHexInput("custom_theme_primary", "FF0000")
    closeColorPicker("custom_theme_primary")
    waitUntilTextContains("custom_theme_primary", "#FF0000")

    composeRule.onNodeWithContentDescription("Close").performClick()
    waitUntilTagExists("theme_editor_exit_dialog")
    composeRule.onNodeWithTag("theme_editor_exit_discard").performClick()

    composeRule.waitUntil(10_000) {
        runBlocking { settingsManager.globalSettings.first() }
            .customThemes
            .any { theme -> theme.name == "Discarded Picker Save" && formatThemeColor(theme.palette.primary) != "#FF0000" }
    }
}
```

Append to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorExitTest.kt`:

```kotlin
@Test
fun invalidDraft_exitDialogSaveRemainsDisabled() {
    val theme = seedTheme("Invalid Draft")
    launchThemeEditor(theme)

    composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
    pressBack()

    composeRule.onNodeWithTag("theme_editor_exit_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("theme_editor_exit_save").assertIsNotEnabled()
}
```

Append to `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`:

```kotlin
@Test
fun pickerOutsideTap_whenClean_stillDoesNothing() {
    launchThemeEditor()
    composeRule.onNodeWithTag("custom_theme_primary_swatch").performScrollTo().performClick()

    dismissColorPickerByOutsideTap("custom_theme_primary_picker_backdrop")
    waitUntilTagExists("custom_theme_primary_picker_hex")
}
```

- [ ] **Step 2: Run the end-to-end settings slices**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsScreenPersistenceTest,com.epubreader.feature.settings.SettingsThemeEditorExitTest,com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `FAIL` until the new persistence/exit expectations are fully wired.

- [ ] **Step 3: Align the existing helpers to the new assertions and re-run the settings slices**

Update only the existing shared helpers if the new persistence/exit tests need tag access that is already conceptually present. Do not add a second “save picker then close editor” path; reuse `replaceHexInput(...)`, `closeColorPicker(...)`, `saveThemeEditor()`, and the existing settings-manager polling style. Then re-run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsScreenPersistenceTest,com.epubreader.feature.settings.SettingsThemeEditorExitTest,com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run full verification**

Run:

```powershell
.\gradlew.bat --console=plain :feature:settings:testDebugUnitTest
.\gradlew.bat --console=plain --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.ThemeColorPickerOverlayTest
.\gradlew.bat --console=plain --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
.\gradlew.bat --console=plain --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorExitTest
.\gradlew.bat --console=plain --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsScreenPersistenceTest
.\gradlew.bat --console=plain checkKotlinFileLineLimit
.\gradlew.bat --console=plain :app:installDebug
python scripts/check_graph_staleness.py
```

Expected: all commands succeed and `SettingsThemeColorPicker.kt` plus the test helpers remain under the line-limit guard.

- [ ] **Step 5: Update continuity docs**

Append this new numbered entry to `docs/agent_memory/step_history.md`:

```markdown
## 130. 2026-04-29 00:00
- Agent model: Codex GPT-5
- Agent name: Codex
- Task goal: Write the merge-blocker follow-up plan for the theme spectrum picker after code review surfaced a guided-loading race, focused-field Back masking, guided-mode self-dirtying, and incomplete numeric-entry save ambiguity.
- Area/files: `docs/superpowers/plans/2026-04-29-theme-spectrum-picker-merge-blockers.md`, `docs/agent_memory/next_steps.md`.
- Action taken:
  1. Mapped the findings back to `SettingsThemeColorPicker.kt`, `ThemeColorPickerCanvas.kt`, `ThemeColorPickerTextEntry.kt`, `SettingsThemeEditor.kt`, and the existing settings instrumentation suites.
  2. Replaced the generic merge/smoke follow-up with a task-by-task implementation plan centered on guided readiness, honest dirty/commit semantics, single-step Back handling, and missing persistence coverage.
  3. Kept the plan scoped to `feature/settings` and avoided DataStore/schema changes.
- Result:
  - The active branch now has an execution-ready plan for the exact pre-merge blockers instead of a generic “smoke or merge” note.
- Verification:
  - Review findings re-read against current production/test files
  - Existing verification baseline from the pre-merge review retained
```

Replace the first item in `docs/agent_memory/next_steps.md` with a note that this new plan is the active follow-up:

```markdown
## Theme Picker Merge-Blocker Follow-Up
- Goal: Execute `docs/superpowers/plans/2026-04-29-theme-spectrum-picker-merge-blockers.md` before merging `codex/theme-spectrum-picker` back to `1.1.2-UI+FPS`.
- Why now: The pre-merge review found one high-severity guided-preview trust break plus medium contract gaps around focused-field Back, guided-mode self-dirtying, partial numeric drafts, and unpinned picker-save-versus-editor-discard persistence.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/settings_persistence.md`, `docs/superpowers/plans/2026-04-29-theme-spectrum-picker-merge-blockers.md`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerTextEntry.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`, `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`, `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorExitTest.kt`, `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`.
- Risks: Merging a guided picker that can confirm one color and save another, keeping the current hidden double-Back test behavior, or treating partial invalid numeric input as a real saveable edit.
- Verification target: Run the full verification block in the new plan before merging.
```

- [ ] **Step 6: Commit the plan-driven continuity refresh**

```powershell
git add docs/agent_memory/step_history.md docs/agent_memory/next_steps.md docs/superpowers/plans/2026-04-29-theme-spectrum-picker-merge-blockers.md
git commit -m "Add theme picker merge blocker follow-up plan"
```
