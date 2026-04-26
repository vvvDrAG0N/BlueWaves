# Reader Select All Action Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `Select All` action to the reader text-selection bar that selects the whole current chapter, immediately scrolls the reader to the chapter bottom, keeps selection mode active, and supports the chosen action order `Copy / Select All / Translate / Define`.

**Architecture:** Keep ownership inside the existing reader selection stack. `ReaderChapterSelectionHost` remains the selection-state owner, `TextSelectionActionBar` remains the visual action bar, and the new `Select All` behavior is wired as a reader-owned selection expansion action instead of a shell/navigation action. Because `ReaderChapterSelectionHost.kt` is already near the 500-line guard, extract the action-bar overlay wrapper and a tiny pure select-all helper seam so the feature lands without breaking the repo size rule.

**Tech Stack:** Kotlin, Jetpack Compose, Compose Material icons, `LazyListState`, JUnit JVM tests, Android Compose instrumentation tests, Gradle

---

## Decisions Locked In

- Button order is `Copy / Select All / Translate / Define`.
- `Select All` does **not** dismiss selection mode.
- `Select All` immediately moves the chapter viewport to the bottom so the user lands near the visible end handle and can act right away.
- Existing `Copy`, `Define`, and `Translate` behavior stays unchanged after they are tapped: they still complete the task and exit selection mode.

## File Map

- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionChapterActions.kt`
  - Owns tiny pure helpers for full-document selection range and the target item index for chapter-end scroll.
- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionActionBarOverlay.kt`
  - Owns the `AnimatedVisibility` action-bar wrapper that is currently inline in `ReaderChapterSelectionHost.kt`, so the host can stay under the 500-line limit after adding `Select All`.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderControlsWidgets.kt`
  - Extend `TextSelectionActionBar` with `onSelectAll`, stable button test tags, and the chosen visual order.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
  - Wire `Select All` into selection state, keep the session active, and scroll `LazyListState` to the last chapter item.
- Create: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderSelectionChapterActionsTest.kt`
  - JVM coverage for the pure full-range and end-scroll-target helpers.
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTestSupport.kt`
  - Expose an injectable `LazyListState` plus a longer chapter fixture for deterministic select-all tests.
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostActionsTest.kt`
  - Add button-order, selection-persistence, chapter-end scroll, and copy-after-select-all regressions.

## Risk Notes

- The biggest behavior change is intentional viewport movement. The implementation must keep it local to the current chapter `LazyListState` and must not touch reader restoration, progress saving, or shell navigation.
- `ReaderChapterSelectionHost.kt` is already around the file-size limit, so the executor must not keep piling logic into that file. The extraction in this plan is part of the feature, not optional cleanup.
- `Define` and `Translate` should still clear selection after they are tapped, even if the text was just expanded by `Select All`.

### Task 1: Add The Pure Select-All Helper Seam

**Files:**
- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionChapterActions.kt`
- Create: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderSelectionChapterActionsTest.kt`

- [ ] **Step 1: Write the failing JVM tests**

Create `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderSelectionChapterActionsTest.kt` with:

```kotlin
package com.epubreader.feature.reader

import androidx.compose.ui.text.TextRange
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectAllRange
import com.epubreader.feature.reader.internal.runtime.epub.resolveReaderSelectAllScrollTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderSelectionChapterActionsTest {

    @Test
    fun resolveReaderSelectAllRange_returnsNullForEmptyDocument() {
        assertNull(resolveReaderSelectAllRange(totalTextLength = 0))
    }

    @Test
    fun resolveReaderSelectAllRange_returnsWholeDocumentRangeForNonEmptyDocument() {
        assertEquals(
            TextRange(0, 42),
            resolveReaderSelectAllRange(totalTextLength = 42),
        )
    }

    @Test
    fun resolveReaderSelectAllScrollTarget_returnsLastItemIndex() {
        assertEquals(6, resolveReaderSelectAllScrollTarget(totalItemsCount = 7))
    }

    @Test
    fun resolveReaderSelectAllScrollTarget_returnsNullWhenThereAreNoItems() {
        assertNull(resolveReaderSelectAllScrollTarget(totalItemsCount = 0))
    }
}
```

- [ ] **Step 2: Run the JVM test to verify it fails**

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderSelectionChapterActionsTest
```

Expected: FAIL with unresolved references for `resolveReaderSelectAllRange` and `resolveReaderSelectAllScrollTarget`.

- [ ] **Step 3: Write the minimal helper implementation**

Create `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionChapterActions.kt` with:

```kotlin
package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.ui.text.TextRange

