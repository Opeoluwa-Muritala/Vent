# Vent Design System

<!-- impeccable:design-schema 1 -->

## Current implementation source

The supplied Stitch exports in `.stitch-reference/` are the visual source of truth for Vent. The implemented set includes authentication states, attendee event detail, live QR scanning, organizer attendee management, CSV column mapping, and import validation. Runtime UI is native Compose Multiplatform; the exported HTML is reference material only.

The slate palette uses `#3B5B75` as the light primary, `#D0E4FA` as the primary container, `#53606C` as secondary, and `#F7F9FB` as the base surface. Screen code consumes these through `MaterialTheme.colorScheme` rather than raw values.

## MVI architecture

Every feature screen owns an immutable `State`, sealed `Intent`, optional one-time `Effect`, and a feature-scoped ViewModel. Composables render state and emit intents; they do not mutate workflow state locally. The shared `MviViewModel` exposes a `StateFlow`, a buffered effect flow, and reducer helpers.

Feature presentation code is grouped by responsibility:

- `event/`: event-detail state and intents.
- `scanner/`: camera/torch state and successful-scan effect.
- `imports/`: attendee list, mapping, and review workflow state.
- `architecture/`: reusable MVI base only.

Reusable UI primitives must use neutral responsibility names such as `DetailLine`, `StatusChip`, `ScannerFrame`, or `ImportScaffold`. Do not prefix a reusable component with a screen or feature name. Screen entry points may retain descriptive screen names.

## Future design updates

The company-event companion exports supply the two-day schedule, seat navigator, quests, Wrapped recap, profile, scanner outcomes, and staff console. Use the active seat and check-in state wherever a reference shows example placement. The offline console uses sample attendance and poll counts. Its five destinations are door scanning, occupancy, poll controls, event setup, and projector display.

Match the compact 390 × 844 composition first, then recompose at the 720 dp detail and 840 dp expanded breakpoints. Reuse semantic colors, `MaterialTheme.spacing`, typography, and shapes. For every new screen, document and implement loading, empty, error, success, disabled, and offline states where applicable; add light/dark previews; verify safe areas, keyboard insets, 200% text scaling, and minimum touch targets.

## Direction

**Signal Hall** turns venue operations into a calm spatial instrument. Curved seating geometry remains Vent’s signature, while deep ink structure, warm paper surfaces, electric teal selections, and amber time signals replace the previous generic purple Material treatment.

The interface operates before it decorates: one obvious action, compact live-state language, and geometry that explains location or progress at a glance.

## Color Roles

- `primary`: electric teal for selected seats, active navigation, and primary actions.
- `secondary`: ink blue for operational summaries and supporting structure.
- `tertiary`: amber for live, time-sensitive, or attention states—not errors.
- `surface`: warm near-white in light mode and blue-black in dark mode.
- `error`: stable red reserved for blocked, invalid, or failed states.
- Available seats are outlined; selected seats are filled; occupied and blocked seats use distinct fill, outline, and text labels.

All screens consume Material semantic roles rather than raw feature-level colors. Light and dark schemes are independently specified.

## Foundations

- Use an 8-point spacing grid with 4-point optical adjustments.
- Compact layouts begin at 375 dp. At 840 dp, navigation becomes a rail and content recomposes into wider or list-detail arrangements.
- Never lock orientation. React to the available window in portrait, landscape, split-screen, tablet, foldable, iOS, and web contexts.
- Use platform-native type through the Material 3 role scale. Headlines are bold; titles are semibold; body copy remains regular.
- Use 10 dp small, 16 dp medium, and 24 dp large corner radii.
- Use tonal surfaces for depth. Do not combine borders and shadows on the same container.

## Components and Interaction

- Navigation uses a bottom bar on compact widths and a rail on expanded widths.
- Session rows are full-width tonal surfaces; live sessions use the primary container.
- Seat selection is direct manipulation: tap an outlined available seat, review the filled pending state, then confirm from the lower action area.
- Disabled, occupied, or blocked seats fail closed and cannot be selected.
- Quest and metric surfaces emphasize progress and values without decorative card grids.
- Touch targets are at least 48 dp on Android and 44 pt on iOS.

## Accessibility and Motion

Meet WCAG AA contrast, preserve system font scaling, label interactive geometry for assistive technology, and never communicate state with color alone. Use shared-axis or fade-through transitions for navigation; reduced-motion settings use crossfades or immediate state changes.
