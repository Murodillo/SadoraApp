-- The first health tables.
--
-- Everything here is health data under section 17 of the TZ: it is written only with the
-- user's `store_health` consent, it is readable only by the account that owns it, and no
-- admin endpoint joins to any of it. The admin service depends on repositories that
-- cannot reach these tables, which is what keeps that true as the product grows.

-- One row per recorded period. This is the ground truth every prediction derives from —
-- cycle length is the gap between consecutive `started_on`, nothing is stored twice.
CREATE TABLE cycle_periods (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    started_on DATE        NOT NULL,
    -- NULL while the period is still going.
    ended_on   DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cycle_periods_order CHECK (ended_on IS NULL OR ended_on >= started_on),
    -- Two periods cannot start on the same day; recording one twice is a bug, not a fact.
    UNIQUE (user_id, started_on)
);

CREATE INDEX cycle_periods_user_start_idx ON cycle_periods (user_id, started_on DESC);

-- The day sheet edits a day as a whole, so a day is a row rather than an event stream.
CREATE TABLE daily_logs (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    log_date   DATE        NOT NULL,
    flow       TEXT,
    mood       TEXT,
    energy     INTEGER CHECK (energy IS NULL OR energy BETWEEN 1 AND 5),
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, log_date)
);

CREATE INDEX daily_logs_user_date_idx ON daily_logs (user_id, log_date DESC);

-- The catalogue the app renders. Served rather than hardcoded so the list can grow, and
-- differ per life stage, without an app release.
CREATE TABLE symptom_definitions (
    key        TEXT PRIMARY KEY,
    label      TEXT    NOT NULL,
    category   TEXT    NOT NULL DEFAULT 'other',
    sort_order INTEGER NOT NULL DEFAULT 0,
    active     BOOLEAN NOT NULL DEFAULT TRUE
);

-- Which stages offer a symptom. No rows for a key means every stage offers it.
CREATE TABLE symptom_life_stages (
    symptom_key TEXT NOT NULL REFERENCES symptom_definitions (key) ON DELETE CASCADE,
    life_stage  TEXT NOT NULL,
    PRIMARY KEY (symptom_key, life_stage)
);

CREATE TABLE daily_symptoms (
    user_id     UUID NOT NULL,
    log_date    DATE NOT NULL,
    symptom_key TEXT NOT NULL REFERENCES symptom_definitions (key),
    severity    TEXT NOT NULL DEFAULT 'moderate',
    PRIMARY KEY (user_id, log_date, symptom_key),
    -- Cascades with the day it belongs to, so clearing a day cannot leave orphans.
    FOREIGN KEY (user_id, log_date) REFERENCES daily_logs (user_id, log_date) ON DELETE CASCADE
);

CREATE INDEX daily_symptoms_key_idx ON daily_symptoms (symptom_key, log_date);
