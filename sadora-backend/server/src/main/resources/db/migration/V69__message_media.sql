-- Photos and videos in messages (2026-10-20). The bytes are files under MEDIA_DIR/messages,
-- sealed with a key of their own; the row keeps that key wrapped by MEDIA_MASTER_KEY, so a
-- copy of the directory alone opens nothing.
--
-- A row is born when the upload starts, without a message. Sending the message attaches
-- it; an upload nobody sent is deleted after a day, and its files follow.

CREATE TABLE message_media (
    id          UUID PRIMARY KEY,
    owner_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    message_id  UUID REFERENCES community_messages (id) ON DELETE CASCADE,
    position    INTEGER     NOT NULL DEFAULT 0,
    kind        TEXT        NOT NULL CHECK (kind IN ('photo', 'video')),
    status      TEXT        NOT NULL CHECK (status IN ('uploading', 'ready')),
    mime_type   TEXT        NOT NULL,
    -- What the upload declared; once ready, the stored file's plaintext size.
    size_bytes  BIGINT      NOT NULL CHECK (size_bytes > 0),
    thumb_bytes BIGINT      NOT NULL DEFAULT 0,
    width       INTEGER     NOT NULL DEFAULT 0,
    height      INTEGER     NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    wrapped_key TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ready_at    TIMESTAMPTZ,
    attached_at TIMESTAMPTZ
);

CREATE INDEX message_media_by_message ON message_media (message_id, position) WHERE message_id IS NOT NULL;
CREATE INDEX message_media_unattached ON message_media (created_at) WHERE message_id IS NULL;
CREATE INDEX message_media_by_owner ON message_media (owner_id);

-- Videos and albums in consultations. Off until the media backup runs.
INSERT INTO feature_flags (key, description, enabled, default_value)
VALUES ('chat_media', 'Konsultatsiyada video va albom (bir xabarda 10 tagacha rasm/video)', TRUE, FALSE)
ON CONFLICT (key) DO NOTHING;

-- The day's allowance, counted when an upload starts: 200 files and 1 GB, for everyone.
INSERT INTO feature_definitions
    (key, description, free_enabled, premium_enabled,
     free_daily_limit, free_monthly_limit, premium_daily_limit, premium_monthly_limit)
VALUES ('chat_media', 'Chatda rasm va video — kuniga nechta fayl', TRUE, TRUE, 200, NULL, 200, NULL),
       ('chat_media_mb', 'Chatda rasm va video — kuniga necha MB', TRUE, TRUE, 1024, NULL, 1024, NULL)
ON CONFLICT (key) DO NOTHING;
