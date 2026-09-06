-- The secret chat, and two onboarding answers the app collects but had nowhere to send.
--
-- Community content is user-generated text about health, so it sits between the two
-- existing rules: it is not a health *record* (no cycle, no symptom rows), but it is
-- written under an alias for a reason. The alias table is the only join from a post
-- back to an account, and the admin API never reads it — moderation acts on post ids,
-- and a restriction is applied to the author *through* the post rather than by naming
-- her. Read the admin routes with that in mind: none of them accept or return a user id.

-- ---------------------------------------------------------------- identity

-- One alias per account, assigned on first visit and never changed. UNIQUE so two
-- people can never post as "Sokin Bulut" and be mistaken for one another.
CREATE TABLE community_identities (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    alias      TEXT        NOT NULL UNIQUE,
    tint       INTEGER     NOT NULL DEFAULT 0 CHECK (tint BETWEEN 0 AND 3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- content

CREATE TABLE community_posts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    topic         TEXT        NOT NULL,
    body          TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 2000),
    -- 'visible' or 'hidden'. Hidden by a moderator, or automatically once enough
    -- readers have reported it; the reason says which.
    status        TEXT        NOT NULL DEFAULT 'visible',
    hidden_reason TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX community_posts_feed_idx ON community_posts (status, created_at DESC);
CREATE INDEX community_posts_topic_idx ON community_posts (topic, status, created_at DESC);
CREATE INDEX community_posts_user_idx ON community_posts (user_id, created_at DESC);

CREATE TABLE community_comments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id       UUID        NOT NULL REFERENCES community_posts (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body          TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    status        TEXT        NOT NULL DEFAULT 'visible',
    hidden_reason TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX community_comments_post_idx ON community_comments (post_id, created_at);
CREATE INDEX community_comments_user_idx ON community_comments (user_id, created_at DESC);

-- ---------------------------------------------------------------- reactions

-- A like is a row, not a counter: the count is derived, so an unlike can never leave
-- the number one off, and "did she like this" is a primary-key lookup.
CREATE TABLE community_post_likes (
    post_id    UUID        NOT NULL REFERENCES community_posts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

CREATE TABLE community_post_saves (
    post_id    UUID        NOT NULL REFERENCES community_posts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

CREATE INDEX community_post_saves_user_idx ON community_post_saves (user_id, created_at DESC);

-- ---------------------------------------------------------------- moderation

-- A report targets exactly one thing. One report per reader per target — a second tap
-- is not a second complaint.
CREATE TABLE community_reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    post_id     UUID        REFERENCES community_posts (id) ON DELETE CASCADE,
    comment_id  UUID        REFERENCES community_comments (id) ON DELETE CASCADE,
    reason      TEXT        NOT NULL,
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    resolved_by UUID,
    -- 'dismissed' or 'hidden' once resolved.
    resolution  TEXT,
    CONSTRAINT community_reports_one_target CHECK ((post_id IS NULL) <> (comment_id IS NULL))
);

CREATE UNIQUE INDEX community_reports_post_once ON community_reports (reporter_id, post_id)
    WHERE post_id IS NOT NULL;
CREATE UNIQUE INDEX community_reports_comment_once ON community_reports (reporter_id, comment_id)
    WHERE comment_id IS NOT NULL;
CREATE INDEX community_reports_open_idx ON community_reports (created_at DESC)
    WHERE resolved_at IS NULL;

-- An author a moderator has silenced. Reached only through one of her posts, so the
-- moderator never learns who she is; expires on its own when `until` is set.
CREATE TABLE community_restrictions (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    reason     TEXT        NOT NULL,
    until      TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The app ships the secret chat as a first-class feature now, so the flag opens by
-- default; it stays a kill switch.
UPDATE feature_flags
SET default_value = TRUE,
    description   = 'Maxfiy chat — o''chirilsa lenta va yozish butunlay yopiladi',
    updated_at    = now()
WHERE key = 'community';

-- ---------------------------------------------------------------- onboarding

-- "Did a doctor recommend SADORA?" Account data, not health data: it is about how she
-- found the app, and the dashboard counts it. NULL is "skipped", which the question
-- allows.
ALTER TABLE users ADD COLUMN referred_by_doctor BOOLEAN;
