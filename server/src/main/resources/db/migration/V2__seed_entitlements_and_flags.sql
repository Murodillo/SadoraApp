-- Starting Free/Premium split and limits.
--
-- These are defaults, not settings in code: operators change them from the admin panel
-- and the change takes effect on the next entitlement read. AI limits in particular are
-- the cost lever — the daily budget page tightens them when spend runs hot.

INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES
    ('ai_chat',            'SADORA AI matnli chat',                 TRUE,  TRUE,  3,    30,   20,   400),
    ('ai_daily_summary',   'Kunlik AI xulosasi (Today)',            FALSE, TRUE,  NULL, NULL, 1,    31),
    ('ai_insights',        'Haftalik va oylik AI insights',         FALSE, TRUE,  NULL, NULL, NULL, 8),
    ('ai_meal_plan',       'AI ovqatlanish rejasi',                 FALSE, TRUE,  NULL, NULL, 2,    20),
    ('insights_history',   'Insights tarixi chuqurligi',            TRUE,  TRUE,  NULL, NULL, NULL, NULL),
    ('cycle_prediction',   'Sikl prognozi va kalendar',             TRUE,  TRUE,  NULL, NULL, NULL, NULL),
    ('nutrition_log',      'Ovqatlanish kundaligi va suv',          TRUE,  TRUE,  NULL, NULL, NULL, NULL),
    ('mind_journal',       'Mind check-in va kundalik',             TRUE,  TRUE,  NULL, NULL, NULL, NULL),
    ('meds_reminders',     'Dorilar va eslatmalar',                 TRUE,  TRUE,  NULL, NULL, NULL, NULL),
    ('learn_premium',      'Learn — Premium materiallar',           FALSE, TRUE,  NULL, NULL, NULL, NULL),
    ('wearable_sync',      'HealthKit / Health Connect sinxroni',   TRUE,  TRUE,  NULL, NULL, NULL, NULL),
    ('data_export',        'Ma''lumotlarni eksport qilish',         TRUE,  TRUE,  1,    3,    NULL, NULL);

INSERT INTO feature_flags (key, description, enabled, default_value) VALUES
    ('ai_chat_enabled',        'AI chat kill switch — o''chirilsa chat butunlay yopiladi', TRUE,  TRUE),
    ('ai_summary_enabled',     'Kunlik AI xulosasi kill switch',                          TRUE,  TRUE),
    ('ai_insights_enabled',    'Insights batch generatsiyasi kill switch',                TRUE,  TRUE),
    ('payme_checkout',         'Payme web-checkout',                                      TRUE,  FALSE),
    ('click_checkout',         'Click web-checkout',                                      TRUE,  FALSE),
    ('store_iap',              'App Store / Google Play obunalari',                       TRUE,  TRUE),
    ('food_scan',              'Food Scan (v1.1 — hozircha yopiq)',                       TRUE,  FALSE),
    ('community',              'Community (v1 dan tashqarida)',                           TRUE,  FALSE),
    ('health_connect_sync',    'Android Health Connect sinxroni',                         TRUE,  TRUE),
    ('healthkit_sync',         'Apple HealthKit sinxroni',                                TRUE,  TRUE);

-- Food Scan opens on 5% of users in dev first — the shape every rollout follows.
INSERT INTO feature_flag_rules (flag_key, environment, rollout_percentage, value, priority)
VALUES ('food_scan', 'dev', 5, TRUE, 10);
