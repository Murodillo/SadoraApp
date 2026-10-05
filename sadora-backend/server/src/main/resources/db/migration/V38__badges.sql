-- Badges: the tiers she has reached, and whether the app has celebrated them yet.
--
-- The counts behind a badge are not stored — they are read from the tables that already
-- hold what she did (periods, meals, intakes…) every time the board is read. Only the
-- moment a tier was crossed is kept, so a tier once earned stays earned even if she
-- later deletes the meal that tipped it over.
CREATE TABLE user_badges (
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    badge     TEXT        NOT NULL,
    tier      INTEGER     NOT NULL CHECK (tier >= 1),
    earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Set once the app has played the unlock animation; NULL is "still to show her".
    seen_at   TIMESTAMPTZ,
    PRIMARY KEY (user_id, badge, tier)
);

CREATE INDEX user_badges_unseen_idx ON user_badges (user_id) WHERE seen_at IS NULL;

-- The amount is the bronze unit: silver pays twice it, gold four times, a single-tier
-- badge twice. Paid once per badge and tier (the ledger reference is "badge:tier").
INSERT INTO coin_rules (reason, amount, daily_cap, description) VALUES
    ('badge_earned', 15, NULL, 'Nishon bosqichi olindi — bronza ×1, kumush ×2, oltin ×4');
