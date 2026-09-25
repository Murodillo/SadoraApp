-- SADORA core schema.
--
-- Two rules shape this file:
--   1. Health data (cycle, symptoms, mood, meds, AI transcripts) is never joined to an
--      admin-facing view. Admin tooling reads only the account and technical tables
--      below, which is why they carry no health columns at all.
--   2. Entitlements and feature flags are data, not code — an operator changes a limit
--      here and it takes effect without an app release.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------- accounts

CREATE TABLE users (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone                TEXT UNIQUE,
    email                TEXT UNIQUE,
    password_hash        TEXT,
    name                 TEXT        NOT NULL DEFAULT '',
    language             TEXT        NOT NULL DEFAULT 'uz',
    timezone             TEXT        NOT NULL DEFAULT 'Asia/Tashkent',
    life_stage           TEXT        NOT NULL DEFAULT 'cycle',
    birth_date           DATE,
    height_cm            INTEGER,
    weight_kg            INTEGER,
    avatar_url           TEXT,
    onboarding_completed BOOLEAN     NOT NULL DEFAULT FALSE,
    status               TEXT        NOT NULL DEFAULT 'active',
    blocked_reason       TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_active_at       TIMESTAMPTZ,
    deletion_requested_at TIMESTAMPTZ,
    CONSTRAINT users_identifier_present CHECK (phone IS NOT NULL OR email IS NOT NULL)
);

CREATE INDEX users_created_at_idx ON users (created_at DESC);
CREATE INDEX users_status_idx ON users (status);
CREATE INDEX users_last_active_idx ON users (last_active_at DESC NULLS LAST);

CREATE TABLE user_goals (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    goal    TEXT NOT NULL,
    PRIMARY KEY (user_id, goal)
);

-- Apple / Google sign-in. One row per provider subject; a user may have several.
CREATE TABLE auth_identities (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   TEXT        NOT NULL,
    subject    TEXT        NOT NULL,
    email      TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, subject)
);

CREATE INDEX auth_identities_user_idx ON auth_identities (user_id);

-- ---------------------------------------------------------------- sign-in

-- The code itself is never stored, only a hash, so a database dump cannot be replayed.
CREATE TABLE otp_challenges (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone        TEXT        NOT NULL,
    code_hash    TEXT        NOT NULL,
    purpose      TEXT        NOT NULL DEFAULT 'sign_in',
    attempts     INTEGER     NOT NULL DEFAULT 0,
    max_attempts INTEGER     NOT NULL DEFAULT 5,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_ip   TEXT
);

CREATE INDEX otp_challenges_phone_idx ON otp_challenges (phone, created_at DESC);
CREATE INDEX otp_challenges_expiry_idx ON otp_challenges (expires_at);

-- Refresh tokens rotate: using one revokes it and issues a successor in the same family.
-- Replaying a spent token kills the whole family, which is how token theft surfaces.
CREATE TABLE refresh_tokens (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id      UUID        NOT NULL,
    token_hash     TEXT        NOT NULL UNIQUE,
    device_id      TEXT,
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason TEXT,
    replaced_by    UUID
);

CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_expiry_idx ON refresh_tokens (expires_at);

CREATE TABLE devices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id    TEXT        NOT NULL,
    platform     TEXT        NOT NULL,
    os_version   TEXT,
    app_version  TEXT,
    model        TEXT,
    push_token   TEXT,
    timezone     TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, device_id)
);

CREATE INDEX devices_push_token_idx ON devices (push_token) WHERE push_token IS NOT NULL;

-- ---------------------------------------------------------------- consent

