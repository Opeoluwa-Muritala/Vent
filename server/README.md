# Vent backend

The server has its own Gradle settings. From the repository root, use these commands with PostgreSQL configured and a supported JDK, such as 17 or 21. The standalone build uses the JDK running Gradle. Run the resulting distribution with that Java version or a compatible newer one.

```powershell
.\gradlew.bat -p server run
.\gradlew.bat -p server test
.\gradlew.bat -p server installDist
```

On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`. The server build uses the repository's wrapper, version catalog and shared contracts. It does not configure the Android app or Compose project. Keep those shared directories alongside `server/` when checking out or packaging the source.

Railway builds from the repository root and starts `server/build/install/server/bin/server`. See the root environment template for server configuration.
