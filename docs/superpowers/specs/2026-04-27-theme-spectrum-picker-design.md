# Theme Picker Editor Refresh Design

## Goal

Evolve the new spectrum-based theme picker into a compact color editor that supports direct numeric entry without weakening the guided system or changing the broader theme persistence contract.

The picker should now support:

- editable `HEX` and `RGB` input for all modes
- a compact live swatch beside those fields
- the existing 2D saturation/value spectrum plus hue slider
- explicit header actions for save and cancel
- preview-only local edits until explicit confirmation
- a protected dirty-exit flow for `Back` and cancel

## Scope

- Theme editor color picking in the Appearance flow
- Basic, Extended, and Advanced theme editor modes
- Picker modal layout and interaction only
- Guided-mode safe-zone behavior inside the 2D spectrum
- Direct `HEX` and `RGB` text entry plus synchronization with the visual picker
- Dirty-state tracking and explicit save/discard flows
- Automated coverage for typed entry, guided adjustments, and picker exit behavior

Out of scope:

- Reworking the broader Appearance apply flow
- Changing DataStore keys, stored schema, or canonical theme color format
- Replacing guided balancing logic with a new color algorithm
- Reworking the theme editor grid outside the picker
- Changing built-in theme identifiers or custom-theme persistence rules

## Current Ownership

- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeColorPicker.kt`
  - Owns the picker modal, current local color state, guided commit behavior, and dismiss semantics.
- `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerCanvas.kt`
  - Owns the 2D spectrum drawing, hit testing, guided veil, and hue-related visual interaction.
- `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeColorPickerGuidance.kt`
  - Owns guided safe-zone sampling, projection, and related color math helpers.
- `feature/settings/src/main/java/com/epubreader/feature/settings/SettingsThemeEditor.kt`
  - Owns picker launch wiring, per-mode color fields, and draft update behavior.
- `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorColorEditing.kt`
  - Owns guided resolution, color edit results, and the picker session contract that bridges the picker back into the editor draft.
- `feature/settings/src/main/java/com/epubreader/feature/settings/ThemeEditorModels.kt`
  - Owns guided draft rebalancing through `rebalanceGuidedFields()`.
- `core/model/src/main/java/com/epubreader/core/model/ThemePaletteGeneration.kt`
  - Owns the real guided palette generation and contrast enforcement rules.

## Approved Interaction Summary

Keep one shared picker surface across Basic, Extended, and Advanced, but treat it as a mini editor instead of a temporary slider popup.

The picker now has three coordinated color input surfaces:

- editable `HEX`
- editable `RGB`
- visual spectrum plus hue controls

All picker edits stay local in every mode until the user explicitly confirms with the header save icon.

This means:

- no live persistence while dragging or typing
- no mode-specific commit behavior split
- no outside-tap dismissal
- no bottom `Done` button

The picker becomes consistent with the rest of the editor: make changes locally, then explicitly save or discard them.

## Visual Layout

The modal stays in the same general family, but the top section changes shape:

1. Header row
   - `X` icon on the left
   - title centered
   - check icon on the right
2. Top content band
   - left side: editable color values
   - right side: live color swatch
3. Lower content
   - 2D saturation/value spectrum
   - hue slider

The previous large preview block is replaced by a tighter two-column top band so the picker feels more like an editing tool and less like a decorative preview card.

## Value Entry Layout

The value entry area should read like this:

- `HEX`: one compact editable six-digit field
- `RGB`: three compact editable integer fields representing red, green, and blue

Formatting rules:

- `HEX` displays uppercase
- `HEX` is six digits only, without making the user type `#`
- `RGB` fields are individually constrained to `0..255`
- the three RGB values should still visually read as one row, not three separated settings controls

The live swatch remains immediately visible at the right of the same band so users can see the result of exact-number edits without looking lower in the picker.

## Input Synchronization

All three input surfaces must stay synchronized:

- dragging the spectrum updates `HEX`, `RGB`, the swatch, and the spectrum handle
- moving the hue slider updates `HEX`, `RGB`, the swatch, and the spectrum handle
- typing a valid `HEX` updates `RGB`, the swatch, and the spectrum handle
- typing valid `RGB` values updates `HEX`, the swatch, and the spectrum handle

This is a one-color local draft with multiple synchronized representations, not separate input models.

## Partial Input Behavior

Typed entry must not fight the user while they are mid-edit.

Rules:

- partial text is allowed in the local field state
- preview does not update until the currently edited value becomes a complete valid color
- while the current input is incomplete or invalid, the last valid preview color remains active
- normalization happens only once the input is valid enough to parse

Examples:

- a half-entered `HEX` value should not erase the current swatch
- an RGB field temporarily containing an empty string during editing should not immediately zero the color

This keeps typing stable and prevents the picker from jittering between valid and invalid intermediate states.

## Guided Safe Zone

Guided mode still uses real boundaries inside the 2D square, not just a decorative overlay.

Behavior rules:

