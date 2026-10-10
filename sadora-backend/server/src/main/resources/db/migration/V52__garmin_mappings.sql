-- Garmin, the third cloud wearable. Its summaries speak seconds for sleep and grams for
-- weight. The nightly sleep score sits where WHOOP's sleep performance does. Stress and
-- Body Battery have no metric of ours, and the skin temperature Garmin reports is a
-- deviation from her baseline, not a reading, so none of them has a row here.

INSERT INTO provider_metric_mappings (provider, provider_metric, metric, provider_unit, scale) VALUES
    ('garmin', 'steps',                'steps',              'count',     1),
    ('garmin', 'distance_in_meters',   'distance',           'm',         1),
    ('garmin', 'active_kilocalories',  'active_energy',      'kcal',      1),
    ('garmin', 'floors_climbed',       'floors',             'count',     1),
    ('garmin', 'resting_heart_rate',   'resting_heart_rate', 'bpm',       1),
    ('garmin', 'average_heart_rate',   'heart_rate',         'bpm',       1),
    ('garmin', 'sleep_duration',       'sleep_duration',     's',         0.0166666667),
    ('garmin', 'deep_sleep_duration',  'sleep_deep',         's',         0.0166666667),
    ('garmin', 'rem_sleep_duration',   'sleep_rem',          's',         0.0166666667),
    ('garmin', 'light_sleep_duration', 'sleep_light',        's',         0.0166666667),
    ('garmin', 'awake_duration',       'sleep_awake',        's',         0.0166666667),
    ('garmin', 'sleep_score',          'sleep_performance',  'percent',   1),
    ('garmin', 'hrv_last_night_avg',   'hrv',                'ms',        1),
    ('garmin', 'spo2_average',         'spo2',               'percent',   1),
    ('garmin', 'respiration_average',  'respiratory_rate',   'brpm',      1),
    ('garmin', 'weight_in_grams',      'weight',             'g',         0.001),
    ('garmin', 'body_fat_in_percent',  'body_fat',           'percent',   1),
    ('garmin', 'vo2_max',              'vo2_max',            'ml/kg/min', 1)
ON CONFLICT (provider, provider_metric) DO NOTHING;
