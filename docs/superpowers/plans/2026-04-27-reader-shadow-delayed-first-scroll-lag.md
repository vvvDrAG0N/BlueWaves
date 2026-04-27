# Reader Shadow Delayed First-Scroll Lag Plan

## Goal

Remove avoidable reader startup work from the default non-selectable-text path so the `Shadow Slave 1435 / 2927 ch` delayed first-scroll case no longer clusters late first-touch jank around the first render.

## Why This Plan Exists

The fresh real-phone retest on 2026-04-27 showed that the scary delayed `Shadow Slave` result is not just stale-report noise:

- exact-state release-like reruns landed at `135`, `145`, and `61` high-input-latency
- exact-state delayed traces landed at `242`, `298`, and `271` high-input-latency
- the strongest trace signal points at cold-start / dex / first-render / Compose traversal overlap

The current reader runtime likely does extra work on the default path:

- `ReaderFeatureShell.kt` already builds `chapterSections`
- `EpubReaderRuntime.kt` builds `chapterSections` again
- `EpubReaderRuntime.kt` always builds a `selectionDocument`
- `ReaderChapterSelectionHost.kt` does selection-map/state work before early-returning when `selectableText == false`
- `ReaderSelectableTextSection.kt` still publishes layout snapshots through selection-oriented structure even when selection is off by default

This plan targets that narrow path first instead of widening into parser or theme work.

## Assumed Scope

- In scope:
  - the EPUB reader runtime path only
  - the default `selectableText = false` experience
  - removing duplicate section-building and bypassing selection scaffolding when selection is off
  - local regression tests plus a focused physical-phone rerun of the exact scary lane
- Out of scope:
  - Theme / Appearance
  - parser architecture changes
  - DataStore schema/default changes
  - broad reader behavior redesign
  - selection-enabled behavior changes beyond preserving current behavior

## Files To Touch

- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/ui/ReaderScreenChrome.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderRuntimeRenderingPlan.kt` new
- `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderRuntimeRenderingPlanTest.kt` new
- `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSelectableTextStructureTest.kt`

## Guardrails

- Do not touch reader restoration timing or semantics.
- Do not change `SettingsManager` defaults or persisted keys.
- Keep selection-enabled behavior intact.
- Keep adjacent prefetch rules intact unless a later verified trace proves they are still on the hot path.
- Keep files under the repo size limits; extract helpers rather than fattening runtime files.

## Task 1: Add A Narrow Rendering-Plan Helper

### Goal

Create one small runtime helper that makes the expensive branches explicit:

- reuse prebuilt `chapterSections`
- only build `selectionDocument` when `selectableText` is enabled

### Files

- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderRuntimeRenderingPlan.kt`
- `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderRuntimeRenderingPlanTest.kt`

### Implementation Shape

Create a tiny plan model:

```kotlin
internal data class ReaderRuntimeRenderingPlan(
    val chapterSections: List<ReaderChapterSection>,
    val selectionDocument: ReaderSelectionDocument?,
)

internal fun buildReaderRuntimeRenderingPlan(
    chapterSections: List<ReaderChapterSection>,
    selectableTextEnabled: Boolean,
): ReaderRuntimeRenderingPlan {
    return ReaderRuntimeRenderingPlan(
        chapterSections = chapterSections,
        selectionDocument = if (selectableTextEnabled) {
            ReaderSelectionDocument.fromChapterSections(chapterSections)
        } else {
            null
        },
    )
}
```

### Test First

Add focused JVM tests that prove:

1. when `selectableTextEnabled = false`, the helper reuses the passed sections and returns `selectionDocument = null`
2. when `selectableTextEnabled = true`, the helper builds a non-null selection document from the provided sections

### Verification

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderRuntimeRenderingPlanTest
```

Commit checkpoint:

```text
test: cover reader runtime rendering plan gating
```

## Task 2: Thread Prebuilt Sections Through The Reader Path

### Goal

Stop rebuilding `chapterSections` inside the production reader runtime when the shell already has them.

### Files

- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/ui/ReaderScreenChrome.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`

### Planned Changes

1. Add `chapterSections` to `ReaderChromeState`, with a default derived from `chapterElements` so existing tests do not need broad fixture churn.
2. Update `buildReaderChromeState(...)` to accept and pass through prebuilt `chapterSections`.
3. Pass the shell-built `chapterSections` from `ReaderFeatureShell.kt`.
4. Pass `state.chapterSections` from `ReaderScreenChrome.kt` into `ReaderChapterContent(...)`.
5. Extend `ReaderChapterContent(...)` with an optional `chapterSections` parameter defaulted from `buildReaderChapterSections(chapterElements)` so direct test callers remain stable.

