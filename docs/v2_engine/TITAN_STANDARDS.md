# Titan Engine: Engineering & Documentation Standards

## Overview
The Blue Waves V2 "Titan" Engine is a high-performance native core (C++) designed for 120fps+ reading experiences. These standards ensure its long-term stability and performance.

## 1. Documentation Requirements
*   **Location**: All V2 docs live in `docs/v2_engine/`.
*   **Trigger**: Any change to JNI, C++ interfaces, or layout math must trigger a documentation update.
*   **Content**: Explain the 'Why' (Rationale) and 'How' (Implementation).

## 2. Engineering Standards
*   **Memory**: Use RAII (Resource Acquisition Is Initialization). Never leave a `new` without a corresponding `delete` (or use `std::unique_ptr`).
*   **IO**: Prefer native file descriptors (`open`/`read`) over high-level streams for performance critical tasks.
*   **JNI**: Keep the bridge narrow. Batch operations rather than making thousands of small JNI calls.

## 3. The Lego Rule
All major features (Unzipper, PDF Reflow, Shader Rendering) must implement an interface from `IEngineComponent.h`. No hard-coding allowed.

## 4. The Logging Rule
No native function is complete without `TLOGI` entry/exit markers and `TitanTimer` blocks for any logic exceeding O(1) complexity.

## 5. The Spine-First Parsing Rule
To avoid the "Missing Chapter" bug (V1 regression where TOC < Actual chapters), the native engine MUST:
1.  Parse the `<spine>` in the OPF first to determine the absolute reading order.
2.  Map TOC entries back to the spine items.
3.  Any spine item missing a TOC label must be assigned a generic "Chapter [N]" label rather than being omitted.
