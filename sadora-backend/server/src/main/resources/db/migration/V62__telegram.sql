-- The Telegram bot (2026-10-19): one chat per account, one-time link codes, and where the
-- update stream was read up to.

CREATE TABLE telegram_links (
    user_id   UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    chat_id   BIGINT      NOT NULL UNIQUE,
    username  TEXT,
    detailed  BOOLEAN     NOT NULL DEFAULT FALSE,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE telegram_link_codes (
    code       TEXT        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ
);

CREATE INDEX telegram_link_codes_user_idx ON telegram_link_codes (user_id);

CREATE TABLE telegram_offset (
    id          INTEGER PRIMARY KEY CHECK (id = 1),
    next_update BIGINT  NOT NULL
);
