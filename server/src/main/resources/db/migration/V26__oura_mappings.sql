-- Oura, the second cloud wearable. Its API speaks seconds for every sleep duration and
-- calls its daily score "readiness"; that score sits where WHOOP's recovery does.
-- The temperature Oura reports is a deviation from her baseline, not a reading, so it
-- has no row here.

INSERT INTO provider_metric_mappings (provider, provider_metric, metric, provider_unit, scale) VALUES
    ('oura', 'total_sleep_duration',        'sleep_duration',     's',       0.0166666667),
    ('oura', 'deep_sleep_duration',         'sleep_deep',         's',       0.0166666667),
    ('oura', 'rem_sleep_duration',          'sleep_rem',          's',       0.0166666667),
    ('oura', 'light_sleep_duration',        'sleep_light',        's',       0.0166666667),
    ('oura', 'awake_time',                  'sleep_awake',        's',       0.0166666667),
    ('oura', 'efficiency',                  'sleep_efficiency',   'percent', 1),
    ('oura', 'average_hrv',                 'hrv',                'ms',      1),
    ('oura', 'lowest_heart_rate',           'resting_heart_rate', 'bpm',     1),
    ('oura', 'average_breath',              'respiratory_rate',   'brpm',    1),
    ('oura', 'readiness_score',             'recovery',           'percent', 1),
    ('oura', 'steps',                       'steps',              'count',   1),
    ('oura', 'active_calories',             'active_energy',      'kcal',    1),
    ('oura', 'equivalent_walking_distance', 'distance',           'm',       1),
    ('oura', 'spo2_average',                'spo2',               'percent', 1),
    ('oura', 'weight',                      'weight',             'kg',      1)
ON CONFLICT (provider, provider_metric) DO NOTHING;