- the visible safe zone represents the area where the guided resolver would keep the selected color literal for the active field and current context
- areas outside that safe zone are visibly dimmed or veiled
- the drag handle cannot move into the dimmed region
- if the user drags toward an invalid point, the chosen position projects to the nearest allowed point on the guided boundary
- the live swatch in guided mode should reflect the actual allowed result, not a knowingly invalid color that will later be changed

The square itself remains the teaching surface for guided mode.

## Guided Text Entry Behavior

Guided mode should behave consistently whether the user drags or types.

Rules:

- once a typed value becomes complete and valid, guided resolution runs
- if the value is acceptable, it remains exactly as entered
- if the value is not acceptable, it rewrites to the guided-safe value
- both `HEX` and `RGB` update to that resolved value
- the swatch and picker handle move with it

If the guided system changes the value, the picker should show a small local cue near the inputs, for example `Adjusted for readability`.

The cue should be lightweight but clear. The goal is to make the adjustment feel intentional rather than mysterious.

## Advanced Mode

Advanced mode stays unrestricted in color choice, but it still adopts the new explicit save model.

Advanced therefore means:

- no guided safe-zone boundary
- no constrained drag projection
- no guided auto-adjust cue
- the current local picker value always stays literal
- save still happens only through the header check action

This preserves Advanced creative freedom while unifying the picker save semantics across modes.

## Save, Cancel, and Exit Contract

The picker should maintain one local dirty state based on whether the current local color differs from the color that was active when the picker opened.

Action rules:

- header check
  - saves the current local picker value
  - closes the picker
- header `X`
  - closes immediately if clean
  - opens a confirm dialog if dirty
- `Back`
  - closes immediately if clean
  - opens the same confirm dialog if dirty
- outside tap
  - disabled entirely

There should be one internal save path only. Header check and dialog `Save` must flow through the same commit logic so guided correction, field synchronization, and persistence semantics remain identical.

## Dirty Exit Dialog

The dirty-exit dialog should stay small and plain.

Recommended shape:

- title: `Save changes?`
- actions:
  - `Save`
  - `Discard`
  - `Keep editing`

Behavior:

- `Save` uses the same commit path as header check
- `Discard` closes the picker without saving local edits
- `Keep editing` dismisses only the dialog

This keeps `Back` and `X` honest without reintroducing accidental dismiss paths.

## Data And State Contract

This is still a picker/editor redesign, not a theme-model redesign.

- persisted theme colors remain `HEX` strings
- existing `ThemeEditorDraft` color fields remain `HEX`-backed
- existing `ThemeEditorColorEditResult` remains the commit path
- guided balancing remains owned by the current guided pipeline
- no DataStore key, settings contract, or schema change is required

Internally the picker now needs:

- one local last-valid color draft
- one local text-edit state for `HEX`
- one local text-edit state for the three `RGB` fields
- one shared dirty-state computation against the picker opening value

## File Shape Expectations

The picker file is already close to the repo's extraction threshold, so the expanded editor contract must stay surgical.

Expected placement:

- keep `SettingsThemeColorPicker.kt` as the dialog owner
- keep `ThemeColorPickerCanvas.kt` as the visual spectrum owner
- keep `ThemeColorPickerGuidance.kt` as the guided-safe-zone owner
- add one small feature-local helper file for text-entry parsing, formatting, and synchronization if needed

That helper should cover only:

- `HEX` parsing and formatting
- `RGB` parsing and formatting
- partial-input validation
- conversion helpers between `HEX`, `RGB`, and the existing picker color state

`SettingsThemeEditor.kt` should remain responsible only for launching the picker and receiving the chosen value, not for owning field-parsing or picker-dirty-state logic.

## Testing Expectations

JVM coverage should include:

- `HEX` parsing and formatting
- `RGB` parsing and formatting
- partial invalid input preserving the last valid preview state
- `HEX <-> RGB <-> picker-state` synchronization helpers
- guided typed-input resolution to a safe value when required
- dirty-state detection

Instrumentation coverage should include:

- header check saves
- header `X` closes immediately when clean
- header `X` opens the confirm dialog when dirty
- `Back` opens the confirm dialog when dirty
- outside tap does nothing
- typing valid `HEX` updates swatch and spectrum position
- typing valid `RGB` updates swatch and spectrum position
- guided mode shows the adjustment cue when typed input is corrected
- Advanced mode does not show guided cue or safe-zone UI

Manual verification should include:

1. valid `HEX` typing
2. valid `RGB` typing
3. drag after typing
4. guided auto-adjust from typed input
5. `Back -> Save`
6. `Back -> Discard`
7. `X -> Keep editing`

## Risks And Guards

- The biggest risk is still lying about guided behavior. Typed values and dragged values must both go through the real guided resolver.
- The second risk is over-eager text normalization that fights the user while they are mid-entry.
- The third risk is growing `SettingsThemeColorPicker.kt` into a second monolith now that the picker is becoming a mini editor.
- Outside tap must stay disabled. Reintroducing it would conflict with the explicit dirty-exit model.
- Because the current picker state is already color-model-backed, the new text inputs should synchronize with that state instead of creating a second competing source of truth.
