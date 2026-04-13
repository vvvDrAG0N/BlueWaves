# TODO Implementation Prompts

These prompts are derived from the current top-level tasks in [`TODO`](../TODO) and the feature-oriented guidance in [`docs/PROMPT_TEMPLATES.md`](./PROMPT_TEMPLATES.md).

Use one prompt per implementation task.

## Theme System

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
If the task crosses packages or the entry point is unclear, run `graphify query "What files own custom themes, theme persistence, and reader theme application?" --budget 1200` before opening source files.
Then read only the docs directly relevant to this task.

Also read `docs/settings_persistence.md`, `docs/reader_screen.md`, `docs/reader_flow.md`, `docs/ai_mental_model.md`, `docs/known_risks.md`, `docs/ui_rules.md`, and `docs/test_checklist.md` before editing.
Also read `docs/app_shell_navigation.md` before editing.
Start with `app/src/main/java/com/epubreader/MainActivity.kt`, `app/src/main/java/com/epubreader/data/settings/SettingsManagerContracts.kt`, `app/src/main/java/com/epubreader/data/settings/SettingsManager.kt`, `app/src/main/java/com/epubreader/core/model/SettingsModels.kt`, `app/src/main/java/com/epubreader/feature/settings/SettingsScreen.kt`, and `app/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`.
Load `app/src/main/java/com/epubreader/feature/reader/ReaderScreenControls.kt` for theme controls and rendering.
Load `app/src/main/java/com/epubreader/app/AppNavigationLibrary.kt` only if library or folder-sidebar visuals need targeted adjustments beyond the shared Material theme.
Load `app/src/main/java/com/epubreader/feature/reader/ReaderScreen.kt` only if theme reactivity or restoration wiring actually requires it.

Task: feature
Goal: implement user-defined custom themes and color palettes for the whole app, including library UI, folders sidebar, settings surfaces, and the reader

User flow:
1. User opens settings and creates, edits, or deletes a named custom reader theme.
2. User selects that custom theme as the active reading theme.
3. The library UI, folders sidebar, settings surfaces, and reader all apply the selected theme immediately and preserve it across app restarts.

Acceptance criteria:
- Users can create, edit, select, and delete custom themes without breaking the existing built-in themes or stored theme names.
- Theme changes propagate immediately through app-shell surfaces and reader surfaces without reopening the book.
- The selected custom theme drives the app-level Material theme path in `MainActivity.kt`, so the library UI and folders sidebar stay visually aligned with the same theme identity.
- The reader still gets explicit content-first foreground/background colors instead of blindly inheriting generic Material surface tokens, but those reader colors must remain part of the same custom theme definition.
- Persistence remains owned by `SettingsManager`.
- Ship unit tests for theme model, persistence, and application paths.
- Update relevant markdown docs under `docs/`.
- Rebuild graphify artifacts after implementation.

Scope:
- Preferred files/packages: `app/src/main/java/com/epubreader/MainActivity.kt`, `app/src/main/java/com/epubreader/data/settings/*`, `app/src/main/java/com/epubreader/core/model/SettingsModels.kt`, `app/src/main/java/com/epubreader/feature/settings/SettingsScreen.kt`, `app/src/main/java/com/epubreader/feature/reader/ReaderScreenContracts.kt`, `app/src/main/java/com/epubreader/feature/reader/ReaderScreenControls.kt`, optional minimal touches in `app/src/main/java/com/epubreader/app/AppNavigationLibrary.kt`, and matching tests under `app/src/test/java/com/epubreader/**` or `app/src/androidTest/java/com/epubreader/**`.
- Avoid: broader app-shell refactors and avoid `feature/reader/ReaderScreen.kt` restoration logic unless absolutely necessary.

Constraints:
- Keep existing behavior unchanged unless required for the feature.
- Keep state ownership aligned with current architecture.
- Ask before major DataStore/schema changes or major refactors.
- Preserve reader restoration/parsing behavior unless this feature explicitly targets them.
- Keep app-level theming centralized through `MainActivity.appColorScheme(...)` or an equivalent single source, rather than sprinkling ad-hoc library colors through app-shell files.
- Treat `isInitialScrollDone`, `isRestoringPosition`, the `delay(100)` restore settle step, and the `delay(500)` save debounce as sacred if `ReaderScreen.kt` is touched.
- Keep built-in theme identifiers backward-compatible and ensure custom-theme persistence is additive.
- Define custom themes so both app-wide Material colors and reader-specific colors can be derived from the same saved theme without desynchronizing library UI from reader UI.
- Add the smallest appropriate automated test for new non-trivial behavior; prefer JVM tests first, and add runtime coverage if local tests cannot prove app-wide or reader theme reactivity.

