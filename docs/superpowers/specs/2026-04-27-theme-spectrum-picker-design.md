# Theme Spectrum Picker Design

## Goal

Upgrade the Appearance theme editor color picker from a slider-only HSV editor to a more direct spectrum-based picker that is easier to understand at a glance, while keeping the existing theme model, guided balancing rules, and HEX persistence contract intact.

## Scope

- Theme editor color picking in the Appearance flow
- Basic, Extended, and Advanced theme editor modes
- Picker modal layout, live preview, readouts, and guided-mode cues
- Theme editor field-cell display after a color is committed
- Automated coverage for the new picker interaction and readout behavior

Out of scope:

- Reworking the broader Appearance auto-apply decision
- Changing DataStore keys, stored schema, or canonical theme color format
- Replacing guided balancing logic with a new color algorithm
- Adding manual text entry for HEX or RGB in this pass

## Current Ownership

- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
  - Owns the picker modal, current HSV state, guided commit behavior, and live status text.
- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
  - Owns picker launch wiring, per-mode color fields, and draft update behavior.
- `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorSections.kt`
  - Owns the two-column color field grid used by the editor body.
- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeStudioComponents.kt`
  - Owns `StudioColorCell`, including the visible color token shown inside each editor cell.

## Approved Approach

Keep one shared picker surface across Basic, Extended, and Advanced, but replace the current slider-first interaction with a direct visual picker:

- a 2D saturation/value spectrum as the primary selection surface
- a separate hue control
- a large live preview swatch
- one readable live color token rendered as `RGB(r, g, b)`

The picker will stay visually consistent across all modes. What changes by mode is the behavior messaging:

- Guided modes may rebalance the picked color for readability
- Advanced mode applies the exact picked color without guidance

HEX remains the canonical stored and internal draft format. RGB is a presentation-only readout for easier human scanning.

## Picker Experience

The new picker should feel more like a modern creative app than a settings form:

1. The user taps a theme color cell.
2. A modal opens with the current color already positioned on the spectrum and hue control.
3. The user drags directly on the spectrum to choose the base color and uses the hue control to move around the wheel.
4. A large preview swatch updates live.
5. The picker shows readable text values instead of exposing raw HEX as the main visible token.
6. `Done` commits the change. BACK or tapping outside dismisses without saving.

The current three stacked HSV sliders are removed from the primary picker surface in this redesign. This keeps the dialog from feeling cramped and makes the visual picker the main interaction model.

## Mode Behavior

### Basic And Extended

Basic and Extended keep the current guided balancing philosophy. The user is still free to pick any point on the spectrum, but guided logic may resolve that pick into a nearby readable color before the draft is committed.

Behavior rules:

- The spectrum always reflects the current live pick.
- The large preview swatch shows the applied color that the theme will actually use.
- When the guided system does not alter the color, the picker shows one live `RGB(...)` readout.
- When the guided system does alter the color, the picker shows:
  - `Picked: RGB(...)`
  - `Applied: RGB(...)`
  - a short cue such as `Adjusted for readability`

The guided picker should be able to derive that applied preview without mutating the saved draft yet. In other words, guided modes may compute a preview-only resolved color while the dialog is open, but the actual draft write still happens only on `Done`.

This keeps guided modes honest and legible without making them feel locked or broken.

### Advanced

Advanced mode stays literal and creator-owned:

- no picked/applied split
- no guided adjustment cue
- one live `RGB(...)` readout
- the preview swatch always matches the exact picked color

## Display Rules Outside The Picker

After the user commits a color and returns to the theme editor grid, each color cell should show:

- the existing color swatch
- the label
- one compact human-readable token as `RGB(r, g, b)`

The editor grid should not show combined `HEX + RGB` strings. The user explicitly chose the single-token approach to keep the cells easier to scan and to avoid overflow pressure inside the two-column layout.

HEX should remain available internally for draft updates, balancing logic, persistence, and tests, but it is no longer the main visible token on the editor surface.

## Data And State Contract

This is a picker-surface redesign, not a theme-model redesign.

- Persisted theme colors remain HEX strings.
- Existing `ThemeEditorDraft` color fields remain HEX-backed.
- Guided color edit paths continue to operate through the existing `ThemeEditorColorEditResult` contract.
- RGB strings are derived in the UI layer from the resolved color value.
- No DataStore key, settings contract, or schema change is required.

If a small helper is introduced, it should be UI-facing and limited to:

- formatting `Long` or parsed colors as `RGB(r, g, b)`
- mapping spectrum interactions back into the existing HSV/HEX pipeline

## Layout And Sizing Expectations

The picker should remain comfortable on phone-sized layouts:

- spectrum gets the visual priority
- hue control sits close to the spectrum, not below a long stack of form controls
- preview swatch and live readout stay immediately visible
- guided status text should be short and never dominate the modal

The editor grid keeps its current two-column structure. The redesign should preserve that layout rather than widening cells or introducing wrapped multiline color tokens as a requirement.

## Testing Expectations

The implementation plan should cover both behavior and presentation seams:

- JVM or local Android-aware coverage for any extracted color-formatting helper
- instrumentation coverage for picker dismiss vs commit behavior
- instrumentation coverage for guided `Picked` versus `Applied` visibility
- instrumentation coverage that committed editor cells render `RGB(...)` after closing the picker
- focused manual verification across Basic, Extended, and Advanced on a phone-sized surface so the picker does not feel cramped

## Risks And Guards

- Guided transparency can become noisy if the picker shows too many simultaneous labels. Keep cues short and only surface the picked/applied split when a real adjustment occurs.
- The two-column editor grid is tight. The redesign must keep the post-commit token concise and single-line.
- The picker already has important dismiss semantics. The redesign must preserve the fixed cancel-vs-commit behavior instead of reintroducing accidental save-on-dismiss behavior.
- Because the current picker state is HSV-backed, spectrum interaction should reuse that pipeline instead of introducing a second competing color-state model.
