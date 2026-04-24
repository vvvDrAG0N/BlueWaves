# Blue Waves Agent Guide

This file is the high-signal working contract for repo agents.

Keep this guide concise. Deep explanations belong in the canonical root docs under `docs/`.

## 1. Priorities

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/project_graph.md`
4. `graphify-out/GRAPH_REPORT.md`
5. the single relevant area doc
6. the current code

Existing code always wins over assumptions.

Large architectural shifts, module graph changes, and DataStore schema changes require explicit user confirmation.

## 2. Current Architecture Snapshot

Blue Waves is a modular Android app:

- `:app`
  - bootstrap and shell assembly only
- `:core:model`
  - shared pure models and theme contracts
- `:core:ui`
  - shared Compose UI and presentation helpers
- `:data:settings`
  - `SettingsManager` and DataStore-backed persistence
- `:data:books`
  - `EpubParser`, EPUB runtime, metadata cache, and parked PDF data/runtime seams
- `:feature:library`
- `:feature:reader`
- `:feature:settings`
- `:feature:editbook`
- `:feature:pdf-legacy`

Product boundary:

- The active shell is EPUB-first.
- PDF-origin books may exist in metadata/library flows.
- PDF open/import paths in the active shell remain intentionally disabled.
- Legacy PDF runtime code is parked and must not be reactivated accidentally.

Architecture rules:

- Navigation stays state-based through `Screen` and `AppNavigation`.
- Do not add Jetpack Navigation unless the user explicitly asks for that architectural change.
- `SettingsManager` remains the persisted source of truth for global settings and per-book progress.
- `EpubParser` remains the public parser facade.
- Dependency wiring stays manual. Do not introduce Hilt, Koin, or similar DI without explicit approval.

## 3. How To Load Context

Do not read the whole repo by default.

Start with:

1. `docs/README.md` if you need the canonical docs map
2. `docs/project_graph.md`
3. `graphify-out/GRAPH_REPORT.md`
4. one focused runtime doc:
   - `docs/app_shell_navigation.md`
   - `docs/settings_persistence.md`
   - `docs/epub_parsing.md`
   - `docs/reader_screen.md`
   - `docs/ui_rules.md`
   - `docs/AI_DEBUG_GUIDE.md`
   - `docs/ai_mental_model.md`
   - `docs/test_checklist.md`
5. only the specific files needed for the task

`docs/agent_memory/` is not part of the default read path.

## 4. Core Working Rules

- Prefer surgical edits over broad rewrites.
- Start each non-trivial task by identifying the owner files and the verification path.
- Preserve behavior unless the user explicitly asks for a behavior change.
- After any change, verify the affected flow.
- New non-trivial work should include the smallest appropriate automated test:
  - JVM for pure logic
  - Robolectric/local Android-aware for framework behavior
  - instrumentation for runtime/UI flows
- If no automated test is added, explain why and provide manual verification steps.

Implementation conventions:

- Use `withContext(Dispatchers.IO)` for file, ZIP, and DataStore work.
- Prefer `rememberCoroutineScope()` for UI-triggered actions and `LaunchedEffect` for state-driven effects.
- Use `com.epubreader.core.debug.AppLog` for durable logging instead of ad-hoc `Log`.
- Never log book contents or noisy per-frame/per-scroll data.

## 5. Anti-Monolith And Extraction Rules

The refactor established a hard size boundary. Keep it intact.

Size triggers:

- No Kotlin file under `src/main`, `src/test`, or `src/androidTest` may exceed 500 lines.
- `checkKotlinFileLineLimit` is a required guard, not a suggestion.
- Start planning a split once a file moves past roughly 350 lines or gains a second responsibility.
- If an edit would push a file near 450 lines, stop and extract before finishing.
- Prefer focused files with one ownership area over region-based "god files".

Placement rules:

- `:app` assembles and routes only. It must not absorb reusable feature logic.
- `:feature:*` owns feature-specific UI and orchestration.
- `:core:ui` owns cross-feature presentation primitives and shared Compose helpers.
- `:core:model` owns shared pure models, contracts, and mappers.
- `:data:*` owns persistence, parsing, storage, and runtime behavior.
- Do not make one feature depend on another feature for convenience reuse. Extract shared pieces downward into `:core:*` or `:data:*`.
- If a reusable piece needs knowledge of multiple feature modules, it likely belongs lower in the graph or should stay duplicated until the real boundary is clear.

Component extraction ladder:

1. Keep code inline if it is tiny, obvious, and single-use.
2. Extract a private local helper in the same file if that only improves readability.
3. Move to a feature-local file when the unit has its own UI, state, or callback surface.
4. Promote to a shared feature component only when multiple files in the same feature use it.
5. Promote to `:core:ui` only when multiple features share it and it stays presentation-only.
6. Promote to `:core:model` for shared pure models, contracts, or mapping logic.
7. Promote to `:data:*` for persistence, parser, storage, or runtime logic.
8. Create a new module only when the boundary is durable and the user has explicitly approved it.

Lego rules:

- Prefer domain-named pieces like `LibraryTabBar` over vague builders like `createTab`, `buildTab`, or `setupTab`.
- Prefer declarative APIs with explicit state plus callbacks over imperative parent, window, or position mutation APIs.
- Group reusable code by domain and ownership, not in catch-all `helpers.kt`, `utils.kt`, or `components.kt` files.
- Default to feature-local reuse first. Shared extraction is earned, not automatic.
- If a file starts holding unrelated pieces, split it even if the line count is still safe.

Pre-merge self-check:

- Where should this code live?
- Can it remain feature-local?
- Is the API declarative and domain-named?
- Does this add a second responsibility to the file?
- Does this push the file toward the split threshold?
- Am I creating convenience coupling between features?

## 6. High-Risk Areas

Reader safety rules:

- Reader smoothness and scroll restoration are sacred.
- Before touching reader restoration logic, read:
  - `docs/reader_screen.md`
  - `docs/ai_mental_model.md`
- Do not casually change:
  - `isInitialScrollDone`
  - `isRestoringPosition`
  - restoration timing
  - overscroll release behavior
- Mentally execute the reader validation checklist before finalizing reader changes.

Parser safety rules:

- Before touching parser logic, read `docs/epub_parsing.md`.
- Preserve `metadata.json` caching behavior.
- Ensure ZIP/stream resources are always closed.
- Keep malformed XHTML cleanup and path normalization robust across EPUB layouts.

Settings safety rules:

- Do not rename DataStore keys.
- Do not change defaults or schema shape without explicit approval.
- Preserve active-theme, folder, and progress persistence semantics unless the user asks otherwise.

Edit Book safety rules:

- Keep EPUB editing saves staged and atomic.
- Preserve chapter order, href stability, and cover update semantics.

## 7. Documentation Rules

- `docs/` root is for AI-agent runtime memory only.
- Root docs must not become prompt libraries, duplicate overview docs, or backlog prompt packs.
- Merge into an existing canonical root doc before creating a new one.
- Put parked or historical notes under `docs/legacy/`, not the root.
- `docs/agent_memory/` is the non-default continuity layer:
  - `step_history.md` for append-only substantial work history (Implementation, Audits, Reviews, or Planning). **Each entry must use a sequential index heading (e.g., `## 1. 2026-04-16 00:00`).**
  - `next_steps.md` for concrete queued follow-up work
