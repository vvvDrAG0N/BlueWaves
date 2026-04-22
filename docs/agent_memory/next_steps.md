# Next Steps

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


