-- Cloud wearables: the standing grant a provider gives the server to pull her data.
--
-- HealthKit and Health Connect are read on the phone; WHOOP, Oura and their kind live
-- in the vendor's cloud and are reached with an OAuth token the server holds. That token
-- is a long-lived credential to someone's health data, so it is encrypted at rest with
-- WEARABLE_TOKEN_KEY and never leaves this table in the clear.
--
-- One row per user and provider. A second authorisation replaces the first rather than
-- adding to it; disconnecting deletes the row, so a revoked grant leaves nothing behind
-- to leak.

CREATE TABLE wearable_connections (
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider          TEXT        NOT NULL,
    external_user_id  TEXT,
    access_token_enc  TEXT        NOT NULL,
    refresh_token_enc TEXT,
    token_expires_at  TIMESTAMPTZ NOT NULL,
    scopes            TEXT        NOT NULL DEFAULT '',
    status            TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'error')),
    last_sync_at      TIMESTAMPTZ,
    -- A short stable key, never the provider's message: their message can quote a token.
    last_error        TEXT,
    connected_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, provider)
);

CREATE INDEX wearable_connections_sync_idx ON wearable_connections (status, last_sync_at);

-- The one-time state an OAuth round trip carries. Consumed on return, and swept when
-- stale: a state that was never used is an authorisation she started and walked away from.
CREATE TABLE oauth_states (
    state      TEXT        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- WHOOP's own field names, as the sync job sends them. Times come in milliseconds and
-- energy in kilojoules; the scale turns them into SADORA's minutes and kilocalories.
INSERT INTO provider_metric_mappings (provider, provider_metric, metric, provider_unit, scale) VALUES
    ('whoop', 'recovery_score',                  'recovery',            'percent', 1),
    ('whoop', 'resting_heart_rate',              'resting_heart_rate',  'bpm',     1),
    ('whoop', 'hrv_rmssd_milli',                 'hrv',                 'ms',      1),
    ('whoop', 'spo2_percentage',                 'spo2',                'percent', 1),
    ('whoop', 'skin_temp_celsius',               'skin_temperature',    'c',       1),
    ('whoop', 'respiratory_rate',                'respiratory_rate',    'brpm',    1),
    ('whoop', 'asleep_milli',                    'sleep_duration',      'ms',      0.0000166666667),
    ('whoop', 'total_slow_wave_sleep_time_milli','sleep_deep',          'ms',      0.0000166666667),
    ('whoop', 'total_rem_sleep_time_milli',      'sleep_rem',           'ms',      0.0000166666667),
    ('whoop', 'total_light_sleep_time_milli',    'sleep_light',         'ms',      0.0000166666667),
    ('whoop', 'total_awake_time_milli',          'sleep_awake',         'ms',      0.0000166666667),
    ('whoop', 'sleep_performance_percentage',    'sleep_performance',   'percent', 1),
    ('whoop', 'sleep_efficiency_percentage',     'sleep_efficiency',    'percent', 1),
    ('whoop', 'strain',                          'strain',              'score',   1),
    ('whoop', 'kilojoule',                       'active_energy',       'kJ',      0.239005736),
    ('whoop', 'average_heart_rate',              'heart_rate',          'bpm',     1),
    ('whoop', 'weight_kilogram',                 'weight',              'kg',      1),
    -- Sleep she types in herself, from the sleep screen, in minutes.
    ('manual', 'sleep_minutes',                  'sleep_duration',      'min',     1)
ON CONFLICT (provider, provider_metric) DO NOTHING;
