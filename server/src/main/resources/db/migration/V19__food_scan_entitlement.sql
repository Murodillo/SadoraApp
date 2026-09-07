-- The food scanner stops being a mock, so it needs the gate every other paid AI call has.
--
-- Premium only, and metered daily and monthly: a photograph costs a model call, and the
-- operator has to be able to close the tap from the admin panel the same way they can
-- for the chat. The flag of the same name already existed from the v1.1 plan; it is
-- turned on here because there is now something behind it.
INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('food_scan', 'Ovqat skaneri — rasmni o''qish', FALSE, TRUE, NULL, NULL, 10, 150)
ON CONFLICT (key) DO UPDATE SET
    description = EXCLUDED.description,
    free_enabled = EXCLUDED.free_enabled,
    premium_enabled = EXCLUDED.premium_enabled,
    premium_daily_limit = EXCLUDED.premium_daily_limit,
    premium_monthly_limit = EXCLUDED.premium_monthly_limit;

UPDATE feature_flags SET description = 'Ovqat skaneri', default_value = TRUE WHERE key = 'food_scan';
DELETE FROM feature_flag_rules WHERE flag_key = 'food_scan';