internal fun resolveReaderSelectAllRange(totalTextLength: Int): TextRange? {
    return if (totalTextLength <= 0) null else TextRange(0, totalTextLength)
}

internal fun resolveReaderSelectAllScrollTarget(totalItemsCount: Int): Int? {
    return if (totalItemsCount <= 0) null else totalItemsCount - 1
}
```

- [ ] **Step 4: Run the JVM test to verify it passes**

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderSelectionChapterActionsTest
```

Expected: PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionChapterActions.kt feature/reader/src/test/java/com/epubreader/feature/reader/ReaderSelectionChapterActionsTest.kt
git commit -m "test: add reader select-all helper seam"
```

### Task 2: Add The Action-Bar Button, Order, And Stable Tags

**Files:**
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderControlsWidgets.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostActionsTest.kt`

- [ ] **Step 1: Write the failing instrumentation order test**

Add this test to `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostActionsTest.kt`:

```kotlin
@Test
fun selectionActionBar_buttonsRenderInCopySelectAllTranslateDefineOrder() {
    composeRule.setReaderSelectionContent()
    composeRule.activateSelection()

    val copyBounds = composeRule
        .onNodeWithTag("text_selection_action_copy", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot
    val selectAllBounds = composeRule
        .onNodeWithTag("text_selection_action_select_all", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot
    val translateBounds = composeRule
        .onNodeWithTag("text_selection_action_translate", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot
    val defineBounds = composeRule
        .onNodeWithTag("text_selection_action_define", useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    assertTrue(copyBounds.left < selectAllBounds.left)
    assertTrue(selectAllBounds.left < translateBounds.left)
    assertTrue(translateBounds.left < defineBounds.left)
}
```

- [ ] **Step 2: Run the instrumentation test to verify it fails**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest#selectionActionBar_buttonsRenderInCopySelectAllTranslateDefineOrder"
```

Expected: FAIL because the `Select All` node/tag does not exist yet.

- [ ] **Step 3: Add the new button surface and stable tags**

Update `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderControlsWidgets.kt`:

```kotlin
@Composable
internal fun TextSelectionActionBar(
    themeColors: ReaderTheme,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onTranslate: () -> Unit,
    onDefine: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .testTag("text_selection_action_bar")
            .shadow(8.dp, RoundedCornerShape(28.dp)),
        color = themeColors.background.copy(alpha = 0.97f),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextSelectionActionButton(
                icon = Icons.Default.ContentCopy,
                label = "Copy",
                testTag = "text_selection_action_copy",
                themeColors = themeColors,
                onClick = onCopy,
            )
            TextSelectionActionButton(
                icon = Icons.Default.SelectAll,
                label = "Select All",
                testTag = "text_selection_action_select_all",
                themeColors = themeColors,
                onClick = onSelectAll,
            )
            TextSelectionActionButton(
                icon = Icons.Default.GTranslate,
                label = "Translate",
                testTag = "text_selection_action_translate",
                themeColors = themeColors,
                onClick = onTranslate,
            )
            TextSelectionActionButton(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                label = "Define",
                testTag = "text_selection_action_define",
                themeColors = themeColors,
                onClick = onDefine,
            )
        }
    }
}

@Composable
private fun TextSelectionActionButton(
    icon: ImageVector,
    label: String,
    testTag: String,
    themeColors: ReaderTheme,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        ...
    }
}
```

Also update the current `TextSelectionActionBar(...)` call in `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt` to compile by passing a temporary no-op placeholder:

```kotlin
TextSelectionActionBar(
    themeColors = themeColors,
    onCopy = { ... },
    onSelectAll = {},
    onTranslate = { ... },
    onDefine = { ... },
)
```

- [ ] **Step 4: Run the targeted instrumentation test to verify it passes**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest#selectionActionBar_buttonsRenderInCopySelectAllTranslateDefineOrder"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderControlsWidgets.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostActionsTest.kt
git commit -m "feat: add reader select-all action bar button shell"
```

### Task 3: Wire Chapter-Wide Select All And Keep The Host Under The Line Limit

**Files:**
- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionActionBarOverlay.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTestSupport.kt`
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostActionsTest.kt`

- [ ] **Step 1: Extend the test support for deterministic long-chapter verification**

Update `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTestSupport.kt`:

