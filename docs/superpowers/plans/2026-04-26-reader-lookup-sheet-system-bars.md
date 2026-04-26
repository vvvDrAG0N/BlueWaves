# Reader Lookup Sheet System Bars Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Android system bars visible for the full lifetime of the reader lookup WebView bottom sheet opened from `Define` and `Translate`, then re-hide them after the sheet is dismissed when global `Show System Bar` is off.

**Architecture:** Treat the lookup sheet as an explicit reader chrome state, not as a transient side effect. The selection host should report lookup-sheet visibility upward, the reader shell should own that visibility state, and `ReaderSystemBarEffect` should include it in the show/hide policy so the delayed immersive hide is canceled while the sheet is opening or visible.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 `ModalBottomSheet`, `WindowInsetsControllerCompat`, Android instrumentation tests via Gradle

**Allowed files:**
- `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/ui/ReaderScreenChrome.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt`

**Files to avoid:**
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderSelectionHandles.kt`
- `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChromeDrawerSwipeTest.kt`
- any TOC-only or selection-geometry-only files not listed above

**Workspace note:** `ReaderScreenChrome.kt` and `ReaderScreenContracts.kt` already have unstaged changes in the current worktree. Read their current diffs before editing and layer this fix on top without reverting unrelated changes.

**Risk areas:**
- Do not break the existing immersive reader behavior when `Show System Bar` is off and no sheet is visible.
- Do not change reader restoration, selection math, or back-layer ordering.
- Do not leave the bars permanently visible after the lookup sheet closes.

---

## File Map

- `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`
  - End-to-end instrumentation coverage for reader system bar behavior.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
  - Reader chrome callback contract. Add a lookup-sheet visibility callback here.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`
  - Public reader content wrapper that passes chrome callbacks into the EPUB runtime.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt`
  - Reader facade pass-through layer. Must mirror the new callback.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
  - Runtime wrapper that passes lookup-sheet callbacks into `ReaderChapterSelectionHost`.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
  - Selection host that creates `pendingWebLookup` and renders `ReaderLookupWebViewBottomSheet`.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/ui/ReaderScreenChrome.kt`
  - Chrome renderer that passes callbacks into `ReaderChapterContent`.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt`
  - Shell-to-chrome callback builder. Add the new callback and keep stale-session rules untouched.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
  - Shell state owner. Store `isLookupSheetVisible` here and feed it into the system bar effect.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt`
  - Immersive system bar owner for the reader. Extend its key set and visibility rule.

### Task 1: Add the failing reader lookup system bar regression

**Files:**
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`

- [ ] **Step 1: Replace the current dismiss-only helper with an open-and-dismiss assertion**

```kotlin
@Test
fun reader_DefineLookup_ShowsSystemBarsWhileSheetIsOpen_andRehidesOnDismiss() = runBlocking {
    val book = createTestBook()
    settingsManager.updateGlobalSettings(
        GlobalSettings(
            firstTime = false,
            showSystemBar = false,
            selectableText = true,
        ),
    )

    openReader(book)
    waitForReaderContent()
    assertLookupShowsSystemBarsWhileOpenAndRehides("Define")
}

@Test
fun reader_TranslateLookup_ShowsSystemBarsWhileSheetIsOpen_andRehidesOnDismiss() = runBlocking {
    val book = createTestBook()
    settingsManager.updateGlobalSettings(
        GlobalSettings(
            firstTime = false,
            showSystemBar = false,
            selectableText = true,
        ),
    )

    openReader(book)
    waitForReaderContent()
    assertLookupShowsSystemBarsWhileOpenAndRehides("Translate")
}

