# Railway deployment

Create a Railway project with a PostgreSQL service and an empty service named `vent-api`. Connect the GitHub repository or deploy through the CI project token.

Set these production variables on `vent-api`:

- `DATABASE_URL`: `${{Postgres.DATABASE_URL}}` when the database service is named `Postgres`. Enter this as the value, in the Variables tab of `vent-api`.
- `JWT_SECRET`: at least 32 cryptographically random bytes.
- `ALLOWED_ORIGINS`: comma-separated frontend origins, for example `http://localhost:8080,https://vent.app` if the browser frontend is served at those addresses. Match the scheme and port shown in the browser. Native Android does not need a CORS entry.
- `VENT_DEMO_OTP_LOGGING`: `false` on public deployments. The current demo sender does not deliver email.
- `PORT`: supplied by Railway.

Keep the service root at the repository root. The Railway build uses `sh ./gradlew -p server installDist`, which works even if the wrapper lacks the executable bit. The standalone server build uses the JDK running Gradle. You can keep `RAILPACK_JDK_VERSION=17` or choose a supported newer version such as `21`.

GitHub production secrets:

- `RAILWAY_TOKEN`: environment-scoped Railway project token.
- `RAILWAY_PROJECT_ID`: target project ID.
- `RAILWAY_SERVICE`: `vent-api` or its service ID.
- `VENT_API_URL`: public HTTPS URL without a trailing slash.

The CI deployment runs only after Ubuntu and macOS verification jobs pass. Flyway applies forward-only migrations during service startup before Ktor accepts traffic. The health probe must return `{"status":"ok"}`.
