# Blue Waves EPUB Reader

A high-performance, native Android reading ecosystem.

## 1. The Engines
*   **V1 (Legacy stable)**: Standard EPUB reader (Compose + Epublib).
*   **V2 (Titan Sovereign)**: High-performance volumetric media (Webnovels, Manga, Anime).

## 2. Context Entry Points
Always start with these three documents:
1.  **[AGENTS.md](file:///c:/Users/Amon/Desktop/projects/Epub_Reader_v2/AGENTS.md)**: Mandatory behavior and safety rules.
2.  **[SYSTEM_ARCHITECTURE.md](file:///c:/Users/Amon/Desktop/projects/Epub_Reader_v2/docs/SYSTEM_ARCHITECTURE.md)**: Dual-engine layout and structural rules.
3.  **[TODO](file:///c:/Users/Amon/Desktop/projects/Epub_Reader_v2/TODO)**: The master roadmap.

## 3. High-Signal Documentation
*   **[AI_RISKS.md](file:///c:/Users/Amon/Desktop/projects/Epub_Reader_v2/docs/AI_RISKS.md)**: Load-bearing logic and regression hazards.
*   **[docs/v2_engine/](file:///c:/Users/Amon/Desktop/projects/Epub_Reader_v2/docs/v2_engine/)**: Deep dive into the Titan V2 ecosystem.
*   **[docs/agent_memory/](file:///c:/Users/Amon/Desktop/projects/Epub_Reader_v2/docs/agent_memory/)**: Sequential development ledger and decision logs.

## 4. UI Design Philosophy
The UI follows the **Liquid Design** language:
*   **Oceanic Shell**: A deep, volumetric 3D navigation environment.
*   **Titan Ocean**: Real-time AGSL shaders and 120fps physics.
*   **Lego Pieces**: Modular, Atomic UI components registered in `ENGINE_ARCHITECTURE.md`.
 UI for reader preferences.
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

## Planned Roadmap

- Theme System
  - User-defined custom themes and color palettes.
  - Expected delivery requirement: add unit coverage for theme model, persistence, and application behavior.
- Format Support
  - PDF support.
  - ZIP support.
  - Expected delivery requirement: add unit coverage for format detection and routing.
- Edit Book
  - Custom book cover.
  - Editable author and title metadata.
  - Chapter deletion and chapter addition.
  - Expected delivery requirement: add unit coverage for metadata and chapter mutation flows.
- Selectable Text
  - Reader text selection for chapter text only.
  - Toggle form matching the `Show System Bar` setting.
  - UI placement in the global `Appearance` settings area.
  - Expected delivery requirement: add unit coverage for persistence, state, and toggle behavior.

All roadmap items should also update the relevant markdown docs and rebuild `graphify-out/` when implemented.

## AI Agent Workflow

- `AGENTS.md`
  - Primary repo rules for all agents.
- `GEMINI.md`
  - Companion guide for planning, scoping, and review-oriented Gemini passes.
- `CODEX.md`
  - Companion guide for implementation, debugging, and verification-oriented Codex passes.
- `docs/ask_mode_prompt_rules.md`
  - Rulebook for generated implementation prompts and handoffs.
- `docs/TODO_PROMPTS.md`
  - Prompt library for the current top-level TODO tracks.
- `docs/agent_memory/README.md`
  - Shared durable memory for agent collaboration, handoffs, decisions, and debug lessons.
