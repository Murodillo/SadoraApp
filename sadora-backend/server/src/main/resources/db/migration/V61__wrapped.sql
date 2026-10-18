-- Cycle Wrapped (2026-10-19): which recaps she has opened, so Today stops offering them.
CREATE TABLE wrapped_seen (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    key     TEXT        NOT NULL CHECK (char_length(key) <= 32),
    seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, key)
);
