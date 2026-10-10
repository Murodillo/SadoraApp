-- Fitbit, the fourth cloud wearable, read through the Google Health API. Sleep comes in
-- minutes already; distance in millimetres and weight in grams. The sleep temperature
-- Google reports is a deviation from her baseline, not a reading, so it has no row here.

INSERT INTO provider_metric_mappings (provider, provider_metric, metric, provider_unit, scale) VALUES
    ('fitbit', 'steps',                'steps',              'count',     1),
    ('fitbit', 'floors',               'floors',             'count',     1),
    ('fitbit', 'distance_millimeters', 'distance',           'mm',        0.001),
    ('fitbit', 'active_energy_kcal',   'active_energy',      'kcal',      1),
    ('fitbit', 'heart_rate_average',   'heart_rate',         'bpm',       1),
    ('fitbit', 'resting_heart_rate',   'resting_heart_rate', 'bpm',       1),
    ('fitbit', 'hrv_average',          'hrv',                'ms',        1),
    ('fitbit', 'spo2_average',         'spo2',               'percent',   1),
    ('fitbit', 'breaths_per_minute',   'respiratory_rate',   'brpm',      1),
    ('fitbit', 'vo2_max',              'vo2_max',            'ml/kg/min', 1),
    ('fitbit', 'minutes_asleep',       'sleep_duration',     'min',       1),
    ('fitbit', 'minutes_deep',         'sleep_deep',         'min',       1),
    ('fitbit', 'minutes_rem',          'sleep_rem',          'min',       1),
    ('fitbit', 'minutes_light',        'sleep_light',        'min',       1),
    ('fitbit', 'minutes_awake',        'sleep_awake',        'min',       1),
    ('fitbit', 'weight_grams',         'weight',             'g',         0.001),
    ('fitbit', 'body_fat_percentage',  'body_fat',           'percent',   1)
ON CONFLICT (provider, provider_metric) DO NOTHING;
