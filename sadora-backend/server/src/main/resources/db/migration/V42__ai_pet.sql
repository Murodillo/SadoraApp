-- The AI companion: a pet that speaks up after her actions with a tip or a feature she
-- has not tried. Premium only; free accounts see it asleep, as a door to the paywall.
INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('ai_pet', 'AI hamroh (pet) maslahatlari', FALSE, TRUE, NULL, NULL, NULL, NULL)
ON CONFLICT (key) DO UPDATE SET
    description = EXCLUDED.description,
    free_enabled = EXCLUDED.free_enabled,
    premium_enabled = EXCLUDED.premium_enabled;

-- Which of the five she picked. No row means the default (nilufar); choosing is free,
-- so the picker can show every pet even before she subscribes.
CREATE TABLE user_pet (
    user_id    UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    pet        TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
