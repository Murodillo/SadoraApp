-- Yaqinim: the one person she lets see how she is.
--
-- A row starts as an invite — a short code, stored only as its hash, with a two-day life.
-- The person she gives it to types it into their own account and the row becomes pending;
-- she says yes and it is active. She may pause it or end it at any moment, and so may
-- they. An ended row is kept, with when it ended, so her settings can say what happened;
-- it answers nothing.
--
-- Like the doctor's QR code, nothing about her health is stored here. What the person
-- sees is built from her records each time it is opened, and only from the parts she
-- ticked, so there is no second copy of anything to protect or to fall out of date.

ALTER TABLE users
    ADD COLUMN account_kind TEXT NOT NULL DEFAULT 'self'
        CHECK (account_kind IN ('self', 'partner'));

CREATE TABLE partner_links (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    partner_id     UUID                 REFERENCES users (id) ON DELETE CASCADE,
    status         TEXT        NOT NULL
        CHECK (status IN ('invited', 'pending', 'active', 'paused', 'ended')),
    relation       TEXT        NOT NULL DEFAULT 'husband',
    -- Cleared once the code is used, so a used code frees its place in the index.
    code_hash      TEXT,
    code_expires_at TIMESTAMPTZ,
    perm_cycle        BOOLEAN NOT NULL DEFAULT TRUE,
    perm_fertile      BOOLEAN NOT NULL DEFAULT FALSE,
    perm_mood         BOOLEAN NOT NULL DEFAULT FALSE,
    perm_symptoms     BOOLEAN NOT NULL DEFAULT FALSE,
    perm_pregnancy    BOOLEAN NOT NULL DEFAULT TRUE,
    perm_appointments BOOLEAN NOT NULL DEFAULT TRUE,
    perm_care         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at    TIMESTAMPTZ,
    approved_at    TIMESTAMPTZ,
    paused_at      TIMESTAMPTZ,
    ended_at       TIMESTAMPTZ,
    -- Who ended it: 'owner', 'partner', 'superseded' (a new code), 'account'.
    ended_by       TEXT,
    last_viewed_at TIMESTAMPTZ,
    view_count     INTEGER     NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    CHECK (partner_id IS NULL OR partner_id <> owner_id),
    CHECK (status = 'invited' OR status = 'ended' OR partner_id IS NOT NULL),
    CHECK (status <> 'invited' OR (code_hash IS NOT NULL AND code_expires_at IS NOT NULL))
);

-- One live row per woman: one person sees her at a time.
CREATE UNIQUE INDEX partner_links_one_live_per_owner
    ON partner_links (owner_id) WHERE status <> 'ended';

CREATE UNIQUE INDEX partner_links_code_idx
    ON partner_links (code_hash) WHERE code_hash IS NOT NULL;

CREATE INDEX partner_links_partner_idx
    ON partner_links (partner_id) WHERE status <> 'ended';

INSERT INTO coin_rules (reason, amount, daily_cap, description) VALUES
    ('partner_linked', 50, NULL, 'Yaqinim ulandi — bir marta');
