-- Wearing a badge after her name is a Premium feature. Earning badges, and the Gul they
-- pay, stay free for everyone; only the medal beside the name is behind the subscription.
INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('badge_wear', 'Nishonni ism yonida taqish', FALSE, TRUE, NULL, NULL, NULL, NULL)
ON CONFLICT (key) DO UPDATE SET
    description = EXCLUDED.description,
    free_enabled = EXCLUDED.free_enabled,
    premium_enabled = EXCLUDED.premium_enabled;
