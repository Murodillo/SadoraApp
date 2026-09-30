-- Private messages grow up: consultations with a verified doctor, photos, and the
-- patient's health record attached for her doctor.
--
-- A consultation is a conversation like any other, between the patient's account and
-- the doctor's, marked with the doctor profile it is held under. The same two accounts
-- may also know each other as aliases; those are two different threads, so the one
-- pair-per-row rule becomes one rule per kind.

-- ---------------------------------------------------------------- consultations

ALTER TABLE community_conversations
    ADD COLUMN doctor_id  UUID REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    -- The window: set when the patient opens it, again when she reopens it.
    ADD COLUMN opened_at  TIMESTAMPTZ,
    ADD COLUMN expires_at TIMESTAMPTZ,
    -- Set when the doctor closes it before the window runs out.
    ADD COLUMN closed_at  TIMESTAMPTZ,
    ADD CONSTRAINT community_conversations_consultation_window CHECK (
        doctor_id IS NULL OR (opened_at IS NOT NULL AND expires_at IS NOT NULL)
    );

ALTER TABLE community_conversations DROP CONSTRAINT community_conversations_user_a_user_b_key;

CREATE UNIQUE INDEX community_conversations_alias_pair
    ON community_conversations (user_a, user_b) WHERE doctor_id IS NULL;
CREATE UNIQUE INDEX community_conversations_consultation_pair
    ON community_conversations (user_a, user_b, doctor_id) WHERE doctor_id IS NOT NULL;
CREATE INDEX community_conversations_doctor_idx
    ON community_conversations (doctor_id, last_message_at DESC) WHERE doctor_id IS NOT NULL;

-- Whether she is taking consultations right now; she switches it in her app.
ALTER TABLE doctor_profiles
    ADD COLUMN accepts_consultations BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------------------------------------------------------------- messages

-- 'text', 'image' (the photo is in community_message_images, the body its caption) or
-- 'record' (the patient's record, assembled when the doctor opens it; no body).
ALTER TABLE community_messages
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'text',
    DROP CONSTRAINT community_messages_body_check,
    ADD CONSTRAINT community_messages_body_length CHECK (char_length(body) <= 1000),
    ADD CONSTRAINT community_messages_text_not_empty CHECK (kind <> 'text' OR char_length(body) >= 1);

-- The photo itself, kept apart so listing a thread never reads a megabyte per row.
-- Like the doctor's documents it lives in the database: there is no object store yet,
-- and a photo in a private thread is exactly what must not sit behind a public URL.
CREATE TABLE community_message_images (
    message_id UUID PRIMARY KEY REFERENCES community_messages (id) ON DELETE CASCADE,
    mime_type  TEXT        NOT NULL,
    width      INTEGER     NOT NULL,
    height     INTEGER     NOT NULL,
    content    BYTEA       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
