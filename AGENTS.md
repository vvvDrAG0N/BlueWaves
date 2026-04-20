# Blue Waves Agent Guide

This document defines the architecture, coding standards, and modification safety rules for the Blue Waves EPUB Reader project.

## 1. Core Philosophy
- **Native & High Performance**: Use Jetpack Compose and native Android APIs. Avoid heavy abstractions.
- **Privacy & Speed**: Offline-first. Cache metadata to avoid re-parsing large EPUBs.
- **Reading Focus**: The UI must remain clean and content-focused.

## 2. Context Priority
1. **AGENT.md**: Defines the behavior rules and constraints for all modifications.
2. **docs/agent_memory/GLOBAL_STEPS.md**: The sequential ledger of all steps taken. **MANDATORY**: Append your latest step to this file at the end of every turn using `scripts/update_global_steps.py`.
3. **TODO**: The master roadmap for future goals and task ordering. **MANDATORY**: Read this to understand the current priority.
4. **docs/** folder: Contains detailed system knowledge and flow documentation.
5. **Existing Code**: Always takes precedence over assumptions. Analyze current implementations before suggesting changes.
6. **Refactors**: Large-scale refactors or architectural shifts require explicit user confirmation.

## 3. AI Development Protocol
1. **Analyze Docs First**: Always review the `/docs` folder before writing any code. It contains the logic behind complex features.
2. **Intent Check (MANDATORY)**: Before implementing any changes, analyze if the user's request is for discussion/planning or direct execution. If the user mentions "talk," "discuss," "let's think," or "planning," **STOP** and wait for confirmation before writing code.
3. **Step-First Protocol**: For complex tasks, record the intended plan in `GLOBAL_STEPS.md` as a "WIP" entry **BEFORE** modifying code. This ensures continuity if the session is interrupted.
4. **Minimal Edits**: Prefer surgical, minimal edits over large-scale rewrites to preserve original intent and code style.
5. **The Lego Philosophy**: Always build individual 'Lego Pieces' (Interfaces, Repos, or Components) in separate files **BEFORE** assembling them into a larger Shell or Screen. Each piece must be verified as functional in isolation.
6. **Protect the Reader**: Reader smoothness and scroll position restoration are sacred. Any changes to `ReaderScreen` must be stress-tested for these two properties.
5. **Confirm Changes**: Always ask for confirmation before making significant architectural changes or altering the DataStore schema.
6. **Verify Flows**: After any modification, re-verify the affected flow (e.g., if changing the parser, verify both metadata extraction and chapter rendering).
7. **Right-Sized Tests**: New non-trivial features should ship with the smallest automated test that proves them: JVM for pure logic, Robolectric/local Android-aware tests for framework-dependent logic, instrumentation for runtime/UI flows. If no automated test is added, explain why and provide manual verification steps.

## 4. Architecture Rules

### Navigation
- **State-Based Navigation**: Use an `enum class Screen` in `MainActivity.kt` to switch views via `AnimatedContent`. 
- **DO NOT** add Jetpack Navigation Component unless complexity drastically increases.

### Data & Persistence
- **SettingsManager**: All global and per-book state must go through `SettingsManager.kt` using **Jetpack DataStore (Preferences)**.
- **Reactive Flow**: UI should collect settings as `State` to ensure real-time updates (e.g., font size changes reflecting instantly in the reader).

### EPUB Engine (`EpubParser.kt`)
- **Extraction Strategy**: EPUBs are extracted into `cacheDir/books/{id}`. 
- **Metadata Caching**: `metadata.json` is stored in the book's folder. Always check for this file before re-parsing the ZIP.
- **Sealed Element Hierarchy**: Use `ChapterElement` (Text/Image) to represent content. This keeps the `LazyColumn` logic clean.

## 4. Reader Logic & UI Conventions

### Forced LTR
- The project enforces `LayoutDirection.Ltr` in `MainActivity` and `ReaderScreen` to maintain consistent reading behavior (scrolling and gestures) even on RTL-configured devices.

### Theme Handling
- Use `getThemeColors(theme)` in `ReaderScreen.kt`. 
- Standard Material 3 color schemes are used for the Library/Settings, but the **Reader** uses specific hex colors (Sepia: `#F4ECD8`, Dark: `#121212`) for better eye comfort.

### Scroll & Progress Restoration
- The most critical logic is the sync between `LaunchedEffect` and `LazyListState` in `ReaderScreen.kt`.
- **Restoration Flow**: Wait for `totalItemsCount` to match `chapterElements.size` before attempting `scrollToItem`.
- **Position Saving**: Debounced saving (500ms delay) to prevent excessive disk writes during scrolling.

## 5. Implicit Coding Conventions
- **IO Safety**: Always use `withContext(Dispatchers.IO)` for parsing, file operations, and DataStore edits.
- **HTML Cleanup**: Use the `unescapeHtml()` and `normalizePath()` helpers in `EpubParser` to handle malformed XHTML and relative image paths.
- **Coroutines**: Prefer `rememberCoroutineScope()` for UI-triggered actions (like button clicks) and `LaunchedEffect` for state-driven side effects.
- **Logging**: Use `core/debug/AppLog.kt` for new app logs instead of scattering raw `android.util.Log` calls. Keep debug/info logs debug-only, keep warn/error logs high-signal, and never log book contents or noisy per-scroll/per-frame events.

## 6. Modification Safety Rules (CRITICAL)
1. **Parser Changes**: When modifying `parseChapter`, ensure that the `ZipFile` stream is always closed and that image path normalization supports various EPUB internal structures (`OEBPS/`, `OPS/`, etc.).
2. **Reader Scroll**: Do not modify the `isInitialScrollDone` or `isRestoringPosition` flags without fully testing book opening and chapter switching. Breaking these will cause the reader to lose the user's place.
3. **Overscroll Navigation**: The `NestedScrollConnection` in `ReaderScreen` is sensitive. Ensure `verticalOverscroll` resets correctly on `Release`.
4. **Version Tracking**: If adding features that change the data schema, update `changelog.json` and handle version migration in `AppNavigation`.
- `Simulate the Validation Checklist: Before finalizing any change to ReaderScreen.kt, mentally execute the Validation Checklist found in ai_mental_model.md (Position Persistence, Chapter Boundary, Theme Reactivity, Parser Stress, Memory Safety). If the proposed change would cause any checklist item to fail, the change is unsafe and must be revised.`


## 7. Library Management
- The library is a scan of the `books` directory.
- `EpubBook.id` is a MD5 hash of URI + FileSize to prevent duplicate entries for the same file.
- Cover thumbnails are extracted once and stored as `cover_thumb.png` in the cache folder.

## AI Documentation Awareness
- `docs/` contains authoritative architectural memory.
- `ai_mental_model.md` defines validation expectations and safety heuristics.
- `quick_ref.md` is optimized for fast context loading and high-signal reference.
- **Mandatory Reading**: Agents must read these files before modifying `ReaderScreen.kt`, `EpubParser.kt`, or any core scroll and parsing logic.

## Documentation Preservation Rule
AI agents must treat AGENT.md and docs/ as stable architectural memory.
Do not refactor or summarize these files unless explicitly instructed.
New knowledge must be appended, never replaced.

## browser-use

this project has web browser through browser-use.md.

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure.
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files.
- **Maintenance**: The graph must be kept fresh. Use `python scripts/check_graph_staleness.py` to check if a rebuild is needed.
- **Rebuild**: After structural code changes or documentation updates, run `python scripts/check_graph_staleness.py --rebuild` to synchronize the graph.
- Automated check is integrated into `scripts/rebuild_graphify.py` and `scripts/check_graph_staleness.py`.

## Graph-First Loading Workflow

For low-token work in this repo:
- Read `docs/project_graph.md` before broad codebase questions or cross-package tasks.
- If `graphify-out/GRAPH_REPORT.md` exists, use it before opening raw source files.
- If scope is unclear, run `graphify query "<question>" --budget 800-1500` to narrow the file set.
- After the graph narrows the area, read only the relevant area doc and then only the files named by the graph.
- Use `python scripts/rebuild_graphify.py` when `graphify-out/` is missing, stale, or after structural code changes.

## AI Agent Workflow Addendum

This section extends the base rules above for repo agents and model handoffs.

### Model Routing
- `Codex` is the primary implementation and verification agent for this repo. Treat it as the default owner for difficult code changes, build/test execution, focused debugging, and final validation.
- `Gemini` is a secondary planning/review agent. Use it for repo-wide scoping, backlog decomposition, prompt authoring, design comparison, and optional second-pass review, but not as the default owner of difficult implementation work unless the user explicitly asks for that split.
- When both are used on the same task, keep one active owner. The planning/review agent should frame the work, but the implementation agent must still verify everything against the current code and docs.
- Do not treat model-specific docs as a second source of truth over `AGENTS.md`; they are companion guides, not replacements.
- Multi-agent coordination through Microsoft Agent Framework is a future optimization layer. Do not introduce framework-specific repo changes unless the task explicitly asks for that integration.

### Persistent Memory
- Durable repo knowledge belongs in `docs/`, `AGENTS.md`, or the root `TODO`, not only in transient chat history.
- When a task reveals stale AI guidance, repair the repo memory close to the affected workflow. Example: add the missing doc, update the index, or append the new rule to the relevant guide.
- Append new knowledge; do not silently rewrite existing architectural memory.
- Keep prompt libraries aligned with the current root `TODO` ordering so future agents do not optimize the wrong backlog item first.

### Shared Memory
- Shared durable memory for agent collaboration lives in `docs/agent_memory/`.
- Treat `docs/agent_memory/README.md` as the entry point, then update the focused memory file (`decision_log.md`, `handoffs.md`, `debug_lessons.md`, `open_questions.md`) instead of scattering one-off notes.
- Microsoft Agent Framework runtime state is coordination state, not the long-term project memory source of truth.
- Obsidian is the human browsing layer for repo markdown and `graphify-out/wiki/`; do not keep critical project memory only in Obsidian config, plugin state, or private local notes.
- When a new decision becomes stable and architectural, promote it from agent memory into the canonical docs.

### Token Efficiency
- Stay graph-first. Prefer `docs/project_graph.md`, `graphify-out/GRAPH_REPORT.md`, `graphify-out/wiki/index.md`, and the low-context area docs before raw source files.
- Do not load every split file in a package by default. Use the area docs to choose the smallest relevant surface.
- Prefer contract-map files (`AppNavigationContracts`, `SettingsManagerContracts`, `ReaderScreenContracts`) when they answer the question without opening the full implementation.
- If a task is broad, first narrow it into a file list, owner list, or execution order before opening more code.

### Task Handling
- Start each non-trivial task by restating the goal, the likely owner files, and the verification path.
- Prefer minimal, surgical edits over broad rewrites.
- Treat prompt quality as part of the implementation quality: the next agent should be able to continue from the repo state without reconstructing the entire context.
- For cross-model or cross-agent handoffs, spell out:
  - the exact goal
  - allowed files
  - files to avoid
  - risk areas
  - required verification
- If a task changes behavior, update the relevant docs in the same pass when practical.
- When a Phase or major Task from the `TODO` is completed, you MUST mark it as `[x]` and update the next priority items if needed.

### Debug Handling
- Start debugging from `docs/AI_DEBUG_GUIDE.md`, then the focused area doc, then the smallest set of implementation files.
- Prefer high-signal instrumentation through `core/debug/AppLog.kt` instead of ad-hoc logging.
- Capture repro steps and expected behavior before changing logic.
- Remove temporary debug noise before finishing unless the retained log materially helps future debugging.

### Prompt Workflow
- `docs/PROMPT_TEMPLATES.md` is the quick-start library for common task types.
- `docs/ask_mode_prompt_rules.md` is the rulebook for generated or delegated implementation prompts.
- `docs/TODO_PROMPTS.md` is the prompt library for top-level backlog items and should track the current root `TODO`.

## 8. Titan Engine Documentation Protocol (CRITICAL)
1.  **Documentation Sync**: No task in the `:engine-waves` module is "Done" until the corresponding documentation in `docs/v2_engine/` is updated.
2.  **Lego Registration**: Every new "Lego Piece" (Interface, Repository, or Component) must be registered in `ENGINE_ARCHITECTURE.md` with its purpose and contract **BEFORE** assembly.
3.  **Vision-to-Execution Pipeline**: 
    -   **Mind Map**: High-level vision. Use this to derive the **Phases**.
    -   **Visual Map**: The spatial "Habitat" and UI layout map for all media types.
    -   **Technique List**: Mandatory technical reference for Shaders, Ray Tracing, and Math.
    -   **TODO**: Master Task List. Convert Mind Map Phases into concrete tasks here.
    -   **GLOBAL_STEPS**: Execution Ledger. Record every atomic change here.
4.  **Native Comments**: C++ code must include "Titan Performance Notes" for any non-obvious optimizations (mmap, vectorization, etc.).
4.  **Zero-Stale Memory**: If a JNI signature changes, the documentation MUST change in the same turn.
5.  **Audit Rule**: Before starting any V2 task, the agent must read `docs/v2_engine/` to ensure continuity.
6.  **Titan Logging Protocol**: Every new native feature MUST include explicit logging via `TitanLog.h` and include `TitanTimer` performance metrics for IO or Rendering paths.
7.  **Melodies Protocol (ENFORCED)**: The AI agent is responsible for proactively recording significant creative inspirations or "Melodic Thoughts" in `docs/v2_engine/MELODIES.md`. **MANDATORY**: At the end of every major phase or session, the agent MUST ask the user if there is a 'Melodic Thought' to record.
8.  **Titan Atomic File Rule**: Prefer many small, 'Atomic' files over large monolithic files. A single file should ideally focus on ONE responsibility and stay under 500 lines. If a file exceeds this or handles multiple logic domains, it MUST be refactored into smaller Lego pieces.
9.  **Titan Header Rule (C++)**: To prevent circular dependencies ("cyclers"):
    -   Every `.h` file MUST have `#ifndef HEADER_NAME_H` guards.
    -   Use **Forward Declarations** (e.g., `class MyClass;`) in headers whenever possible instead of `#include`.
    -   Only include dependencies in the `.cpp` file to keep header compilation fast and clean.
