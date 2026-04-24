# Blue Waves EPUB Reader

Blue Waves is a native Android EPUB reader built with Jetpack Compose. The project is optimized for offline reading, fast metadata loading, stable scroll-position restoration, and a modular codebase that avoids file and feature monoliths.

## Current Architecture

The project uses a modular single-activity shell with state-based navigation.

- `MainActivity`
  - App bootstrap, theme selection, and the `Screen` enum.
- `AppNavigation`
  - Top-level navigation and library/folder shell state.
- `SettingsManager`
  - DataStore-backed persisted source of truth.
- `EpubParser`
  - EPUB extraction, metadata caching, chapter parsing, and EPUB mutation support.
- `ReaderScreen`
  - Reader lifecycle, restoration, and chapter navigation behavior.
- `EditBookScreen`
  - EPUB metadata, cover, and chapter mutation UI.

## Module Layout

```text
:app
:core:model
:core:ui
:data:settings
:data:books
:feature:library
:feature:reader
:feature:settings
:feature:editbook
:feature:pdf-legacy
```

## Build

```powershell
.\gradlew.bat assembleDebug
```

## Documentation

- [Docs Index](docs/README.md)
- [Project Graph](docs/project_graph.md)
- [Quick Reference](docs/quick_ref.md)
- [Architecture](docs/architecture.md)
- [App Shell Navigation](docs/app_shell_navigation.md)
- [Settings Persistence](docs/settings_persistence.md)
- [EPUB Parsing](docs/epub_parsing.md)
- [Reader Screen](docs/reader_screen.md)
- [UI Rules](docs/ui_rules.md)
- [AI Debug Guide](docs/AI_DEBUG_GUIDE.md)
- [AI Mental Model](docs/ai_mental_model.md)
- [Test Checklist](docs/test_checklist.md)
- [Legacy PDF Note](docs/legacy/PDF_review.md)

## Notes For Future Refactors

- `ReaderScreen` remains the highest-risk surface. Preserve restoration flow unless it is fully revalidated.
- `SettingsManager` remains the persisted source of truth for app and per-book state.
- `EpubParser` still owns EPUB extraction and chapter parsing, but helper responsibilities are split across focused files.
- Shared runtime guidance belongs in the canonical root docs, not scattered prompt files.

## Planned Roadmap

- Selectable Text follow-up
  - Stabilize selection behavior and keep the in-app WebView define/translate flow without breaking reader controls.
- Infinite Scroll Reader Mode
  - Keep behind the performance/stability and reader-safety boundary.
- PDF / format support later
  - Revisit only through an explicit design pass and the legacy PDF note.

## AI Agent Workflow

- `AGENTS.md`
  - Primary repo rules for all agents.
- `GEMINI.md`
  - Companion guide for planning, scoping, and review-oriented Gemini passes.
- `CODEX.md`
  - Companion guide for implementation, debugging, and verification-oriented Codex passes.
- `docs/agent_memory/README.md`
  - Schema for structured step history and next-step continuity.
- `docs/agent_memory/step_history.md`
  - Append-only record of substantial agent work.
- `docs/agent_memory/next_steps.md`
  - Concrete queued follow-up work.
