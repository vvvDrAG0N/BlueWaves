from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--trace", required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--package", default="com.epubreader")
    parser.add_argument("--output-md", required=True)
    parser.add_argument("--output-json", required=True)
    return parser.parse_args()


def add_perfetto_path(repo_root: Path) -> None:
    perfetto_root = repo_root / "logs" / "_perfetto_py"
    if not perfetto_root.exists():
        raise SystemExit(f"Perfetto Python package not found at {perfetto_root}")
    sys.path.insert(0, str(perfetto_root))


def rows_to_dicts(rows) -> list[dict]:
    return [dict(row.__dict__) for row in rows]


def safe_query(tp, sql: str) -> list[dict]:
    try:
        return rows_to_dicts(tp.query(sql))
    except Exception as exc:  # noqa: BLE001
        return [{"error": str(exc), "sql": sql}]


def sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def first_int(rows: list[dict], key: str) -> int | None:
    if not rows or "error" in rows[0]:
        return None
    value = rows[0].get(key)
    return None if value is None else int(value)


def render_table(title: str, rows: list[dict]) -> list[str]:
    lines = [f"## {title}", ""]
    if not rows:
        lines.append("No rows returned.")
        lines.append("")
        return lines
    if "error" in rows[0]:
        lines.append(f"Query failed: `{rows[0]['error']}`")
        lines.append("")
        return lines

    headers = list(rows[0].keys())
    lines.append("| " + " | ".join(headers) + " |")
    lines.append("| " + " | ".join(["---"] * len(headers)) + " |")
    for row in rows:
        values = [str(row.get(header, "")) for header in headers]
        lines.append("| " + " | ".join(values) + " |")
    lines.append("")
    return lines


def get_frame_tables(tp) -> list[str]:
    rows = safe_query(
        tp,
        """
        SELECT name
        FROM sqlite_master
        WHERE type = 'table' AND name LIKE '%frame%'
        ORDER BY name
        """,
    )
    if not rows or "error" in rows[0]:
        return []
    return [str(row["name"]) for row in rows]


def table_columns(tp, table_name: str) -> list[str]:
    rows = safe_query(tp, f"PRAGMA table_info({table_name})")
    if not rows or "error" in rows[0]:
        return []
    return [str(row["name"]) for row in rows]


def maybe_frame_summary(tp, app_upid: int | None) -> list[dict]:
    if app_upid is None:
        return []

    for table_name in ("actual_frame_timeline_slice", "frame_timeline_slice"):
        columns = table_columns(tp, table_name)
        if not columns:
            continue
        if "upid" in columns and "dur" in columns:
            select_bits = [
                "COUNT(*) AS frames",
                "ROUND(AVG(dur) / 1e6, 2) AS avg_dur_ms",
                "ROUND(MAX(dur) / 1e6, 2) AS max_dur_ms",
            ]
            group_by = []
            if "jank_type" in columns:
                select_bits.insert(0, "COALESCE(jank_type, '') AS jank_type")
                group_by.append("jank_type")
            sql = f"""
                SELECT {", ".join(select_bits)}
                FROM {table_name}
                WHERE upid = {app_upid}
                {"GROUP BY " + ", ".join(group_by) if group_by else ""}
                ORDER BY max_dur_ms DESC
                LIMIT 20
            """
            rows = safe_query(tp, sql)
            if rows and "error" not in rows[0]:
                return rows
    return []


