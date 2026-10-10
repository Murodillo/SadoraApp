-- Likes on comments, the same shape as likes on posts: a like is a row, the count is
-- read from the rows, and a comment that goes takes its likes with it.
CREATE TABLE community_comment_likes (
    comment_id UUID        NOT NULL REFERENCES community_comments (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (comment_id, user_id)
);

-- Erasing an account walks its rows by user.
CREATE INDEX community_comment_likes_user_idx ON community_comment_likes (user_id);
