# Reader Selection Handle Stem Reduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce both Reader selectable-text handle stems to 50% of their current length while keeping start/end geometry, drag pickup, and semantics in sync.

**Architecture:** Keep ownership at the reader runtime host. Add a targeted instrumentation regression test in `ReaderChapterSelectionHostTest.kt` that derives stem height from `ReaderSelectionHandleSemanticsData`, then change `ReaderChapterSelectionHost.kt` to pass half-height stems into the existing `ReaderSelectionHandleLayer`. Leave `ReaderSelectionHandles.kt` and `ReaderSelectionGeometry.kt` behavior intact so the shorter host-provided height automatically flows through rendering, layout, semantics, and drag pickup.

**Tech Stack:** Kotlin, Jetpack Compose, Android instrumentation tests, JVM unit tests, JUnit4, Gradle

---

## File Structure

- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTest.kt`
  - Add the red/green instrumentation regression that asserts both handles expose half-height stems for a known `fontSize`.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
  - Halve the host-derived `handleStemHeightPx` before it is passed into `ReaderSelectionHandleLayer`.
- Verify only: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderSelectionGeometryTest.kt`
  - Re-run the existing mirrored-layout JVM test without changing its production coverage.

Workspace guard:

- The reader selection files already have uncommitted workspace changes. Do not revert or rewrite unrelated edits in `ReaderSelectionHandles.kt`, `ReaderSelectionGeometry.kt`, `ReaderSelectionLayoutRegistry.kt`, or `ReaderSelectionHandleBehaviorTest.kt`.

### Task 1: Add the failing handle-height regression test

**Files:**
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTest.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test next to the existing handle semantics coverage:

```kotlin
@Test
fun activeSelection_usesHalfHeightStemsForBothSelectionHandles() {
    val fontSizeSp = 24

    composeRule.runOnUiThread {
        composeRule.activity.setContent {
            MaterialTheme {
                ReaderSelectionTestSurface(
                    settings = GlobalSettings(
                        selectableText = true,
                        fontSize = fontSizeSp,
                    ),
                    chapterElements = listOf(
                        ChapterElement.Text("Scholarship", id = "p1"),
                    ),
                )
            }
        }
    }

    activateSelection()
    composeRule.waitForIdle()

    val startSemantics = composeRule.onNodeWithTag("reader_selection_handle_start", useUnmergedTree = true)
        .fetchSemanticsNode().config[ReaderSelectionHandleSemanticsKey]
    val endSemantics = composeRule.onNodeWithTag("reader_selection_handle_end", useUnmergedTree = true)
        .fetchSemanticsNode().config[ReaderSelectionHandleSemanticsKey]

    val expectedStemHeightPx =
        composeRule.activity.resources.displayMetrics.scaledDensity * fontSizeSp * 0.5f
    val startStemHeightPx = startSemantics.stemBottomYInHandle - startSemantics.stemTopYInHandle
    val endStemHeightPx = endSemantics.stemBottomYInHandle - endSemantics.stemTopYInHandle

    assertEquals(expectedStemHeightPx, startStemHeightPx, 1f)
    assertEquals(expectedStemHeightPx, endStemHeightPx, 1f)
    assertEquals(startStemHeightPx, endStemHeightPx, 1f)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
.\gradlew :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostTest#activeSelection_usesHalfHeightStemsForBothSelectionHandles"
```

Expected: `FAIL` because `handleStemHeightPx` still equals the full `fontSize.sp.toPx()`, so one or both stem-height assertions report roughly double the expected value.

### Task 2: Implement the minimal host-side stem reduction

**Files:**
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTest.kt`

- [ ] **Step 1: Write the minimal implementation**

Add a named scale constant near the top-level declarations in `ReaderChapterSelectionHost.kt`, then apply it where the host derives `handleStemHeightPx`:

```kotlin
private const val ReaderSelectionHandleStemHeightScale = 0.5f
```

```kotlin
val handleStemHeightPx = with(density) {
    settings.fontSize.sp.toPx() * ReaderSelectionHandleStemHeightScale
}
```

Do not change `ReaderSelectionHandles.kt` or `ReaderSelectionGeometry.kt` in this task. The shorter host-provided height should flow through the existing pipeline unchanged.

- [ ] **Step 2: Run the targeted test to verify it passes**

Run:

```powershell
.\gradlew :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostTest#activeSelection_usesHalfHeightStemsForBothSelectionHandles"
```

Expected: `PASS`

### Task 3: Run non-regression verification and commit

**Files:**
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTest.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
- Verify: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderSelectionGeometryTest.kt`

- [ ] **Step 1: Re-run the existing mirrored-layout JVM test**

Run:

```powershell
.\gradlew :feature:reader:testDebugUnitTest --tests "com.epubreader.feature.reader.ReaderSelectionGeometryTest.resolveReaderSelectionHandleLayout_mirrorsTheHandlesAroundTheSelectedText"
```

Expected: `PASS`

- [ ] **Step 2: Re-run the whole host selection instrumentation class**

Run:

```powershell
.\gradlew :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostTest"
```

Expected: `PASS`

- [ ] **Step 3: Commit the implementation**

Run:

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTest.kt
git commit -m "reader: shorten selection handle stems"
```

Expected: a single commit containing only the host stem-scaling change and the new regression test.
