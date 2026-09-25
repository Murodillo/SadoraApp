-- The readings the phone readers send beyond V10's first set: sleep stages, oxygen,
-- temperatures and — for Health Connect — the names the reader actually uses.
--
-- Both readers send sleep in seconds, one row per night. SpO2 arrives as a percentage
-- (HealthKit's fraction is multiplied on the phone), so its scale is 1.

INSERT INTO provider_metric_mappings (provider, provider_metric, metric, provider_unit, scale) VALUES
    -- Apple HealthKit
    ('apple_health', 'HKQuantityTypeIdentifierOxygenSaturation',             'spo2',             'percent', 1),
    ('apple_health', 'HKQuantityTypeIdentifierAppleSleepingWristTemperature', 'skin_temperature', 'c',       1),
    ('apple_health', 'HKQuantityTypeIdentifierBasalBodyTemperature',         'body_temperature', 'c',       1),
    ('apple_health', 'HKCategoryValueSleepAnalysisAsleepDeep',               'sleep_deep',       's',       0.0166666667),
    ('apple_health', 'HKCategoryValueSleepAnalysisAsleepREM',                'sleep_rem',        's',       0.0166666667),
    ('apple_health', 'HKCategoryValueSleepAnalysisAsleepCore',               'sleep_light',      's',       0.0166666667),
    ('apple_health', 'HKCategoryValueSleepAnalysisAwake',                    'sleep_awake',      's',       0.0166666667),

    -- Android Health Connect
    ('health_connect', 'OxygenSaturation',     'spo2',             'percent', 1),
    ('health_connect', 'SkinTemperature',      'skin_temperature', 'c',       1),
    ('health_connect', 'BasalBodyTemperature', 'body_temperature', 'c',       1),
    ('health_connect', 'SleepDeep',            'sleep_deep',       's',       0.0166666667),
    ('health_connect', 'SleepRem',             'sleep_rem',        's',       0.0166666667),
    ('health_connect', 'SleepLight',           'sleep_light',      's',       0.0166666667),
    ('health_connect', 'SleepAwake',           'sleep_awake',      's',       0.0166666667)
ON CONFLICT (provider, provider_metric) DO NOTHING;
