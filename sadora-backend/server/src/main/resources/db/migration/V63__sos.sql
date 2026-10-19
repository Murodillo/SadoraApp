-- SOS (2026-10-19): the people she wants alerted, and each alert sent (for the hourly limit
-- and for support). The location is kept only on the alert row it was sent with.

CREATE TABLE sos_contacts (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    position   INTEGER     NOT NULL CHECK (position BETWEEN 0 AND 9),
    name       TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    phone      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, position)
);

CREATE TABLE sos_alerts (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    latitude   DOUBLE PRECISION,
    longitude  DOUBLE PRECISION,
    sms_sent   INTEGER     NOT NULL DEFAULT 0,
    sms_failed INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX sos_alerts_user_idx ON sos_alerts (user_id, created_at);
