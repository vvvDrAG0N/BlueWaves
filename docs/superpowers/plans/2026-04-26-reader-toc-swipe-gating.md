# Reader TOC Swipe Gating Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow TOC swipe gestures while `selectableText` is enabled but no selection session is active, while preserving the existing block during an active text-selection session.

**Architecture:** Keep the change local to the reader chrome gesture gate. Extract a tiny pure helper for drawer-gesture eligibility, switch `ModalNavigationDrawer.gesturesEnabled` to that helper, and cover the new behavior with one JVM contract test plus focused reader chrome instrumentation. Do not change selection arming, drawer ownership, BACK order, TOC callbacks, or reader shell state flow.

**Tech Stack:** Kotlin, Jetpack Compose Material3 `ModalNavigationDrawer`, JUnit JVM tests, Android Compose instrumentation tests, Gradle

---

## File Map

- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
  - Add one pure helper for TOC drawer gesture eligibility.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt`
  - Replace the inline `gesturesEnabled` expression with the helper.
- `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenContractsTest.kt`
  - Add focused JVM coverage for the new helper contract.
- `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChromeTapBehaviorTest.kt`
  - Add runtime gesture regressions for:
    - selectable text enabled + no active selection session -> drawer swipe opens
    - active selection session -> drawer swipe stays blocked

## Risk Notes

- The main behavior risk is left-edge gesture arbitration: an edge swipe can now win before long-press selection arms if the user moves horizontally near the left edge.
- This plan intentionally does **not** change:
  - selection long-press gesture code
  - `ReaderSelectableTextSection.kt`
  - TOC open/close callbacks in the shell
  - drawer BACK behavior
- Manual smoke must explicitly cover long-pressing the first visible characters near the left edge after the code change.

### Task 1: Extract The Drawer Gesture Contract

**Files:**
- Modify: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenContractsTest.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`

- [ ] **Step 1: Write the failing JVM tests**

Add these tests to `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenContractsTest.kt`:

```kotlin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

@Test
fun shouldEnableReaderTocDrawerGestures_returnsTrueWhenNoSelectionSessionIsActive() {
    assertTrue(
        shouldEnableReaderTocDrawerGestures(
            isTextSelectionSessionActive = false,
        ),
    )
}

@Test
fun shouldEnableReaderTocDrawerGestures_returnsFalseWhenSelectionSessionIsActive() {
    assertFalse(
        shouldEnableReaderTocDrawerGestures(
            isTextSelectionSessionActive = true,
        ),
    )
}
```

- [ ] **Step 2: Run the JVM test to verify it fails**

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderScreenContractsTest
```

Expected: FAIL with an unresolved reference for `shouldEnableReaderTocDrawerGestures`.

- [ ] **Step 3: Write the minimal helper implementation**

Add this helper next to the existing small reader contract helpers in `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`:

```kotlin
internal fun shouldEnableReaderTocDrawerGestures(
    isTextSelectionSessionActive: Boolean,
): Boolean {
    return !isTextSelectionSessionActive
}
```

- [ ] **Step 4: Run the JVM test to verify it passes**

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderScreenContractsTest
```

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenContractsTest.kt
git commit -m "test: cover reader TOC drawer gesture contract"
```

### Task 2: Wire The Helper Into Reader Chrome And Add Runtime Regressions

**Files:**
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChromeTapBehaviorTest.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt`

- [ ] **Step 1: Write the failing instrumentation regression**

Add a left-edge swipe helper and these two tests to `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChromeTapBehaviorTest.kt`:

