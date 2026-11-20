# Vent Flask backend

Independent Python 3.13 API in `backend/python/`, backed by Neon PostgreSQL. Routes use the camelCase JSON contracts in `shared-api/`. Tables have a `vent_py_` prefix so they can coexist with the Ktor server. Existing Ktor data is **not** automatically migrated. The frontend must point at this service's base URL; no frontend source changes are included here.

## Setup on Windows

From the repository root:

```powershell
py -3.13 -m venv backend/python/.venv
backend/python/.venv/Scripts/python.exe -m pip install -r backend/python/requirements-dev.txt
Copy-Item backend/python/.env.example backend/python/.env
```

Edit `backend/python/.env`:

- Set `DATABASE_URL` to your Neon connection string. Keep `sslmode=require` or stricter and `channel_binding=require` from the Neon console. Both pooled and direct Neon URLs are supported. Use a direct connection for migration administration when possible.
- Generate `SECRET_KEY` with `python -c "import secrets; print(secrets.token_hex(32))"`. Keep it stable across workers and deployments.
- Set `ORGANIZER_EMAILS` to the email addresses permitted to create their first event. There is no public role escalation endpoint. Creating an event assigns its creator the organizer membership for that event.
- Set `ALLOWED_ORIGINS` to comma-separated browser origins. Native clients use bearer tokens without an Origin header.
- `EMAIL_SERVICE_URL` defaults to `https://emailope.vercel.app/`, matching FieldCRM. The server POSTs JSON `{to, subject, text, html}` with a 15-second timeout. **No email token or Authorization header is used.** Redirects are rejected, and failed delivery returns 503. Codes are never logged or returned by the API.

Run migrations and start the API from its folder:

```powershell
Set-Location backend/python
.venv/Scripts/python.exe -m alembic upgrade head
.venv/Scripts/python.exe -m flask --app wsgi:app run --host 127.0.0.1 --port 8000
```

For deployment, set environment variables through your host and use a production WSGI server behind HTTPS:

```powershell
.venv/Scripts/waitress-serve.exe --host=0.0.0.0 --port=8000 wsgi:app
```

Migrations are explicit; starting the service never creates tables. Run them once before rolling out application workers. The frozen `migrations/initial_schema.py` records the first schema; future schema changes require new Alembic revisions.

## API workflow

All paths below begin with `/api/v1`. Authenticated calls require `Authorization: Bearer <accessToken>`; cookies are not used.

1. `POST /auth/otp/request` with `{email}`; `POST /auth/otp/verify` with `{email, code}`. Successful verification creates a user if needed. OTPs expire after ten minutes, allow five attempts, and have a 30-second resend delay. Requests are limited per remote IP in the database. Configure your edge's rate limits too, particularly when your proxy shares one remote IP across users.
2. `POST /auth/refresh` with `{refreshToken}` rotates the refresh token and revokes the old session. Access tokens last 15 minutes; refresh sessions last 30 days. `POST /auth/logout` revokes the current session immediately.
3. An allowlisted organizer creates an event with `POST /events` using `{title, startsAt, endsAt, seedArgb}`. Times require timezone-bearing ISO-8601 strings; `seedArgb` is eight hexadecimal digits. Optional HTTPS `coverImageUrl` is validated but image hosting is external.
4. `POST /events/{id}/attendees/import` accepts `{attendees: [{name, email}]}` (up to 500). It creates missing accounts and event memberships. `GET /events/{id}/attendees` lists members for organizers/staff. Role administration is intentionally restricted to trusted database administration.
5. `POST /events/{id}/halls` accepts `{name, width, height, seats, elements}`. Layout fields follow `HallDto`/`SeatDto`. `GET /halls/{id}` reads it; `POST /halls/{id}/layout` saves `{width, height, seats, elements}`. Existing bookings prevent layout replacement.
6. `POST /sessions` accepts `CreateSessionRequest`. The hall must belong to the same event. `GET /events` and `GET /events/{id}` return event schedules. `PUT /events/{id}/seed` accepts `{seedArgb}`.
7. `POST /sessions/{id}/assignments/preview` accepts `{attendeeIds}`; commit its `{previewId, baseVersion}` at `/assignments/commit`. Previews expire after five minutes, belong to their organizer and session, and stale versions return 409. Per-session row locks and booking uniqueness prevent duplicate seats. `GET /sessions/{id}/seats?since=...` returns authoritative snapshots; matching versions return no seats. `POST /sessions/{id}/seats/{seatId}/unassign` releases an unchecked booking.
8. Organizers/staff obtain short-lived screen codes at `GET /sessions/{id}/checkin-codes/current`. Organizers rotate printed codes at `POST /sessions/{id}/printed-code/rotate`; rotation invalidates old printed codes. Attendees call `POST /sessions/{id}/checkin` with `{token, deviceId, clientTimestamp}`. Assigned seats and the server check-in window are enforced. Repeated check-in awards zero additional points. Staff may use `/checkin/manual` with a database booking UUID as `{bookingCode}`.
9. `GET /events/{id}/pass` returns a five-minute event pass. Staff verify it at `POST /events/{id}/passes/verify` with `{token}`. Passes and check-in codes are separate token purposes and are not interchangeable.
10. `GET/POST /sessions/{id}/polls`, `POST /polls/{id}/answer`, `POST /polls/{id}/close`, and `GET /polls/{id}/results` manage polls. Answers are unique per user and poll. Closed polls reject votes.
11. `GET/POST /events/{id}/quests` supports `SESSIONS_CHECKED_IN` and `POLLS_ANSWERED`. Create with `{type, target, points, badgeId}`. Progress, badges, leaderboard, wrapped cards, and occupancy derive from persistent attendance and votes. `GET /events/{id}/leaderboard`, `GET /events/{id}/wrapped/me`, `POST /events/{id}/wrapped/generate`, and `GET /events/{id}/occupancy` expose them. Wrapped data is computed live; generate persists earned quest awards and returns 202.
12. `GET /me`, `GET/PUT /me/preferences`, and `GET /health` provide role, preferences, and database readiness.

## Verify

From the repository root (no Gradle required):

```powershell
backend/python/.venv/Scripts/python.exe -m pytest backend/python/tests -q
backend/python/.venv/Scripts/python.exe -m ruff check --config backend/python/pyproject.toml backend/python
backend/python/.venv/Scripts/python.exe -m ruff format --check --config backend/python/pyproject.toml backend/python
```

Tests use isolated SQLite databases and a fake email sender; they never send real emails or require Neon credentials. PostgreSQL-specific concurrency behavior still needs a staging Neon check. Configuration must be supplied before running against Neon. This backend does not yet provide push notifications, image storage, background jobs, or live seating subscriptions; seat updates use snapshot polling.

References: [Flask application factories](https://flask.palletsprojects.com/en/stable/patterns/appfactories/), [Neon connection pooling](https://neon.com/docs/connect/connection-pooling), [SQLAlchemy PostgreSQL connections](https://docs.sqlalchemy.org/en/20/dialects/postgresql.html).