Verification:
- Run `.\gradlew.bat assembleDebug`
- Run `.\gradlew.bat testDebugUnitTest`
- If runtime theme propagation changes, run the smallest relevant filtered instrumentation coverage for settings persistence and reader theme reactivity
- Run `python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
- Summarize behavior changes, docs updated, tradeoffs, and remaining risks
```

## Format Support

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
If the task crosses packages or the entry point is unclear, run `graphify query "What files own PDF support, ZIP import flows, format detection, import routing, and parser/reader entry points?" --budget 1200` before opening source files.
Then read only the docs directly relevant to this task.

Also read `docs/app_shell_navigation.md`, `docs/epub_parsing.md`, `docs/quick_ref.md`, `docs/AI_DEBUG_GUIDE.md`, `docs/ai_mental_model.md`, and `docs/test_checklist.md` before editing.
Read `docs/reader_screen.md`, `docs/reader_flow.md`, and `docs/known_risks.md` as well if the implementation changes any reader entry point or EPUB handoff behavior.
Start with `app/src/main/java/com/epubreader/app/AppNavigationContracts.kt`, `app/src/main/java/com/epubreader/app/AppNavigation.kt`, `app/src/main/java/com/epubreader/app/AppNavigationOperations.kt`, `app/src/main/java/com/epubreader/data/parser/EpubParser.kt`, `app/src/main/java/com/epubreader/data/parser/EpubParserBooks.kt`, and `app/src/main/java/com/epubreader/core/model/LibraryModels.kt`.
Load `app/src/main/java/com/epubreader/data/parser/EpubParserChapter.kt` only if EPUB chapter/image behavior changes, and load `app/src/main/java/com/epubreader/feature/reader/*` only if EPUB/PDF reader routing or reader reuse requires it.

Task: feature
Goal: implement PDF support and ZIP-based import flows without regressing existing EPUB import or reading behavior

User flow:
1. User imports a supported file from the library flow.
2. The app detects the format and routes it to the correct import and cache path.
3. The imported item opens through the correct reader entry point, and unsupported archive contents fail gracefully with a clear result.

Acceptance criteria:
- The import path can distinguish supported EPUB, PDF, and ZIP-based inputs before committing a library mutation.
- Existing EPUB duplicate detection, cache layout, metadata loading, and reader behavior remain stable.
- PDF items can be represented in the library and opened through an explicit, maintainable reader path.
- ZIP-based imports handle unsupported, empty, or ambiguous archives deterministically instead of silently half-importing.
- Ship unit tests for format detection, import routing, and parser/reader entry points.
- Update relevant markdown docs under `docs/`.
- Rebuild graphify artifacts after implementation.

Scope:
- Preferred files/packages: `app/src/main/java/com/epubreader/core/model/LibraryModels.kt`, `app/src/main/java/com/epubreader/app/*`, `app/src/main/java/com/epubreader/data/parser/*`, any new dedicated format-specific feature package if needed, and matching tests under `app/src/test/java/com/epubreader/**`.
- Avoid: `SettingsManager` schema changes unless strictly necessary, and avoid destabilizing `feature/reader/ReaderScreen.kt` for EPUBs if a PDF-specific surface is the safer option.

Constraints:
- Keep existing behavior unchanged unless required for the feature.
- Keep state ownership aligned with current architecture.
- Ask before DataStore/schema changes, new external dependencies, or major refactors.
- Preserve reader restoration/parsing behavior unless this feature explicitly targets them.
- Keep `Screen`-based navigation; do not introduce Jetpack Navigation.
- Keep file-system and archive work in the parser layer, app-shell orchestration in `AppNavigation`, and persistence ownership in `SettingsManager`.
- Preserve EPUB invariants: existing EPUB book IDs, `metadata.json` behavior, `normalizePath()`, malformed-XHTML tolerance, and `.use {}` ZIP/InputStream handling.
- If PDF support needs a dedicated runtime surface, isolate it from the sacred EPUB restoration logic instead of overloading `ReaderScreen.kt`.
- Add the smallest appropriate automated tests; prefer JVM tests for routing/detection logic and only add instrumentation when runtime viewer behavior truly requires it.

Verification:
- Run `.\gradlew.bat assembleDebug`
- Run `.\gradlew.bat testDebugUnitTest`
- If runtime reader/viewer behavior changes, run the smallest relevant filtered instrumentation coverage
- Run `python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
- Summarize behavior changes, docs updated, tradeoffs, and remaining risks
```

