# UI And Styling Rules

Blue Waves is content-first, especially inside the reader.

## Global Layout

- Keep forced `LayoutDirection.Ltr` for stable reader gestures and scrubber behavior.
- Keep edge-to-edge behavior intact for reading surfaces.

## Reader Themes

- Reader content surfaces do not use default Material surface colors.
- Reader background/foreground must come from the explicit reader theme model.
- Library, settings, and drawer surfaces stay on the Material theme path.

Built-in reader themes:
- Light: `#FFFFFF` / `#000000`
- Sepia: `#F4ECD8` / `#5B4636`
- Dark: `#121212` / `#FFFFFF`

## Typography

- Reader typography uses the Karla family.
- Font size and line height remain user-adjustable.
- Header rendering should stay visually distinct from body text.

## Component Conventions

- Reader controls stay layered over content and must not block the whole reading surface unnecessarily.
- A single tap on reader content toggles reader chrome.
- Theme selection affordances should make the current selection obvious.
- Library grids should remain adaptive rather than hard-coded to one device width.

## Visual Feedback

- Overscroll feedback should clearly communicate next/previous chapter intent.
- Scroll-to-top affordances should appear only when they are genuinely useful.
