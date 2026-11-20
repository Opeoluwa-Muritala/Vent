# Events redesign implementation

Source: TechCon Summit Redesign Spec, November 20, 2026, and the supplied
`events_logo.svg` and `ic_events_mark.xml`. The later identity section takes
precedence over the earlier TechCon splash name.

## Implemented

- Events identity, Android launcher mark, native Android dark splash, and Web
  loading mark removed after the first Compose composition.
- Shared light/dark role colors, typography, shape tokens, success color, and
  seed-generated action/selection accents. Palette tests verify body-text
  contrast for the supplied seeds and extreme input colors.
- Shared Vector Drawable icon resources and resource mapping for all platforms.
- Poster login with a matching tinted card, six-digit code entry treatment,
  disabled-action explanations, and removal of the attendee-facing demo code.
- Code entry scrolls above the keyboard and shows one focused digit box in
  secondary tint with 8dp box spacing and 1dp outlines.
- Flat schedule cards, secondary day selection, bookmarks, labeled navigation,
  session hero layout, and a labeled New session FAB.
- Session creation sheet with time validation, speaker chips, image placeholder,
  and explanations for incomplete fields.
- Quest empty state, stored earned points, claimed status, type selection, and
  a points stepper defaulting to 10.
- Attendee initials fallback, stored profile statistics, preferences Save action,
  dietary information removed from the pass summary, and destructive sign out.
- Theme cards with three swatches, a two-second preview interval and explicit
  confirmation; raw color codes are no longer displayed.
- Attendee email empty state and add dialog guidance; demo poll banner and
  explicit demo stop/resume controls.
- Hall assignment progress text, blocked-seat warning, map thumbnail treatment,
  and removal of the unavailable anomaly panel.
- Seating editor sheet with Seats, Rows, and Publish controls, publish
  confirmation, row movement explanations, attendee seat-selection sheet, and
  map pinch zoom clamped to 1x–3x.

## Remaining specification work

This is not a claim of full completion of all 37 screen states.

- The iOS host is provided in `frontend/iosApp` with an XcodeGen definition,
  SwiftUI entry point, storyboard-free launch configuration, and a native
  AVFoundation scanner with permission recovery, torch control, and cleanup.
  An Xcode build and device validation still require macOS.
- Session lifecycle is organizer-controlled and persisted: starting a session
  ends the previous live session. Schedule/detail screens use upcoming, live,
  and ended states. Personal time-wheel reminders and session ratings persist
  per device. Reminder banners currently require the app to remain open.
  Pass issuance now checks event membership, returns a short-lived signed token,
  and provides an event-scoped staff verification endpoint. QRose renders the
  QR in shared Compose code. Tokens stay in memory and refresh every five minutes.
  Show to staff temporarily raises Android/iOS brightness and restores it when
  dismissed. Web requests fullscreen because browsers cannot set hardware brightness.
- Android camera torch control uses the device's actual torch state, handles
  missing flash hardware and failed requests, and turns off on disposal.
  Camera permission is rechecked when returning from system settings.
- Seat maps now use a cached bitmap for stage and venue labels with a separate
  Canvas seat layer. Zoom preserves the gesture centroid, hit testing resolves
  the nearest seat within a 22dp radius, and Find my seat centers the confirmed
  seat. A labeled seat list provides a keyboard/screen-reader alternative.
- Attendee pass and organizer preview now reuse the same layout. Until an
  authenticated pass code is available, the pass shows an explicit unissued
  state rather than a fabricated QR. Expansion transitions still need work.
  Session forms include the event hall dropdown. Quest options use editable chips, and
  preference edits are applied only when Save is pressed.
- Placeholder photographs have been removed. Login uses original geometric
  poster artwork, and conference imagery uses a 124KB licensed photograph by
  Teemu Paananen. Source and license records are in `assets/ASSET_SOURCES.md`.
  Speaker/attendee identity is shown as text or initials until consented photos
  are supplied. Photo resources use Compose's unqualified drawable directory.
- The Inter Medium wordmark is now outlined into SVG and shared Vector Drawable
  paths, so it no longer depends on a font installation. The font license and
  reproducible outlining script are included.

## Verification

An earlier Android/Web compilation and the shared-domain tests passed.
Later form, preference, and camera changes have not been compiled: Gradle
was stopped at the user's request and must not be restarted without permission.
XML resource parsing and Git whitespace checks passed.

Run from the repository root:

```powershell
.\gradlew.bat :composeApp:compileKotlinWasmJs :androidApp:assembleDebug :shared-domain:jvmTest --no-configuration-cache
```

The Android application minimum SDK is 34, so its native splash uses the
Android 12+ platform attributes. No pre-Android-12 compatibility dependency is
needed for the supported Android versions.

iOS compilation and device visual QA require macOS and an iOS host.
Android runtime visual QA requires a connected device or emulator.
