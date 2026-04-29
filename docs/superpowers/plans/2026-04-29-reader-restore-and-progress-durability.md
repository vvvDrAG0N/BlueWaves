# Reader Restore And Progress Durability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the reader's background/process-kill progress loss edge cases and remove the saved-position reopen flicker without changing app-shell route persistence or DataStore schema.

**Architecture:** Keep `SettingsManager` as the only persisted source of truth and keep reader behavior behind `ReaderFeatureShell`. Phase 1 adds reader-local lifecycle flush helpers plus a stable fallback path for unsafe restore windows. Phase 2 adds a narrow initial-reveal gate for saved mid-chapter cold reopens so the wrong top-of-chapter content never visibly paints before restore settles.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX lifecycle, DataStore Preferences, JUnit4, Robolectric/JVM tests, Compose instrumentation tests, Gradle

---

## File Structure

- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderProgressPersistence.kt`
  - Own pure progress snapshot/fallback helpers used by lifecycle flushes and the existing debounced save path.
- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderLifecycleProgressEffect.kt`
  - Own the lifecycle observer that triggers a non-blocking flush from the reader shell on `ON_STOP`.
- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderRestorePresentation.kt`
  - Own pure rules for whether the initial reader content reveal should remain masked.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
  - Thread the new lifecycle flush state and restore-reveal state through the shell without expanding the file's responsibilities.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenHelpers.kt`
  - Reuse the new pure snapshot helper instead of duplicating progress-shape logic inline.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
  - Add the restore-reveal flag to `ReaderChromeState` so the shell can pass it without leaking restore logic into the presentational layer.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt`
  - Thread the restore-reveal flag through `buildReaderChromeState(...)`.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt`
  - Pass the restore-reveal flag from `ReaderChromeState` into `ReaderChapterContent(...)`.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`
  - Thread a narrow "block content reveal" boolean into the runtime.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
  - Show the existing loading content while the initial restore reveal is intentionally blocked.
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContentCommon.kt`
  - Add a stable test tag to the loading indicator.
- Create: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderProgressPersistenceTest.kt`
  - Cover lifecycle snapshot selection, fallback behavior, and reveal-gate rules as pure logic.
- Create: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderLifecycleProgressTest.kt`
  - Cover background/recreate progress durability.
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt`
  - Add first-presentation assertions for the saved-mid-chapter reopen flow.
- Modify: `docs/reader_screen.md`
  - Document the new lifecycle flush and initial-reveal helper seams.
- Modify: `docs/test_checklist.md`
  - Add the new reader lifecycle instrumentation and JVM test names.
- Modify: `docs/agent_memory/next_steps.md`
  - Replace the older generic investigation notes with the new combined execution pointer if any follow-up remains.
- Modify: `docs/agent_memory/step_history.md`
  - Append the implementation summary after the work is done.

Workspace guards:

- `ReaderFeatureShell.kt` is already near the 500-line hard limit. Any new lifecycle or reveal rules must live in extracted helper files, not as another large block in the shell.
- Do not modify `app/AppNavigation.kt` or `SettingsManagerContracts.kt` in this plan.
- Do not touch reader selection files in this plan.

### Task 1: Add the failing pure tests for progress snapshot selection and reveal gating

**Files:**
- Create: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderProgressPersistenceTest.kt`
- Test: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderProgressPersistenceTest.kt`

- [ ] **Step 1: Write the failing pure helper tests**

Create `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderProgressPersistenceTest.kt` with this content:

```kotlin
package com.epubreader.feature.reader