## Edit Book

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
If the task crosses packages or the entry point is unclear, run `graphify query "What files own editing book metadata, custom covers, and chapter mutation workflows?" --budget 1200` before opening source files.
Then read only the docs directly relevant to this task.

Also read `docs/app_shell_navigation.md`, `docs/epub_parsing.md`, `docs/reader_screen.md`, `docs/reader_flow.md`, `docs/settings_persistence.md`, `docs/quick_ref.md`, `docs/AI_DEBUG_GUIDE.md`, `docs/ai_mental_model.md`, `docs/known_risks.md`, and `docs/test_checklist.md` before editing.
Start with `app/src/main/java/com/epubreader/app/AppNavigationContracts.kt`, `app/src/main/java/com/epubreader/app/AppNavigation.kt`, `app/src/main/java/com/epubreader/app/AppNavigationOperations.kt`, `app/src/main/java/com/epubreader/data/parser/EpubParser.kt`, `app/src/main/java/com/epubreader/data/parser/EpubParserBooks.kt`, and `app/src/main/java/com/epubreader/core/model/LibraryModels.kt`.
Load `app/src/main/java/com/epubreader/data/parser/EpubParserChapter.kt` if chapter/spine mutations affect parser entry resolution, `app/src/main/java/com/epubreader/data/settings/SettingsManager.kt` if progress fallback behavior changes, and `app/src/main/java/com/epubreader/feature/reader/ReaderScreen.kt` only if reader handoff or deleted-chapter recovery requires it.

Task: feature
Goal: implement in-app book editing for custom cover, editable title/author metadata, and chapter add/delete operations

User flow:
1. User opens an edit-book flow for an imported book.
2. User updates the cover, title, or author, and can add or delete chapters.
3. The library and reader reflect the changes, and the edited book remains stable after reopen, rescan, and reading-progress restoration.

Acceptance criteria:
- Users can set a custom cover and edit title/author from the app without corrupting cached metadata.
- Library surfaces and reader-facing metadata reflect the edited values consistently.
- Chapter add/delete operations update spine/TOC/book metadata in a way the parser and reader can reopen safely.
- If a saved chapter/progress target disappears because of edits, fallback behavior is explicit and safe instead of crashing or restoring to an invalid location.
- Ship unit tests for metadata editing and chapter mutation paths.
- Update relevant markdown docs under `docs/`.
- Rebuild graphify artifacts after implementation.

Scope:
- Preferred files/packages: `app/src/main/java/com/epubreader/app/*`, `app/src/main/java/com/epubreader/data/parser/*`, `app/src/main/java/com/epubreader/core/model/LibraryModels.kt`, any small UI files needed for the edit surface, and matching tests under `app/src/test/java/com/epubreader/**`.
- Avoid: changing book ID generation, changing DataStore key names/defaults, or refactoring `ReaderScreen.kt` restoration behavior unless the implementation proves that change is necessary and safe.

Constraints:
- Keep existing behavior unchanged unless required for the feature.
- Keep state ownership aligned with current architecture.
- Ask before DataStore/schema changes or major refactors.
- Preserve reader restoration/parsing behavior unless this feature explicitly targets them.
- Keep EPUB file operations and metadata/chapter mutation logic in the parser layer, not in UI or `SettingsManager`.
- Preserve EPUB invariants: cache folder layout, `metadata.json` integrity, `.use {}` stream safety, `normalizePath()`, malformed-XHTML tolerance, and cover thumbnail consistency.
- Treat `BookProgress.lastChapterHref` and chapter restoration as compatibility-sensitive; if chapter edits invalidate old hrefs, add a safe fallback path instead of letting progress restoration fail.
- Do not change `isInitialScrollDone`, `isRestoringPosition`, the `delay(100)` restoration settle step, or overscroll behavior unless there is explicit validation proving reader safety.
- Add the smallest appropriate automated tests; prefer JVM tests for metadata/chapter mutation helpers and only add runtime coverage when reader or UI behavior cannot be proven locally.

Verification:
- Run `.\gradlew.bat assembleDebug`
- Run `.\gradlew.bat testDebugUnitTest`
- If reader restoration or runtime edit flows change, run the smallest relevant filtered instrumentation coverage
- Run `python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
- Summarize behavior changes, docs updated, tradeoffs, and remaining risks
```

## Selectable Text

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
If the task crosses packages or the entry point is unclear, run `graphify query "What files own reader text rendering, appearance-bar toggles, and global settings toggles for selectable text?" --budget 1200` before opening source files.
Then read only the docs directly relevant to this task.

