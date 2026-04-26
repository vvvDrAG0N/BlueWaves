# Reader Lookup Sheet System Bar Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-hide immersive system bars immediately after the Define or Translate lookup sheet closes when `Show System Bar` is disabled.

**Architecture:** Keep window-insets ownership in the reader shell by teaching `ReaderSystemBarEffect` to rerun on an explicit shell-owned refresh token. Bubble a narrow `onLookupSheetDismissed` callback from the shell down to the lookup bottom-sheet dismissal seam, and use that dismissal to bump the refresh token instead of letting the selection host manipulate system bars directly.

**Tech Stack:** Kotlin, Jetpack Compose, `ModalBottomSheet`, `WindowInsetsControllerCompat`, Android instrumentation tests, Gradle connected Android tests.

---

## File Map

- `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`
  - Add deterministic regressions for Define and Translate dismissal re-hiding system bars.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt`
  - Rerun reader immersive-mode hide/show logic when the shell increments a refresh token.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
  - Own the refresh token state and pass it into `ReaderSystemBarEffect`.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
  - Extend `ReaderChromeCallbacks` with a lookup-sheet-dismiss callback.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt`
  - Thread the new callback through `buildReaderChromeCallbacks(...)`.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt`
  - Map lookup-sheet dismissal onto the shell refresh token.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt`
  - Forward the callback from chrome callbacks into chapter content.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`
  - Forward the callback into the EPUB runtime.
- `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
  - Forward the callback into the chapter selection host.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt`
  - Keep the facade wrapper in sync with the new callback parameter.
- `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
  - Invoke the callback from the lookup bottom-sheet dismissal seam.

## Guardrails

- Do not move system-bar ownership into `ReaderLookupWebViewBottomSheet(...)`; keep the actual `show()/hide()` calls inside `ReaderSystemBarEffect`.
- Do not touch restoration flags, progress saving, overscroll release, or reader navigation.
- Do not widen scope into app-shell immersive handling; `ReaderSurfacePlugin` already marks the reader as an immersive surface.
- Keep all new callback parameters defaulted to no-op lambdas where needed so selection-only tests and other call sites stay source-compatible.

### Task 1: Reader Lookup Dismissal Re-Hides Immersive Bars

**Files:**
- Modify: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt`
- Modify: `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`

- [ ] **Step 1: Write the failing regression tests**

Add the following to `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`:

```kotlin
@Test
fun reader_DismissingDefineLookup_RehidesSystemBars() = runBlocking {
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
    assertLookupDismissRehidesSystemBars("Define")
}

@Test
fun reader_DismissingTranslateLookup_RehidesSystemBars() = runBlocking {
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
    assertLookupDismissRehidesSystemBars("Translate")
}