private fun assertLookupShowsSystemBarsWhileOpenAndRehides(actionLabel: String) {
    assertSystemBarsVisibility(visible = false)
    activateReaderSelection()

    composeRule.onNodeWithText(actionLabel, useUnmergedTree = true).performClick()
    composeRule.onNodeWithTag("web_lookup_webview", useUnmergedTree = true).assertExists()

    assertSystemBarsVisibility(visible = true)

    dismissLookupSheet()
    composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag("web_lookup_webview", useUnmergedTree = true)
            .fetchSemanticsNodes().isEmpty()
    }

    assertSystemBarsVisibility(visible = false)
}
```

- [ ] **Step 2: Remove the manual visibility shim that hides the real bug**

Delete this helper and its call sites from `ReaderSystemBarTest.kt`:

```kotlin
private fun forceSystemBarsVisible() {
    composeRule.runOnUiThread {
        val controller = WindowCompat.getInsetsController(
            composeRule.activity.window,
            composeRule.activity.window.decorView,
        )
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
```

- [ ] **Step 3: Run the instrumentation test to verify it fails for the right reason**

Run:

```powershell
./gradlew :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderSystemBarTest"
```

Expected: `FAIL` with an assertion like `Expected system bars visible=true within 5000ms`, while the `web_lookup_webview` node exists.

### Task 2: Wire lookup-sheet visibility into reader chrome state

**Files:**
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/ui/ReaderScreenChrome.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`

- [ ] **Step 1: Add a lookup-sheet visibility callback to the chrome contract**

Update `ReaderScreenContracts.kt`:

```kotlin
@Stable
internal data class ReaderChromeCallbacks(
    val onShowControlsChange: (Boolean) -> Unit,
    val onTextSelectionActiveChange: (Int, Boolean) -> Unit,
    val onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    val onClearTextSelection: () -> Unit,
    val onToggleTocSort: () -> Unit,
    val onReleaseOverscroll: () -> Unit,
    val onSaveAndBack: () -> Unit,
    val onOpenToc: () -> Unit,
    val onCloseToc: () -> Unit,
    val onLocateCurrentChapterInToc: () -> Unit,
    val onJumpToChapter: (Int) -> Unit,
    val onSelectTocChapter: (Int) -> Unit,
    val onPreviewSettings: (GlobalSettingsTransform) -> Unit,
    val onPersistSettings: (GlobalSettingsTransform) -> Unit,
    val onNavigatePrev: () -> Unit,
    val onNavigateNext: () -> Unit,
    val onMainScrubberDragStart: () -> Unit,
    val onLookupSheetVisibilityChange: (Boolean) -> Unit = {},
    val onLookupSheetDismissed: () -> Unit = {},
)
```

- [ ] **Step 2: Mirror that callback through the reader wrapper chain**

Update `ReaderChapterContent.kt`, `ReaderInternalFacades.kt`, `EpubReaderRuntime.kt`, and `ReaderScreenChrome.kt` so the new callback is passed through without changing any other behavior:

```kotlin
// ReaderChapterContent.kt
internal fun ReaderChapterContent(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
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
        chapterElements = chapterElements,
        isLoadingChapter = isLoadingChapter,
        currentChapterIndex = currentChapterIndex,
        selectionSessionEpoch = selectionSessionEpoch,
        onSelectionActiveChange = onSelectionActiveChange,
        onSelectionHandleDragChange = onSelectionHandleDragChange,
        onLookupSheetVisibilityChange = onLookupSheetVisibilityChange,
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
```

```kotlin
// ReaderScreenChrome.kt
ReaderChapterContent(
    settings = state.settings,
    themeColors = state.themeColors,
    listState = state.listState,
    chapterElements = state.chapterElements,
    isLoadingChapter = state.isLoadingChapter,
    currentChapterIndex = state.currentChapterIndex,
    selectionSessionEpoch = state.selectionSessionEpoch,
    onSelectionActiveChange = callbacks.onTextSelectionActiveChange,
    onSelectionHandleDragChange = callbacks.onSelectionHandleDragChange,
    onLookupSheetVisibilityChange = callbacks.onLookupSheetVisibilityChange,
    onLookupSheetDismissed = callbacks.onLookupSheetDismissed,
)
```

- [ ] **Step 3: Report lookup-sheet visibility from the selection host as state, not as a one-off event**

Update `ReaderChapterSelectionHost.kt`:

```kotlin
internal fun ReaderChapterSelectionHost(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    selectionDocument: ReaderSelectionDocument,
    selectionSessionEpoch: Int,
    onSelectionActiveChange: (Int, Boolean) -> Unit,
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetVisibilityChange: (Boolean) -> Unit = {},
    onLookupSheetDismissed: () -> Unit = {},
    content: @Composable (ReaderSelectionController) -> Unit,
) {
    var pendingWebLookup by remember { mutableStateOf<WebLookupAction?>(null) }

    LaunchedEffect(pendingWebLookup != null) {
        onLookupSheetVisibilityChange(pendingWebLookup != null)
    }

    // existing selection action bar...

    pendingWebLookup?.let { action ->
        ReaderLookupWebViewBottomSheet(
            url = action.url,
            scrimColor = overlayScrim,
            onDismiss = {
                pendingWebLookup = null
                onLookupSheetDismissed()
            },
        )
    }
}
```

- [ ] **Step 4: Store lookup-sheet visibility in the shell and feed it into the reader effect**

Update `ReaderFeatureShell.kt` and `ReaderFeatureShellCallbacks.kt`:

```kotlin
// ReaderFeatureShell.kt
var isLookupSheetVisible by remember(book.id) { mutableStateOf(false) }

ReaderSystemBarEffect(
    showControls = showControls,
    globalSettings = globalSettings,
    isLookupSheetVisible = isLookupSheetVisible,
    refreshToken = systemBarRefreshToken,
)

val chromeCallbacks = buildReaderFeatureShellChromeCallbacks(
    selectionSessionEpoch = selectionSessionEpoch,
    invalidateSelectionSession = ::invalidateSelectionSession,
    onShowControlsChange = { showControls = it },
    onTextSelectionSessionActiveChange = { isTextSelectionSessionActive = it },
    onSelectionHandleDragActiveChange = { isSelectionHandleDragActive = it },
    tocSort = tocSort,
    onTocSortChange = { tocSort = it },
    onReleaseOverscroll = ::releaseOverscroll,
    onSaveAndBack = { scope.launch { saveAndBack() } },
    onOpenToc = { scope.launch { drawerState.open() } },
    onCloseToc = { scope.launch { drawerState.close() } },
    onLocateCurrentChapterInToc = ::locateCurrentChapterInToc,
    onJumpToChapter = ::jumpToChapter,
    onSelectTocChapter = ::selectTocChapter,
    effectiveSettings = effectiveSettings,
    globalSettings = globalSettings,
    settingsDraft = settingsDraft,
    onSettingsDraftChange = { settingsDraft = it },
    onPersistGlobalSettings = { updatedSettings ->
        scope.launch { settingsManager.updateGlobalSettings(updatedSettings) }
    },
    onNavigatePrev = { navigatePrev(toBottom = false) },
    onNavigateNext = ::navigateNext,
    onMainScrubberDragStart = ::handleMainScrubberDragStart,
    onLookupSheetVisibilityChange = { visible -> isLookupSheetVisible = visible },
    onLookupSheetDismissed = {
        isLookupSheetVisible = false
        systemBarRefreshToken++
    },
)
```

```kotlin
// ReaderFeatureShellCallbacks.kt
internal fun buildReaderFeatureShellChromeCallbacks(
    selectionSessionEpoch: Int,
    invalidateSelectionSession: (String) -> Unit,
    onShowControlsChange: (Boolean) -> Unit,
    onTextSelectionSessionActiveChange: (Boolean) -> Unit,
    onSelectionHandleDragActiveChange: (Boolean) -> Unit,
    tocSort: TocSort,
    onTocSortChange: (TocSort) -> Unit,
    onReleaseOverscroll: () -> Unit,
    onSaveAndBack: () -> Unit,
    onOpenToc: () -> Unit,
    onCloseToc: () -> Unit,
    onLocateCurrentChapterInToc: () -> Unit,
    onJumpToChapter: (Int) -> Unit,
    onSelectTocChapter: (Int) -> Unit,
    effectiveSettings: GlobalSettings,
    globalSettings: GlobalSettings,
    settingsDraft: ReaderSettingsDraft?,
    onSettingsDraftChange: (ReaderSettingsDraft?) -> Unit,
    onPersistGlobalSettings: (GlobalSettings) -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onMainScrubberDragStart: () -> Unit,
    onLookupSheetVisibilityChange: (Boolean) -> Unit,
    onLookupSheetDismissed: () -> Unit,
): ReaderChromeCallbacks {
    return buildReaderChromeCallbacks(
        // existing callbacks unchanged...
        onMainScrubberDragStart = onMainScrubberDragStart,
        onLookupSheetVisibilityChange = onLookupSheetVisibilityChange,
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
```

### Task 3: Make the reader system bar effect cancel immersive hide while the sheet is open

**Files:**
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`

- [ ] **Step 1: Extend `ReaderSystemBarEffect` with lookup-sheet visibility**

Update `ReaderScreenEffects.kt`:

```kotlin
@Composable
internal fun ReaderSystemBarEffect(
    showControls: Boolean,
    globalSettings: GlobalSettings,
    isLookupSheetVisible: Boolean,
    refreshToken: Int = 0,
) {
    val view = LocalView.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        showControls,
        globalSettings.showSystemBar,
        isLookupSheetVisible,
        resumeTrigger,
        refreshToken,
    ) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (showControls || globalSettings.showSystemBar || isLookupSheetVisible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            kotlinx.coroutines.delay(450)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
```

- [ ] **Step 2: Run the reader system bar instrumentation tests again**

Run:

```powershell
./gradlew :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderSystemBarTest"
```

Expected: `BUILD SUCCESSFUL`, including the new Define/Translate “visible while open, hidden after dismiss” assertions.

- [ ] **Step 3: Run a focused regression sweep for lookup actions and reader chrome**

Run:

```powershell
./gradlew :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest,com.epubreader.feature.reader.ReaderSystemBarTest"
```

Expected: `BUILD SUCCESSFUL`, with lookup WebView existence tests still passing and no regressions in selection dismissal.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/plans/2026-04-26-reader-lookup-sheet-system-bars.md
git add feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/internal/ui/ReaderScreenChrome.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt
git add feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt
git commit -m "fix: keep system bars visible for reader lookup sheet"
```

## Self-Review

**Spec coverage:** This plan covers the exact reported behavior:
- bars visible while `Define` sheet is opening/open
- bars visible while `Translate` sheet is opening/open
- bars hidden again after dismissal when global `Show System Bar` is off
- no app-shell change, because reader immersive ownership already lives in the reader feature

**Placeholder scan:** No `TODO`, `TBD`, or “write tests later” placeholders remain.

**Type consistency:** The new contract name stays consistent as `onLookupSheetVisibilityChange` from the contract, through wrappers, into the selection host, and back to the shell.
