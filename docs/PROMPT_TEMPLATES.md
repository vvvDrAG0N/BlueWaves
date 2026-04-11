# Prompt Templates

This file provides copy-paste starter prompts for future agents working on Blue Waves.

Use this together with:
- `AGENTS.md`
- `docs/README.md`
- `docs/package_map.md`

Do not confuse:
- `feature`: implement new functionality
- `review`: inspect existing code or a diff for bugs, regressions, and risks

## Default Rule

Start most prompts with:

```text
Read `AGENTS.md`, `docs/README.md`, and `docs/package_map.md` first.
Then read only the docs directly relevant to this task.
```

## Bug Fix

```text
Read `AGENTS.md`, `docs/README.md`, `docs/package_map.md`, `docs/AI_ENTRY_POINTS.md`, and `docs/AI_DEBUG_GUIDE.md` first.
Read any additional docs that are directly relevant to the touched area.

Task: bug fix
Goal: <exact bug to fix>
Repro:
1. <step>
2. <step>
3. <current wrong behavior>

Expected behavior:
- <expected result>

Scope:
- Allowed: <files/packages>
- Avoid: <files/packages>

Constraints:
- Keep behavior unchanged outside this bug
- Keep `SettingsManager` as the persisted source of truth where applicable
- Ask before any DataStore/schema or major architectural change
- Prefer minimal edits

Verification:
- Run `.\gradlew.bat assembleDebug`
- Explain root cause
- Explain why the fix does not introduce stale state or regressions
```

## Feature

```text
Read `AGENTS.md`, `docs/README.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.
Read any additional docs relevant to the feature area before editing.

Task: feature
Goal: implement <feature name>

User flow:
1. <user action>
2. <system response>
3. <result>

Acceptance criteria:
- <criterion 1>
- <criterion 2>
- <criterion 3>

Scope:
- Preferred files/packages: <files/packages>

Constraints:
- Keep existing behavior unchanged unless required for the feature
- Keep state ownership aligned with current architecture
- Ask before DataStore/schema changes or major refactors
- Preserve reader restoration/parsing behavior unless this feature explicitly targets them

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize behavior changes
- Mention tradeoffs and remaining risks
```

## Code Review

```text
Read `AGENTS.md`, `docs/README.md`, `docs/package_map.md`, and the docs relevant to the changed area first.

Task: review
Scope: review <files / package / branch diff>

Review focus:
- Bugs
- Behavioral regressions
- State management risks
- Missing tests
- Performance or memory risks where relevant

Constraints:
- Do not make code changes unless I ask
- Findings first, ordered by severity
- Include file and line references
- Mention residual risks or testing gaps if no findings

Output format:
- Findings
- Open questions / assumptions
- Short summary
```

## Safe Refactor

```text
Read `AGENTS.md`, `docs/README.md`, `docs/package_map.md`, `docs/architecture.md`, and `docs/system_overview.md` first.

Task: safe refactor
Goal: refactor <area> for modularity/readability without behavior changes

Scope:
- Allowed: <files/packages>
- Avoid: <files/packages>

Constraints:
- No behavior changes
- No DataStore/schema changes
- No navigation model changes
- No reader restoration logic changes unless explicitly approved
- Keep edits incremental and package responsibilities clear

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize old vs new file boundaries
- Call out any residual coupling left for a later phase
```

## Reader Add-On

Use this when the task touches the reader:

```text
Also read `docs/reader_flow.md`, `docs/ai_mental_model.md`, and `docs/known_risks.md` before editing.
Do not change `isInitialScrollDone`, `isRestoringPosition`, overscroll behavior, or restoration sequencing without explicit validation.
```

## Parser Add-On

Use this when the task touches EPUB parsing:

```text
Also read `docs/quick_ref.md`, `docs/AI_DEBUG_GUIDE.md`, and `docs/ai_mental_model.md` before editing.
Preserve ZIP stream safety, `normalizePath()`, and book ID generation.
```

## Minimal Version

Use this for straightforward tasks:

```text
Read `AGENTS.md`, `docs/README.md`, and `docs/package_map.md` first, then load only relevant docs.

Task: <bug fix / feature / review / safe refactor>
Goal: <exact outcome>
Scope: <allowed files/packages>
Constraints:
- <must not change>
- Ask before schema or major architecture changes

Verification:
- Run `.\gradlew.bat assembleDebug`
- Summarize root cause / changes / risks
```

## Notes

- Be explicit about the success condition.
- Be explicit about allowed scope.
- If the task touches reader restoration, parser safety, or DataStore shape, say so directly.
- `ask_mode_prompt_rules.md` is the rulebook for generated implementation prompts. This file is the human-facing quick-start version.
