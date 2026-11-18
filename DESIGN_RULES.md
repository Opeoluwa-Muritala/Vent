# Design Rules — Vent

> Read this file before any UI or UX work in this repository.

## Stack

- Kotlin Multiplatform and Compose Multiplatform with Material 3
- Kotlin 2.4.21, Compose 1.12.0, AGP 9.4.0
- Android minSdk 34 and targetSdk 37
- Extend mode: the Signal Hall direction in `DESIGN.md` remains authoritative

## Aesthetic Anchor

- Primary: Material You / Android Expressive
- Brand seed: electric teal (`#006B65`)
- Density: balanced
- Dark theme: required
- Dynamic wallpaper color: off

Vent owns its light and dark color schemes. Never derive colors from the device wallpaper or allow system personalization to replace Vent surfaces.

## Active Bans

- No generic gradients.
- No filler copy or generic calls to action.
- No arbitrary dimensions, colors, shapes, or typography in composables.
- No glass or blur decoration.
- No nested cards.
- No generic feature-card layouts.
- No decorative looping motion.

## Token Sources

| Concern | Source |
| --- | --- |
| Color schemes | `frontend/composeApp/src/commonMain/kotlin/app/vent/ui/theme/Color.kt` |
| Typography | `frontend/composeApp/src/commonMain/kotlin/app/vent/ui/theme/Type.kt` |
| Shapes | `frontend/composeApp/src/commonMain/kotlin/app/vent/ui/theme/Shape.kt` |
| Spacing and breakpoints | `frontend/composeApp/src/commonMain/kotlin/app/vent/ui/theme/Spacing.kt` |
| Theme wrapper | `frontend/composeApp/src/commonMain/kotlin/app/vent/ui/theme/VentTheme.kt` |

Use filled tonal surfaces for grouped content and ripple press feedback. Use Material Symbols Outlined, with Filled reserved only for selected navigation state. Android touch targets are at least 48 dp.

## Adaptive Behavior

Use bottom navigation on compact phones, a navigation rail on wider windows, and multi-pane layouts when related content benefits from simultaneous visibility. Preserve state through rotation, resizing, and folding. Never lock orientation.

## Verification

Run `./gradlew detekt detektDesign lint` before release. During this rebuild, Gradle execution is deferred at the user’s request; static audits still apply.
