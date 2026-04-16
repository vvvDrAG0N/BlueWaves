# TODO Prompts

This file is the prompt library for top-level TODO work in Blue Waves.

Rules:
- The root `TODO` file defines execution order.
- This file is a reusable prompt library, not the source of truth for priority by itself.
- Start with the smallest prompt that gives the next agent enough context to execute safely.

## 1. AI Agent Improvement Plan

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/PROMPT_TEMPLATES.md`, and `docs/ask_mode_prompt_rules.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before loading raw files.
Then read only the docs directly relevant to the repo's AI workflow.

Task: docs/workflow improvement
Goal: strengthen the repo's AI-agent workflow for Codex and Gemini without changing app behavior

Acceptance criteria:
- Improve the shared agent guidance in `AGENTS.md` by appending model-routing, token-efficiency, task-handling, and debug-handling rules
- Ensure both `Gemini` and `Codex` have repo-specific companion guides
- Repair stale AI-doc references by creating any missing prompt-support docs that are already referenced by the repo
- Keep the docs discoverable by updating the relevant doc index files when practical

Scope:
- Allowed: `AGENTS.md`, `GEMINI.md`, `CODEX.md`, `docs/README.md`, `docs/PROMPT_TEMPLATES.md`, `docs/ask_mode_prompt_rules.md`, `docs/TODO_PROMPTS.md`, `README.md`
- Avoid: app source files unless the task explicitly expands into tooling work

Constraints:
- Append to stable architectural memory instead of rewriting it
- Do not change app architecture, DataStore shape, or reader/parser behavior
- Keep the pass docs-first and minimal

Verification:
- Summarize stale references found and repaired
- Run `python scripts/check_graph_staleness.py --rebuild`
- Summarize any residual gaps left for a later tooling pass
```

## 2. Performance And Stability Pass

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/AI_ENTRY_POINTS.md`, `docs/AI_DEBUG_GUIDE.md`, and `docs/test_checklist.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before loading raw files.
If the slow path is unclear, run `graphify query "Which files likely own startup, import, edit-save, and settings performance hot paths?" --budget 1200`.
Then read only the area docs relevant to the hot path you confirm.

Task: performance investigation and targeted fixes
Goal: reduce startup/settings lag, investigate APK size growth, and improve large-book import/edit-save responsiveness

Acceptance criteria:
- Identify the concrete causes of each confirmed regression before patching
- Add the smallest safe fixes that materially improve responsiveness
- Add visible import progress feedback if the import path currently appears stalled
- Document any remaining large follow-up work instead of hiding it inside broad refactors

Constraints:
- Preserve reader restoration and parser safety rules
- Ask before architecture-wide rewrites or persistence-shape changes
- Prefer measurement-driven fixes over speculative rewrites

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize measurements, root causes, fixes, and remaining risks
```

## 3. Reader And Theme UX Polish

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/reader_screen.md`, `docs/settings_persistence.md`, and `docs/ui_rules.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
Then read only the files needed for theme state, settings UI, reader controls, and dialog styling.

Task: feature/polish pass
Goal: improve theme-selection feedback, system foreground support, dialog button colors, and cover-chapter-info contrast

Acceptance criteria:
- Add system foreground color support through the advanced theme system
- Make the currently applied theme visually obvious in both Settings and the reader bottom bar
- Make theme entry points reveal the current theme instead of forcing extra scrolling/searching
- Standardize confirm/cancel dialog colors using primary/system-foreground rules
- Fix cover chapter-info contrast when light reader foreground colors are active

Constraints:
- Keep persisted state ownership in `SettingsManager`
- Preserve reader restoration behavior
- Prefer the smallest appropriate automated test or explain why the pass remains manual

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize behavior changes and manual verification steps
```

## 4. Selectable Text Follow-Up

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/reader_screen.md`, and `docs/ui_rules.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
Then read only the reader and settings docs/files relevant to selectable text and external-app intents.

Task: feature
Goal: extend selectable text so users can launch define/translate overlays through installed system apps

Acceptance criteria:
- Reader text selection can route into supported external define/translate apps
- The integration fails gracefully when no compatible app is installed
- Existing reader selection behavior remains stable outside the new actions

Constraints:
- Preserve reader scroll and restoration behavior
- Avoid broad reader refactors
- Add the smallest practical automated coverage, or explain why the behavior is best verified manually

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize supported flows, fallbacks, and risks
```

## 5. Theme And Settings Expansion

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/settings_persistence.md`, and `docs/ui_rules.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
If the entry point is unclear, run `graphify query "Which files own theme import and Global Settings screen structure?" --budget 1000`.

Task: feature
Goal: add bulk theme import and redesign Global Settings into a list-based flow

Acceptance criteria:
- Users can import multiple themes via `.zip` or multi-select when supported by the picker flow
- Global Settings becomes a list-based shell with focused subviews for General, Fonts, Themes, and Network (future placeholder)
- Existing theme and settings state remains compatible

Constraints:
- Ask before changing persistence shape
- Keep file ownership aligned with current architecture
- Prefer incremental UI restructuring over a broad settings rewrite

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize UX changes, persistence impact, and remaining follow-ups
```

## 6. Infinite Scroll Reader Mode

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/reader_flow.md`, `docs/ai_mental_model.md`, `docs/known_risks.md`, `docs/reader_screen.md`, and `docs/test_checklist.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
Then read only the reader files needed for chapter loading, restoration, progress, and controls.

Task: feature
Goal: implement Infinite Scroll Reader Mode using the root `TODO` design and safety rules

Acceptance criteria:
- Use a bounded chapter window instead of loading the whole book
- Preserve progress tracking and restore behavior across mode switches
- Respect theme, font, spacing, and performance constraints
- Guard against fast scroll, duplicate loads, boundary flicker, and inconsistent window state

Constraints:
- Treat reader restoration as high risk
- Do not change sacred flags or sequencing without validation against the reader checklist
- Add the smallest meaningful automated coverage where practical, then give manual verification steps for runtime flows

Verification:
- Run `.\gradlew.bat assembleDebug`
- Run the most targeted reader tests available for touched behavior
- Summarize remaining reader risks explicitly
```

## 7. PDF / Format Support Later

```text
Read `AGENTS.md`, `docs/README.md`, `docs/project_graph.md`, `docs/package_map.md`, `docs/PDF_review.md`, `docs/AI_DEBUG_GUIDE.md`, and `docs/test_checklist.md` first.
If `graphify-out/GRAPH_REPORT.md` exists, read it before raw files.
If scope is unclear, run `graphify query "Which files now own PDF import gating, legacy runtime seams, and format-routing tests?" --budget 1200`.

Task: feature / safe refactor
Goal: reintroduce coherent PDF or broader format support without breaking the active EPUB shell

Acceptance criteria:
- Format detection and routing are explicitly tested
- Import/open flows are coherent end to end
- Legacy seams are either preserved safely or replaced deliberately
- Relevant docs and graphify artifacts are updated in the same pass

Constraints:
- Do not partially re-enable PDF flows
- Keep the app shell coherent at every intermediate step
- Prefer narrow, test-backed milestones over a giant format rewrite

Verification:
- Run `.\gradlew.bat assembleDebug`
- Run the targeted test suite for format detection/routing
- Run `python scripts/check_graph_staleness.py --rebuild`
- Summarize any staged follow-up work
```
