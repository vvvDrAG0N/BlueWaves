from pathlib import Path
import re
import shutil
import sys

from graphify.analyze import god_nodes, suggest_questions, surprising_connections
from graphify.build import build_from_json
from graphify.cluster import cluster, score_all
from graphify.detect import detect
from graphify.export import to_json
from graphify.extract import extract
from graphify.report import generate
from graphify.wiki import to_wiki


def sanitize_path(path: Path, root: Path) -> str:
    resolved = path.resolve().as_posix().lower()
    return re.sub(r"[^a-z0-9]+", "_", resolved).strip("_")


def build_file_label_map(code_files, root: Path):
    return {sanitize_path(path, root): path.stem for path in code_files}


def build_labels(graph, communities, file_labels):
    labels = {}
    for community_id, nodes in communities.items():
        if not nodes:
            labels[community_id] = f"Community {community_id}"
            continue
        ordered_nodes = sorted(nodes, key=lambda node: (-graph.degree(node), str(node)))
        friendly = next((file_labels[node] for node in ordered_nodes if node in file_labels), None)
        if friendly is None:
            friendly = next(iter(ordered_nodes), f"Community {community_id}")
        labels[community_id] = f"{friendly} Cluster"
    return labels


def rebuild(root: Path) -> int:
    detected = detect(root)
    code_files = [Path(path) for path in detected["files"]["code"]]
    if not code_files:
        print("[graphify] No code files found.")
        return 1

    extracted = extract(code_files)
    graph = build_from_json(extracted)
    communities = cluster(graph)
    cohesion = score_all(graph, communities)
    gods = god_nodes(graph)
    surprises = surprising_connections(graph, communities)
    file_labels = build_file_label_map(code_files, root)
    labels = build_labels(graph, communities, file_labels)
    questions = suggest_questions(graph, communities, labels)

    output_dir = root / "graphify-out"
    output_dir.mkdir(exist_ok=True)
    wiki_dir = output_dir / "wiki"
    if wiki_dir.exists():
        shutil.rmtree(wiki_dir)

    report = generate(
        graph,
        communities,
        cohesion,
        labels,
        gods,
        surprises,
        {
            "files": {
                "code": [str(path) for path in code_files],
                "document": [],
                "paper": [],
                "image": [],
            },
            "total_files": len(code_files),
            "total_words": detected.get("total_words", 0),
        },
        {"input": 0, "output": 0},
        str(root),
        suggested_questions=questions,
    )
    (output_dir / "GRAPH_REPORT.md").write_text(report, encoding="utf-8")
    to_json(graph, communities, str(output_dir / "graph.json"))
    wiki_count = to_wiki(graph, communities, wiki_dir, labels, cohesion, gods)

    stale_flag = output_dir / "needs_update"
    if stale_flag.exists():
        stale_flag.unlink()

    print(
        "[graphify] Rebuilt "
        f"{graph.number_of_nodes()} nodes, {graph.number_of_edges()} edges, "
        f"{len(communities)} communities, {wiki_count} wiki pages"
    )
    print(f"[graphify] Output: {output_dir}")
    return 0


if __name__ == "__main__":
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
    raise SystemExit(rebuild(root))
