-- A verified doctor's badges (2026-10-07), the same idea as hers in V38.
--
-- Kept apart from user_badges: a doctor is also an ordinary account and may hold a
-- woman's badges in the client app, and the two boards must never mark each other's
-- unlocks as seen. The counts are read live from what she did — answers, consultations,
-- notes — and only the moment a tier was crossed is stored.
CREATE TABLE doctor_badges (
    doctor_id UUID        NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    badge     TEXT        NOT NULL,
    tier      INTEGER     NOT NULL CHECK (tier >= 1),
    earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Set once her app (or panel) has played the unlock; NULL is "still to show her".
    seen_at   TIMESTAMPTZ,
    PRIMARY KEY (doctor_id, badge, tier)
);

CREATE INDEX doctor_badges_unseen_idx ON doctor_badges (doctor_id) WHERE seen_at IS NULL;
