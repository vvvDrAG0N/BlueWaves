# UI & Styling Rules

Blue Waves follows a "Content-First" design philosophy, especially within the reader interface.

Path note after Phase 1 refactor:
- Reader UI logic remains in `feature/reader/ReaderScreen.kt`
- Shared library presentation components now live in `core/ui/LibraryCards.kt`

## 1. Global Layout
- **Forced LTR**: To ensure consistent scrolling and gesture behavior, the entire app (or at least the Navigation and Reader) is wrapped in `LocalLayoutDirection provides LayoutDirection.Ltr`.
- **Edge-to-Edge**: The app uses `enableEdgeToEdge()` with system bars hidden or in transient mode during reading.

## 2. Reader Themes
The reader does not use standard Material 3 Surface colors for its background. Instead, it uses an explicit `ReaderTheme` model:
- **Light**: Background `#FFFFFF`, Foreground `#000000`
- **Sepia**: Background `#F4ECD8`, Foreground `#5B4636`
- **Dark**: Background `#121212`, Foreground `#FFFFFF`
- Custom themes may define their own reader background/foreground pair, but those colors must come from the same saved palette that drives the app-level Material theme in `MainActivity`.
- Library, settings, and drawer surfaces should stay on the Material theme path; only reader content surfaces should use the explicit reader foreground/background pair.

## 3. Typography
- **Primary Font**: `Karla` (defined in `ReaderScreen.kt`) is used for a modern, readable feel.
- **Dynamic Sizing**: Font size is user-adjustable (12sp - 32sp).
- **Line Height**: Configurable between 1.2x and 2.0x of the font size.
- **Headers**: Tags like `h1`-`h6` are rendered with `headlineSmall` style, bold weight, and a `+4sp` size modifier.

## 4. Component Conventions
- **Reader Controls**: 
    - Contained in a `Card` with `0.98f` alpha to show a hint of content behind.
    - Uses `navigationBarsPadding()` to avoid overlap with system nav.
- **Interactive Elements**:
    - **Taps**: A single tap on the reader content toggles the visibility of `TopAppBar` and `ReaderControls`.
    - **Buttons**: Theme selection buttons are circular with a `3.dp` border when selected.
- **Library Grid**: Uses `GridCells.Adaptive(140.dp)` to ensure a consistent look across different screen sizes.

## 5. Visual Feedback
- **Overscroll**: A floating `Surface` (black with 0.6 alpha) appears at the top/bottom of the screen to indicate "Pull for next/previous chapter".
- **Scroll to Top**: A `FloatingActionButton` appears only when the user scrolls up and is not at the very top.
