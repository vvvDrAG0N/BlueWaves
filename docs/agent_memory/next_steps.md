# Next Steps

## Reader Content Engine Phase 2
- Goal: Replace the `Compose Lazy Improved` placeholder with the first real non-legacy chapter body engine while keeping the new phase-1 host boundary and the broader reader state machine intact.
- Why now: The phase-1 scaffold is now in place. The engine type is persisted, the host/dispatcher routes through the chapter body boundary, the Library settings row is reserved, and both future engines already have compile-safe placeholder files.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/ai_mental_model.md`, `docs/test_checklist.md`, `core/model/src/main/java/com/epubreader/core/model/ReaderContentEngine.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContent.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContentLegacy.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderChapterContentComposeLazyImproved.kt`, `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderChapterContentRoutingTest.kt`
- Risks: Regressing reader smoothness, changing restoration timing accidentally, reintroducing the paragraph-level selectable-text problems under a new name, or breaking theme parity and image handling while the new engine is still partial.
- Verification target: Focused reader unit/instrumentation checks plus a small manual book matrix across both webnovel-style and light-novel-style chapters before any user-facing engine switch is exposed.

## Book Open/Close Optional Follow-Up
- Goal: Only revisit book entry/exit performance if someone can still feel a delay on the release-like build, then trace the exact rough case instead of reopening a broad matrix.
- Why now: The real-phone release-like open/close audit is complete. `Shadow Slave` did not improve with a 15-second wait, `ttev6` improved only modestly, and the close path was mostly stable across runs, so there is no strong startup-overlap signal left to chase broadly.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/test_checklist.md`, `scripts/run_book_open_close_release_live.ps1`, `logs/book-open-close-release-live-20260425-023012/summary.md`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreen.kt`
- Risks: Over-tuning an already acceptable release-like flow, spending time on low-visibility transition polish, and misreading small `gfxinfo` deltas as product-significant when the user may not feel them.
- Verification target: Only if a delay is still noticeable, rerun the exact offending case on the phone and capture one targeted trace for that open or close transition.

## Reader Cold-Open Scroll Lag Optional Polish
- Goal: Decide whether the new reader-prefetch gating polish is enough, or whether one more release-live verification pass is worth doing after parallel theme work settles.
- Why now: A narrow reader-only polish is now in place: adjacent chapter prefetch waits until the reader session has actually been touched, which should reduce cold-open overlap without changing the broader investigation result that release-like builds already felt smooth.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/ai_mental_model.md`, `docs/epub_parsing.md`, `docs/test_checklist.md`, `scripts/run_reader_lag_release_live.ps1`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreen.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenEffects.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreenHelpers.kt`, `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenPrefetchTest.kt`, `logs/reader-lag-release-live-20260424-090627/summary.md`
- Risks: Over-tuning an already acceptable release-like experience, slightly delaying adjacent-cache warmup for the first untouched chapter in a session, and conflating this small reader polish with unrelated theme-cleanup changes if verification is not sequenced carefully.
- Verification target: After theme cleanup settles, optionally rerun the small release-live matrix to see whether the immediate-vs-delayed gap narrows further. If the user still feels no lag, stop here and close the topic.

## Appearance Performance Optional Polish
- Goal: Decide whether the Appearance tab’s first-use jank is worth a proactive polish pass after the new device audit, or whether the current release-like behavior is acceptable as-is.
- Why now: The real-device Appearance audit is complete. Waiting on the library only modestly helped `appearance-open`, did not help pager swipes, and Perfetto traces point to first-entry Compose/layout/render work plus first-use JIT in `SettingsAppearanceTab`, not to the old reader-style startup overlap theory.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/test_checklist.md`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsAppearanceTab.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsAppearanceVisuals.kt`, `logs/theme-perf-release-live-20260425-013425/summary.md`, `logs/theme-perf-trace-followup-20260425-015711/summary.md`
- Risks: Chasing low-visibility first-use polish, over-optimizing around JIT-heavy warm-state measurements, or destabilizing the already-green Appearance/theme flows for gains the user may not feel.
- Verification target: Re-run the small release-live Appearance subset and compare `appearance-open` plus `appearance-pager-swipe-return` against the current baseline if a polish pass is attempted.

## Selectable Text Follow-Up
- Goal: Stabilize selectable text and the existing in-app WebView define/translate actions without breaking reader controls, layout, or multi-paragraph selection.
- Why now: It is the highest-risk remaining reader-facing follow-up in the current root `TODO`.
- Suggested owner/model: Codex / GPT-5 for implementation, with planning support if needed.
- Starting docs/files: `docs/reader_screen.md`, `docs/ai_mental_model.md`, `docs/ui_rules.md`, `docs/test_checklist.md`, `feature/reader/ReaderScreen.kt`
- Risks: Restoration regressions, one-tap accidental selection, controls interference, font/layout shifts, partial selection failure, and regressions in the in-app WebView lookup flow.
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