private fun waitForReaderContent() {
    composeRule.waitUntil(30_000) {
        composeRule.onAllNodesWithText("Chapter 1", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

private fun assertLookupDismissRehidesSystemBars(actionLabel: String) {
    assertSystemBarsVisibility(visible = false)
    activateReaderSelection()

    composeRule.onNodeWithText(actionLabel, useUnmergedTree = true).performClick()
    composeRule.onNodeWithTag("web_lookup_webview", useUnmergedTree = true).assertExists()

    forceSystemBarsVisible()
    assertSystemBarsVisibility(visible = true)

    dismissLookupSheetWithBack()
    composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithTag("web_lookup_webview", useUnmergedTree = true)
            .fetchSemanticsNodes().isEmpty()
    }

    assertSystemBarsVisibility(visible = false)
}

private fun activateReaderSelection() {
    composeRule.onNodeWithTag("reader_compose_text_section", useUnmergedTree = true)
        .performTouchInput {
            longClick(Offset(x = 60f, y = 120f))
        }

    composeRule.waitUntil(10_000) {
        composeRule.onAllNodesWithText("Define", useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

private fun forceSystemBarsVisible() {
    composeRule.runOnUiThread {
        val controller = WindowCompat.getInsetsController(
            composeRule.activity.window,
            composeRule.activity.window.decorView,
        )
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun dismissLookupSheetWithBack() {
    composeRule.runOnUiThread {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
    }
}
```

Update the EPUB fixture paragraph in the same file so selection always has enough text:

```kotlin
addEntry(zip, "OEBPS/chapter1.xhtml", """
    <html xmlns="http://www.w3.org/1999/xhtml">
      <body>
        <h1>Chapter 1</h1>
        <p>Scholarship keeps the lookup sheet path realistic for selection testing.</p>
      </body>
    </html>
""".trimIndent())
```

- [ ] **Step 2: Run the regression test and verify it fails for the right reason**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderSystemBarTest"
```

Expected: FAIL in the new Define and Translate tests because `assertSystemBarsVisibility(visible = false)` times out after the lookup sheet is dismissed.

- [ ] **Step 3: Implement the minimal production change**

Apply the following exact edits.

In `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt`:

```kotlin
@Composable
internal fun ReaderSystemBarEffect(
    showControls: Boolean,
    globalSettings: GlobalSettings,
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(showControls, globalSettings.showSystemBar, resumeTrigger, refreshToken) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (showControls || globalSettings.showSystemBar) {
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

In `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`:

```kotlin
var showControls by remember { mutableStateOf(false) }
var selectionSessionEpoch by remember(book.id) { mutableIntStateOf(0) }
var systemBarRefreshToken by remember(book.id) { mutableIntStateOf(0) }
var isTextSelectionSessionActive by remember(book.id) { mutableStateOf(false) }
var isSelectionHandleDragActive by remember(book.id) { mutableStateOf(false) }
```

```kotlin
ReaderSystemBarEffect(
    showControls = showControls,
    globalSettings = globalSettings,
    refreshToken = systemBarRefreshToken,
)
```

```kotlin
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
    onPersistGlobalSettings = { updated -> scope.launch { settingsManager.updateGlobalSettings(updated) } },
    onNavigatePrev = ::navigatePrev,
    onNavigateNext = ::navigateNext,
    onMainScrubberDragStart = {
        hasReaderUserInteracted = true
        isInitialScrollDone = true
    },
    onLookupSheetDismissed = { systemBarRefreshToken++ },
)
```

In `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`:

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
    val onLookupSheetDismissed: () -> Unit = {},
)
```

In `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt`:

```kotlin
internal fun buildReaderChromeCallbacks(
    onShowControlsChange: (Boolean) -> Unit,
    onTextSelectionActiveChange: (Int, Boolean) -> Unit,
    onSelectionHandleDragChange: (Int, Boolean) -> Unit,
    onClearTextSelection: () -> Unit,
    onToggleTocSort: () -> Unit,
    onReleaseOverscroll: () -> Unit,
    onSaveAndBack: () -> Unit,
    onOpenToc: () -> Unit,
    onCloseToc: () -> Unit,
    onLocateCurrentChapterInToc: () -> Unit,
    onJumpToChapter: (Int) -> Unit,
    onSelectTocChapter: (Int) -> Unit,
    onPreviewSettings: (GlobalSettingsTransform) -> Unit,
    onPersistSettings: (GlobalSettingsTransform) -> Unit,
    onNavigatePrev: () -> Unit,
    onNavigateNext: () -> Unit,
    onMainScrubberDragStart: () -> Unit,
    onLookupSheetDismissed: () -> Unit = {},
): ReaderChromeCallbacks {
    return ReaderChromeCallbacks(
        onShowControlsChange = onShowControlsChange,
        onTextSelectionActiveChange = onTextSelectionActiveChange,
        onSelectionHandleDragChange = onSelectionHandleDragChange,
        onClearTextSelection = onClearTextSelection,
        onToggleTocSort = onToggleTocSort,
        onReleaseOverscroll = onReleaseOverscroll,
        onSaveAndBack = onSaveAndBack,
        onOpenToc = onOpenToc,
        onCloseToc = onCloseToc,
        onLocateCurrentChapterInToc = onLocateCurrentChapterInToc,
        onJumpToChapter = onJumpToChapter,
        onSelectTocChapter = onSelectTocChapter,
        onPreviewSettings = onPreviewSettings,
        onPersistSettings = onPersistSettings,
        onNavigatePrev = onNavigatePrev,
        onNavigateNext = onNavigateNext,
        onMainScrubberDragStart = onMainScrubberDragStart,
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
```

In `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt`:

```kotlin
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
    onLookupSheetDismissed: () -> Unit,
): ReaderChromeCallbacks {
    return buildReaderChromeCallbacks(
        onShowControlsChange = { shouldShow ->
            if (shouldShow) {
                invalidateSelectionSession("showControls")
            }
            onShowControlsChange(shouldShow)
        },
        onTextSelectionActiveChange = { epoch, isActive ->
            if (epoch == selectionSessionEpoch) {
                onTextSelectionSessionActiveChange(isActive)
            } else {
                logReaderSelectionTransition {
                    "selection.shell.ignoreActiveCallback staleEpoch=$epoch currentEpoch=$selectionSessionEpoch active=$isActive"
                }
            }
        },
        onSelectionHandleDragChange = { epoch, isDragging ->
            if (epoch == selectionSessionEpoch) {
                onSelectionHandleDragActiveChange(isDragging)
            } else {
                logReaderSelectionTransition {
                    "selection.shell.ignoreHandleCallback staleEpoch=$epoch currentEpoch=$selectionSessionEpoch dragging=$isDragging"
                }
            }
        },
        onClearTextSelection = { invalidateSelectionSession("chromeClearSelection") },
        onToggleTocSort = {
            onTocSortChange(
                if (tocSort == TocSort.Ascending) TocSort.Descending else TocSort.Ascending,
            )
        },
        onReleaseOverscroll = onReleaseOverscroll,
        onSaveAndBack = onSaveAndBack,
        onOpenToc = {
            invalidateSelectionSession("openToc")
            onOpenToc()
        },
        onCloseToc = onCloseToc,
        onLocateCurrentChapterInToc = onLocateCurrentChapterInToc,
        onJumpToChapter = onJumpToChapter,
        onSelectTocChapter = onSelectTocChapter,
        onPreviewSettings = { transform ->
            val previewedSettings = transform(effectiveSettings)
            onSettingsDraftChange(ReaderSettingsDraft.from(previewedSettings))
        },
        onPersistSettings = { transform ->
            val updatedSettings = transform(effectiveSettings)
            if (
                settingsDraft != null ||
                updatedSettings.fontSize != globalSettings.fontSize ||
                updatedSettings.lineHeight != globalSettings.lineHeight ||
                updatedSettings.horizontalPadding != globalSettings.horizontalPadding
            ) {
                onSettingsDraftChange(ReaderSettingsDraft.from(updatedSettings))
            }
            onPersistGlobalSettings(updatedSettings)
        },
        onNavigatePrev = onNavigatePrev,
        onNavigateNext = onNavigateNext,
        onMainScrubberDragStart = onMainScrubberDragStart,
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
```

In `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt`:

```kotlin
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
    onLookupSheetDismissed = callbacks.onLookupSheetDismissed,
)
```

In `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`:

```kotlin
@Composable
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
        onLookupSheetDismissed = onLookupSheetDismissed,
    )
}
```

In `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`:

```kotlin
@Composable
internal fun EpubReaderRuntime(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    chapterElements: List<ChapterElement>,
    isLoadingChapter: Boolean,
    currentChapterIndex: Int,
    selectionSessionEpoch: Int = 0,
    onSelectionActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetDismissed: () -> Unit = {},
) {
    if (currentChapterIndex == -1 || isLoadingChapter) {
        ReaderChapterLoadingContent(themeColors)
        return
    }

    val sections = remember(chapterElements) {
        buildReaderChapterSections(chapterElements)
    }
    val selectionDocument = remember(sections) {
        buildReaderSelectionDocument(sections)
    }

    key(currentChapterIndex, selectionSessionEpoch) {
        ReaderChapterSelectionHost(
            settings = settings,
            themeColors = themeColors,
            listState = listState,
            selectionDocument = selectionDocument,
            selectionSessionEpoch = selectionSessionEpoch,
            onSelectionActiveChange = onSelectionActiveChange,
            onSelectionHandleDragChange = onSelectionHandleDragChange,
            onLookupSheetDismissed = onLookupSheetDismissed,
        ) { selectionController ->
            // existing LazyColumn body unchanged
        }
    }
}
```

In `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt`:

```kotlin
@Composable
internal fun ReaderChapterSelectionHost(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectionDocument: com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionDocument,
    selectionSessionEpoch: Int,
    onSelectionActiveChange: (Int, Boolean) -> Unit,
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetDismissed: () -> Unit = {},
    content: @Composable (
        com.epubreader.feature.reader.internal.runtime.epub.ReaderSelectionController,
    ) -> Unit,
) {
    com.epubreader.feature.reader.internal.runtime.epub.ReaderChapterSelectionHost(
        settings = settings,
        themeColors = themeColors,
        listState = listState,
        selectionDocument = selectionDocument,
        selectionSessionEpoch = selectionSessionEpoch,
        onSelectionActiveChange = onSelectionActiveChange,
        onSelectionHandleDragChange = onSelectionHandleDragChange,
        onLookupSheetDismissed = onLookupSheetDismissed,
        content = content,
    )
}
```

In `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt`:

```kotlin
@Composable
internal fun ReaderChapterSelectionHost(
    settings: GlobalSettings,
    themeColors: ReaderTheme,
    listState: LazyListState,
    selectionDocument: ReaderSelectionDocument,
    selectionSessionEpoch: Int,
    onSelectionActiveChange: (Int, Boolean) -> Unit,
    onSelectionHandleDragChange: (Int, Boolean) -> Unit = { _, _ -> },
    onLookupSheetDismissed: () -> Unit = {},
    content: @Composable (ReaderSelectionController) -> Unit,
) {
    // existing selection-host body unchanged above this point

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

- [ ] **Step 4: Run the updated regression and verify it passes**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderSystemBarTest"
```

Expected: PASS for:
- `reader_DismissingDefineLookup_RehidesSystemBars`
- `reader_DismissingTranslateLookup_RehidesSystemBars`
- the existing `reader_ToggleControls_UpdatesSystemBars`

- [ ] **Step 5: Commit the focused fix**

```powershell
git add feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt
git commit -m "fix: rehide reader system bars after lookup dismiss"
```

### Task 2: Focused Regression Sweep And Manual Reader Smoke

**Files:**
- Modify: none
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt`
- Test: `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderChapterSelectionHostActionsTest.kt`

- [ ] **Step 1: Run the lookup-dismiss regression together with the selection-action regression**

Run:

```powershell
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderSystemBarTest"
.\gradlew.bat :feature:reader:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.epubreader.feature.reader.ReaderChapterSelectionHostActionsTest"
```

Expected: PASS. The new system-bar regression stays green, and Copy/Define/Translate still dismiss selection correctly.

- [ ] **Step 2: Re-run the repo line-limit guard**

Run:

```powershell
.\gradlew.bat checkKotlinFileLineLimit
```

Expected: PASS with no Kotlin file exceeding the 500-line cap.

- [ ] **Step 3: Perform the manual acceptance smoke**

Manual sequence:

1. Open an EPUB in the reader with `Show System Bar` disabled.
2. Long-press text, tap `Define`, then exit the webview sheet. Confirm the system bars are hidden again without opening reader controls.
3. Repeat with `Translate`.
4. Tap once to open reader controls, then tap again to close them. Confirm the old control-toggle hide/show behavior still works.
5. Repeat one `Copy` action and confirm selection still dismisses immediately.

- [ ] **Step 4: If the manual smoke matches the automated result, verify only the intended reader files changed**

Run:

```powershell
git diff --stat -- feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderSystemBarTest.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenBindings.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShellCallbacks.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderInternalFacades.kt feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterSelectionHost.kt
```

Expected: the diff stat only lists the intended reader files for this fix, without dragging in unrelated app/settings/library changes from the already-dirty worktree.

## Self-Review

- Spec coverage: the plan adds deterministic regression coverage for both Define and Translate dismissal, keeps the system-bar side effect owned by the reader shell, and avoids touching restoration/progress/navigation.
- Placeholder scan: no `TODO`, `TBD`, or “similar to above” shortcuts remain.
- Type consistency: the callback name stays `onLookupSheetDismissed` from `ReaderChromeCallbacks` through `ReaderChapterContent`, `EpubReaderRuntime`, the facade wrapper, and the internal selection host; the shell trigger stays `systemBarRefreshToken` from state to `ReaderSystemBarEffect`.
