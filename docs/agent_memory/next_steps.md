# Next Steps

## Reader Selection Reopen Only On Fresh Repro
- Goal: Keep the reader selectable-text stabilization lane closed unless a fresh content-specific repro appears or the user explicitly asks for a separate physical-device confirmation pass.
- Why now: The baseline restoration/session gate is green again, the focused 30-test reader selection slice is green, and the real-book emulator walkthrough on `Shadow Slave` confirmed live selection, mirrored handle readability, stable bottom-edge release, and clean deselection without any production reader changes.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/test_checklist.md`, `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt`, `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenOverscrollTest.kt`, `logs/reader_qa_select1.png`, `logs/reader_qa_bottom_release_c.png`, `logs/reader_qa_bottom_release_d.png`, `logs/reader_qa_deselect.png`
- Risks: Reopening the selection lane based on superseded instrumentation assumptions, or retuning stable geometry and handle behavior without a newly reproduced failure.
- Verification target: If reopened, reproduce the failure first on the exact content path, then rerun only the narrow affected test slice plus one representative emulator reader pass.

## Builder Plugin Cleanup Follow-Up
- Goal: Finish retiring the remaining app-side pure helper duplicates now that the live builder routes exclusively through feature lego/plugins.
- Why now: The production path now goes through `AppRoute`, `AppFeatureRegistry`, and root feature plugins, but a few older app-local helpers and tests still mirror library-era behavior and can drift if they are left around indefinitely.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/app_shell_navigation.md`, `docs/project_graph.md`, `app/src/main/java/com/epubreader/app/AppNavigationDerivedState.kt`, `app/src/main/java/com/epubreader/app/AppNavigationOperations.kt`, `app/src/main/java/com/epubreader/app/AppNavigationProgress.kt`, `feature/library/src/main/java/com/epubreader/feature/library/internal/`, `app/src/test/java/com/epubreader/app/`
- Risks: Letting dead or duplicated helper logic silently diverge from the feature-owned path, and keeping future refactors split across both the builder and library feature for no product reason.
- Verification target: After any cleanup, rerun builder JVM tests plus `:feature:library:testDebugUnitTest`, and confirm the canonical docs still point only at the live builder/plugin path.

## Reader Single-Runtime Real-Book Validation
- Goal: Validate the shipped single EPUB runtime on representative long-form books and decide whether any remaining work is performance polish instead of more architecture churn.
- Why now: The reader plugin's custom-selection suite is green and now explicitly covers immediate tear appearance, tear disappearance once the selected word scrolls offscreen, and selection reset across chapter changes. Live emulator QA also confirms one-tap chrome show/hide, long-press selection, and the custom action bar on a real EPUB. The remaining signal is true human-finger feel: direct selection expansion, handle smoothness while dragging, restore stability, and whether the release-live lag harness shows meaningful regressions on representative samples.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/ai_mental_model.md`, `docs/test_checklist.md`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/EpubReaderRuntime.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/runtime/epub/ReaderChapterSelectionHost.kt`, `scripts/run_reader_lag_release_live.ps1`, `temps/`
- Risks: Assuming the new single runtime is smooth enough without real-book confirmation, missing selection pain near rare safety splits, and chasing structural rewrites when the remaining work is really just profiling or gesture polish.
- Verification target: Manual matrix on representative `temps/` books covering reopen/restore, TOC jump, next/prev, overscroll, chapter-surface tap-to-toggle behavior when no selection is active, active-selection tap-to-dismiss behavior, direct finger expansion from a long-press, handle drag smoothness while the text stays anchored, multi-paragraph selection, image/blank-area deselection, theme/font changes, define/translate, and one release-live lag harness run if the user still feels hitching.
- Extra note: the latest edge-case fixes clamp a dragged tear fully inside the reader host, force drag cleanup when a border drag loses the pointer, and reset selection cleanly on chapter boundaries. If a real phone can still reproduce "release near the bottom border keeps scrolling/expanding," the next escalation should be a host-level active-drag capture lego for the whole reader surface.

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
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/ai_mental_model.md`, `docs/epub_parsing.md`, `docs/test_checklist.md`, `scripts/run_reader_lag_release_live.ps1`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderScreenEffects.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderScreenHelpers.kt`, `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenPrefetchTest.kt`, `logs/reader-lag-release-live-20260424-090627/summary.md`
- Risks: Over-tuning an already acceptable release-like experience, slightly delaying adjacent-cache warmup for the first untouched chapter in a session, and conflating this small reader polish with unrelated theme-cleanup changes if verification is not sequenced carefully.
- Verification target: After theme cleanup settles, optionally rerun the small release-live matrix to see whether the immediate-vs-delayed gap narrows further. If the user still feels no lag, stop here and close the topic.

## Appearance Performance Optional Polish
- Goal: Decide whether the Appearance tab's first-use jank is worth a proactive polish pass after the new device audit, or whether the current release-like behavior is acceptable as-is.
- Why now: The real-device Appearance audit is complete. Waiting on the library only modestly helped `appearance-open`, did not help pager swipes, and Perfetto traces point to first-entry Compose/layout/render work plus first-use JIT in `SettingsAppearanceTab`, not to the old reader-style startup overlap theory.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/test_checklist.md`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsAppearanceTab.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsAppearanceVisuals.kt`, `logs/theme-perf-release-live-20260425-013425/summary.md`, `logs/theme-perf-trace-followup-20260425-015711/summary.md`
- Risks: Chasing low-visibility first-use polish, over-optimizing around JIT-heavy warm-state measurements, or destabilizing the already-green Appearance/theme flows for gains the user may not feel.
- Verification target: Re-run the small release-live Appearance subset and compare `appearance-open` plus `appearance-pager-swipe-return` against the current baseline if a polish pass is attempted.

## PDF Future Decision
- Goal: Decide whether the parked PDF path should be safely revived as a separate surface or removed from the product direction entirely.
- Why now: Parked runtime code still exists, but the active shell intentionally blocks it and the repo should not drift into accidental half-support.
- Suggested owner/model: Gemini for scoping and design comparison, Codex for any later implementation.
- Starting docs/files: `docs/legacy/PDF_review.md`, `docs/app_shell_navigation.md`, `docs/epub_parsing.md`, `app/AppNavigation.kt`, `app/AppFeatureRegistry.kt`, `feature/pdf-legacy/src/main/java/com/epubreader/feature/pdf/legacy/PdfLegacyLegoPlugin.kt`, `data/parser/PdfLegacyBridge.kt`
- Risks: Re-enabling broken shell paths, progress persistence mismatch, conversion-state staleness, memory pressure.
- Verification target: Explicit design decision plus targeted parser/shell tests before any runtime reactivation.

## Parked PDF Theme Debt
- Goal: If PDF support is ever revived, replace the parked white page surface with an explicit PDF page token before exposing that runtime again.
- Why now: After the semantic coverage pass, the audit's only remaining production bypass is the parked PDF page card.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/legacy/PDF_review.md`, `logs/ui_theme_color_audit.md`, `feature/pdf-legacy/src/main/java/com/epubreader/feature/pdf/legacy/PdfReaderScreen.kt`
- Risks: Spending time on a parked runtime prematurely, or reviving PDF visuals without the rest of the PDF shell/runtime contract being ready.
- Verification target: Only if PDF is reactivated: targeted PDF UI checks plus explicit audit refresh.