- Do not treat `docs/agent_memory/` as the canonical architecture source of truth.
- Promote stable rules into `AGENTS.md` or canonical root docs instead of leaving them only in agent memory.
- After substantial work, append a structured entry to `docs/agent_memory/step_history.md`.
- If follow-up remains, update `docs/agent_memory/next_steps.md`.

This repo also includes:

- `browser-use.md`
- `graphify-out/`

Graph maintenance:

- Run `python scripts/check_graph_staleness.py` when unsure.
- Run `python scripts/check_graph_staleness.py --rebuild` after structural code changes or meaningful documentation updates.

## 8. Collaboration Rules

- `Codex` is the primary implementation and verification agent for this repo.
- `Gemini` is a secondary planning/review agent when the user wants that split.
- If multiple agents are involved, keep one clear implementation owner.
- If agents are working in parallel, keep file ownership disjoint whenever possible and avoid touching files another active agent is using unless the user explicitly wants overlap.
- Durable decisions belong in `AGENTS.md`, canonical root docs, or the structured `docs/agent_memory/` history.
- Planning or review agents must not let stale roadmap text in `README.md`, `TODO`, or older notes override current code, explicit user direction, or newer recorded decisions.

For substantial handoffs or follow-up notes, spell out:

- exact goal
- allowed files
- files to avoid
- risk areas
- required verification

## 9. Remote Build And Validation

If the user is remote or using a device over wireless debugging:

1. Verify with `./gradlew assembleDebug --info`
2. Prefer `./gradlew installDebug` when device install is possible
3. Provide the APK fallback at `app/build/outputs/apk/debug/app-debug.apk`
4. Use Android emulator/device verification tooling when UI confidence matters
5. Capture screenshots or other evidence for user-facing UI changes when practical

## 10. Workspace Hygiene

- **Root Preservation**: Do not create new files in the project root unless they are mandatory configuration files (e.g., new Gradle/Git configs).
- **Scripts**: All helper scripts, automation tools, and maintenance utilities MUST live in `scripts/`.
- **Logs**: Any persistent log files or build artifacts generated by agents should be placed in `logs/` or a dedicated temporary directory, never in the root.
- **Documentation**: New documentation belongs in `docs/`. Only the primary entry points (`README.md`, `AGENTS.md`, `GEMINI.md`) should reside in root.