```kotlin
private fun swipeRightFromDrawerEdge() {
    composeRule.onNodeWithTag("reader_content_surface", useUnmergedTree = true).performTouchInput {
        down(Offset(1f, centerY))
        repeat(6) {
            moveBy(Offset(width * 0.12f, 0f))
            advanceEventTime(16)
        }
        up()
    }
}

@Test
fun selectableTextEnabled_swipingFromLeftEdgeOpensTocWhenNoSelectionSessionIsActive() {
    lateinit var drawerState: androidx.compose.material3.DrawerState

    composeRule.runOnUiThread {
        composeRule.activity.setContent {
            drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val listState = rememberLazyListState()
            val tocListState = rememberLazyListState()
            val progressState = remember { mutableFloatStateOf(0f) }
            val overscrollState = remember { mutableFloatStateOf(0f) }

            val state = ReaderChromeState(
                book = EpubBook(
                    id = "book-1",
                    title = "Sample",
                    author = "Author",
                    coverPath = null,
                    rootPath = composeRule.activity.filesDir.absolutePath,
                    spineHrefs = listOf("chapter-1.xhtml"),
                ),
                settings = GlobalSettings(selectableText = true),
                themeColors = getThemeColors("light"),
                drawerState = drawerState,
                listState = listState,
                tocListState = tocListState,
                currentChapterIndex = 0,
                chapterElements = listOf(ChapterElement.Text("Paragraph one", id = "p1")),
                renderedItemCount = 1,
                isLoadingChapter = false,
                showControls = false,
                isTextSelectionSessionActive = false,
                tocSort = TocSort.Ascending,
                sortedToc = emptyList(),
                verticalOverscrollState = overscrollState,
                overscrollThreshold = 80f,
                nestedScrollConnection = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {} },
                progressPercentageState = progressState,
                selectionSessionEpoch = 0,
            )
            val callbacks = ReaderChromeCallbacks(
                onShowControlsChange = {},
                onTextSelectionActiveChange = { _, _ -> },
                onClearTextSelection = {},
                onToggleTocSort = {},
                onReleaseOverscroll = {},
                onSaveAndBack = {},
                onOpenToc = {},
                onCloseToc = {},
                onLocateCurrentChapterInToc = {},
                onJumpToChapter = {},
                onSelectTocChapter = {},
                onPreviewSettings = {},
                onPersistSettings = {},
                onNavigatePrev = {},
                onNavigateNext = {},
                onMainScrubberDragStart = {},
            )

            MaterialTheme {
                ReaderScreenChrome(
                    state = state,
                    callbacks = callbacks,
                )
            }
        }
    }

    composeRule.waitForIdle()
    swipeRightFromDrawerEdge()

    composeRule.waitUntil(5_000) {
        drawerState.currentValue == DrawerValue.Open
    }
}

@Test
fun activeSelectionSession_swipingFromLeftEdgeDoesNotOpenToc() {
    lateinit var drawerState: androidx.compose.material3.DrawerState

    composeRule.runOnUiThread {
        composeRule.activity.setContent {
            drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val listState = rememberLazyListState()
            val tocListState = rememberLazyListState()
            val progressState = remember { mutableFloatStateOf(0f) }
            val overscrollState = remember { mutableFloatStateOf(0f) }

            val state = ReaderChromeState(
                book = EpubBook(
                    id = "book-1",
                    title = "Sample",
                    author = "Author",
                    coverPath = null,
                    rootPath = composeRule.activity.filesDir.absolutePath,
                    spineHrefs = listOf("chapter-1.xhtml"),
                ),
                settings = GlobalSettings(selectableText = true),
                themeColors = getThemeColors("light"),
                drawerState = drawerState,
                listState = listState,
                tocListState = tocListState,
                currentChapterIndex = 0,
                chapterElements = listOf(ChapterElement.Text("Paragraph one", id = "p1")),
                renderedItemCount = 1,
                isLoadingChapter = false,
                showControls = false,
                isTextSelectionSessionActive = true,
                tocSort = TocSort.Ascending,
                sortedToc = emptyList(),
                verticalOverscrollState = overscrollState,
                overscrollThreshold = 80f,
                nestedScrollConnection = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {} },
                progressPercentageState = progressState,
                selectionSessionEpoch = 0,
            )
            val callbacks = ReaderChromeCallbacks(
                onShowControlsChange = {},
                onTextSelectionActiveChange = { _, _ -> },
                onClearTextSelection = {},
                onToggleTocSort = {},
                onReleaseOverscroll = {},
                onSaveAndBack = {},
                onOpenToc = {},
                onCloseToc = {},
                onLocateCurrentChapterInToc = {},
                onJumpToChapter = {},
                onSelectTocChapter = {},
                onPreviewSettings = {},
                onPersistSettings = {},
                onNavigatePrev = {},
                onNavigateNext = {},
                onMainScrubberDragStart = {},
            )

            MaterialTheme {
                ReaderScreenChrome(
                    state = state,
                    callbacks = callbacks,
                )
            }
        }
    }

    composeRule.waitForIdle()
    swipeRightFromDrawerEdge()
    composeRule.waitForIdle()

    assertFalse(drawerState.isOpen)
}
```

- [ ] **Step 2: Run the instrumentation test to verify it fails**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChromeTapBehaviorTest#selectableTextEnabled_swipingFromLeftEdgeOpensTocWhenNoSelectionSessionIsActive"
```

Expected: FAIL because the drawer stays closed under the current `!selectableText && !isTextSelectionSessionActive` gate.

- [ ] **Step 3: Write the minimal production change**

In `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt`, replace the inline drawer gesture gate with the helper:

```kotlin
import com.epubreader.feature.reader.shouldEnableReaderTocDrawerGestures

ModalNavigationDrawer(
    drawerState = state.drawerState,
    gesturesEnabled = shouldEnableReaderTocDrawerGestures(
        isTextSelectionSessionActive = state.isTextSelectionSessionActive,
    ),
    drawerContent = {
        ReaderTocDrawerContent(
            state = state,
            callbacks = callbacks,
        )
    },
    content = {
        ReaderContentSurface(
            state = state,
            callbacks = callbacks,
        )
    },
)
```

- [ ] **Step 4: Run the focused runtime regressions to verify they pass**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChromeTapBehaviorTest"
```

Expected: PASS with the new edge-swipe-open test green and the active-selection-blocked test still green.

- [ ] **Step 5: Stage the chrome/runtime changes for final verification**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChromeTapBehaviorTest.kt
```

### Task 3: Full Verification And Manual Risk Check

**Files:**
- No additional production files

- [ ] **Step 1: Run the focused JVM contract coverage**

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderScreenContractsTest
```

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run the focused reader chrome instrumentation**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChromeTapBehaviorTest"
```

Expected: PASS with no reader chrome gesture failures.

- [ ] **Step 3: Run the repo file-size guard**

Run:

```powershell
.\gradlew.bat checkKotlinFileLineLimit
```

Expected: PASS with no 500-line Kotlin file violations.

- [ ] **Step 4: Perform the manual left-edge risk smoke**

Run this exact manual sequence on the reader:

```text
1. Enable Selectable Text in reader settings.
2. With no active selection, swipe from the left edge and confirm TOC opens.
3. Close TOC with the reverse swipe and confirm it closes normally.
4. Long-press the first visible characters near the left edge of a paragraph and confirm selection still starts.
5. While a text-selection session is active, try the same left-edge swipe and confirm TOC does not open.
```

Expected: TOC is more forgiving when idle, but active selection still owns the interaction.

- [ ] **Step 5: Commit the fully verified state**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenContractsTest.kt feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChromeTapBehaviorTest.kt
git commit -m "fix: allow idle TOC swipe when selection is inactive"
```
