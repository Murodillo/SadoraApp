-- The doctor's PDF report (2026-10-19). Made on demand and not kept — except when she sends
-- it into a consultation, where the file is the message and lives beside it like a photo.

CREATE TABLE community_message_documents (
    message_id UUID PRIMARY KEY REFERENCES community_messages (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    mime_type  TEXT        NOT NULL,
    content    BYTEA       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('doctor_report_pdf', 'Shifokor uchun PDF hisobot', TRUE, TRUE, NULL, 1, NULL, NULL)
ON CONFLICT (key) DO NOTHING;
