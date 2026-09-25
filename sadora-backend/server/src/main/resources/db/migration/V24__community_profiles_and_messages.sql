-- The secret chat grows a face and a door: an alias profile with a bio, and private
-- messages between two aliases.
--
-- The same rule as V12 holds throughout. A profile is addressed by its alias, which is
-- unique and never changes, so nothing new about an account reaches the wire. The
-- conversation and block tables hold user ids because they must — the server has to
-- know who is talking to whom — and no route returns them.

-- ---------------------------------------------------------------- profile

-- A line she writes about herself, under the alias. Short on purpose: long enough for
-- "38 yosh, ikki bola, perimenopauza", too short for a life story that could name her.
ALTER TABLE community_identities
    ADD COLUMN bio     TEXT CHECK (char_length(bio) <= 160),
    ADD COLUMN dm_open BOOLEAN NOT NULL DEFAULT TRUE;

-- ---------------------------------------------------------------- messages

-- One row per pair. The two ids are stored in a fixed order so the pair is one row
-- whichever side opened it, and each side keeps its own read mark.
CREATE TABLE community_conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_a          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_b          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    a_read_at       TIMESTAMPTZ,
    b_read_at       TIMESTAMPTZ,
    CONSTRAINT community_conversations_ordered CHECK (user_a < user_b),
    UNIQUE (user_a, user_b)
);

CREATE INDEX community_conversations_a_idx ON community_conversations (user_a, last_message_at DESC);
CREATE INDEX community_conversations_b_idx ON community_conversations (user_b, last_message_at DESC);

CREATE TABLE community_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID        NOT NULL REFERENCES community_conversations (id) ON DELETE CASCADE,
    sender_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body            TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    -- 'visible' or 'hidden', the same two states as a post; hidden by moderation.
    status          TEXT        NOT NULL DEFAULT 'visible',
    hidden_reason   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX community_messages_thread_idx ON community_messages (conversation_id, created_at);
CREATE INDEX community_messages_sender_idx ON community_messages (sender_id, created_at DESC);

-- A block closes the door both ways: neither side can message the other, and the
-- blocked one is told nothing beyond "not available".
CREATE TABLE community_blocks (
    blocker_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id)
);

-- ---------------------------------------------------------------- moderation

-- A message can be reported like a post or a comment; the one-target rule widens.
ALTER TABLE community_reports
    DROP CONSTRAINT community_reports_one_target,
    ADD COLUMN message_id UUID REFERENCES community_messages (id) ON DELETE CASCADE,
    ADD CONSTRAINT community_reports_one_target CHECK (
        (CASE WHEN post_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN comment_id IS NULL THEN 0 ELSE 1 END) +
        (CASE WHEN message_id IS NULL THEN 0 ELSE 1 END) = 1
    );

CREATE UNIQUE INDEX community_reports_message_once ON community_reports (reporter_id, message_id)
    WHERE message_id IS NOT NULL;