### Verification

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderScreenContractsTest
```

Commit checkpoint:

```text
refactor: reuse prebuilt reader chapter sections
```

## Task 3: Bypass Selection Scaffolding When Selection Is Off

### Goal

On the default reader path, render chapter text without building or hosting selection infrastructure.

### Files

- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
- `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSelectableTextStructureTest.kt`

### Test First

Add a new instrumentation test:

```text
selectableTextDisabled_rendersPlainTextSectionsWithoutSelectionScaffolding
```

Expectation:

- `reader_selectable_text_item` count is `0`
- plain text section node count is present for the same content path

This should fail before the implementation because the current runtime still routes through selectable-text structure even when selection is off.

### Implementation Shape

In `EpubReaderRuntime.kt`:

1. Build `renderingPlan` with:

```kotlin
val renderingPlan = remember(chapterSections, settings.selectableText) {
    buildReaderRuntimeRenderingPlan(
        chapterSections = chapterSections,
        selectableTextEnabled = settings.selectableText,
    )
}
```

2. Branch the content path:
   - if `renderingPlan.selectionDocument != null`, keep the existing selection-host path
   - if `renderingPlan.selectionDocument == null`, render a plain `LazyColumn` section list that uses existing non-selection text blocks and keeps scroll/restoration behavior intact
3. Extract any shared list/layout plumbing needed to keep `EpubReaderRuntime.kt` readable and under size limits

### Verification

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderSelectableTextStructureTest#selectableTextDisabled_rendersPlainTextSectionsWithoutSelectionScaffolding
```

Then rerun the focused structure class:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderSelectableTextStructureTest
```

Commit checkpoint:

```text
perf: skip reader selection scaffolding when selection is off
```

## Task 4: Run Reader Regression Slices

### Goal

Prove the change did not destabilize reader structure, restoration, or prefetch rules.

### Verification Commands

```powershell
.\gradlew.bat checkKotlinFileLineLimit verifyTestChecklistReferences
```

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderRuntimeRenderingPlanTest --tests com.epubreader.feature.reader.ReaderChapterSectionsTest --tests com.epubreader.feature.reader.ReaderSelectionDocumentTest --tests com.epubreader.feature.reader.ReaderScreenPrefetchTest --tests com.epubreader.feature.reader.ReaderScreenContractsTest
```

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderSelectableTextStructureTest
```

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderScreenRestorationTest
```

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderScreenOverscrollTest
```

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChromeTapBehaviorTest
```

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest
```

### Manual Check

On emulator or device, confirm:

- normal reader open still restores to the expected place
- one tap still toggles chrome
- long-press selection still works when selectable text is enabled
- no visual regression appears in chapter text layout

Commit checkpoint:

```text
test: cover reader selection-off runtime path
```

## Task 5: Prove The Perf Change On The Actual Phone

### Goal

Measure only the scary lane again after the code change.

### Device

Use the current real-phone serial:

```text
adb-FY2434410A95-pebaQK._adb-tls-connect._tcp
```

### Release-Like Validation

Build and install:

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
```

```powershell
adb -s "adb-FY2434410A95-pebaQK._adb-tls-connect._tcp" install -r -d "app/build/outputs/apk/release/app-release-debugsigned.apk"
```

Run the focused delayed probe:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_reader_lag_shadow_delayed_probe.ps1 -DeviceSerial "adb-FY2434410A95-pebaQK._adb-tls-connect._tcp" -Mode release -RepeatCount 1 -OutputDir "logs/reader-lag-shadow-delayed-release-postfix-$(Get-Date -Format yyyyMMdd-HHmmss)"
```

Important:

- re-place the phone at `Shadow Slave 1435 / 2927 ch` before each release-like rerun
- do not rely on the old chapter-1 Shadow snapshot

### Optional Trace Follow-Up

Only if the release-like rerun still feels rough:

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

```powershell
adb -s "adb-FY2434410A95-pebaQK._adb-tls-connect._tcp" install -r -d "app/build/outputs/apk/debug/app-debug.apk"
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_reader_lag_shadow_delayed_probe.ps1 -DeviceSerial "adb-FY2434410A95-pebaQK._adb-tls-connect._tcp" -Mode trace -RepeatCount 3 -SnapshotTarPath "logs/reader-lag-shadow-midbook-snapshot-20260427-0206/shadow-1435-2927-cmd.tar" -PythonExe python -OutputDir "logs/reader-lag-shadow-delayed-trace-postfix-$(Get-Date -Format yyyyMMdd-HHmmss)"
```

Reinstall the release-like APK afterward.

### Success Criteria

- the focused release-like delayed lane improves materially versus the prior `135 / 145 / 61` reruns
- if traces are needed, the late first-touch cluster around roughly `20.7s` to `21.5s` after startup is meaningfully reduced

## Recommended Execution Order

1. Add the rendering-plan helper and its JVM tests.
2. Thread shell-built `chapterSections` through the reader state path.
3. Add the failing structure test for selection-off rendering.
4. Implement the selection-off runtime bypass.
5. Run the reader regression slices.
6. Run the exact scary-lane phone validation.
7. Update the portable perf report only if the new phone run materially changes the verdict.

## Risks To Watch

- accidentally touching restoration-sensitive runtime behavior while simplifying the render path
- leaving hidden coupling to selection-enabled flows and breaking long-press behavior
- making the runtime file too large instead of extracting a small helper
- reading too much into trace-only wins if the release-like phone lane does not improve

## Execution Recommendation

Use Subagent-Driven Execution for implementation if you want the code change and the phone verification split cleanly. Inline execution is still fine if you want one agent to carry the entire fix from code through device retest.
