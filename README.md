# Vent

Vent is a Kotlin Multiplatform internal-event application for Android, iOS, tablet, and web, backed by Ktor and PostgreSQL.

## Build in GitHub Actions

Every push and pull request runs lint, tests, server distribution, Android APK, web distribution, and an iOS simulator framework build. A successful push to `main` deploys `server` to Railway and verifies `/api/v1/health`.

Local commands are available when needed:

```powershell
.\gradlew.bat build check
.\gradlew.bat :server:run
.\gradlew.bat :composeApp:assembleDebug
.\gradlew.bat :composeApp:wasmJsBrowserDistribution
```

## Configuration

Copy `.env.example` values into the shell or IDE run configuration. Never commit secrets. Production variables live in Railway and GitHub environment secrets.

## Modules

- `shared-domain`: pure Kotlin business models and rules.
- `shared-api`: serializable API contracts.
- `server`: Ktor routes, use cases, persistence, authentication, and migrations.
- `composeApp`: adaptive shared UI and platform entry points.
- `infra`: Railway deployment guidance.

## Local demo persistence

The shared UI uses a `VentLocalStore` repository. Android persists demo check-in, role, and seat state with Room in `vent-demo.db`; iOS uses `NSUserDefaults`; Wasm uses browser `localStorage`. Platform storage stays behind the common interface so screens and app state remain portable and testable.

## UI architecture

Vent follows MVVM with unidirectional data flow:

- `VentViewModel` owns immutable `StateFlow<VentUiState>` and processes `VentAction` intents.
- Navigation 3 uses the JetBrains multiplatform artifact across Android, iOS, and Wasm; Wasm also synchronizes the back stack with browser history.
- Feature composables render `VentUiState` and emit actions without reading databases directly.
- `VentLocalStore` is the repository boundary for Room and other platform persistence.
- The adaptive shell uses bottom navigation on compact windows and a navigation rail on landscape and expanded windows; seating becomes a two-pane workflow at 720 dp.
