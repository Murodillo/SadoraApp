-- Pelvic floor (Kegel) exercises (2026-10-19): sessions, her reminders and program choice,
-- and the four-weekly leak check.

CREATE TABLE kegel_sessions (
    id               UUID        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    program          TEXT        NOT NULL CHECK (program IN ('cycle', 'pregnancy', 'postpartum', 'menopause')),
    level            INTEGER     NOT NULL CHECK (level BETWEEN 1 AND 10),
    done_on          DATE        NOT NULL,
    reps             INTEGER     NOT NULL CHECK (reps BETWEEN 1 AND 500),
    duration_seconds INTEGER     NOT NULL CHECK (duration_seconds BETWEEN 1 AND 3600),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX kegel_sessions_user_day_idx ON kegel_sessions (user_id, done_on);

-- Reminders are "HH:MM" times, comma-separated; empty is off. A null program follows her stage.
CREATE TABLE kegel_settings (
    user_id    UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    program    TEXT        CHECK (program IN ('cycle', 'pregnancy', 'postpartum', 'menopause')),
    reminders  TEXT        NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE kegel_checks (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    taken_on   DATE        NOT NULL,
    frequency  INTEGER     NOT NULL CHECK (frequency BETWEEN 0 AND 4),
    amount     INTEGER     NOT NULL CHECK (amount BETWEEN 0 AND 3),
    situations TEXT        NOT NULL DEFAULT '',
    bother     INTEGER     NOT NULL CHECK (bother BETWEEN 0 AND 10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, taken_on)
);

INSERT INTO coin_rules (reason, amount, daily_cap, description) VALUES
    ('kegel_session', 5, 3, 'Tos tubi (Kegel) mashqi')
ON CONFLICT (reason) DO NOTHING;

INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('kegel_advanced', 'Kegel mashqlari — 3-darajadan yuqorisi', FALSE, TRUE, NULL, NULL, NULL, NULL)
ON CONFLICT (key) DO NOTHING;