```kotlin
internal fun ReaderComposeRule.setReaderSelectionContent(
    settings: GlobalSettings = GlobalSettings(selectableText = true),
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    chapterElements: List<ChapterElement> = defaultReaderSelectionChapterElements(),
    listState: LazyListState = LazyListState(),
) {
    runOnUiThread {
        activity.setContent {
            MaterialTheme {
                ReaderSelectionTestSurface(
                    settings = settings,
                    onSelectionActiveChange = onSelectionActiveChange,
                    chapterElements = chapterElements,
                    listState = listState,
                )
            }
        }
    }
}

@Composable
internal fun ReaderSelectionTestSurface(
    settings: GlobalSettings,
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    chapterElements: List<ChapterElement> = defaultReaderSelectionChapterElements(),
    listState: LazyListState = rememberLazyListState(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("selection_surface"),
    ) {
        ReaderChapterContent(
            settings = settings,
            themeColors = getThemeColors("light"),
            listState = listState,
            chapterElements = chapterElements,
            isLoadingChapter = false,
            currentChapterIndex = 0,
            onSelectionActiveChange = onSelectionActiveChange,
        )
    }
}

internal fun longReaderSelectionChapterElements(): List<ChapterElement> {
    return List(12) { index ->
        ChapterElement.Text(
            "Paragraph ${index + 1} keeps the reader selection test deterministic.",
            id = "p${index + 1}",
        )
    }
}
```

- [ ] **Step 2: Write the failing select-all behavior regressions**

Add these tests to `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostActionsTest.kt`:

```kotlin
@Test
fun selectionActionBar_selectAllKeepsSelectionActive_andScrollsToChapterEnd() {
    val selectionActive = mutableStateOf(false)
    val listState = LazyListState()
    val chapterElements = longReaderSelectionChapterElements()

    composeRule.setReaderSelectionContent(
        chapterElements = chapterElements,
        listState = listState,
        onSelectionActiveChange = { _, isActive ->
            selectionActive.value = isActive
        },
    )

    composeRule.activateSelection()
    composeRule.waitUntil(5_000) { selectionActive.value }
    composeRule.onNodeWithTag("text_selection_action_select_all", useUnmergedTree = true).performClick()

    composeRule.waitUntil(5_000) {
        selectionActive.value &&
            listState.layoutInfo.totalItemsCount > 0 &&
            listState.firstVisibleItemIndex >= (listState.layoutInfo.totalItemsCount - 1)
    }

    composeRule.onNodeWithTag("text_selection_action_bar", useUnmergedTree = true).assertExists()
    assertTrue(selectionActive.value)
}

@Test
fun selectionActionBar_selectAllThenCopyWritesWholeChapterToClipboard_andDismissesSelection() {
    val clipboardManager = composeRule.requireClipboardManager()
    val selectionActive = mutableStateOf(false)
    val chapterElements = longReaderSelectionChapterElements()
    val expectedText = chapterElements
        .filterIsInstance<ChapterElement.Text>()
        .joinToString(separator = com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionParagraphSeparator) {
            it.content
        }

    composeRule.setReaderSelectionContent(
        chapterElements = chapterElements,
        onSelectionActiveChange = { _, isActive ->
            selectionActive.value = isActive
        },
    )

    composeRule.activateSelection()
    composeRule.waitUntil(5_000) { selectionActive.value }
    composeRule.onNodeWithTag("text_selection_action_select_all", useUnmergedTree = true).performClick()
    composeRule.waitUntil(5_000) { selectionActive.value }
    composeRule.onNodeWithTag("text_selection_action_copy", useUnmergedTree = true).performClick()

    composeRule.waitUntil(5_000) {
        clipboardManager.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(composeRule.activity)
            ?.toString() == expectedText
    }
    composeRule.waitUntil(5_000) { !selectionActive.value }

    assertEquals(
        expectedText,
        clipboardManager.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(composeRule.activity)
            ?.toString(),
    )
    assertFalse(selectionActive.value)
}
```

- [ ] **Step 3: Run the targeted instrumentation test to verify it fails**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest#selectionActionBar_selectAllKeepsSelectionActive_andScrollsToChapterEnd"
```

Expected: FAIL because `onSelectAll` is still a no-op and the list does not move.

- [ ] **Step 4: Extract the action-bar overlay and implement select-all behavior**

Create `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionActionBarOverlay.kt`:

```kotlin
package com.epubreader.feature.reader.internal.runtime.epub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.epubreader.feature.reader.ReaderTheme
import com.epubreader.feature.reader.internal.ui.TextSelectionActionBar

