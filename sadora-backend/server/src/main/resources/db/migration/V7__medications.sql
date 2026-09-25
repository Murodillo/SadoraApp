-- Medications and what actually happened with them.
--
-- Health data, under the same rules as V3 and V5. One design decision worth stating:
-- doses are derived from the schedule at read time, not materialised ahead. Changing a
-- course from daily to three-times-weekly then leaves no trail of orphaned future rows,
-- and the only thing stored is what the user did.

CREATE TABLE medications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name              TEXT        NOT NULL,
    emoji             TEXT,
    dosage            TEXT,
    unit              TEXT,
    food_relation     TEXT        NOT NULL DEFAULT 'any',
    note              TEXT,
    schedule_kind     TEXT        NOT NULL DEFAULT 'daily',
    -- "08:00,20:00" — every dose in a day. A twice-daily course is one row, not two.
    times             TEXT        NOT NULL DEFAULT '',
    -- ISO weekday numbers for schedule_kind = 'weekdays', e.g. "1,3,5".
    weekdays          TEXT        NOT NULL DEFAULT '',
    interval_days     INTEGER,
    reminders_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    started_on        DATE        NOT NULL,
    ended_on          DATE,
    -- Doses left in the pack. NULL means she is not tracking supply.
    stock_units       INTEGER CHECK (stock_units IS NULL OR stock_units >= 0),
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT medications_dates CHECK (ended_on IS NULL OR ended_on >= started_on)
);

CREATE INDEX medications_user_idx ON medications (user_id, active);

-- Only recorded events. A due dose with no row here is pending, which is why there is
-- no 'pending' status to store.
CREATE TABLE medication_intakes (
    medication_id UUID        NOT NULL REFERENCES medications (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    due_on        DATE        NOT NULL,
    due_at        TIME        NOT NULL,
    status        TEXT        NOT NULL CHECK (status IN ('taken', 'skipped')),
    recorded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (medication_id, due_on, due_at)
);

CREATE INDEX medication_intakes_user_date_idx ON medication_intakes (user_id, due_on DESC);
