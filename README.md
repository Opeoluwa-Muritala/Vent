# Vent

Vent is a conference companion for attendees and organizers. Attendees browse the agenda, reserve seats, check in, and take part in quests. Organizers configure the event, manage attendees, plan seating, and run session controls.

**Project submission:** [Opeoluwa-Muritala/Vent on GitHub](https://github.com/Opeoluwa-Muritala/Vent)

The app uses Kotlin **2.4.21**, Compose Multiplatform, and a Ktor API backed by PostgreSQL. Android phone and tablet share the same APK. The web target uses Kotlin/Wasm, and the iOS target builds a native Compose framework.

## Demo video and Android APK

- [Watch the Android organizer demo](docs/vent-android-demo.mp4). The recording shows the session agenda, clock picker, quest editor, occupancy view, and event setup on a physical Android phone.
- [Download the Android debug APK from GitHub Actions](https://github.com/Opeoluwa-Muritala/Vent/actions/workflows/ci.yml). Open a successful workflow run, download **vent-android-apk**, and install the extracted `androidApp-debug.apk` with `adb install -r androidApp-debug.apk`. GitHub may require sign-in to download workflow artifacts.
- If you have the APK, install it with `adb install -r androidApp-debug.apk` and use the offline demo accounts below.

## Choose a launch path

| Target | What to launch | Sign-in |
| --- | --- | --- |
| Android phone or tablet | Debug APK on Android 14 or newer | Built-in offline demo accounts |
| Web | Wasm development server in a current Chrome, Edge, Firefox, or Safari | Seeded accounts with a local Ktor server |
| iOS | `VentKit.framework` in a SwiftUI host on macOS | Seeded accounts with a local Ktor server |
| Ktor API | Standalone Gradle build with PostgreSQL | Demo OTPs are available from local server logs |

**Reviewers:** The Android debug demo works without a backend, third-party API keys, or an email provider. The web and iOS source builds require the local backend steps below. The repository currently provides one setup-free platform, so the requirement for two setup-free platforms is still open.

## Get the source

```sh
git clone https://github.com/Opeoluwa-Muritala/Vent.git
cd Vent
```

Use JDK 21 and the included Gradle wrapper. Use `./gradlew` on macOS or Linux and `.\gradlew.bat` in Windows PowerShell. Install Android SDK Platform 37 for the frontend Gradle build. Android Studio can install it through SDK Manager and generate `local.properties` for your SDK location. The first build downloads Gradle and dependencies.

## Android quick start

1. Connect an Android 14 or newer device with USB debugging enabled, or start an Android emulator on API 34 or newer.
2. From the repository root, install the debug build.

   ```sh
   ./gradlew :androidApp:installDebug
   ```

   On Windows use `.\gradlew.bat :androidApp:installDebug`. To build an APK without installing it, run `:androidApp:assembleDebug`. Gradle writes the APK to `frontend/androidApp/build/outputs/apk/debug/`.

3. Open **Vent**. Enter an email, choose **Send code**, and enter the matching on-device code.

| Role | Email | Android debug code |
| --- | --- | --- |
| Attendee | `attendee@vent.demo` | `000000` |
| Organizer | `organizer@vent.demo` | `123456` |

The debug codes are for the local demo. For builds that use server sign-in, set `BACKEND_URL` to the API origin before building. Android also accepts `VENT_MOBILE_BACKEND_URL` in your user-level Gradle properties, which takes precedence. The Android fallback is `http://10.0.2.2:8080` for the emulator. Use the public HTTPS API origin for a hosted build or a physical device.

## Local backend for web and iOS

Use Docker to start an isolated development database. If you already have PostgreSQL, create a development database and set the equivalent connection variables from [`.env.example`](.env.example).

```sh
docker run --name vent-postgres -e POSTGRES_DB=vent -e POSTGRES_USER=vent -e POSTGRES_PASSWORD=vent-local-only -p 127.0.0.1:5432:5432 -d postgres:16
docker exec vent-postgres pg_isready -U vent -d vent
```

Once PostgreSQL is ready, run these commands from the repository root. On later runs use `docker start vent-postgres` to reuse the database.

### macOS or Linux

```sh
export DATABASE_URL=jdbc:postgresql://localhost:5432/vent
export DATABASE_USER=vent
export DATABASE_PASSWORD=vent-local-only
export JWT_SECRET="$(openssl rand -hex 32)"
export VENT_DEMO_OTP_LOGGING=true
export ALLOWED_ORIGINS=http://localhost:8080,http://127.0.0.1:8080
export PORT=8081
./gradlew -p server seed
./gradlew -p server run
```

### Windows PowerShell

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/vent'
$env:DATABASE_USER = 'vent'
$env:DATABASE_PASSWORD = 'vent-local-only'
$ventSecret = New-Object byte[] 32
$ventRandom = [Security.Cryptography.RandomNumberGenerator]::Create()
$ventRandom.GetBytes($ventSecret)
$ventRandom.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($ventSecret)
$env:VENT_DEMO_OTP_LOGGING = 'true'
$env:ALLOWED_ORIGINS = 'http://localhost:8080,http://127.0.0.1:8080'
$env:PORT = '8081'
.\gradlew.bat -p server seed
.\gradlew.bat -p server run
```

Flyway migrates the database automatically. The seed creates a demo event, sessions, seats, and sign-in accounts. With the server running on port 8081, [the health endpoint](http://localhost:8081/api/v1/health) should return `{"status":"ok"}`.

For web or iOS sign-in, use `attendee@vent.demo` or `organizer@vent.demo`. Choose **Send code**, then copy the six-digit code printed in the Ktor terminal for that email. Keep demo OTP logging disabled on public deployments. The current demo sender uses logs for local testing and does not deliver email.

## Web

Leave the local backend running on **8081**. In a second terminal, start the Wasm development server.

```sh
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

On Windows use `.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun`. Open the URL printed by Gradle, typically [http://localhost:8080](http://localhost:8080). If the frontend uses a different origin, add that exact origin to `ALLOWED_ORIGINS` and restart Ktor.

To build static files for hosting, set `BACKEND_URL` to the API’s public HTTPS origin and run `:composeApp:wasmJsBrowserDistribution`. The output is `frontend/composeApp/build/dist/wasmJs/productionExecutable/`. See the [Railway deployment guide](infra/RAILWAY.md) for hosting settings.

## iOS simulator

On an Apple Silicon Mac with Xcode and an iOS simulator installed:

1. Start the local backend as above with `PORT=8080` for iOS. The iOS client uses `http://127.0.0.1:8080`. Stop the web dev server first if it uses that port.
2. Build the simulator framework.

   ```sh
   ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
   ```

3. Create an iOS SwiftUI App in Xcode. Add `frontend/composeApp/build/bin/iosSimulatorArm64/debugFramework/VentKit.framework` to the app target under **Frameworks, Libraries, and Embedded Content** and choose **Do Not Embed** for this static framework.
4. Set **App Transport Security Settings → Allow Local Networking** to **Yes** in the host target’s Info settings for local development. Replace the template `ContentView` with the following bridge.

   ```swift
   import SwiftUI
   import UIKit
   import VentKit

   struct ContentView: UIViewControllerRepresentable {
       func makeUIViewController(context: Context) -> UIViewController {
           MainViewControllerKt.MainViewController()
       }

       func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
   }
   ```

5. Select an iOS simulator in Xcode, run the host, and use a seeded account. This repository provides the framework and Kotlin entry point. The Xcode host is created in this step.

## Try the key features

### Attendee

1. Open **Sessions**, swipe between days, use Search, then open the keynote.
2. Open the **Seat Map**, choose an available seat, and confirm it. In the keynote demo, try manual check-in with `894205`. Android also accepts a QR code containing the six-digit session code when camera permission is granted.
3. Open **Quests** to explore available activities. In **Summit Pass**, open **Conference preferences** and change a preference.
4. Reopen the app to check device-local state. In an authenticated server session, bookmarks and preferences sync through the API when it becomes reachable.

### Organizer

1. Open **Sessions** and use **+** to create a session. Choose a day and use the clock pickers for the start and end times.
2. From **Events**, open **Manage attendees**. Add addresses or import a CSV. Open a session’s seating tools to edit the layout or auto-seat the attendee list.
3. Open **Occupancy** from Events. Select a hall and session to change the dot map and open the matching seat map.
4. From **Quests**, use **+** to create an action, poll, or quiz. For a quiz, enter the options and select the correct one.
5. Open **Settings** to set the event title, day count, or color accent. Swipe between Event Setup and Live Controls. On Android, event configuration is stored in Room.

The organizer occupancy view and seating workflows currently use local demo data. Server sign-in and preference sync use PostgreSQL. Staff verification, live poll controls, and the remaining event editing workflows are demo implementations.

## Project layout and checks

- [`frontend/composeApp`](frontend/composeApp) contains shared Compose screens and Android, iOS, and web implementations. Android uses Room, iOS uses `NSUserDefaults`, and web uses browser storage.
- [`frontend/androidApp`](frontend/androidApp) packages the Android app.
- [`server`](server) contains the Ktor API, database repositories, Flyway migrations, and the demo seed.
- [`shared-domain`](shared-domain) contains shared business rules, and [`shared-api`](shared-api) defines serialized API contracts.
- [`infra`](infra) contains deployment documentation.

```sh
./gradlew -p server test
./gradlew :androidApp:assembleDebug :composeApp:wasmJsBrowserDistribution
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 # macOS only
```

The [GitHub Actions workflow](.github/workflows/ci.yml) lists the CI checks and artifact paths.
