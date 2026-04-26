# Next Steps

## Reader Perf Follow-Up Only If Fresh Phone Feel Still Seems Bad
- Goal: Only reopen reader performance work if the refreshed 2026-04-27 phone report still matches a user-visible hitch on the physical device.
- Why now: The real-phone refresh is complete and the new baseline is captured in `logs/perf_report_book_open_and_chapter_scroll_2026-04-27_refresh.md`. Book open/close did not show a broad regression, `TTEV6 CH11` scrolling improved clearly, and the one noisy `Shadow Slave` delayed release-like spike did not reproduce as a delayed-only issue in the controlled trace lane.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/test_checklist.md`, `docs/superpowers/plans/2026-04-27-reader-real-phone-perf-refresh.md`, `scripts/run_book_open_close_release_live.ps1`, `scripts/run_reader_lag_release_live.ps1`, `scripts/run_reader_lag_trace_matrix.ps1`, `logs/perf_report_book_open_and_chapter_scroll_2026-04-27_refresh.md`, `logs/book-open-close-release-live-20260427-013237/summary.md`, `logs/reader-lag-release-live-20260427-013816/summary.md`, `logs/reader-lag-trace-matrix-20260427-014033/summary.md`
- Risks: Overreacting to one noisy release-like sample, forgetting that the trace lane needs a debuggable install, or drifting the benchmark books away from `Shadow Slave 1435 / 2927 ch` and `TTEV6 11 / 45 ch`.
- Verification target: Reproduce the felt hitch first on the phone, then rerun only the exact offending release-like lane and compare it against the refreshed 2026-04-27 report before opening a broader perf investigation.

## Theme Spectrum Picker Plan Execution
- Goal: Execute the approved Appearance theme-picker redesign with the newer exact-zone overlay direction, keeping HEX persistence intact while moving the UI to a spectrum-first picker and RGB display tokens.
- Why now: The design is now captured in `docs/superpowers/specs/2026-04-27-theme-spectrum-picker-design.md`, the newer picker-only mockup clarified that the exact-zone circle and outer veil should be the main teaching tool, and the implementation plan is ready at `docs/superpowers/plans/2026-04-27-theme-spectrum-picker.md`.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/settings_persistence.md`, `docs/test_checklist.md`, `docs/superpowers/specs/2026-04-27-theme-spectrum-picker-design.md`, `docs/superpowers/plans/2026-04-27-theme-spectrum-picker.md`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeStudioComponents.kt`, `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorSections.kt`
- Risks: Letting the visual exact-zone circle drift away from the real guided resolver, reintroducing save-on-dismiss behavior, or letting RGB display work accidentally leak into persistence/model contracts.
- Verification target: Execute the scoped JVM and instrumentation slices from the plan, confirm `checkKotlinFileLineLimit` still passes, and do one manual emulator smoke on Basic versus Advanced picker behavior.

## Reader Selection Reopen Only On Fresh Repro
- Goal: Keep the reader selectable-text stabilization lane closed unless a fresh content-specific repro appears or the user explicitly asks for a separate physical-device confirmation pass.
- Why now: The baseline restoration/session gate is green again, the focused 30-test reader selection slice is green, and the real-book emulator walkthrough on `Shadow Slave` confirmed live selection, mirrored handle readability, stable bottom-edge release, and clean deselection without any production reader changes.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/test_checklist.md`, `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenRestorationTest.kt`, `feature/reader/src/androidTest/java/com/epubreader/feature/reader/ReaderScreenOverscrollTest.kt`, `logs/reader_qa_select1.png`, `logs/reader_qa_bottom_release_c.png`, `logs/reader_qa_bottom_release_d.png`, `logs/reader_qa_deselect.png`
- Risks: Reopening the selection lane based on superseded instrumentation assumptions, or retuning stable geometry and handle behavior without a newly reproduced failure.
- Verification target: If reopened, reproduce the failure first on the exact content path, then rerun only the narrow affected test slice plus one representative emulator reader pass.

