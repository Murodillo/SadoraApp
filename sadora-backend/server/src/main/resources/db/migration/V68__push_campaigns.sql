-- "Yangiliklar va takliflar": her own switch for the team's campaigns, on until she turns it off.
ALTER TABLE user_notification_settings ADD COLUMN news BOOLEAN NOT NULL DEFAULT TRUE;

-- A push the team writes once and the server sends to a segment, in each account's language.
-- Delivery goes through the ordinary outbox (dedupe key campaign:<id>:<user>), so a campaign
-- obeys the same quiet hours and caps as everything else and is never sent twice.
CREATE TABLE push_campaigns (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 120),
    -- {"uz": {"title": …, "body": …}, "ru": …, "en": …}; Uzbek is required, the others fall back to it.
    texts         JSONB       NOT NULL,
    -- {"lifeStages": [...], "languages": [...], "tier": "all|free|premium", "accountKinds": [...]}
    segment       JSONB       NOT NULL,
    status        TEXT        NOT NULL CHECK (status IN ('scheduled', 'sending', 'sent', 'cancelled')),
    scheduled_for TIMESTAMPTZ NOT NULL,
    created_by    UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    -- Accounts in the segment, and why the ones not queued were left out.
    audience      INTEGER,
    queued        INTEGER,
    skipped       JSONB
);

CREATE INDEX push_campaigns_due_idx ON push_campaigns (status, scheduled_for);
