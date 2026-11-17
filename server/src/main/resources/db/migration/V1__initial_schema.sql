CREATE TABLE users (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
  email VARCHAR(254) NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT users_email_ci_unique UNIQUE (email)
);
CREATE TABLE events (id UUID PRIMARY KEY, title VARCHAR(160) NOT NULL, starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL, seed_argb BIGINT NOT NULL, cover_image_url TEXT, published BOOLEAN NOT NULL DEFAULT FALSE, CHECK (ends_at > starts_at));
CREATE TABLE user_roles (user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE, role VARCHAR(20) NOT NULL CHECK (role IN ('ATTENDEE','ORGANIZER','STAFF')), PRIMARY KEY(user_id,event_id));
CREATE TABLE halls (id UUID PRIMARY KEY, event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE, name VARCHAR(120) NOT NULL, width DOUBLE PRECISION NOT NULL CHECK(width > 0), height DOUBLE PRECISION NOT NULL CHECK(height > 0));
CREATE TABLE hall_elements (id UUID PRIMARY KEY, hall_id UUID NOT NULL REFERENCES halls(id) ON DELETE CASCADE, kind VARCHAR(30) NOT NULL, label VARCHAR(120) NOT NULL, x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, width DOUBLE PRECISION NOT NULL, height DOUBLE PRECISION NOT NULL);
CREATE TABLE seats (id UUID PRIMARY KEY, hall_id UUID NOT NULL REFERENCES halls(id) ON DELETE CASCADE, label VARCHAR(40) NOT NULL, x DOUBLE PRECISION NOT NULL, y DOUBLE PRECISION NOT NULL, rotation DOUBLE PRECISION NOT NULL DEFAULT 0, type VARCHAR(20) NOT NULL, UNIQUE(hall_id,label), UNIQUE(hall_id,x,y));
CREATE TABLE sessions (id UUID PRIMARY KEY, event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE, hall_id UUID NOT NULL REFERENCES halls(id), title VARCHAR(160) NOT NULL, starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL, checkin_opens_at TIMESTAMPTZ NOT NULL, checkin_closes_at TIMESTAMPTZ NOT NULL, seat_version BIGINT NOT NULL DEFAULT 0, CHECK(ends_at > starts_at));
CREATE TABLE bookings (id UUID PRIMARY KEY, session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, seat_id UUID NOT NULL REFERENCES seats(id), user_id UUID NOT NULL REFERENCES users(id), checked_in_at TIMESTAMPTZ, checkin_device_id VARCHAR(128), checkin_code_type VARCHAR(20), UNIQUE(session_id,seat_id), UNIQUE(session_id,user_id));
CREATE TABLE session_secrets (session_id UUID PRIMARY KEY REFERENCES sessions(id) ON DELETE CASCADE, hmac_key BYTEA NOT NULL, printed_epoch INTEGER NOT NULL DEFAULT 1);
CREATE TABLE polls (id UUID PRIMARY KEY, session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, question VARCHAR(300) NOT NULL, options_json JSONB NOT NULL, status VARCHAR(10) NOT NULL CHECK(status IN ('OPEN','CLOSED')), created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE poll_answers (poll_id UUID NOT NULL REFERENCES polls(id) ON DELETE CASCADE, user_id UUID NOT NULL REFERENCES users(id), option_index INTEGER NOT NULL CHECK(option_index >= 0), answered_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(poll_id,user_id));
CREATE TABLE points_ledger (id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id), event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE, source VARCHAR(80) NOT NULL, source_id UUID, amount INTEGER NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(user_id,event_id,source,source_id));
CREATE TABLE badges (id UUID PRIMARY KEY, event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE, name VARCHAR(100) NOT NULL, description VARCHAR(300) NOT NULL);
CREATE TABLE quests (id UUID PRIMARY KEY, event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE, type VARCHAR(50) NOT NULL, target INTEGER NOT NULL CHECK(target > 0), points INTEGER NOT NULL CHECK(points >= 0), badge_id UUID NOT NULL REFERENCES badges(id));
CREATE TABLE quest_completions (quest_id UUID NOT NULL REFERENCES quests(id) ON DELETE CASCADE, user_id UUID NOT NULL REFERENCES users(id), completed_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(quest_id,user_id));
CREATE TABLE user_badges (badge_id UUID NOT NULL REFERENCES badges(id) ON DELETE CASCADE, user_id UUID NOT NULL REFERENCES users(id), earned_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(badge_id,user_id));
CREATE TABLE wrapped_cards (event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE, user_id UUID NOT NULL REFERENCES users(id), payload_json JSONB NOT NULL, generated_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(event_id,user_id));
CREATE TABLE refresh_tokens (id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, token_hash CHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL, revoked_at TIMESTAMPTZ, replaced_by UUID REFERENCES refresh_tokens(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE audit_log (id UUID PRIMARY KEY, actor_user_id UUID REFERENCES users(id), event_id UUID REFERENCES events(id), action VARCHAR(80) NOT NULL, resource_type VARCHAR(50) NOT NULL, resource_id UUID, metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE layout_versions (hall_id UUID NOT NULL REFERENCES halls(id) ON DELETE CASCADE, version BIGINT NOT NULL, snapshot_json JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(hall_id,version));
CREATE TABLE seat_versions (session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE, version BIGINT NOT NULL, seat_id UUID NOT NULL REFERENCES seats(id), state VARCHAR(20) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(session_id,version,seat_id));
CREATE INDEX bookings_user_idx ON bookings(user_id,session_id);
CREATE INDEX points_event_rank_idx ON points_ledger(event_id,user_id);
CREATE INDEX audit_event_created_idx ON audit_log(event_id,created_at DESC);