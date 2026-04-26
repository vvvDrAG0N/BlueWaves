# Guided Picker Dismiss And Test Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the guided theme color picker treat BACK and outside-tap dismissal as cancel, while keeping `Done` as the only explicit commit path, and strengthen the settings instrumentation tests so they exercise the real picker close flow before saving.

**Architecture:** Keep the behavior fix local to `ThemeColorPickerOverlay` by splitting confirm and dismiss semantics instead of changing draft persistence elsewhere. Cover the runtime behavior with one guided-picker instrumentation test and harden the existing persistence test so it explicitly closes the dialog before saving the theme.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 `AlertDialog`, Compose UI instrumentation tests, AndroidX Test, Gradle.

---

## File Structure

- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
  - Owns guided picker dialog behavior, including `Done`, BACK, outside-tap dismissal, and pending guided slider state.
- `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
  - Owns behavior-focused instrumentation around guided-vs-advanced picker semantics and guided readability adjustments.
- `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`
  - Owns end-to-end persistence checks through `SettingsScreen`, including custom theme save behavior.
- `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTestSupport.kt`
  - Shared instrumentation helpers for theme editor, gallery, and picker interactions. Add picker-close helpers here instead of duplicating them per test class.

## Scope Guard

- Do not change Appearance-tab theme swipe behavior. The user explicitly confirmed that auto-apply is intentional for that surface.
- Do not change `SettingsManager`, theme persistence schema, or theme generation rules.
- Do not change advanced-mode literal editing behavior. This plan only fixes guided-picker cancel semantics and the test coverage gap around real picker closure.

### Task 1: Guided Picker Cancel Path

**Files:**
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt`
- Modify: `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`

- [ ] **Step 1: Write the failing instrumentation test for guided cancel behavior**

```kotlin
@Test
fun basicAccent_backDismiss_discardsPendingGuidedChoice() {
    launchThemeEditor()

    composeRule.onNodeWithTag("custom_theme_primary_swatch").performClick()
    setSliderProgress("custom_theme_primary_picker_hue", 0f)
    setSliderProgress("custom_theme_primary_picker_saturation", 1f)
    setSliderProgress("custom_theme_primary_picker_value", 1f)
    androidx.test.espresso.Espresso.pressBack()
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("custom_theme_primary").assertTextContains("#4F46E5")
    assertTagDoesNotExist("custom_theme_primary_picker_hue")
}
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest#basicAccent_backDismiss_discardsPendingGuidedChoice
```

Expected: FAIL because pressing BACK currently routes through `dismissPicker()`, which calls `commitPendingColor()` first, so the accent field mutates instead of staying at `#4F46E5`.

- [ ] **Step 3: Split confirm and dismiss paths in `ThemeColorPickerOverlay`**

```kotlin
val dismissPicker = {
    onDismiss()
}

val confirmPicker = {
    commitPendingColor()
    onDismiss()
}

AlertDialog(
    onDismissRequest = dismissPicker,
    confirmButton = { TextButton(onClick = confirmPicker) { Text("Done") } },
    title = { Text("$label Color") },
)
```

Implementation note:

```kotlin
// Keep guided slider changes dialog-local until Done.
// BACK and outside tap should close without calling commitPendingColor().
```

- [ ] **Step 4: Re-run the targeted guided-picker test and the existing guided suite**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest#basicAccent_backDismiss_discardsPendingGuidedChoice
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest
```

Expected: PASS. The new test should show that BACK dismiss leaves the accent field unchanged, and the existing `Done`-based guided tests should still pass.

- [ ] **Step 5: Commit the picker cancel fix**

```bash
git add feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsThemeEditorGuidedPickerTest.kt
git commit -m "fix: cancel guided theme picker dismissals"
```

### Task 2: Harden Persistence Coverage Around Real Picker Closure

**Files:**
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`
- Modify: `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTestSupport.kt`

- [ ] **Step 1: Make the persistence test assert that the picker is closed before save**

Update the existing test body so it requires the dialog to be gone before `Save` is pressed:

