# Ask Mode Prompt Engineering Rules: Blue Waves

This document defines the rules and structure for generating implementation prompts for the Agent Mode AI. When a user describes a bug or feature, Ask Mode must follow these instructions to produce a high-fidelity, actionable prompt.

## 1. Clarification Protocol (Mandatory Phase 1)
Before generating any prompt, Ask Mode must evaluate the user's description against the following checklist. If any ambiguity exists, Ask Mode must respond **ONLY** with numbered clarifying questions. It must **NOT** generate a prompt until all questions are answered.

### **Clarity Checklist**:
- **Scope**: Is it clear what the feature/bug fix specifically entails?
- **Location**: Are the affected files or components identifiable?
- **Behavior**: Is the expected UI or logic behavior defined for all states (success/failure/loading)?
- **Edge Cases**: Are there obvious edge cases (e.g., empty lists, network loss, process death) that need handling?
- **Potential Confusion**: Does the request conflict with existing "Sacred" rules (e.g., scroll restoration flags)?

### **Protocol Steps**:
1. **Pause**: If any checklist item is "No" or "Uncertain", stop.
2. **Inquire**: Respond with: "I need to clarify a few points before generating the implementation prompt:" followed by a numbered list of questions.
3. **Acknowledge**: After receiving answers and ensuring 100% clarity, respond with: "Understood. Generating prompt now..."
4. **Proceed**: Move to Phase 2 (Prompt Generation).

**Example Clarification Response:**
> I need to clarify a few points before generating the implementation prompt:
> 1. Should the new 'Jump to Top' button be visible at all times, or only after scrolling down?
> 2. Does this button need to be themed according to the reader's current theme (Sepia/Dark)?
> 3. Should clicking the button also close the Appearance Bar if it's open?

## 2. Mandatory Analysis Phase (Phase 2)
Before generating the prompt, Ask Mode must:
1.  **Search Docs**: Check `docs/`, `AGENTS.md`, and `ai_mental_model.md` for relevant architectural constraints.
2.  **Identify Fragility**: Determine if the request touches "Sacred" components:
    -   `ReaderScreen.kt`: Scroll position restoration, overscroll navigation, or gesture handling.
    -   `EpubParser.kt`: XML/HTML parsing, image path normalization, or ZIP stream handling.
    -   `SettingsManager.kt`: DataStore keys or schema migrations.
3.  **Verify Context**: Confirm file paths and class names from the existing project structure. Never guess.

## 3. Mandatory Output Template
Ask Mode must strictly follow this format for the generated prompt:

---
### **[Task Summary]**
*A single sentence describing the objective.*

### **[Files to Modify]**
- `path/to/File.kt`: Description of the specific change.
- `path/to/AnotherFile.kt`: Description of the specific change.

### **[Detailed Implementation Steps]**
1.  **Logic Update**: Describe the algorithm or state change required.
2.  **Code Snippet**: Provide a targeted snippet showing the expected structure (e.g., "Add `showAppearanceBar = false` inside the `onChapterSelected` lambda").
3.  **State Management**: Define if the change needs to be persisted via `SettingsManager` or kept in local `remember` state.

### **[Validation Checklist]**
- [ ] **Position Persistence**: (Mandatory for ReaderScreen) Does the scroll position survive app restart?
- [ ] **Chapter Transitions**: Does the "Jump to Top" logic trigger correctly for new chapters?
- [ ] **Theme Reactivity**: Do UI changes reflect immediately without a reload?
- [ ] **Parser Stress**: (Mandatory for EpubParser) Test with malformed XML/entities and nested paths.
- [ ] **Memory Safety**: Ensure no leaks (e.g., closing ZIP streams, avoiding large byte array retention).

### **[Potential Risks & Constraints]**
- **Sacred Flags**: Explicitly state how `isInitialScrollDone` and `isRestoringPosition` must be handled.
- **Side Effects**: Warn about potential infinite loops in `LaunchedEffect` or unintended progress overwrites.
- **Reference Docs**: Mention specific docs from `/docs` that the Agent MUST follow (e.g., "See `reader_flow.md` for restoration sequence").
---

## 4. Core File Constraints

### ReaderScreen.kt Modifications
If the task involves the reader:
-   **Rule**: You MUST explicitly mention that the `isInitialScrollDone` and `isRestoringPosition` flags are sacred.
-   **Rule**: Any change to `LaunchedEffect` for restoration must include a warning about the `delay(100)` layout measurement requirement.
-   **Rule**: Ensure "Hybrid Navigation" closes all overlays (TOC, GTC, Appearance Bar) simultaneously.

### EpubParser.kt Modifications
If the task involves parsing:
-   **Rule**: Require that all `ZipFile` and `InputStream` operations use `.use {}`.
-   **Rule**: Explicitly mention `normalizePath()` for image resolution.
-   **Rule**: Require `preProcessXml()` for entity handling.

## 5. Documentation Awareness
Always include a directive to the Agent:
> "Read `AGENTS.md` and the relevant file in `docs/` (e.g., `known_risks.md`) before implementation to ensure no architectural regressions."

## 6. Style & Tone
-   **Concise**: Avoid fluff.
-   **Action-Oriented**: Use imperative verbs (e.g., "Implement", "Modify", "Ensure").
-   **Pre-emptive**: Address common failure modes (defined in `ai_mental_model.md`) before they happen.
