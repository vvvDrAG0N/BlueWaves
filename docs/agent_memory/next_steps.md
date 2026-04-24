# Next Steps

## Reader Cold-Open Scroll Lag Optional Polish
- Goal: Decide whether the new reader-prefetch gating polish is enough, or whether one more release-live verification pass is worth doing after parallel theme work settles.
- Why now: A narrow reader-only polish is now in place: adjacent chapter prefetch waits until the reader session has actually been touched, which should reduce cold-open overlap without changing the broader investigation result that release-like builds already felt smooth.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/ai_mental_model.md`, `docs/epub_parsing.md`, `docs/test_checklist.md`, `scripts/run_reader_lag_release_live.ps1`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreen.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenHelpers.kt`, `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenPrefetchTest.kt`, `logs/reader-lag-release-live-20260424-090627/summary.md`
- Risks: Over-tuning an already acceptable release-like experience, slightly delaying adjacent-cache warmup for the first untouched chapter in a session, and conflating this small reader polish with unrelated theme-cleanup changes if verification is not sequenced carefully.
- Verification target: After theme cleanup settles, optionally rerun the small release-live matrix to see whether the immediate-vs-delayed gap narrows further. If the user still feels no lag, stop here and close the topic.

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

## Parked PDF Theme Debt
- Goal: If PDF support is ever revived, replace the parked white page surface with an explicit PDF page token before exposing that runtime again.
- Why now: After the semantic coverage pass, the audit’s only remaining production bypass is the parked PDF page card.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/legacy/PDF_review.md`, `logs/ui_theme_color_audit.md`, `feature/pdf-legacy/src/main/java/com/epubreader/feature/pdf/legacy/PdfReaderScreen.kt`
- Risks: Spending time on a parked runtime prematurely, or reviving PDF visuals without the rest of the PDF shell/runtime contract being ready.
- Verification target: Only if PDF is reactivated: targeted PDF UI checks plus explicit audit refresh.