```kotlin
@Test
fun customThemeColorPicker_updatesHexFieldAndSavedPalette() {
    launchSettingsScreen()
    waitUntilDisplayed("Settings")
    openAppearanceSection()

    composeRule.onNodeWithTag("create_custom_theme_button").performClick()
    waitUntilTagExists("custom_theme_name")
    composeRule.onNodeWithTag("custom_theme_name").performTextClearance()
    composeRule.onNodeWithTag("custom_theme_name").performTextInput("Sunset")
    composeRule.onNodeWithTag("custom_theme_primary_swatch").performClick()

    setSliderProgress("custom_theme_primary_picker_hue", 0f)
    setSliderProgress("custom_theme_primary_picker_saturation", 1f)
    setSliderProgress("custom_theme_primary_picker_value", 1f)

    waitUntilTextContains("custom_theme_primary", "#FF0000")
    composeRule.waitUntil(5_000) { !tagExists("custom_theme_primary_picker_hue") }
    composeRule.onNodeWithContentDescription("Save").performClick()

    composeRule.waitUntil(10_000) {
        val settings = runBlocking { settingsManager.globalSettings.first() }
        settings.customThemes.any { theme ->
            theme.name == "Sunset" && formatThemeColor(theme.palette.primary) == "#FF0000"
        }
    }
}
```

- [ ] **Step 2: Run the persistence test to verify it fails before the helper/change**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsScreenPersistenceTest#customThemeColorPicker_updatesHexFieldAndSavedPalette
```

Expected: FAIL because the current test never closes the picker, so the direct `composeRule.waitUntil(5_000) { !tagExists("custom_theme_primary_picker_hue") }` check times out.

- [ ] **Step 3: Add a shared picker-close helper and call it from the persistence test**

In `SettingsScreenPersistenceTestSupport.kt` add:

```kotlin
internal fun SettingsScreenPersistenceTestBase.closeColorPicker() {
    composeRule.onNodeWithText("Done").performClick()
    composeRule.waitForIdle()
}

internal fun SettingsScreenPersistenceTestBase.waitUntilTagGone(
    tag: String,
    timeoutMillis: Long = 10_000,
) {
    composeRule.waitUntil(timeoutMillis) { !tagExists(tag) }
}
```

Then update the persistence test:

```kotlin
waitUntilTextContains("custom_theme_primary", "#FF0000")
closeColorPicker()
waitUntilTagGone("custom_theme_primary_picker_hue")
composeRule.onNodeWithContentDescription("Save").performClick()
```

- [ ] **Step 4: Re-run the persistence test and one broader Settings instrumentation slice**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsScreenPersistenceTest#customThemeColorPicker_updatesHexFieldAndSavedPalette
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsScreenPersistenceTest,com.epubreader.feature.settings.SettingsScreenThemeEditorPersistenceTest
```

Expected: PASS. The save-path test should now exercise the real `Done` close flow, and the nearby theme-editor persistence tests should remain green.

- [ ] **Step 5: Commit the coverage hardening**

```bash
git add feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTestSupport.kt
git commit -m "test: harden theme picker save coverage"
```

## Final Verification Sweep

- [ ] **Step 1: Compile the Android test source set after both tasks**

Run:

```powershell
.\gradlew.bat :feature:settings:compileDebugAndroidTestKotlin
```

Expected: PASS.

- [ ] **Step 2: Run the smallest combined runtime verification slice**

Run:

```powershell
.\gradlew.bat --% :feature:settings:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.settings.SettingsThemeEditorGuidedPickerTest,com.epubreader.feature.settings.SettingsScreenPersistenceTest
```

Expected: PASS for the guided picker runtime behavior and the save-path coverage.

- [ ] **Step 3: Manual sanity check**

Manual flow:

```text
Settings -> Appearance -> Create Theme -> tap Accent swatch
Move sliders in Basic or Extended mode
Press BACK
Expected: dialog closes and the color field stays unchanged

Reopen the swatch
Move sliders again
Tap Done
Expected: the color field updates, then Save persists the palette
```

## Self-Review

- Spec coverage:
  - Finding 1 is covered by Task 1.
  - Finding 3 is covered by Task 2.
  - Finding 2 is intentionally excluded because the user explicitly confirmed that Appearance auto-apply is desired behavior.
- Placeholder scan:
  - No `TODO`, `TBD`, or "similar to above" shortcuts remain.
- Type consistency:
  - All referenced helpers and tags match current repo names: `ThemeColorPickerOverlay`, `custom_theme_primary_swatch`, `custom_theme_primary_picker_hue`, `SettingsThemeEditorGuidedPickerTest`, `SettingsScreenPersistenceTest`, and `SettingsScreenPersistenceTestSupport`.
