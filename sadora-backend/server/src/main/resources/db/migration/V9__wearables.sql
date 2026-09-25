-- The wearable normalisation layer.
--
-- Health data, under the V3 rules. The shape exists so a new provider is rows in
-- `provider_metric_mappings` rather than a migration and a deploy — which is what
-- section 12 of the admin panel is for, and what makes the v1.1 integrations cheap.

CREATE TABLE health_samples (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider      TEXT             NOT NULL,
    -- The provider's own id. Re-syncing the same sample updates this row rather than
    -- adding a second one, which is the difference between a step count and double it.
    external_id   TEXT             NOT NULL,
    metric        TEXT             NOT NULL,
    value         DOUBLE PRECISION NOT NULL,
    unit          TEXT             NOT NULL,
    started_at    TIMESTAMPTZ      NOT NULL,
    ended_at      TIMESTAMPTZ,
    -- The calendar day in the user's timezone, decided at ingest so a query never has
    -- to guess which day a 23:50 sample belongs to.
    local_date    DATE             NOT NULL,
    source_device TEXT,
    recorded_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (user_id, provider, external_id)
);

CREATE INDEX health_samples_user_day_idx ON health_samples (user_id, local_date DESC, metric);
CREATE INDEX health_samples_provider_idx ON health_samples (user_id, provider, recorded_at DESC);

-- Reduced per day and metric. Recomputed from the samples on ingest rather than
-- incremented, so a corrected or re-synced sample cannot leave the total wrong.
CREATE TABLE daily_health_metrics (
    user_id      UUID             NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    local_date   DATE             NOT NULL,
    metric       TEXT             NOT NULL,
    value        DOUBLE PRECISION NOT NULL,
    unit         TEXT             NOT NULL,
    sample_count INTEGER          NOT NULL DEFAULT 0,
    providers    TEXT             NOT NULL DEFAULT '',
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, local_date, metric)
);

CREATE INDEX daily_health_metrics_day_idx ON daily_health_metrics (user_id, local_date DESC);

-- Provider metric name to canonical metric, with the unit conversion. Editable from the
-- admin panel: a provider renaming a field is a row change, not a release.
CREATE TABLE provider_metric_mappings (
    provider        TEXT             NOT NULL,
    provider_metric TEXT             NOT NULL,
    metric          TEXT             NOT NULL,
    provider_unit   TEXT,
    scale           DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    active          BOOLEAN          NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, provider_metric)
);
