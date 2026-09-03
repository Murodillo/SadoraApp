-- Mind and Nutrition. Health data, subject to the same rules as V3: written only with
-- `store_health` consent, scoped to the owning account, unreachable from the admin API.

-- The Mind tab's third dial. Mood and energy already live on the daily row.
ALTER TABLE daily_logs ADD COLUMN stress INTEGER
    CHECK (stress IS NULL OR stress BETWEEN 1 AND 5);

-- Water is a running total per day, not an event log: the app adds and undoes amounts,
-- and the only question anything asks is how much she has drunk today.
ALTER TABLE daily_logs ADD COLUMN water_ml INTEGER NOT NULL DEFAULT 0
    CHECK (water_ml >= 0);

-- The journal. Labelled "Faqat siz ko'rasiz" in the app, and that is a promise about
-- this table: nothing joins to it outside the owning user's own requests.
CREATE TABLE journal_entries (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    entry_date DATE        NOT NULL,
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX journal_entries_user_date_idx ON journal_entries (user_id, entry_date DESC);

-- Breathing and the practices that follow it. Append-only; a finished session is a fact.
CREATE TABLE mind_practices (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind             TEXT        NOT NULL DEFAULT 'breathing',
    duration_seconds INTEGER     NOT NULL CHECK (duration_seconds BETWEEN 1 AND 7200),
    completed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX mind_practices_user_idx ON mind_practices (user_id, completed_at DESC);

-- Macros are stored as eaten rather than recomputed from the catalogue: a catalogue
-- correction must not silently rewrite what she ate last March.
CREATE TABLE meals (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    log_date    DATE        NOT NULL,
    slot        TEXT        NOT NULL DEFAULT 'snack',
    eaten_at    TIME,
    description TEXT        NOT NULL,
    kcal        INTEGER     NOT NULL DEFAULT 0 CHECK (kcal BETWEEN 0 AND 10000),
    protein_g   INTEGER     NOT NULL DEFAULT 0 CHECK (protein_g BETWEEN 0 AND 1000),
    fat_g       INTEGER     NOT NULL DEFAULT 0 CHECK (fat_g BETWEEN 0 AND 1000),
    carbs_g     INTEGER     NOT NULL DEFAULT 0 CHECK (carbs_g BETWEEN 0 AND 1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX meals_user_date_idx ON meals (user_id, log_date DESC);

-- Targets are per user and editable. They are never computed from her weight — that
-- would be dietary advice the product does not give.
CREATE TABLE nutrition_goals (
    user_id        UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    calorie_goal   INTEGER     NOT NULL DEFAULT 1850,
    protein_goal_g INTEGER     NOT NULL DEFAULT 85,
    fat_goal_g     INTEGER     NOT NULL DEFAULT 62,
    carbs_goal_g   INTEGER     NOT NULL DEFAULT 210,
    water_goal_ml  INTEGER     NOT NULL DEFAULT 2000,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Shared catalogue, not per user. Uzbek dishes lead it, because that is what the search
-- is for; a database of American cereals would be useless here.
CREATE TABLE food_items (
    key       TEXT PRIMARY KEY,
    name      TEXT    NOT NULL,
    kcal      INTEGER NOT NULL,
    protein_g INTEGER NOT NULL DEFAULT 0,
    fat_g     INTEGER NOT NULL DEFAULT 0,
    carbs_g   INTEGER NOT NULL DEFAULT 0,
    per_piece BOOLEAN NOT NULL DEFAULT FALSE,
    active    BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX food_items_name_idx ON food_items (lower(name));
