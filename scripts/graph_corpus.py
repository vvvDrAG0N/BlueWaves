from __future__ import annotations

from pathlib import Path

PRODUCTION_SOURCE_DIRS = (
    "app/src/main",
    "core/model/src/main",
    "core/ui/src/main",
    "data/books/src/main",
    "data/settings/src/main",
    "feature/editbook/src/main",
    "feature/library/src/main",
    "feature/pdf-legacy/src/main",
    "feature/reader/src/main",
    "feature/settings/src/main",
)

GRAPH_WORKFLOW_CODE_FILES = (
    "scripts/check_graph_staleness.py",
    "scripts/rebuild_graphify.py",
    "scripts/graph_corpus.py",
)

CANONICAL_DOCUMENT_FILES = (
    "docs/README.md",
    "docs/project_graph.md",
)

GRAPH_RULE_FILES = (
    ".graphifyignore",
    "scripts/graph_corpus.py",
)

GRAPHIFYIGNORE_PATTERNS = (
    ".agents/**",
    ".codex/**",
    ".gemini/**",
    ".gradle/**",
    ".idea/**",
    ".kotlin/**",
    ".obsidian/**",
    "build/**",
    "docs/agent_memory/**",
    "docs/legacy/**",
    "feature/**/src/androidTest/**",
    "feature/**/src/test/**",
    "graphify-out/**",
    "logs/**",
    "temps/**",
    "**/build/**",
    "**/src/androidTest/**",
    "**/src/test/**",
)

SOURCE_CODE_SUFFIXES = {".java", ".kt", ".kts"}
WORKFLOW_CODE_SUFFIXES = {".py"}


def _repo_paths(root: Path, relative_paths: tuple[str, ...]) -> list[Path]:
    return [root / relative_path for relative_path in relative_paths]


def production_source_dirs(root: Path) -> list[Path]:
    return [path for path in _repo_paths(root, PRODUCTION_SOURCE_DIRS) if path.exists()]


def graph_workflow_code_files(root: Path) -> list[Path]:
    return [path for path in _repo_paths(root, GRAPH_WORKFLOW_CODE_FILES) if path.exists()]


def graph_document_files(root: Path) -> list[Path]:
    return [path for path in _repo_paths(root, CANONICAL_DOCUMENT_FILES) if path.exists()]


def graph_rule_files(root: Path) -> list[Path]:
    return [path for path in _repo_paths(root, GRAPH_RULE_FILES) if path.exists()]


def graph_code_files(root: Path) -> list[Path]:
    files: list[Path] = []

    for source_dir in production_source_dirs(root):
        files.extend(
            path
            for path in source_dir.rglob("*")
            if path.is_file() and path.suffix in SOURCE_CODE_SUFFIXES
        )

    files.extend(
        path
        for path in graph_workflow_code_files(root)
        if path.suffix in WORKFLOW_CODE_SUFFIXES
    )

    return sorted(set(files))


def graph_input_files(root: Path) -> list[Path]:
    return sorted(set(graph_code_files(root) + graph_document_files(root)))


def staleness_watch_paths(root: Path) -> list[Path]:
    return (
        production_source_dirs(root)
        + graph_workflow_code_files(root)
        + graph_document_files(root)
        + graph_rule_files(root)
    )


def count_words(path: Path) -> int:
    try:
        return len(path.read_text(encoding="utf-8", errors="ignore").split())
    except OSError:
        return 0
