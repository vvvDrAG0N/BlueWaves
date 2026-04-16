# Decision Log

Append stable decisions here when they matter across sessions or across agents.

Template:
- Date:
- Decision:
- Why:
- Impact:
- Related files:

## Entries

- Date: 2026-04-16
- Decision: `Codex` is the primary execution and verification agent for this repo; `Gemini` is a secondary planning/review agent.
- Why: This repo benefits from using the higher-capacity model for difficult implementation, debugging, and validation, while still using Gemini for lighter planning and second-pass review.
- Impact: Agent prompts, handoffs, and task routing should default execution ownership to Codex unless the user explicitly asks for a different split.
- Related files: `AGENTS.md`, `CODEX.md`, `GEMINI.md`

- Date: 2026-04-16
- Decision: Durable shared memory lives in repo markdown under `docs/agent_memory/`, while Microsoft Agent Framework is treated as runtime orchestration state rather than the long-term memory source of truth.
- Why: Repo markdown is diffable, reviewable, Obsidian-friendly, and readable by future agents without depending on one workflow runtime or one configured backend.
- Impact: New durable workflow knowledge should be written into this folder or promoted into canonical docs instead of being left only in chat history or MAF runtime state.
- Related files: `docs/agent_memory/README.md`, `AGENTS.md`

- Date: 2026-04-16
- Decision: Obsidian is the preferred human browsing layer for the shared memory and graphify wiki, but `.obsidian/` config is not a source of truth.
- Why: The repo is already an Obsidian-friendly vault, which is useful for navigation, but critical project memory must stay in normal markdown files that agents can read and version.
- Impact: Shared knowledge goes in tracked markdown files inside the repo, not private local notes or plugin state.
- Related files: `.obsidian/`, `docs/agent_memory/README.md`, `graphify-out/wiki/`
