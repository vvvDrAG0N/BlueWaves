# Prompt Templates

This file provides copy-paste starter prompts for future agents working on Blue Waves.

Use this together with:
- `AGENTS.md`
- `docs/README.md`
- `docs/package_map.md`

Do not confuse:
- `feature`: implement new functionality
- `review`: inspect existing code or a diff for bugs, regressions, and risks

## Default Rule

Start most prompts with:

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, and `docs/package_map.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before loading raw files.
If the task crosses packages or the entry point is unclear, run `graphify query "<question>" --budget 1200` before opening source files.
Then read only the docs directly relevant to this task.
```

## Bug Fix

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/AI_ENTRY_POINTS.md`, and `docs/AI_DEBUG_GUIDE.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
Read any additional docs that are directly relevant to the touched area.

Task: bug fix
Goal: <exact bug to fix>
Repro:
1. <step>
2. <step>
3. <current wrong behavior>

Expected behavior:
- <expected result>

Scope:
- Allowed: <files/packages>
- Avoid: <files/packages>

Constraints:
- Keep behavior unchanged outside this bug
- Keep `SettingsManager` as the persisted source of truth where applicable
- Ask before any DataStore/schema or major architectural change
- Prefer minimal edits

Verification:
- Run `.\gradlew.bat assembleDebug`
- Explain root cause
- Explain why the fix does not introduce stale state or regressions
```

## Feature

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
Read any additional docs relevant to the feature area before editing.

Task: feature
Goal: implement <feature name>

User flow:
1. <user action>
2. <system response>
3. <result>

Acceptance criteria:
- <criterion 1>
- <criterion 2>
- <criterion 3>

Scope:
- Preferred files/packages: <files/packages>

Constraints:
- Keep existing behavior unchanged unless required for the feature
- Keep state ownership aligned with current architecture
- Ask before DataStore/schema changes or major refactors
- Preserve reader restoration/parsing behavior unless this feature explicitly targets them
- Add the smallest appropriate automated test for new non-trivial behavior, or explain why that test is not practical yet and give manual verification steps

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize behavior changes
- Mention tradeoffs and remaining risks
```

## Code Review

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, and the docs relevant to the changed area first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.

Task: review
Scope: review <files / package / branch diff>

Review focus:
- Bugs
- Behavioral regressions
- State management risks
- Missing tests
- Performance or memory risks where relevant

Constraints:
- Do not make code changes unless I ask
- Findings first, ordered by severity
- Include file and line references
- Mention residual risks or testing gaps if no findings

Output format:
- Findings
- Open questions / assumptions
- Short summary
```

## Safe Refactor

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.

Task: safe refactor
Goal: refactor <area> for modularity/readability without behavior changes

Scope:
- Allowed: <files/packages>
- Avoid: <files/packages>

Constraints:
- No behavior changes
- No DataStore/schema changes
- No navigation model changes
- No reader restoration logic changes unless explicitly approved
- Keep edits incremental and package responsibilities clear

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize old vs new file boundaries
- Call out any residual coupling left for a later phase
```

## Unit Test

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
Read any additional docs directly relevant to the touched area before editing.

Task: unit test
Goal: add or update unit tests for <target behavior / file / function>

Target under test:
- File/package: <file or package>
- Public or internal API: <functions / classes / behaviors>
- Test type: JVM unit test first (`app/src/test/java`) unless Android runtime is truly required

Coverage goals:
- <main happy path>
- <important edge case>
- <regression scenario>

Scope:
- Allowed: <test files, minimal production changes only if required for testability>
- Avoid: <unrelated production refactors, schema changes, navigation changes>

Constraints:
- Prefer pure JVM tests over instrumentation tests
- Do not change behavior just to satisfy a test unless explicitly approved
- Keep production edits minimal and package responsibilities clear
- If Android/runtime dependencies make JVM tests impossible, explain why before switching to `androidTest`
- Preserve reader restoration, parser safety, and persistence behavior unless the task explicitly targets them

Verification:
- Run `.\gradlew.bat testDebugUnitTest`
- If production code changed, also run `.\gradlew.bat assembleDebug`
- Summarize tested behaviors
- Call out any remaining untested risks or cases that would require instrumentation tests
```

## Instrumentation Test

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
Read any additional docs directly relevant to the touched area before editing.

Task: instrumentation test
Goal: add or update runtime/device coverage for <target behavior / screen / flow>

Target under test:
- File/package: <file or package>
- Runtime behavior: <screen, gesture, persistence, parser integration, or app flow>
- Test type: instrumentation test (`app/src/androidTest/java`)

Coverage goals:
- <main end-to-end behavior>
- <important runtime edge case>
- <regression scenario>

Scope:
- Allowed: <androidTest files, minimal production changes only if required for testability or accessibility>
- Avoid: <unrelated production refactors, schema changes, navigation changes>

Constraints:
- Prefer filtered class runs instead of the full instrumentation suite while iterating
- Do not change behavior just to satisfy a test unless explicitly approved
- Keep production edits minimal and package responsibilities clear
- Preserve reader restoration, parser safety, and persistence behavior unless the task explicitly targets them

Verification:
- Run `.\gradlew.bat --% -Pandroid.testInstrumentationRunnerArguments.class=<fqcn> connectedDebugAndroidTest`
- If production code changed, also run `.\gradlew.bat assembleDebug`
- Summarize tested behaviors
- Call out remaining gaps that still are not covered
```