Also read `docs/settings_persistence.md`, `docs/reader_screen.md`, `docs/reader_flow.md`, `docs/ai_mental_model.md`, `docs/known_risks.md`, `docs/ui_rules.md`, and `docs/test_checklist.md` before editing.
Start with `app/src/main/java/com/epubreader/core/model/SettingsModels.kt`, `app/src/main/java/com/epubreader/data/settings/SettingsManagerContracts.kt`, `app/src/main/java/com/epubreader/data/settings/SettingsManager.kt`, `app/src/main/java/com/epubreader/feature/settings/SettingsScreen.kt`, and `app/src/main/java/com/epubreader/feature/reader/ReaderScreenControls.kt`.
Load `app/src/main/java/com/epubreader/feature/reader/ReaderScreen.kt` only if text-selection gestures need minimal coordination with tap-to-toggle controls, overscroll, or restoration behavior.
Load `app/src/main/java/com/epubreader/feature/reader/ReaderScreenChrome.kt` only if the appearance-bar mounting or chrome layout must change.

Task: feature
Goal: implement selectable text in the reader for chapter text only, with a persisted toggle in both the appearance bar and global settings

User flow:
1. User enables or disables selectable text from the library's global settings or the reader appearance bar.
2. When enabled, the reader allows selecting and copying chapter text only.
3. The preference persists across app restarts and both toggle surfaces stay in sync.

Acceptance criteria:
- Selectable text applies only to `ChapterElement.Text` rendering and does not make images, TOC content, overlays, or other reader chrome selectable.
- The global settings screen adds a row/switch patterned after the existing `Show System Bar` control.
- The appearance bar adds a matching toggle patterned after the existing `Show System Bar` / `Show Scrubber` controls.
- Persistence remains owned by `SettingsManager`, and the reader reflects the setting immediately without reopening the book.
- Reader scrolling, tap-to-show-controls behavior, scrubber behavior, overscroll chapter navigation, and reading-position restoration remain stable when the toggle is off and do not regress when the toggle is on.
- Ship unit tests for reader text-selection state, persistence, and UI toggle behavior.
- Update relevant markdown docs under `docs/`.
- Rebuild graphify artifacts after implementation if code structure changes.

Scope:
- Preferred files/packages: `app/src/main/java/com/epubreader/core/model/SettingsModels.kt`, `app/src/main/java/com/epubreader/data/settings/*`, `app/src/main/java/com/epubreader/feature/settings/SettingsScreen.kt`, `app/src/main/java/com/epubreader/feature/reader/ReaderScreenControls.kt`, and only the smallest necessary touch in `app/src/main/java/com/epubreader/feature/reader/ReaderScreen.kt`.
- Preferred tests: `app/src/test/java/com/epubreader/data/settings/*` for mapping/persistence and the smallest relevant UI/runtime coverage under `app/src/androidTest/java/com/epubreader/feature/settings/*` or `app/src/androidTest/java/com/epubreader/feature/reader/*`.
- Avoid: parser files, app-shell files, and broader reader restoration refactors unless they are proven necessary.

Constraints:
- Keep existing behavior unchanged unless required for the feature.
- Keep state ownership aligned with current architecture.
- Ask before DataStore/schema changes or major refactors.
- Preserve reader restoration/parsing behavior unless this feature explicitly targets them.
- Model the new setting as an additive global preference alongside the existing `showSystemBar` / `showScrubber` flow; do not rename existing preference keys or defaults.
- Implement selection only around rendered chapter text, using the smallest composable-scoped solution that avoids making the entire reader surface selectable.
- Be explicit about gesture coexistence: long-press/text selection must not accidentally break scroll, single-tap control toggling, or overscroll navigation.
- If `ReaderScreen.kt` must change, treat `isInitialScrollDone`, `isRestoringPosition`, the `delay(100)` restoration settle step, the `delay(500)` save debounce, and overscroll release behavior as sacred.
- Prefer JVM coverage for settings mapping and persistence, and add the smallest runtime coverage needed for real Compose toggle/selectable-text behavior if unit tests alone cannot prove it.

Verification:
- Run `.\gradlew.bat assembleDebug`
- Run `.\gradlew.bat testDebugUnitTest`
- If Compose toggle behavior or reader text selection changes cannot be proven locally, run the smallest relevant filtered instrumentation coverage
- If code structure changed, run `python3 -c "from graphify.watch import _rebuild_code; from pathlib import Path; _rebuild_code(Path('.'))"`
- Summarize behavior changes, docs updated, tradeoffs, and remaining risks
```
