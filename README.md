# Vent

Vent is a Kotlin Multiplatform internal-event application for Android, iOS, tablet, and web, backed by Ktor and PostgreSQL.

## Build in GitHub Actions

Every push and pull request runs lint, tests, server distribution, Android APK, web distribution, and an iOS simulator framework build. A successful push to `main` deploys `server` to Railway and verifies `/api/v1/health`.

Local commands are available when needed:

```powershell
.\gradlew.bat build check
.\gradlew.bat -p server run
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :composeApp:wasmJsBrowserDistribution
```

For local OTP demos, run the backend with `VENT_DEMO_OTP_LOGGING=true`. Android emulators connect to `10.0.2.2:8080`; the Wasm client expects the backend on port `8081` so it does not collide with the web development server.

Set `BACKEND_URL` to the backend's public HTTPS origin before building Android or Wasm for a hosted deployment. Use the public origin without `/api/v1`.

To configure Android separately, set `VENT_MOBILE_BACKEND_URL` in your user-level Gradle properties file (typically `%USERPROFILE%\.gradle\gradle.properties` on Windows). It takes precedence over `BACKEND_URL` for Android. The URL is embedded in the app. Server credentials belong in server environment variables.

## Configuration

Copy `.env.example` values into the shell or IDE run configuration. Never commit secrets. Production variables live in Railway and GitHub environment secrets.

## Modules

- `shared-domain`: pure Kotlin business models and rules.
- `shared-api`: serializable API contracts.
- `server`: Ktor routes, use cases, persistence, authentication, and migrations.
- `frontend/composeApp`: shared UI and Android/iOS/Wasm platform implementations.
- `frontend/androidApp`: Android entry point and APK packaging. Gradle task paths remain `:composeApp` and `:androidApp`.
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
