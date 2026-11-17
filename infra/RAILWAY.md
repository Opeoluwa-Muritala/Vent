# Railway deployment

Create a Railway project with a PostgreSQL service and an empty service named `vent-api`. Connect the GitHub repository or deploy through the CI project token.

Set these production variables on `vent-api`:

- `DATABASE_URL`: reference the Railway PostgreSQL connection URL.
- `JWT_SECRET`: at least 32 cryptographically random bytes.
- `ALLOWED_ORIGINS`: comma-separated HTTPS origins for approved web clients.
- `PORT`: supplied by Railway.

GitHub production secrets:

- `RAILWAY_TOKEN`: environment-scoped Railway project token.
- `RAILWAY_PROJECT_ID`: target project ID.
- `RAILWAY_SERVICE`: `vent-api` or its service ID.
- `VENT_API_URL`: public HTTPS URL without a trailing slash.

The CI deployment runs only after Ubuntu and macOS verification jobs pass. Flyway applies forward-only migrations during service startup before Ktor accepts traffic. The health probe must return `{"status":"ok"}`.