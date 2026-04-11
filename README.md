# Blue Waves EPUB Reader

Blue Waves is a native Android EPUB reader built with Jetpack Compose. The project is optimized for offline reading, fast metadata loading, and stable scroll-position restoration.

## Current Architecture

The project uses a single-activity app shell with state-based navigation.

- `com.epubreader.MainActivity`
  - App bootstrap, theme selection, and the `Screen` enum.
- `com.epubreader.app.AppNavigation`
  - Top-level navigation and library/folder UI state.
- `com.epubreader.data.settings.SettingsManager`
  - DataStore-backed persisted source of truth.
- `com.epubreader.data.parser.EpubParser`
  - EPUB extraction, metadata caching, and chapter parsing.
- `com.epubreader.feature.reader.ReaderScreen`
  - Reader UI, chapter lifecycle, scroll restoration, and overscroll navigation.
- `com.epubreader.feature.settings.SettingsScreen`
  - Settings UI for reader preferences.
- `com.epubreader.core.model.*`
  - Shared domain models such as `EpubBook`, `GlobalSettings`, and `BookProgress`.
- `com.epubreader.core.ui.*`
  - Shared UI components reused across the app.

## Project Layout

```text
app/src/main/java/com/epubreader
  MainActivity.kt
  app/AppNavigation.kt
  core/model/
  core/ui/
  data/parser/
  data/settings/
  feature/reader/
  feature/settings/
```

## Build

```powershell
.\gradlew.bat assembleDebug
```

## Documentation

- [Architecture](docs/architecture.md)
- [System Overview](docs/system_overview.md)
- [Package Map](docs/package_map.md)
- [AI Entry Points](docs/AI_ENTRY_POINTS.md)
- [Quick Reference](docs/quick_ref.md)
- [Reader Flow](docs/reader_flow.md)
- [AI Debug Guide](docs/AI_DEBUG_GUIDE.md)
- [Prompt Templates](docs/PROMPT_TEMPLATES.md)
- [Known Risks](docs/known_risks.md)

## Notes For Future Refactors

- `ReaderScreen` remains the highest-risk file. Preserve the restoration flow unless it is fully revalidated.
- `SettingsManager` remains the persisted source of truth for app and per-book state.
- `EpubParser` still owns EPUB extraction and chapter parsing, but shared models no longer live inside it.
