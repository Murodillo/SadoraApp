-- Home-screen widgets and the watch.

-- Discreet mode: widgets, the watch and pushes in neutral words. On the account, not the
-- device, because the lock screen is the same risk on every phone she signs in on.
ALTER TABLE users ADD COLUMN discreet_mode BOOLEAN NOT NULL DEFAULT FALSE;

-- The widget extension's and the watch's own sessions are children of the app's: signing
-- the app out, or a detected replay, ends them too.
ALTER TABLE refresh_tokens ADD COLUMN parent_family_id UUID;
CREATE INDEX refresh_tokens_parent_family_idx ON refresh_tokens (parent_family_id) WHERE parent_family_id IS NOT NULL;

-- A widget's or watch's water tap, by the id the device gave it. The offline queue may
-- send the same tap twice; the second finds its row here and adds nothing. Her rows
-- older than a week are dropped on her next tap: a queue never holds one that long.
CREATE TABLE water_taps (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    client_id  TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, client_id)
);
