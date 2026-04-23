# Next Steps

## Settings Appearance Test Refresh
- Goal: Bring the older `SettingsScreenPersistenceTest` cases back in sync with the current Appearance UI so the full settings instrumentation suite runs green again.
- Why now: The new swipe-exit persistence regression passes, but the full class still has older failures tied to stale selectors and assumptions like a text `Done` button and legacy theme-editor tags.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/test_checklist.md`, `feature/settings/src/androidTest/java/com/epubreader/feature/settings/SettingsScreenPersistenceTest.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsAppearanceTab.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeGallery.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
- Risks: Cementing stale test behavior, missing a real regression under the current gallery/editor flows, or weakening coverage while adapting tests to new UI hooks.
- Verification target: `SettingsScreenPersistenceTest` full class green on the emulator, plus a quick spot-check of the custom-theme create/edit path.

## Theme Gallery Post-Fix QA
- Goal: Re-run the Theme Gallery flow on the target device only if the user still sees a visible freeze, dead-touch layer, renderer issue, or stale selected-theme highlight after a fast close/switch/reopen cycle.
- Why now: Emulator verification now passes the close-and-interact and hidden-sync regressions, but the earlier non-idle gallery behavior and the swipe-timing sync path may still be worth checking on the target device if symptoms remain.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/test_checklist.md`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsAppearanceTab.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeGallery.kt`
- Risks: Device-specific GPU behavior, hidden-state layer churn, regressing the keep-alive reopen UX by over-correcting with teardown, and pager-timing races that are harder to hit in instrumentation.
- Verification target: Fresh device logcat for the exact close path plus a quick manual reopen check for preserved scroll/session state, restored post-dismiss touch input, and correct selected-theme sync after a quick theme switch.

## Selectable Text Follow-Up
- Goal: Stabilize selectable text so define/translate actions can be added without breaking reader controls, layout, or multi-paragraph selection.
- Why now: It is the highest-risk remaining reader-facing follow-up in the current root `TODO`.
- Suggested owner/model: Codex / GPT-5 for implementation, with planning support if needed.
- Starting docs/files: `docs/reader_screen.md`, `docs/ai_mental_model.md`, `docs/ui_rules.md`, `docs/test_checklist.md`, `feature/reader/ReaderScreen.kt`
- Risks: Restoration regressions, one-tap accidental selection, controls interference, font/layout shifts, partial selection failure.
- Verification target: Reader-focused targeted tests plus filtered reader instrumentation and manual selection checks.

## PDF Future Decision
- Goal: Decide whether the parked PDF path should be safely revived as a separate surface or removed from the product direction entirely.
- Why now: Parked runtime code still exists, but the active shell intentionally blocks it and the repo should not drift into accidental half-support.
- Suggested owner/model: Gemini for scoping and design comparison, Codex for any later implementation.
- Starting docs/files: `docs/legacy/PDF_review.md`, `docs/app_shell_navigation.md`, `docs/epub_parsing.md`, `app/AppNavigation.kt`, `data/parser/PdfLegacyBridge.kt`
- Risks: Re-enabling broken shell paths, progress persistence mismatch, conversion-state staleness, memory pressure.
- Verification target: Explicit design decision plus targeted parser/shell tests before any runtime reactivation.