## Surface Registry Generalization Follow-Up
- Goal: Reduce the remaining shell-specific surface wiring so a new top-level surface can be added with one registry entry instead of touching multiple app-shell seams.
- Why now: The live path now routes through `AppRoute`, `AppSurfaceRegistry`, and root surface plugins, but `AppSurfaceHost`, startup gating, system-bar policy, and unavailable-surface behavior tests still encode built-in surface assumptions.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/app_shell_navigation.md`, `docs/project_graph.md`, `app/src/main/java/com/epubreader/app/AppSurfaceRegistry.kt`, `app/src/main/java/com/epubreader/app/AppSurfaceHost.kt`, `app/src/main/java/com/epubreader/app/AppNavigation.kt`, `app/src/main/java/com/epubreader/app/AppNavigationEffects.kt`, `app/src/main/java/com/epubreader/app/AppNavigationStartupState.kt`
- Risks: Keeping new-surface onboarding split across registry, host, shell-policy, and fallback seams; or over-generalizing the builder before the next real surface proves the boundary.
- Verification target: After any follow-up, rerun builder JVM tests plus the relevant surface-plugin tests, confirm unavailable-surface back behavior is covered, and confirm the canonical docs still point only at the live surface path.

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
- Why now: The refreshed 2026-04-27 release-like open/close audit is complete. `Shadow Slave` improved on open and stayed flat-to-better on close, while `TTEV6` delayed open and delayed close were the only clearly worse lanes, so there is still no strong broad signal that justifies reopening the full matrix.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/test_checklist.md`, `scripts/run_book_open_close_release_live.ps1`, `logs/perf_report_book_open_and_chapter_scroll_2026-04-27_refresh.md`, `logs/book-open-close-release-live-20260427-013237/summary.md`, `feature/reader/src/main/java/com/epubreader/feature/reader/ReaderScreen.kt`
- Risks: Over-tuning an already acceptable release-like flow, spending time on low-visibility transition polish, and misreading small `gfxinfo` deltas as product-significant when the user may not feel them.
- Verification target: Only if a delay is still noticeable, rerun the exact offending case on the phone and capture one targeted trace for that open or close transition.

## Reader Cold-Open Scroll Lag Optional Polish
- Goal: Decide whether the new reader-prefetch gating polish is enough, or whether one more release-live verification pass is worth doing after parallel theme work settles.
- Why now: The refreshed 2026-04-27 phone run improved `Shadow Slave` immediate scroll and improved `TTEV6 CH11` strongly, but one `Shadow Slave` delayed release-like sample spiked. The controlled trace lane did not confirm that spike as a delayed-only regression, so this area should stay optional unless the phone still feels hitchy.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/reader_screen.md`, `docs/ai_mental_model.md`, `docs/epub_parsing.md`, `docs/test_checklist.md`, `scripts/run_reader_lag_release_live.ps1`, `scripts/run_reader_lag_trace_matrix.ps1`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderFeatureShell.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderScreenEffects.kt`, `feature/reader/src/main/java/com/epubreader/feature/reader/internal/shell/ReaderScreenHelpers.kt`, `feature/reader/src/test/java/com/epubreader/feature/reader/ReaderScreenPrefetchTest.kt`, `logs/perf_report_book_open_and_chapter_scroll_2026-04-27_refresh.md`, `logs/reader-lag-release-live-20260427-013816/summary.md`, `logs/reader-lag-trace-matrix-20260427-014033/summary.md`
- Risks: Over-tuning an already acceptable release-like experience, slightly delaying adjacent-cache warmup for the first untouched chapter in a session, and conflating this small reader polish with unrelated theme-cleanup changes if verification is not sequenced carefully.
- Verification target: After theme cleanup settles, optionally rerun only the `Shadow Slave` delayed release-like lane and the matching trace lane to see whether the spike repeats under the exact same book/progress state. If the user still feels no lag, stop here and close the topic.

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
- Starting docs/files: `docs/legacy/PDF_review.md`, `docs/app_shell_navigation.md`, `docs/epub_parsing.md`, `app/AppNavigation.kt`, `app/AppSurfaceRegistry.kt`, `feature/pdf-legacy/src/main/java/com/epubreader/feature/pdf/legacy/PdfLegacyLegoPlugin.kt`, `data/books/PdfLegacyBridge.kt`
- Risks: Re-enabling broken shell paths, progress persistence mismatch, conversion-state staleness, memory pressure.
- Verification target: Explicit design decision plus targeted parser/shell tests before any runtime reactivation.

## Parked PDF Theme Debt
- Goal: If PDF support is ever revived, replace the parked white page surface with an explicit PDF page token before exposing that runtime again.
- Why now: After the semantic coverage pass, the audit's only remaining production bypass is the parked PDF page card.
- Suggested owner/model: Codex / GPT-5.
- Starting docs/files: `AGENTS.md`, `docs/legacy/PDF_review.md`, `logs/ui_theme_color_audit.md`, `feature/pdf-legacy/src/main/java/com/epubreader/feature/pdf/legacy/PdfReaderScreen.kt`
- Risks: Spending time on a parked runtime prematurely, or reviving PDF visuals without the rest of the PDF shell/runtime contract being ready.
- Verification target: Only if PDF is reactivated: targeted PDF UI checks plus explicit audit refresh.