@Composable
internal fun ReaderSelectionActionBarOverlay(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    moveToTop: Boolean,
    themeColors: ReaderTheme,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onMeasured: (Int) -> Unit,
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onTranslate: () -> Unit,
    onDefine: () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
            .then(
                if (moveToTop) {
                    Modifier
                        .padding(top = 24.dp)
                        .statusBarsPadding()
                } else {
                    Modifier
                        .padding(bottom = bottomPadding)
                        .navigationBarsPadding()
                },
            )
            .onGloballyPositioned { coordinates ->
                onMeasured(coordinates.size.height)
            },
    ) {
        TextSelectionActionBar(
            themeColors = themeColors,
            onCopy = onCopy,
            onSelectAll = onSelectAll,
            onTranslate = onTranslate,
            onDefine = onDefine,
        )
    }
}
```

Then update `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`:

```kotlin
val scope = rememberCoroutineScope()

fun selectAll() {
    val range = resolveReaderSelectAllRange(selectionDocument.totalTextLength) ?: return
    selectionState.activate(range)
    rawDragPointerInHost = null
    resolvedHandleTarget = null
    isSelectionAutoScrollActive = false
    resolveReaderSelectAllScrollTarget(listState.layoutInfo.totalItemsCount)?.let { targetIndex ->
        scope.launch {
            listState.scrollToItem(targetIndex)
        }
    }
}
```

Replace the inline `AnimatedVisibility { TextSelectionActionBar(...) }` block with:

```kotlin
ReaderSelectionActionBarOverlay(
    modifier = Modifier.align(
        if (moveSelectionActionBarToTop) Alignment.TopCenter else Alignment.BottomCenter,
    ),
    isVisible = hasUsableSelectionSession,
    moveToTop = moveSelectionActionBarToTop,
    themeColors = themeColors,
    bottomPadding = selectionActionBarBottomPadding,
    onMeasured = { actionBarHeightPx = it },
    onCopy = {
        clipboardManager.setText(AnnotatedString(selectedText))
        clearSelection()
    },
    onSelectAll = ::selectAll,
    onTranslate = {
        if (selectedText.isNotBlank()) {
            pendingWebLookup = WebLookupAction.Translate(
                text = selectedText,
                targetLanguage = settings.targetTranslationLanguage,
            )
        }
        clearSelection()
    },
    onDefine = {
        if (selectedText.isNotBlank()) {
            pendingWebLookup = WebLookupAction.Define(selectedText)
        }
        clearSelection()
    },
)
```

Important: keep `onSelectAll` as the only action that does **not** call `clearSelection()`.

- [ ] **Step 5: Run the targeted instrumentation tests to verify they pass**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest#selectionActionBar_selectAllKeepsSelectionActive_andScrollsToChapterEnd"
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest#selectionActionBar_selectAllThenCopyWritesWholeChapterToClipboard_andDismissesSelection"
```

Expected: PASS.

- [ ] **Step 6: Run the full focused verification set**

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests com.epubreader.feature.reader.ReaderSelectionChapterActionsTest
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest"
.\gradlew.bat checkKotlinFileLineLimit
```

Expected: all commands PASS with `BUILD SUCCESSFUL`.

- [ ] **Step 7: Manual smoke on a long chapter**

Verify this exact flow manually:

1. Open a long chapter in reader with selectable text enabled.
2. Long-press near the upper half of the chapter to open selection mode.
3. Tap `Select All`.
4. Confirm the reader jumps to the bottom of the current chapter.
5. Confirm the action bar remains visible and selection mode remains active.
6. Tap `Copy`, then repeat the flow with `Translate`, then `Define`.
7. Confirm each of those three actions still exits selection mode after the tap.

- [ ] **Step 8: Commit**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionActionBarOverlay.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostTestSupport.kt feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostActionsTest.kt
git commit -m "feat: add reader select-all chapter action"
```

## Self-Review

- Spec coverage: the plan covers the UX decisions the user locked in: selected order, persistent selection after `Select All`, bottom jump after `Select All`, and unchanged exit behavior for `Copy`, `Translate`, and `Define`.
- Placeholder scan: no `TODO`, `TBD`, or “handle appropriately” placeholders remain.
- Type consistency: `onSelectAll`, `resolveReaderSelectAllRange`, `resolveReaderSelectAllScrollTarget`, `ReaderSelectionActionBarOverlay`, and the test tags are named consistently across tasks.
