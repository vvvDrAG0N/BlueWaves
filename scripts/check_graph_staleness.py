import os
import shutil
import subprocess
import sys
from pathlib import Path

from graph_corpus import staleness_watch_paths

def get_last_modified(paths):
    last_mod = 0
    for path in paths:
        p = Path(path)
        if p.is_dir():
            for f in p.glob("**/*"):
                if f.is_file():
                    last_mod = max(last_mod, f.stat().st_mtime)
        elif p.exists():
            last_mod = max(last_mod, p.stat().st_mtime)
    return last_mod

def check_staleness(root: Path):
    graph_file = root / "graphify-out" / "graph.json"
    
    if not graph_file.exists():
        print("STALE: Graph file missing.")
        return True

    watched_paths = staleness_watch_paths(root)

    last_source_mod = get_last_modified(watched_paths)
    graph_mod = graph_file.stat().st_mtime
    
    if last_source_mod > graph_mod:
        print("STALE: Graph inputs or corpus rules modified since last graph build.")
        return True
    
    print("FRESH: Graph is up to date.")
    return False


def interpreter_has_graphify(python_executable: str) -> bool:
    try:
        result = subprocess.run(
            [python_executable, "-c", "import graphify"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
    except OSError:
        return False
    return result.returncode == 0


def infer_python_from_graphify_bin(graphify_bin: str) -> str | None:
    graphify_path = Path(graphify_bin)

    if os.name == "nt" and graphify_path.suffix.lower() == ".exe":
        candidate = graphify_path.parent.parent / "python.exe"
        if candidate.exists():
            return str(candidate)
        return None

    try:
        first_line = graphify_path.read_text(encoding="utf-8").splitlines()[0].strip()
    except (OSError, IndexError, UnicodeDecodeError):
        return None

    if not first_line.startswith("#!"):
        return None

    candidate = first_line[2:].split()[0]
    if Path(candidate).exists():
        return candidate
    return shutil.which(candidate)


def resolve_graphify_python() -> str | None:
    candidates: list[str] = []

    if sys.executable:
        candidates.append(sys.executable)

    graphify_bin = shutil.which("graphify")
    if graphify_bin:
        inferred = infer_python_from_graphify_bin(graphify_bin)
        if inferred:
            candidates.append(inferred)

    seen = set()
    for candidate in candidates:
        if candidate in seen:
            continue
        seen.add(candidate)
        if interpreter_has_graphify(candidate):
            return candidate

    return None

if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    is_stale = check_staleness(root)
    exit_code = 1 if is_stale else 0

    if is_stale and "--rebuild" in sys.argv:
        print("Rebuilding graph...")
        graphify_python = resolve_graphify_python()
        if graphify_python is None:
            print("ERROR: Could not find a Python interpreter with the 'graphify' package installed.")
            print("Install graphify for the active interpreter, or make the interpreter behind the `graphify` command available.")
            sys.exit(2)

        rebuild_result = subprocess.run(
            [graphify_python, str(root / "scripts" / "rebuild_graphify.py"), str(root)],
            check=False,
        )
        exit_code = rebuild_result.returncode

    sys.exit(exit_code)
