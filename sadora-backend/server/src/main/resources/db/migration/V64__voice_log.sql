-- Logging by voice (2026-10-19): three a day free, Premium without a limit. Nothing is stored
-- but the usage count: the recording is read by the model and dropped.
INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('voice_log', 'Ovoz bilan belgilash — Gemini', TRUE, TRUE, 3, NULL, NULL, NULL)
ON CONFLICT (key) DO NOTHING;
