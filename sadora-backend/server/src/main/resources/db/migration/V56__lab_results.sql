-- Lab results (2026-10-18): her laboratory sheets, typed in or read from photos.
--
-- One row per sheet, one row per printed line, and the photographed pages beside it.
-- Low/high is not stored: it is worked out from the laboratory's own printed range each
-- time the row is read, so correcting a range corrects the flag with it.

CREATE TABLE lab_reports (
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    taken_on   DATE        NOT NULL,
    place      TEXT,
    note       TEXT,
    scanned    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX lab_reports_user_idx ON lab_reports (user_id, taken_on DESC, id DESC);

-- A line of a sheet. marker_code is set for the markers the app knows (the catalogue
-- lives in the server's code, not here), and is what a chart across sheets follows.
CREATE TABLE lab_values (
    id          UUID PRIMARY KEY,
    report_id   UUID    NOT NULL REFERENCES lab_reports (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL,
    marker_code TEXT,
    name        TEXT    NOT NULL,
    value       DOUBLE PRECISION,
    value_text  TEXT,
    unit        TEXT,
    ref_low     DOUBLE PRECISION,
    ref_high    DOUBLE PRECISION,
    ref_text    TEXT,
    CONSTRAINT lab_values_has_result CHECK (value IS NOT NULL OR value_text IS NOT NULL)
);

CREATE INDEX lab_values_report_idx ON lab_values (report_id, position);

-- The pages, in the database like message photos: there is no object store, and a lab
-- sheet is exactly what must not sit behind a public URL. Deleting the sheet deletes them.
CREATE TABLE lab_report_photos (
    report_id  UUID        NOT NULL REFERENCES lab_reports (id) ON DELETE CASCADE,
    page       INTEGER     NOT NULL,
    mime_type  TEXT        NOT NULL,
    width      INTEGER     NOT NULL,
    height     INTEGER     NOT NULL,
    content    BYTEA       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (report_id, page)
);

-- A sheet sent to her doctor as a card. Read live; a deleted sheet leaves the line's
-- plain-text body behind.
ALTER TABLE community_messages
    ADD COLUMN lab_report_id UUID REFERENCES lab_reports (id) ON DELETE SET NULL;

-- Reading a photo costs a model call, so it is metered like the food scanner — but free
-- accounts get a few a month: one sheet every few weeks is what most women have.
INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('lab_scan', 'Tahlil skaneri — rasmni o''qish', TRUE, TRUE, NULL, 3, 20, NULL)
ON CONFLICT (key) DO NOTHING;
