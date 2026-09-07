-- Appointments: the visits, scans and tests she is keeping track of.
--
-- Health data, under the same rules as V3, V5 and V7 — the admin tables cannot reach it.
--
-- Not pregnancy-only, though pregnancy is where the app surfaces it first: a
-- perimenopausal user books the same kind of appointment, and a table named after one
-- life stage would have to be renamed or duplicated the moment she does.
--
-- The app does not prescribe a screening schedule. There is no seeded list of "expected"
-- appointments here and no due-date arithmetic anywhere near this table: telling a
-- pregnant woman she has missed a scan the clinic never booked would be worse than
-- keeping no list at all.

CREATE TABLE appointments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title        TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 120),
    -- The day it is on. Required: an appointment with no date is a note, and notes have
    -- their own place in the journal.
    scheduled_on DATE        NOT NULL,
    -- NULL when the clinic gave her a day but not an hour, which is common here.
    scheduled_at TIME,
    place        TEXT,
    note         TEXT,
    -- Hours before the appointment to remind her; NULL means she does not want one.
    remind_hours_before INTEGER CHECK (remind_hours_before IS NULL OR remind_hours_before BETWEEN 0 AND 168),
    -- Set when she marks it done. Kept rather than deleted so the list can show what
    -- has already happened this pregnancy.
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The list is always read in date order for one user, which is what this covers.
CREATE INDEX appointments_user_date_idx ON appointments (user_id, scheduled_on);