def main() -> int:
    args = parse_args()
    trace_path = Path(args.trace).resolve()
    repo_root = Path(__file__).resolve().parent.parent
    add_perfetto_path(repo_root)

    from perfetto.trace_processor import TraceProcessor, TraceProcessorConfig  # pylint: disable=import-error

    config = TraceProcessorConfig(load_timeout=15)
    tp = TraceProcessor(trace=str(trace_path), config=config)

    package_sql = sql_string(args.package)
    process_rows = safe_query(
        tp,
        f"""
        SELECT upid, pid, name
        FROM process
        WHERE name = {package_sql} OR name LIKE {package_sql[:-1] + "%'"}
        ORDER BY pid
        """,
    )

    app_upid = first_int(process_rows, "upid")
    thread_rows = []
    main_thread_rows = []
    render_thread_rows = []
    main_thread_state_rows = []
    render_thread_state_rows = []
    main_thread_slice_rows = []
    render_thread_slice_rows = []
    app_slice_rows = []
    choreographer_rows = []
    frame_rows = []

    if app_upid is not None:
        thread_rows = safe_query(
            tp,
            f"""
            SELECT tid, name, is_main_thread, utid
            FROM thread
            WHERE upid = {app_upid}
            ORDER BY is_main_thread DESC, tid
            LIMIT 30
            """,
        )

        main_utid = None
        render_utid = None
        if thread_rows and "error" not in thread_rows[0]:
            for row in thread_rows:
                if row.get("is_main_thread") == 1 and main_utid is None:
                    main_utid = int(row["utid"])
                if row.get("name") == "RenderThread" and render_utid is None:
                    render_utid = int(row["utid"])

        if main_utid is not None:
            main_thread_rows = safe_query(
                tp,
                f"""
                SELECT state, ROUND(SUM(dur) / 1e6, 2) AS total_ms
                FROM thread_state
                WHERE utid = {main_utid}
                GROUP BY state
                ORDER BY total_ms DESC
                """,
            )

            main_thread_state_rows = safe_query(
                tp,
                f"""
                SELECT
                  slice.name AS slice_name,
                  COUNT(*) AS count,
                  ROUND(MAX(dur) / 1e6, 2) AS max_ms,
                  ROUND(SUM(dur) / 1e6, 2) AS total_ms
                FROM slice
                JOIN thread_track ON slice.track_id = thread_track.id
                WHERE thread_track.utid = {main_utid} AND dur >= 1000000
                GROUP BY slice.name
                ORDER BY max_ms DESC, total_ms DESC
                LIMIT 20
                """,
            )

            choreographer_rows = safe_query(
                tp,
                f"""
                SELECT
                  COUNT(*) AS frames,
                  ROUND(AVG(dur) / 1e6, 2) AS avg_ms,
                  ROUND(MAX(dur) / 1e6, 2) AS max_ms
                FROM slice
                JOIN thread_track ON slice.track_id = thread_track.id
                WHERE thread_track.utid = {main_utid}
                  AND slice.name = 'Choreographer#doFrame'
                """,
            )

        if render_utid is not None:
            render_thread_rows = safe_query(
                tp,
                f"""
                SELECT state, ROUND(SUM(dur) / 1e6, 2) AS total_ms
                FROM thread_state
                WHERE utid = {render_utid}
                GROUP BY state
                ORDER BY total_ms DESC
                """,
            )

            render_thread_state_rows = safe_query(
                tp,
                f"""
                SELECT
                  slice.name AS slice_name,
                  COUNT(*) AS count,
                  ROUND(MAX(dur) / 1e6, 2) AS max_ms,
                  ROUND(SUM(dur) / 1e6, 2) AS total_ms
                FROM slice
                JOIN thread_track ON slice.track_id = thread_track.id
                WHERE thread_track.utid = {render_utid} AND dur >= 1000000
                GROUP BY slice.name
                ORDER BY max_ms DESC, total_ms DESC
                LIMIT 20
                """,
            )

        app_slice_rows = safe_query(
            tp,
            f"""
            SELECT
              thread.name AS thread_name,
              slice.name AS slice_name,
              ROUND(slice.dur / 1e6, 2) AS dur_ms
            FROM slice
            JOIN thread_track ON slice.track_id = thread_track.id
            JOIN thread USING (utid)
            WHERE thread.upid = {app_upid}
              AND slice.dur >= 2000000
            ORDER BY slice.dur DESC
            LIMIT 40
            """,
        )

        frame_rows = maybe_frame_summary(tp, app_upid)

    metadata_rows = safe_query(
        tp,
        """
        SELECT name, str_value
        FROM metadata
        WHERE name IN ('android_build_fingerprint', 'trace_uuid')
        ORDER BY name
        """,
    )

    frame_tables = get_frame_tables(tp)
    frame_table_rows = [{"table_name": name, "columns": ", ".join(table_columns(tp, name))} for name in frame_tables]

    summary = {
        "label": args.label,
        "trace_path": str(trace_path),
        "package": args.package,
        "metadata": metadata_rows,
        "processes": process_rows,
        "threads": thread_rows,
        "main_thread_states": main_thread_rows,
        "main_thread_slices": main_thread_state_rows,
        "render_thread_states": render_thread_rows,
        "render_thread_slices": render_thread_state_rows,
        "top_app_slices": app_slice_rows,
        "choreographer": choreographer_rows,
        "frame_tables": frame_table_rows,
        "frame_summary": frame_rows,
    }

    output_json = Path(args.output_json)
    output_json.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    lines = [f"# Trace Summary: {args.label}", "", f"- Trace: `{trace_path}`", f"- Package: `{args.package}`", ""]
    lines += render_table("Metadata", metadata_rows)
    lines += render_table("Processes", process_rows)
    lines += render_table("Threads", thread_rows)
    lines += render_table("Main Thread State Totals", main_thread_rows)
    lines += render_table("Main Thread Long Slices", main_thread_state_rows)
    lines += render_table("RenderThread State Totals", render_thread_rows)
    lines += render_table("RenderThread Long Slices", render_thread_state_rows)
    lines += render_table("Choreographer DoFrame", choreographer_rows)
    lines += render_table("Top App Slices", app_slice_rows)
    lines += render_table("Frame Tables", frame_table_rows)
    lines += render_table("Frame Summary", frame_rows)

    output_md = Path(args.output_md)
    output_md.write_text("\n".join(lines), encoding="utf-8")
    tp.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
