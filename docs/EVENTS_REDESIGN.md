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

- The repository has no iOS application host, Info.plist, or launch asset
  catalog. The iOS launch screen must be connected in that host on macOS.
  The existing iOS camera implementation remains a manual-code fallback.
- The current UI model does not expose a session lifecycle or authenticated
  pass QR payload. Live/upcoming/ended variants, a real scannable pass, brightness
  boost, reminder scheduling, and rating submission still need their product
  integrations. Do not substitute fabricated pass codes or session status.
- Android camera torch control uses the device's actual torch state, handles
  missing flash hardware and failed requests, and turns off on disposal.
  Camera permission is rechecked when returning from system settings.
- Seat map rendering still uses Compose seat controls rather than the specified
  cached Canvas layer. Find-seat currently uses the existing selection action;
  precise zoom-centroid and automatic map positioning need further work.
- Pass-preview parity and the prescribed expansion transitions need another
  pass. Session forms include the event hall dropdown. Quest options use editable chips, and
  preference edits are applied only when Save is pressed.
- Replace existing poster and conference photos with individually licensed
  commercial-use assets; speaker images require consent. Photos have not been
  replaced, license-verified, or compressed as part of this change.
- The logo mark is supplied; an outlined Inter Medium wordmark has not been
  supplied. The application currently renders the wordmark as text.

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