import com.epubreader.core.model.BookProgress
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSection
import com.epubreader.feature.reader.internal.shell.ReaderVisibleProgressSnapshot
import com.epubreader.feature.reader.internal.shell.buildLifecycleReaderProgressSnapshot
import com.epubreader.feature.reader.internal.shell.shouldBlockInitialReaderReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressPersistenceTest {

    @Test
    fun buildLifecycleReaderProgressSnapshot_usesVisibleReaderPosition_whenReaderStateIsStable() {
        val snapshot = buildLifecycleReaderProgressSnapshot(
            currentChapterHref = "chapter2.xhtml",
            visibleSnapshot = ReaderVisibleProgressSnapshot(
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 18,
            ),
            chapterSections = sampleSections(),
            isInitialScrollDone = true,
            isRestoringPosition = false,
            fallbackProgress = BookProgress(
                scrollIndex = 1,
                scrollOffset = 5,
                lastChapterHref = "chapter2.xhtml",
            ),
        )

        assertEquals(
            BookProgress(
                scrollIndex = 20,
                scrollOffset = 18,
                lastChapterHref = "chapter2.xhtml",
            ),
            snapshot,
        )
    }

    @Test
    fun buildLifecycleReaderProgressSnapshot_fallsBackToLastKnownGoodProgress_whenRestoreIsStillRunning() {
        val fallback = BookProgress(
            scrollIndex = 44,
            scrollOffset = 9,
            lastChapterHref = "chapter7.xhtml",
        )

        val snapshot = buildLifecycleReaderProgressSnapshot(
            currentChapterHref = "chapter7.xhtml",
            visibleSnapshot = ReaderVisibleProgressSnapshot(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
            chapterSections = sampleSections(),
            isInitialScrollDone = false,
            isRestoringPosition = true,
            fallbackProgress = fallback,
        )

        assertEquals(fallback, snapshot)
    }

    @Test
    fun buildLifecycleReaderProgressSnapshot_returnsNull_whenNoSafeSnapshotExists() {
        val snapshot = buildLifecycleReaderProgressSnapshot(
            currentChapterHref = "chapter1.xhtml",
            visibleSnapshot = null,
            chapterSections = emptyList(),
            isInitialScrollDone = false,
            isRestoringPosition = true,
            fallbackProgress = null,
        )

        assertNull(snapshot)
    }

    @Test
    fun shouldBlockInitialReaderReveal_onlyBlocksSavedMidChapterRestore() {
        assertTrue(
            shouldBlockInitialReaderReveal(
                hasLoadedChapterContent = true,
                isLoadingChapter = false,
                isInitialScrollDone = false,
                shouldRestoreSavedInChapterOffset = true,
                isExplicitTopNavigation = false,
            ),
        )

        assertFalse(
            shouldBlockInitialReaderReveal(
                hasLoadedChapterContent = true,
                isLoadingChapter = false,
                isInitialScrollDone = false,
                shouldRestoreSavedInChapterOffset = false,
                isExplicitTopNavigation = true,
            ),
        )
    }

    private fun sampleSections(): List<ReaderChapterSection> {
        return listOf(
            ReaderChapterSection.TextSection(
                id = "s0",
                blocks = emptyList(),
                startElementIndex = 0,
                endElementIndex = 9,
            ),
            ReaderChapterSection.TextSection(
                id = "s1",
                blocks = emptyList(),
                startElementIndex = 10,
                endElementIndex = 19,
            ),
            ReaderChapterSection.TextSection(
                id = "s2",
                blocks = emptyList(),
                startElementIndex = 20,
                endElementIndex = 29,
            ),
        )
    }
}
```

- [ ] **Step 2: Run the new pure tests to verify they fail**

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests "com.epubreader.feature.reader.ReaderProgressPersistenceTest"
```

Expected: `FAIL` because `ReaderVisibleProgressSnapshot`, `buildLifecycleReaderProgressSnapshot(...)`, and `shouldBlockInitialReaderReveal(...)` do not exist yet.

- [ ] **Step 3: Commit the failing test scaffold**

```powershell
git add feature/reader/src/test/java/com/epubreader/feature/reader/ReaderProgressPersistenceTest.kt
git commit -m "test: add failing reader progress persistence helper coverage"
```

### Task 2: Implement the pure progress snapshot and reveal-gate helpers

**Files:**
- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderProgressPersistence.kt`
- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderRestorePresentation.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenHelpers.kt`
- Test: `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderProgressPersistenceTest.kt`

- [ ] **Step 1: Add the new pure progress helper file**

Create `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderProgressPersistence.kt` with this content:

```kotlin
package com.epubreader.feature.reader.internal.shell

import com.epubreader.core.model.BookProgress
import com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSection

internal data class ReaderVisibleProgressSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

internal fun buildLifecycleReaderProgressSnapshot(
    currentChapterHref: String,
    visibleSnapshot: ReaderVisibleProgressSnapshot?,
    chapterSections: List<ReaderChapterSection>,
    isInitialScrollDone: Boolean,
    isRestoringPosition: Boolean,
    fallbackProgress: BookProgress?,
): BookProgress? {
    if (isInitialScrollDone && !isRestoringPosition && visibleSnapshot != null) {
        val mappedIndex = chapterSections
            .getOrNull(visibleSnapshot.firstVisibleItemIndex)
            ?.startElementIndex
            ?: 0
        return BookProgress(
            scrollIndex = mappedIndex,
            scrollOffset = visibleSnapshot.firstVisibleItemScrollOffset,
            lastChapterHref = currentChapterHref,
        )
    }
    return fallbackProgress?.takeIf { it.lastChapterHref == currentChapterHref }
}
```

- [ ] **Step 2: Add the pure reveal-gate helper**

Create `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderRestorePresentation.kt` with this content:

```kotlin
package com.epubreader.feature.reader.internal.shell

internal fun shouldBlockInitialReaderReveal(
    hasLoadedChapterContent: Boolean,
    isLoadingChapter: Boolean,
    isInitialScrollDone: Boolean,
    shouldRestoreSavedInChapterOffset: Boolean,
    isExplicitTopNavigation: Boolean,
): Boolean {
    return hasLoadedChapterContent &&
        !isLoadingChapter &&
        !isInitialScrollDone &&
        shouldRestoreSavedInChapterOffset &&
        !isExplicitTopNavigation
}
```

- [ ] **Step 3: Reuse the helper inside `saveReaderProgressSnapshot(...)`**

Update the body of `saveReaderProgressSnapshot(...)` in `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenHelpers.kt` to this implementation:

```kotlin
internal suspend fun saveReaderProgressSnapshot(
    book: com.epubreader.core.model.EpubBook,
    settingsManager: SettingsManager,
    currentChapterIndex: Int,
    listState: LazyListState,
    chapterSections: List<ReaderChapterSection>,
    isInitialScrollDone: Boolean,
    isRestoringPosition: Boolean,
    isGestureNavigation: Boolean,
    skipRestoration: Boolean,
    shouldScrollToBottom: Boolean,
) {
    if (currentChapterIndex == -1 || currentChapterIndex >= book.spineHrefs.size) {
        return
    }

    withContext(NonCancellable) {
        val progress = when {
            isGestureNavigation || skipRestoration -> {
                BookProgress(
                    scrollIndex = if (shouldScrollToBottom) Int.MAX_VALUE else 0,
                    scrollOffset = 0,
                    lastChapterHref = book.spineHrefs[currentChapterIndex],
                )
            }

            else -> buildLifecycleReaderProgressSnapshot(
                currentChapterHref = book.spineHrefs[currentChapterIndex],
                visibleSnapshot = ReaderVisibleProgressSnapshot(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                ),
                chapterSections = chapterSections,
                isInitialScrollDone = isInitialScrollDone,
                isRestoringPosition = isRestoringPosition,
                fallbackProgress = null,
            )
        }

        progress?.let {
            settingsManager.saveBookProgress(
                book.id,
                it,
                representation = BookRepresentation.EPUB,
            )
        }
    }
}
```

- [ ] **Step 4: Run the pure tests to verify they pass**

Run:

```powershell
.\gradlew.bat :feature:reader:testDebugUnitTest --tests "com.epubreader.feature.reader.ReaderProgressPersistenceTest"
```

Expected: `PASS`

- [ ] **Step 5: Commit the pure helper layer**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderProgressPersistence.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderRestorePresentation.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenHelpers.kt feature/reader/src/test/java/com/epubreader/feature/reader/ReaderProgressPersistenceTest.kt
git commit -m "feat: add reader progress snapshot helpers"
```

### Task 3: Add the failing instrumentation coverage for background progress flush and saved-position first presentation

**Files:**
- Create: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderLifecycleProgressTest.kt`
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderLifecycleProgressTest.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt`

- [ ] **Step 1: Mirror the restoration fixture locally and write the failing lifecycle instrumentation**

Create `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderLifecycleProgressTest.kt` by mirroring the small private EPUB builder that already exists in `ReaderScreenRestorationTest.kt`. Keep the local helper in the new test file instead of extracting a shared fixture so this plan stays focused on reader behavior, not test-support reshaping. The private helper should stay focused on:

- creating a unique temp book folder under `cacheDir`
- writing a 2-chapter EPUB with predictable paragraph labels
- tracking created book IDs and folders for cleanup

Use this content as the starting point:

```kotlin
package com.epubreader.feature.reader

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.epubreader.MainActivity
import com.epubreader.core.model.BookRepresentation
import com.epubreader.core.model.GlobalSettings
import com.epubreader.data.parser.EpubParser
import com.epubreader.data.settings.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReaderLifecycleProgressTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun movingReaderToBackground_flushesLatestProgressBeforeRecreate() = runBlocking {
        val settingsManager = SettingsManager(composeRule.activity)
        val parser = EpubParser.create(composeRule.activity)
        val book = createRestorationBook()

        composeRule.activity.setContent {
            MaterialTheme {
                ReaderScreen(
                    book = book,
                    globalSettings = GlobalSettings(),
                    settingsManager = settingsManager,
                    parser = parser,
                    onBack = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Paragraph 40", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle {
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        }

        val restored = settingsManager.getBookProgress(book.id, BookRepresentation.EPUB).first()
        assertTrue(restored.scrollIndex > 0)
    }
}
```

Also mirror the `@After` cleanup pattern from `ReaderScreenRestorationTest.kt`, and add the supporting imports used by the local fixture and assertion path, including `androidx.compose.ui.test.onAllNodesWithText` and `kotlinx.coroutines.flow.first`.

- [ ] **Step 2: Extend restoration coverage to the first visible presentation**

Append this test to `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt`:

```kotlin
@Test
fun reopenWithSavedMidChapterProgress_keepsLoadingVisibleUntilRestoredTargetAppears() = runBlocking {
    val book = createRestorationBook()

    settingsManager.saveBookProgress(
        book.id,
        BookProgress(
            scrollIndex = 18,
            scrollOffset = 0,
            lastChapterHref = "chapter2.xhtml",
        ),
    )

    composeRule.runOnUiThread {
        composeRule.activity.setContent {
            MaterialTheme {
                ReaderScreen(
                    book = book,
                    globalSettings = GlobalSettings(),
                    settingsManager = settingsManager,
                    parser = parser,
                    onBack = {},
                )
            }
        }
    }

    composeRule.onNodeWithTag("reader_chapter_loading").assertExists()
    waitUntilDisplayed("Chapter 2 Paragraph 18")
    composeRule.onNodeWithText("Chapter 2 Paragraph 18", substring = true, useUnmergedTree = true).assertIsDisplayed()
}
```

Also add the missing test imports for `assertExists` and `onNodeWithTag`.

- [ ] **Step 3: Run the instrumentation slice to verify it fails**

Run:

```powershell
.\gradlew.bat --% :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderLifecycleProgressTest,com.epubreader.feature.reader.ReaderScreenRestorationTest
```

Expected: `FAIL` because the lifecycle stop flush does not exist yet and `reader_chapter_loading` is not tagged yet.

- [ ] **Step 4: Commit the failing runtime coverage**

```powershell
git add feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderLifecycleProgressTest.kt feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt
git commit -m "test: add failing reader lifecycle and restore presentation coverage"
```

### Task 4: Implement the lifecycle flush effect and wire it through the reader shell

**Files:**
- Create: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderLifecycleProgressEffect.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderLifecycleProgressTest.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderLifecycleProgressTest.kt`

- [ ] **Step 1: Add the lifecycle observer helper**

Create `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderLifecycleProgressEffect.kt` with this content:

```kotlin
package com.epubreader.feature.reader.internal.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun ReaderLifecycleProgressEffect(
    onStopFlush: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnStopFlush = rememberUpdatedState(onStopFlush)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                latestOnStopFlush.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
```

- [ ] **Step 2: Track the last known good reader progress and flush it on `ON_STOP`**

Update `ReaderFeatureShell.kt` with these focused changes:

```kotlin
var lastKnownGoodProgress by remember(book.id) { mutableStateOf<BookProgress?>(null) }
var shouldRestoreSavedInChapterOffset by remember(book.id) { mutableStateOf(false) }

LaunchedEffect(book.id) {
    if (currentChapterIndex == -1) {
        val savedProgress = settingsManager
            .getBookProgress(book.id, BookRepresentation.EPUB)
            .first()
        lastKnownGoodProgress = savedProgress.takeIf { it.lastChapterHref != null }
        if (book.spineHrefs.isNotEmpty()) {
            val index = if (savedProgress.lastChapterHref != null) {
                val foundIndex = book.spineHrefs.indexOf(savedProgress.lastChapterHref)
                if (foundIndex != -1) foundIndex else 0
            } else {
                0
            }
            currentChapterIndex = index.coerceIn(book.spineHrefs.indices)
            shouldRestoreSavedInChapterOffset =
                savedProgress.lastChapterHref == book.spineHrefs[currentChapterIndex] &&
                    (savedProgress.scrollIndex > 0 || savedProgress.scrollOffset > 0)
        }
    }
}

ReaderLifecycleProgressEffect(
    onStopFlush = {
        scope.launch {
            val currentHref = book.spineHrefs.getOrNull(currentChapterIndex) ?: return@launch
            buildLifecycleReaderProgressSnapshot(
                currentChapterHref = currentHref,
                visibleSnapshot = ReaderVisibleProgressSnapshot(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                ),
                chapterSections = chapterSections,
                isInitialScrollDone = isInitialScrollDone,
                isRestoringPosition = isRestoringPosition,
                fallbackProgress = lastKnownGoodProgress,
            )?.let { snapshot ->
                settingsManager.saveBookProgress(
                    book.id,
                    snapshot,
                    representation = BookRepresentation.EPUB,
                )
                lastKnownGoodProgress = snapshot
            }
        }
    },
)
```

- [ ] **Step 3: Keep the debounced save path updating the last known good progress**

Inside the existing debounced save block in `ReaderFeatureShell.kt`, add this line immediately after `saveBookProgress(...)` succeeds:

```kotlin
lastKnownGoodProgress = BookProgress(
    scrollIndex = mapRenderedItemIndexToReaderProgressIndex(
        renderedItemIndex = index,
        chapterSections = chapterSections,
    ),
    scrollOffset = offset,
    lastChapterHref = book.spineHrefs[chapterIndex],
)
```

- [ ] **Step 4: Run the lifecycle instrumentation test to verify it passes**

Run:

```powershell
.\gradlew.bat --% :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderLifecycleProgressTest
```

Expected: `PASS`

- [ ] **Step 5: Commit the lifecycle flush implementation**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderLifecycleProgressEffect.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt
git commit -m "feat: flush reader progress on lifecycle stop"
```

### Task 5: Add the initial restore reveal gate so saved-position open no longer flickers

**Files:**
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContentCommon.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt`

- [ ] **Step 1: Add a stable loading tag**

Update `ReaderChapterLoadingContent(...)` in `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContentCommon.kt` to:

```kotlin
@Composable
internal fun ReaderChapterLoadingContent(themeColors: ReaderTheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reader_chapter_loading"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = themeColors.foreground)
    }
}
```

- [ ] **Step 2: Thread a narrow reveal-block boolean through the chrome state path and into the runtime**

Update `ReaderScreenContracts.kt` to add the new field to `ReaderChromeState`:

```kotlin
@Stable
internal data class ReaderChromeState(
    ...
    val isLoadingChapter: Boolean,
    val blockInitialRestoreReveal: Boolean,
    val showControls: Boolean,
    ...
)
```

Update `ReaderScreenBindings.kt` so `buildReaderChromeState(...)` accepts `blockInitialRestoreReveal: Boolean` and assigns it into `ReaderChromeState(...)`.

Update `ReaderScreenChrome.kt` so the `ReaderChapterContent(...)` call receives:

```kotlin
blockInitialRestoreReveal = state.blockInitialRestoreReveal,
```

Update `ReaderChapterContent.kt` to this signature and call:

```kotlin
@Composable
internal fun ReaderChapterContent(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterSections: List<ReaderChapterSection>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    blockInitialRestoreReveal: Boolean,
    selectionSessionEpoch: Int = 0,
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetVisibilityChange: (Boolean) -> Unit = {},
    onLookupSheetDismissed: () -> Unit = {},
) {
    EpubReaderRuntime(
        settings = settings,
        themeColors = themeColors,
        listState = listState,
        chapterSections = chapterSections,
        isLoadingChapter = isLoadingChapter,
        currentChapterIndex = currentChapterIndex,
        blockInitialRestoreReveal = blockInitialRestoreReveal,
        selectionSessionEpoch = selectionSessionEpoch,
        onSelectionActiveChange = onSelectionActiveChange,
        onSelectionHandleDragChange = onSelectionHandleDragChange,
        onLookupSheetVisibilityChange = onLookupSheetVisibilityChange,
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
```

- [ ] **Step 3: Honor that boolean in the runtime**

Update the top of `EpubReaderRuntime(...)` in `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt` to:

```kotlin
internal fun EpubReaderRuntime(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterSections: List<ReaderChapterSection>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    blockInitialRestoreReveal: Boolean,
    selectionSessionEpoch: Int = 0,
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetVisibilityChange: (Boolean) -> Unit = {},
    onLookupSheetDismissed: () -> Unit = {},
) {
    if (currentChapterIndex == -1 || isLoadingChapter || blockInitialRestoreReveal) {
        ReaderChapterLoadingContent(themeColors)
        return
    }
```

- [ ] **Step 4: Derive and clear the reveal gate in the shell**

Add this derived state inside `ReaderFeatureShell.kt` and pass it into `buildReaderChromeState(...)`:

```kotlin
val blockInitialRestoreReveal = shouldBlockInitialReaderReveal(
    hasLoadedChapterContent = chapterElements.isNotEmpty(),
    isLoadingChapter = isLoadingChapter,
    isInitialScrollDone = isInitialScrollDone,
    shouldRestoreSavedInChapterOffset = shouldRestoreSavedInChapterOffset,
    isExplicitTopNavigation = skipRestoration || isGestureNavigation || shouldScrollToBottom,
)
```

Then, after the saved-position restore branch completes successfully, clear the gate:

```kotlin
shouldRestoreSavedInChapterOffset = false
```

Also ensure the TOC jump and explicit top-navigation paths clear it before loading:

```kotlin
shouldRestoreSavedInChapterOffset = false
```

- [ ] **Step 5: Run the restoration instrumentation slice to verify it passes**

Run:

```powershell
.\gradlew.bat --% :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderScreenRestorationTest
```

Expected: `PASS`

- [ ] **Step 6: Commit the restore reveal gate**

```powershell
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContentCommon.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt
git commit -m "feat: hide reader content until saved restore settles"
```

### Task 6: Run the reader regression slice, sync docs, and record the remaining manual matrix

**Files:**
- Modify: `docs/reader_screen.md`
- Modify: `docs/test_checklist.md`
- Modify: `docs/agent_memory/next_steps.md`
- Modify: `docs/agent_memory/step_history.md`

- [ ] **Step 1: Update the reader docs**

Add this bullet to the `ReaderFeatureShell.kt` ownership section in `docs/reader_screen.md`:

```markdown
- `internal/shell/ReaderLifecycleProgressEffect.kt`
  - Owns reader-local lifecycle stop flushing only; it must not introduce app-shell route persistence or bypass `SettingsManager`.
```

Add this bullet to the restoration flow notes in `docs/reader_screen.md`:

```markdown
- Cold reopens with saved in-chapter progress may keep the loading surface visible until the first restore landing settles; this hides the wrong top-of-chapter paint without changing the underlying restore ordering.
```

- [ ] **Step 2: Update the checklist doc**

Add these bullets to the reader runtime sections in `docs/test_checklist.md`:

```markdown
- `com.epubreader.feature.reader.ReaderLifecycleProgressTest`
- `com.epubreader.feature.reader.ReaderProgressPersistenceTest`
```

- [ ] **Step 3: Run the focused automated verification**

Run:

```powershell
.\gradlew.bat checkKotlinFileLineLimit verifyTestChecklistReferences
.\gradlew.bat :feature:reader:testDebugUnitTest --tests "com.epubreader.feature.reader.ReaderProgressPersistenceTest"
.\gradlew.bat :data:settings:testDebugUnitTest --tests "com.epubreader.data.settings.SettingsManagerProgressPersistenceTest"
.\gradlew.bat --% :feature:reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderLifecycleProgressTest,com.epubreader.feature.reader.ReaderScreenRestorationTest,com.epubreader.feature.reader.ReaderScreenOverscrollTest,com.epubreader.feature.reader.ReaderScreenThemeReactivityTest,com.epubreader.feature.reader.ReaderChromeTapBehaviorTest,com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest,com.epubreader.feature.reader.ReaderSurfacePluginUnavailableTest
```

Expected: all commands `PASS`

- [ ] **Step 4: Run the manual lifecycle matrix**

Run this manual/device matrix:

```text
1. Open a long book, scroll to a fresh position, press HOME, reopen app, confirm same live reader state.
2. Open a long book, scroll again, press HOME, run `adb shell am force-stop com.epubreader`, relaunch app, reopen the same book, confirm the restored position matches the latest safe backgrounded point.
3. Open a long book, scroll again, dismiss the app from Android recents, relaunch, reopen the same book, confirm the restored position still matches the latest safe point.
4. Reopen a book with saved mid-chapter progress and confirm `reader_chapter_loading` is visible before the restored paragraph appears, and that the top-of-chapter paragraph never visibly paints first.
5. Run TOC jump, next/prev, and overscroll once to confirm no regression in explicit navigation behavior.
```

Expected:

```text
- Background-with-process-alive returns to the same live reader.
- Force-stop / recents-close returns to the library route, but reopening the book lands at the latest safe saved progress.
- Saved mid-chapter reopen shows loading until the restored target appears; no visible top-of-chapter flash.
```

- [ ] **Step 5: Update continuity notes after verification**

Append one new numbered entry to `docs/agent_memory/step_history.md` summarizing:

```markdown
- implementation files changed
- automated verification run
- manual lifecycle matrix result
- whether route persistence remained intentionally unchanged
```

Replace the top reader restore/progress note in `docs/agent_memory/next_steps.md` with:

```markdown
## Reader Restore And Progress Durability Follow-Up
- Goal: Reopen this lane only if manual lifecycle validation still shows a missed save window or if product later wants route persistence after cold restart.
- Why now: The combined phased reader stabilization plan has been implemented and verified. Route persistence remains intentionally out of scope for this pass.
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/test_checklist.md`, `docs/superpowers/specs/2026-04-29-reader-restore-and-progress-durability-design.md`, `docs/superpowers/plans/2026-04-29-reader-restore-and-progress-durability.md`
```

- [ ] **Step 6: Commit the docs and verification wrap-up**

```powershell
git add docs/reader_screen.md docs/test_checklist.md docs/agent_memory/step_history.md docs/agent_memory/next_steps.md
git commit -m "docs: record reader restore and progress durability work"
```
