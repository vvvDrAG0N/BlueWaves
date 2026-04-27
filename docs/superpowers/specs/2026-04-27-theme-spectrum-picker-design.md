# Theme Spectrum Picker Design

## Goal

Upgrade the Appearance theme editor color picker from a slider-only HSV editor to a more direct spectrum-based picker that is easier to use, while keeping the existing theme model, guided balancing rules, and HEX persistence contract intact.

## Scope

- Theme editor color picking in the Appearance flow
- Basic, Extended, and Advanced theme editor modes
- Picker modal layout and interaction only
- Guided-mode safe-zone behavior inside the 2D spectrum
- Automated coverage for the new picker interaction

Out of scope:

- Reworking the broader Appearance apply flow
- Changing DataStore keys, stored schema, or canonical theme color format
- Replacing guided balancing logic with a new color algorithm
- Changing the theme editor grid token format or post-commit field-cell display
- Adding manual text entry for HEX or RGB in this pass

## Current Ownership

- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
  - Owns the picker modal, current HSV state, guided commit behavior, and dismiss semantics.
- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
  - Owns picker launch wiring, per-mode color fields, and draft update behavior.
- `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`
  - Owns guided resolution, color edit results, and the picker session contract that bridges the picker back into the editor draft.
- `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorModels.kt`
  - Owns guided draft rebalancing through `rebalanceGuidedFields()`.
- `core/model/src/main/java/com/epubreader/core/model/ThemePaletteGeneration.kt`
  - Owns the real guided palette generation and contrast enforcement rules.

## Approved Approach

Keep one shared picker surface across Basic, Extended, and Advanced, but replace the current slider trio with:

- a 2D saturation/value spectrum as the primary selection surface
- a separate hue control
- the existing large live preview swatch
- the existing commit and dismiss flow

The picker stays visually familiar. The redesign is about direct manipulation, not a broader theme-editor rewrite.

Mode behavior changes only in how the spectrum is constrained:

- `Advanced` keeps full freedom across the full 2D square.
- `Basic` and `Extended` show a guided safe zone inside the square.
- In guided modes, the user cannot drag the handle outside that safe zone.

This means guided mode stops teaching its rule after the fact and instead teaches it directly in the interaction surface.

## Picker Experience

The new picker should feel like a direct visual control rather than a stack of form sliders:

1. The user taps a theme color cell.
2. A modal opens with the current color already positioned on the 2D spectrum and hue control.
3. The user drags on the square to adjust saturation and brightness.
4. The user uses the hue control to move across color families.
5. The preview swatch updates live.
6. `Done` commits the change. BACK or tapping outside dismisses without saving.

The rest of the dialog should stay stable:

- keep the same modal ownership
- keep the same preview area
- keep the same `Done` affordance
- keep the same cancel-on-dismiss behavior

## Guided Safe Zone

Guided mode should use real boundaries inside the 2D square, not just a decorative overlay.

Behavior rules:

- The visible safe zone represents the area where the guided resolver would keep the selected color literal for the active field and current context.
- Areas outside that safe zone are visibly dimmed or veiled.
- The drag handle cannot move into the dimmed region.
- If the user drags toward an invalid point, the chosen position projects to the nearest allowed point on the guided boundary.
- The live preview swatch in guided mode should therefore reflect the actual allowed result, not a knowingly invalid color that will later be changed.

This keeps guided mode honest. The square itself becomes the contract.

## How The Safe Zone Must Be Computed

The safe zone cannot be a fake fixed circle or a hard-coded box.

The current guided system resolves color through:

- `ThemeEditorDraft.rebalanceGuidedFields()`
- `generatePaletteFromGuidedInput(...)`
- contrast enforcement in `ThemePaletteGeneration.kt`

That means the allowed region depends on:

- which field is being edited
- the current hue
- the surrounding theme state
- whether reader colors are linked
- the current mode

So the implementation should derive the guided safe zone from the real guided resolver. If the boundary ends up irregular, that is acceptable. Accuracy matters more than geometric simplicity.

## Advanced Mode

Advanced mode stays literal and unrestricted:

- no guided safe-zone boundary
- no constrained drag projection
- no dimmed invalid region
- the preview always matches the exact chosen HSV point

This preserves the existing "full freedom" contract of Advanced mode.

## Data And State Contract

This is a picker-surface redesign, not a theme-model redesign.

- Persisted theme colors remain HEX strings.
- Existing `ThemeEditorDraft` color fields remain HEX-backed.
- Existing `ThemeEditorColorEditResult` remains the commit path.
- Guided balancing remains owned by the current guided pipeline.
- No DataStore key, settings contract, or schema change is required.

If a helper is introduced, it should stay feature-local and limited to:

- spectrum hit-testing and coordinate mapping
- guided safe-zone evaluation and nearest-valid projection
- drawing helpers for the 2D square and hue control

## Layout And Sizing Expectations

The picker should remain comfortable on phone-sized layouts:

- the 2D spectrum gets visual priority
- the hue control sits near the square, not below a long stack of controls
- the preview swatch stays immediately visible
- the square uses a stable fixed height so drag interaction cannot shift layout
- the guided boundary remains legible without turning the square into a noisy diagram

The theme editor outside the picker should remain visually unchanged in this pass.

## File Shape Expectations

The picker file is already near the repo's split threshold, so the redesign should stay surgical:

- keep `SettingsThemeColorPicker.kt` as the dialog owner
- extract the 2D spectrum and hue control into a focused feature-local helper file if needed
- avoid pushing `SettingsThemeColorPicker.kt` or `SettingsThemeEditor.kt` deeper toward the 450-500 line danger zone

`SettingsThemeEditor.kt` should stay responsible only for launching the picker and receiving the chosen value, not for owning spectrum math.

## Testing Expectations

The implementation plan should cover:

- guided mode constrained drag behavior
- advanced mode unrestricted drag behavior
- `Done` still being the only commit path
- BACK and outside-dismiss still discarding changes
- guided safe-zone visibility for guided fields and absence for advanced fields
- live preview staying consistent with the real applied picker state

Manual verification should include:

1. Basic mode guided field
2. Extended mode guided field
3. Advanced mode unrestricted field
4. One dismiss-without-save pass
5. One commit pass

## Risks And Guards

- The biggest risk is lying about guided behavior. A pretty overlay that does not match the real resolver would make the picker less trustworthy.
- The second risk is accidental scope creep into theme-editor labels, persistence, or broader Appearance UX.
- The picker already has important dismiss semantics. The redesign must preserve cancel-vs-commit behavior.
- Because the current picker state is HSV-backed, the new square should reuse that pipeline instead of introducing a second competing color-state model.
- If nearest-valid projection feels jumpy, the implementation should smooth the projection path, but it still must remain inside the true guided-safe region.
