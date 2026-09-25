-- HealthKit and Health Connect, the two providers wired for v1.
--
-- `scale` converts the provider's unit to SADORA's: kilojoules to kilocalories, seconds
-- to minutes, kilometres to metres. A provider that changes a field name needs a row
-- here and nothing else.

INSERT INTO provider_metric_mappings (provider, provider_metric, metric, provider_unit, scale) VALUES
    -- Apple HealthKit
    ('apple_health', 'HKQuantityTypeIdentifierStepCount',                'steps',              'count', 1),
    ('apple_health', 'HKQuantityTypeIdentifierActiveEnergyBurned',       'active_energy',      'kcal',  1),
    ('apple_health', 'HKQuantityTypeIdentifierDistanceWalkingRunning',   'distance',           'm',     1),
    ('apple_health', 'HKQuantityTypeIdentifierHeartRate',                'heart_rate',         'bpm',   1),
    ('apple_health', 'HKQuantityTypeIdentifierRestingHeartRate',         'resting_heart_rate', 'bpm',   1),
    ('apple_health', 'HKQuantityTypeIdentifierHeartRateVariabilitySDNN', 'hrv',                'ms',    1),
    ('apple_health', 'HKQuantityTypeIdentifierRespiratoryRate',          'respiratory_rate',   'brpm',  1),
    ('apple_health', 'HKQuantityTypeIdentifierBodyTemperature',          'body_temperature',   'c',     1),
    ('apple_health', 'HKQuantityTypeIdentifierBodyMass',                 'weight',             'kg',    1),
    ('apple_health', 'HKCategoryTypeIdentifierSleepAnalysis',            'sleep_duration',     's',     0.0166666667),

    -- Android Health Connect
    ('health_connect', 'Steps',                  'steps',              'count', 1),
    ('health_connect', 'ActiveCaloriesBurned',   'active_energy',      'kJ',    0.239005736),
    ('health_connect', 'Distance',               'distance',           'm',     1),
    ('health_connect', 'HeartRate',              'heart_rate',         'bpm',   1),
    ('health_connect', 'RestingHeartRate',       'resting_heart_rate', 'bpm',   1),
    ('health_connect', 'HeartRateVariabilityRmssd', 'hrv',             'ms',    1),
    ('health_connect', 'RespiratoryRate',        'respiratory_rate',   'brpm',  1),
    ('health_connect', 'BodyTemperature',        'body_temperature',   'c',     1),
    ('health_connect', 'Weight',                 'weight',             'kg',    1),
    ('health_connect', 'SleepSession',           'sleep_duration',     's',     0.0166666667),

    -- Entered in the app rather than synced.
    ('manual', 'weight', 'weight', 'kg', 1),
    ('manual', 'steps',  'steps',  'count', 1);
