-- Profile shares: the QR code she shows a doctor.
--
-- A share is a random token with a short life. Only the hash of the token is kept, so a
-- read of this table does not yield a working link — the link exists once, on the phone
-- that made it. A doctor scanning it sees a read-only summary; the row counts the views
-- so she can see that it was opened, and a revoked row stops answering at once.
--
-- The summary itself is not stored anywhere. It is built from her records at the moment
-- the page is opened, so a share never shows a stale copy and there is no second table
-- of health data to protect.

CREATE TABLE profile_shares (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     TEXT        NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    view_count     INTEGER     NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    last_viewed_at TIMESTAMPTZ,
    CHECK (expires_at > created_at)
);

CREATE INDEX profile_shares_user_idx ON profile_shares (user_id, created_at DESC);
