ALTER TABLE users DROP COLUMN password_hash;

CREATE TABLE otp_codes (
  id UUID PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  code_hash CHAR(64) NOT NULL,
  requested_ip_hash CHAR(64) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
  consumed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX otp_codes_email_created_idx ON otp_codes(lower(email), created_at DESC);
CREATE INDEX otp_codes_ip_created_idx ON otp_codes(requested_ip_hash, created_at DESC);
CREATE INDEX otp_codes_expiry_idx ON otp_codes(expires_at);
