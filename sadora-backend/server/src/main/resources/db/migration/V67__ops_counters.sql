-- What the system health page cannot read off the product's own rows: an SMS the provider
-- refused leaves no challenge behind, and the Telegram poller writes nothing when it works.
-- One row per service per Tashkent day; the writers only ever increment.
CREATE TABLE ops_counters (
    service       TEXT        NOT NULL,
    day           DATE        NOT NULL,
    ok            INTEGER     NOT NULL DEFAULT 0,
    failed        INTEGER     NOT NULL DEFAULT 0,
    last_ok_at    TIMESTAMPTZ,
    last_error_at TIMESTAMPTZ,
    last_error    TEXT,
    PRIMARY KEY (service, day)
);
