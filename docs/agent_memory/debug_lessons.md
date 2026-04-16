# Debug Lessons

Record reusable debugging lessons, tooling gotchas, and high-signal fixes here.

Template:
- Date:
- Lesson:
- Symptom:
- Fix or rule:
- Related files:

## Entries

- Date: 2026-04-16
- Lesson: Graphify rebuild may depend on a different Python interpreter than the shell's default `python`.
- Symptom: `python scripts/check_graph_staleness.py --rebuild` reported stale docs but failed with `ModuleNotFoundError: No module named 'graphify'` even though the `graphify` CLI was installed.
- Fix or rule: The staleness checker now resolves an interpreter that can import `graphify`, including the interpreter behind the `graphify` command on Windows. Use the documented command first before falling back to manual interpreter selection.
- Related files: `scripts/check_graph_staleness.py`, `scripts/rebuild_graphify.py`