## Feature Test Matrix

Use this when deciding what kind of coverage a new feature needs:

- Pure logic, sorting, parsing helpers, JSON helpers:
  - JVM unit test
- Android-aware local logic with `Context`, resources, or package info:
  - Robolectric / local Android-aware test
- Compose UI, gestures, restoration, persistence through real runtime, parser integration:
  - instrumentation test
- Tiny cosmetic-only changes with no logic branch:
  - manual verification may be enough, but say that explicitly

## Graphify Add-On

Use this when the task is broad, cross-package, or the first file is unclear:

```text
Also use the project graph first.
If `graphify-out/GRAPH_REPORT.md` is missing or stale, run `python scripts/rebuild_graphify.py`.
Read `graphify-out/GRAPH_REPORT.md` before raw files.
Run `graphify query "<question>" --budget 800-1500` to narrow the file set.
If `graphify-out/wiki/index.md` exists, browse it instead of opening multiple large files.
```

## Reader Add-On

Use this when the task touches the reader:

```text
Also read `docs/reader_flow.md`, `docs/ai_mental_model.md`, and `docs/known_risks.md` before editing.
Also read `docs/reader_screen.md` before loading reader files.
Start with `feature/reader/ReaderScreen.kt`.
Load `feature/reader/ReaderScreenContracts.kt` if you only need the reader surface map.
Load `feature/reader/ReaderScreenChrome.kt` only for TOC/overlay/app-bar work.
Load `feature/reader/ReaderScreenControls.kt` only for controls/scrubber/chapter rendering work.
Do not change `isInitialScrollDone`, `isRestoringPosition`, overscroll behavior, or restoration sequencing without explicit validation.
```

## Parser Add-On

Use this when the task touches EPUB parsing:

```text
Also read `docs/quick_ref.md`, `docs/AI_DEBUG_GUIDE.md`, and `docs/ai_mental_model.md` before editing.
Also read `docs/epub_parsing.md` before loading parser files.
Start with `data/parser/EpubParser.kt`.
Load `data/parser/EpubParserBooks.kt` only for import/cache/TOC/metadata/book-ID work.
Load `data/parser/EpubParserChapter.kt` only for chapter/image/normalizePath work.
Preserve ZIP stream safety, `normalizePath()`, and book ID generation.
```

## Settings Add-On

Use this when the task touches DataStore-backed settings, folder metadata persistence, or reading progress shape:

```text
Also read `docs/settings_persistence.md` before editing.
Start with `data/settings/SettingsManagerContracts.kt` for keys/defaults and model mapping.
Then read `data/settings/SettingsManager.kt`, and load `data/settings/SettingsManagerJson.kt` only if the task is about folder/order/group JSON behavior.
Do not change preference key names or default values unless the task explicitly includes migration work.
```

## App Shell Add-On

Use this when the task touches library navigation, folder management, app-shell dialogs, or screen switching:

```text
Also read `docs/app_shell_navigation.md` before editing.
Start with `app/AppNavigationContracts.kt` if you need the shell state/callback map.
Then read `app/AppNavigation.kt`.
Load `app/AppNavigationStartup.kt` only for first-run/version/changelog work.
Load `app/AppNavigationOperations.kt` only for import/delete/last-read side effects.
Load `app/AppNavigationLibraryData.kt` only for folder-order/sort/drag derivation work.
Load only `app/AppNavigationLibrary.kt` or `app/AppNavigationDialogs.kt` if the task is rendering-specific.
Keep persistence ownership in `SettingsManager` and file operations in `EpubParser`.
```

## Minimal Version

Use this for straightforward tasks:

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, and `docs/package_map.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files, then load only relevant docs.

Task: <bug fix / feature / review / safe refactor>
Goal: <exact outcome>
Scope: <allowed files/packages>
Constraints:
- <must not change>
- Ask before schema or major architecture changes

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize root cause / changes / risks
```

## Notes

- Be explicit about the success condition.
- Be explicit about allowed scope.
- If the task touches reader restoration, parser safety, or DataStore shape, say so directly.
- If the task touches library navigation, folders, or app-shell dialogs, include the app shell add-on to reduce context load.
- If the task touches persistence, folder metadata, or reading progress shape, include the settings add-on to reduce context load.
- Use the instrumentation test template instead of the unit test template when the behavior requires a real device/emulator or Compose runtime.
- When adding new diagnostics, use `core/debug/AppLog.kt` instead of introducing new raw `Log` calls.
- `ask_mode_prompt_rules.md` is the rulebook for generated implementation prompts. This file is the human-facing quick-start version.
