-- Huawei, the fifth cloud wearable, read through Huawei Health Kit. Its day statistics
-- are already in metres, kilograms and degrees, and its sleep record in minutes. The
-- nightly sleep score sits where WHOOP's sleep performance does. Breathing rate is only
-- handed out inside a sleep record's sub-data and is not read, so it has no row here.

INSERT INTO provider_metric_mappings (provider, provider_metric, metric, provider_unit, scale) VALUES
    ('huawei', 'steps',              'steps',              'count',     1),
    ('huawei', 'distance_meters',    'distance',           'm',         1),
    ('huawei', 'active_calories',    'active_energy',      'kcal',      1),
    ('huawei', 'heart_rate_average', 'heart_rate',         'bpm',       1),
    ('huawei', 'resting_heart_rate', 'resting_heart_rate', 'bpm',       1),
    ('huawei', 'hrv_rmssd',          'hrv',                'ms',        1),
    ('huawei', 'spo2_average',       'spo2',               'percent',   1),
    ('huawei', 'skin_temperature',   'skin_temperature',   'c',         1),
    ('huawei', 'body_temperature',   'body_temperature',   'c',         1),
    ('huawei', 'vo2_max',            'vo2_max',            'ml/kg/min', 1),
    ('huawei', 'minutes_asleep',     'sleep_duration',     'min',       1),
    ('huawei', 'minutes_deep',       'sleep_deep',         'min',       1),
    ('huawei', 'minutes_rem',        'sleep_rem',          'min',       1),
    ('huawei', 'minutes_light',      'sleep_light',        'min',       1),
    ('huawei', 'minutes_awake',      'sleep_awake',        'min',       1),
    ('huawei', 'sleep_score',        'sleep_performance',  'percent',   1),
    ('huawei', 'weight_kg',          'weight',             'kg',        1),
    ('huawei', 'body_fat_rate',      'body_fat',           'percent',   1)
ON CONFLICT (provider, provider_metric) DO NOTHING;
