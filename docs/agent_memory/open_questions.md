# Open Questions

Track unresolved questions here so later agents do not silently guess their way through them.

Template:
- Date:
- Question:
- Why it matters:
- Recommended next check:

## Entries

- Date: 2026-04-16
- Question: Should future durable memory stay repo-native only, or should we add a mirrored external memory layer through MCP, Mem0, Neo4j, or another backend?
- Why it matters: External memory could improve retrieval and cross-agent sharing, but it also adds infra, synchronization, and trust-surface complexity.
- Recommended next check: Use the repo-native memory layer first and only add an external memory backend after we find concrete retrieval or scale limits.

- Date: 2026-04-16
- Question: What exact Microsoft Agent Framework role do we want next: simple orchestration for local agents, or a fuller long-running multi-agent runtime?
- Why it matters: The integration shape is very different depending on whether we only need role routing and handoffs, or we want durable hosted conversations and multi-step orchestrations.
- Recommended next check: Define one concrete MAF use case for this repo before adding framework-specific code or configuration.
