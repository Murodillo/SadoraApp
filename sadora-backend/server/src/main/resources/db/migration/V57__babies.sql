-- The baby tracker (2026-10-18): her babies, and per baby the feeds, sleeps, nappies,
-- growth, vaccines and milestones.
--
-- Feeds already live in stage_events; sleeps and nappies join them there, each carrying
-- the baby it is about. A feed logged before she made a profile has no baby and stays so.

CREATE TABLE babies (
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name             TEXT        NOT NULL,
    sex              TEXT        NOT NULL CHECK (sex IN ('girl', 'boy')),
    birth_date       DATE        NOT NULL,
    birth_weight_g   INTEGER,
    birth_length_cm  DOUBLE PRECISION,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX babies_user_idx ON babies (user_id, birth_date DESC);

-- A sleep runs for hours and is written when it starts, so its length is unknown until it
-- ends; the old six-hour ceiling was for feeds and contractions.
ALTER TABLE stage_events
    ADD COLUMN baby_id UUID REFERENCES babies (id) ON DELETE CASCADE,
    DROP CONSTRAINT stage_events_kind_check,
    ADD CONSTRAINT stage_events_kind_check
        CHECK (kind IN ('feeding', 'kick_count', 'contraction', 'hot_flush', 'mood_screen', 'sleep', 'diaper')),
    DROP CONSTRAINT stage_events_duration_seconds_check,
    ADD CONSTRAINT stage_events_duration_seconds_check
        CHECK (duration_seconds IS NULL OR duration_seconds BETWEEN 0 AND 86400);

CREATE INDEX stage_events_baby_time ON stage_events (baby_id, started_at DESC) WHERE baby_id IS NOT NULL;

CREATE TABLE baby_growth (
    id          UUID PRIMARY KEY,
    baby_id     UUID        NOT NULL REFERENCES babies (id) ON DELETE CASCADE,
    measured_on DATE        NOT NULL,
    weight_g    INTEGER,
    length_cm   DOUBLE PRECISION,
    head_cm     DOUBLE PRECISION,
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT baby_growth_has_value CHECK (weight_g IS NOT NULL OR length_cm IS NOT NULL OR head_cm IS NOT NULL)
);

CREATE INDEX baby_growth_baby_idx ON baby_growth (baby_id, measured_on);

-- A dose of the national calendar she marked given. The calendar itself is in the server's
-- code; a dose not here is not given yet.
CREATE TABLE baby_vaccinations (
    baby_id    UUID        NOT NULL REFERENCES babies (id) ON DELETE CASCADE,
    code       TEXT        NOT NULL,
    given_on   DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (baby_id, code)
);

CREATE TABLE baby_milestones (
    baby_id     UUID        NOT NULL REFERENCES babies (id) ON DELETE CASCADE,
    code        TEXT        NOT NULL,
    achieved_on DATE        NOT NULL,
    note        TEXT,
    photo_mime  TEXT,
    photo       BYTEA,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (baby_id, code)
);

-- The WHO percentile curves are Premium; the measurements stay free for everyone.
INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('baby_growth_chart', 'Chaqaloq o''sishi — WHO persentil grafigi', FALSE, TRUE, NULL, NULL, NULL, NULL)
ON CONFLICT (key) DO NOTHING;