CREATE TABLE user_consents (
    user_id        UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    store_health   BOOLEAN     NOT NULL DEFAULT FALSE,
    ai_insights    BOOLEAN     NOT NULL DEFAULT FALSE,
    analytics      BOOLEAN     NOT NULL DEFAULT FALSE,
    marketing      BOOLEAN     NOT NULL DEFAULT FALSE,
    policy_version TEXT        NOT NULL DEFAULT '2026-08-01',
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only. Regulators ask "what did she agree to, and when" — this answers it.
CREATE TABLE consent_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    consent_key    TEXT        NOT NULL,
    granted        BOOLEAN     NOT NULL,
    policy_version TEXT        NOT NULL,
    source         TEXT        NOT NULL DEFAULT 'app',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX consent_events_user_idx ON consent_events (user_id, created_at DESC);

-- ---------------------------------------------------------------- baselines

-- Onboarding answers only. The cycle log itself arrives in sprint 2.
CREATE TABLE cycle_baselines (
    user_id               UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    last_period_start     DATE,
    average_cycle_length  INTEGER     NOT NULL DEFAULT 28,
    average_period_length INTEGER     NOT NULL DEFAULT 5,
    is_regular            BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stage_baselines (
    user_id           UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    due_date          DATE,
    child_birth_date  DATE,
    last_period_start DATE,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- money

CREATE TABLE subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    tier            TEXT        NOT NULL DEFAULT 'premium',
    source          TEXT        NOT NULL,
    product_id      TEXT,
    external_id     TEXT,
    status          TEXT        NOT NULL DEFAULT 'active',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    auto_renewing   BOOLEAN     NOT NULL DEFAULT FALSE,
    in_grace_period BOOLEAN     NOT NULL DEFAULT FALSE,
    granted_by      UUID,
    grant_reason    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source, external_id)
);

CREATE INDEX subscriptions_user_idx ON subscriptions (user_id, status);
CREATE INDEX subscriptions_expiry_idx ON subscriptions (expires_at) WHERE status = 'active';

-- ---------------------------------------------------------------- entitlements

-- One row per feature. The admin panel's "Entitlements va limitlar" page is a direct
-- editor over this table; NULL limit means unmetered.
CREATE TABLE feature_definitions (
    key                   TEXT PRIMARY KEY,
    description           TEXT        NOT NULL DEFAULT '',
    free_enabled          BOOLEAN     NOT NULL DEFAULT FALSE,
    premium_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    free_daily_limit      INTEGER,
    free_monthly_limit    INTEGER,
    premium_daily_limit   INTEGER,
    premium_monthly_limit INTEGER,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by            UUID
);

-- Per-user exceptions: support grants, abuse clamps, beta access.
CREATE TABLE user_entitlement_overrides (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    feature_key   TEXT        NOT NULL REFERENCES feature_definitions (key) ON DELETE CASCADE,
    enabled       BOOLEAN,
    daily_limit   INTEGER,
    monthly_limit INTEGER,
    reason        TEXT        NOT NULL,
    expires_at    TIMESTAMPTZ,
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, feature_key)
);

-- Counted per calendar day in the user's own timezone, so a limit resets at her
-- midnight rather than the server's. Monthly usage is summed from these rows.
CREATE TABLE feature_usage_daily (
    user_id     UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    feature_key TEXT    NOT NULL,
    usage_date  DATE    NOT NULL,
    used        INTEGER NOT NULL DEFAULT 0,
    cost_micros BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, feature_key, usage_date)
);

CREATE INDEX feature_usage_date_idx ON feature_usage_daily (usage_date, feature_key);

-- ---------------------------------------------------------------- feature flags

CREATE TABLE feature_flags (
    key           TEXT PRIMARY KEY,
    description   TEXT        NOT NULL DEFAULT '',
    enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    default_value BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    UUID
);

-- Rules are evaluated in priority order; the first match wins. A NULL column means
-- "any". rollout_percentage buckets on a stable hash of (flag key, user id), so a user
-- who is in the 5% stays in it as the rollout widens.
CREATE TABLE feature_flag_rules (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key           TEXT        NOT NULL REFERENCES feature_flags (key) ON DELETE CASCADE,
    environment        TEXT,
    country            TEXT,
    language           TEXT,
    life_stage         TEXT,
    platform           TEXT,
    cohort             TEXT,
    rollout_percentage INTEGER     NOT NULL DEFAULT 100 CHECK (rollout_percentage BETWEEN 0 AND 100),
    value              BOOLEAN     NOT NULL DEFAULT TRUE,
    priority           INTEGER     NOT NULL DEFAULT 100,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX feature_flag_rules_flag_idx ON feature_flag_rules (flag_key, priority);

-- ---------------------------------------------------------------- admin

CREATE TABLE admin_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           TEXT        NOT NULL UNIQUE,
    password_hash   TEXT        NOT NULL,
    name            TEXT        NOT NULL,
    role            TEXT        NOT NULL DEFAULT 'support',
    totp_secret     TEXT,
    totp_enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    status          TEXT        NOT NULL DEFAULT 'active',
    failed_attempts INTEGER     NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only; the admin panel's page 14 reads straight off it.
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_type  TEXT        NOT NULL,
    actor_id    UUID,
    actor_label TEXT,
    action      TEXT        NOT NULL,
    entity_type TEXT,
    entity_id   TEXT,
    reason      TEXT,
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ip          TEXT,
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_created_idx ON audit_log (created_at DESC);
CREATE INDEX audit_log_actor_idx ON audit_log (actor_type, actor_id, created_at DESC);
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, created_at DESC);
