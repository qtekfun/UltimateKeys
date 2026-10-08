<!--
SPDX-FileCopyrightText: 2026 UltimateKeys contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# ADR 0021: Settings design system, insets and navigation

Status: accepted

## Context

The app screens had grown one by one: the home screen was a single long column (setup steps, a sample field,
eleven buttons, every setting), each activity drew its own back button, and the styles gallery and the
licenses screen padded for the status bar by hand while the other screens did not, so the settings title was
drawn under the status bar on Android 15 and later (the platform draws apps edge to edge there). The owner
asked for a calmer, consistent look: grouped lists, a large collapsing title, and every setting still
reachable.

## Decisions

1. **A small design system in its own module, `:ui`.** Stateless components that take plain values and lambdas:
   `UkScreen` (bar, collapsing large title, list), `UkGroup` (rounded card over a tinted page, header above,
   footer below, hairline separators inset from the leading edge), rows (`UkRow`, `UkNavRow`, `UkSwitchRow`,
   `UkSliderRow`, `UkSegmentedRow`, `UkPickerRow`, `UkValueRow`, `UkActionRow`, `UkContentRow`), controls
   (`UkSwitch`, `UkSegmented`, `UkTextField`) and a few original line icons drawn on a canvas (`UkGlyph`), so no
   icon font or library is needed. The module has no strings (callers pass text, so the translation check stays
   in `:app`) and no dependency that was not already in the build. The old helpers (`Heading`, `LabeledSlider`,
   `LabeledSwitch`, `MinTouchTarget`) were migrated into it rather than dropped; their semantics are kept (see 5).
2. **Colours come from the Material You scheme.** `UkColors.from(colorScheme)` derives the page (a tinted
   surface container), the card (lighter in the light theme, lifted in the dark one), separators and text roles
   from the same scheme the app already used, so the screens follow the system light and dark setting and the
   wallpaper colours. Icon badges use the primary, secondary and tertiary containers. Tests pass a fixed scheme.
3. **Insets are handled in one place.** Every activity calls `setUkContent`, which enables edge to edge and
   hosts the screen. `UkScreen` takes a `ScreenInsets` (system bars plus display cutout, and the on-screen
   keyboard separately): the bar sits below the status bar, the sides respect cutouts, the list stops above the
   keyboard and its last row clears the navigation bar by the list's bottom padding. The default is the live
   window insets; tests (`SettingsScreenTest`) pass fixed values and assert the title is below the status bar,
   the last row clears the gesture bar, and the list ends above the keyboard.
4. **Navigation is state, not fragments.** `MainActivity` shows a home list of groups (Setup, Typing,
   Suggestions, Gestures, Feedback; Appearance, Dictation, Clipboard and emoji, Privacy; About). Rows open
   sub-screens of the same activity through an immutable `RouteStack` (opening a screen already in the stack
   returns to it, so two screens that link to each other cannot pile up), saved across rotation as the route
   names, with the system back and the bar's back button popping it. Appearance (the styles gallery and editor),
   Dictation (settings and models), My dictionary and Licenses stay activities of their own: the keyboard
   opens some of them directly. The style editor is the same pattern inside: an overview of its groups, each
   group a sub-screen, with the live keyboard preview pinned under the bar on all of them.
5. **Accessibility is kept, and moved into the components.** Every row is one focus stop with a role (button,
   switch, radio button, dropdown) and its state (checked, selected, value in its own unit as the state
   description of a slider); group headers and the large title are headings; the decorative chevrons, badges and
   swatches are hidden from screen readers; touch targets are at least 48 dp; text is in sp and wraps, so large
   system fonts grow the rows instead of cutting them. Choices of two to four short options are a segmented
   control (`ChoicePresentation`), anything longer a picker row.
6. **Every existing setting stays reachable and keeps its meaning.** Slider rows show the value with its unit
   at the end (`ValueFormat`), so the unit left the labels (the Spanish and English strings were changed
   together). The letter layout, which only the language key could change before, is now a picker in Typing.
7. **Tests.** Pure logic (`RouteStack`, `HomeModel`, `ValueFormat`, `TitleCollapse`, `ChoicePresentation`) is
   unit tested; the screens are rendered under Robolectric in `:app` (the layout and semantics tests and the
   golden screenshots in `app/src/test/snapshots`, recorded with `-Proborazzi.test.record=true`) rather than in
   `:screenshots`, because they need the app's real strings and resources. Compose glue is excluded from the
   coverage gate like the other screens.

## Consequences

- A new screen is a `UkScreen` with groups; it cannot forget the insets or the back button.
- The home screen no longer shows the app version; it is in About.
- The style gallery opens a menu per style (edit, duplicate, export, share, delete) instead of a row of buttons.
- The Robolectric tests run in both flavors (`testFullDebugUnitTest` and `testLiteDebugUnitTest`) against the
  same goldens; they do not touch flavor-specific code.
