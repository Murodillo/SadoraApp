-- PCOS and endometriosis (2026-10-19): the conditions she marks, the pain diary, and the
-- two PCOS signs the symptom catalogue did not have.

CREATE TABLE user_conditions (
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    condition    TEXT        NOT NULL CHECK (condition IN ('pcos', 'endometriosis')),
    status       TEXT        NOT NULL CHECK (status IN ('diagnosed', 'suspected')),
    diagnosed_on DATE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, condition)
);

-- One row per day. Areas and kinds are short fixed words, comma-separated: the diary is read
-- a day or a few months at a time, never searched by area.
CREATE TABLE pain_logs (
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day               DATE        NOT NULL,
    level             INTEGER     NOT NULL CHECK (level BETWEEN 0 AND 10),
    areas             TEXT        NOT NULL DEFAULT '',
    kinds             TEXT        NOT NULL DEFAULT '',
    painkiller        BOOLEAN     NOT NULL DEFAULT FALSE,
    painkiller_name   TEXT,
    missed_activities BOOLEAN     NOT NULL DEFAULT FALSE,
    note              TEXT,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, day)
);

-- Excess hair and hair loss join acne as the signs PCOS is followed by. They are offered to
-- the stages with a cycle; acne already is.
INSERT INTO symptom_definitions (key, label, label_ru, label_en, category, sort_order) VALUES
    ('excess_hair', 'Ortiqcha tuk', 'Избыточный рост волос', 'Excess hair growth', 'skin', 101),
    ('hair_loss',   'Soch to''kilishi', 'Выпадение волос', 'Hair loss', 'skin', 102)
ON CONFLICT (key) DO NOTHING;

INSERT INTO symptom_life_stages (symptom_key, life_stage) VALUES
    ('excess_hair', 'cycle'), ('excess_hair', 'trying_to_conceive'), ('excess_hair', 'perimenopause'),
    ('hair_loss', 'cycle'), ('hair_loss', 'trying_to_conceive'), ('hair_loss', 'perimenopause'),
    ('hair_loss', 'postpartum'), ('hair_loss', 'menopause')
ON CONFLICT DO NOTHING;

INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('condition_report', 'Holat hisoboti (PCOS, endometrioz) — 3–6 oylik', FALSE, TRUE, NULL, NULL, NULL, NULL)
ON CONFLICT (key) DO NOTHING;
