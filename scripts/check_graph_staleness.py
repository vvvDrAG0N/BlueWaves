import os
import sys
from pathlib import Path

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
    docs_dir = root / "docs"
    graph_file = root / "graphify-out" / "graph.json"
    
    if not graph_file.exists():
        print("STALE: Graph file missing.")
        return True

    # Critical files that should trigger a rebuild
    critical_files = [
        docs_dir,
        # Grep for AI_ENTRY_POINT could be expensive, so we just check core source dirs
        root / "app/src/main/java/com/epubreader/app",
        root / "app/src/main/java/com/epubreader/data",
        root / "app/src/main/java/com/epubreader/feature",
    ]
    
    last_source_mod = get_last_modified(critical_files)
    graph_mod = graph_file.stat().st_mtime
    
    if last_source_mod > graph_mod:
        print(f"STALE: Source or docs modified since last graph build.")
        return True
    
    print("FRESH: Graph is up to date.")
    return False

if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    is_stale = check_staleness(root)
    if is_stale and "--rebuild" in sys.argv:
        print("Rebuilding graph...")
        import rebuild_graphify
        rebuild_graphify.rebuild(root)
    sys.exit(1 if is_stale else 0)
