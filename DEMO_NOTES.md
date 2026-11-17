# Demo Notes

## Implemented

- Kotlin Multiplatform modules for pure domain logic, API contracts, shared Compose UI, Ktor, iOS framework, Android, and Wasm.
- Adaptive attendee and organizer UI with venue map, schedule, quests, leaderboard, profile, and occupancy surfaces.
- PostgreSQL/Flyway schema, Argon2id login, short-lived JWT access tokens, rotating hashed refresh tokens, per-event role checks, and signed session check-in tokens.
- Contract routes for events, halls, sessions, assignments, check-in, polls, quests, leaderboard, Wrapped, and occupancy.
- GitHub Actions builds Android, iOS, web, shared modules, and server before deploying the backend to Railway.

## Verification still required

- The user requested no local Gradle execution. Compilation, lint, tests, APK/framework creation, and dependency compatibility must therefore be confirmed by GitHub Actions.
- Android CameraX scanning still needs its device implementation; the current shared UI presents the scan action and the API supports signed tokens.
- iOS and web intentionally use manual token entry for the demo scope.
- Most demo feature routes expose deterministic demo state while the schema and secure authentication use PostgreSQL. Replace remaining demo-state mutations with Exposed repositories before a production launch.
- Native screenshots and performance measurements require Android hardware/emulators and macOS simulators.
- Local Git commits are blocked because this workspace exposes `.git` as read-only after initialization. Run `git add -A && git add -f .editorconfig .gitattributes && git commit -m "M 0: repository and tooling"` when Git metadata is writable.