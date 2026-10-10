-- How many readers a post has reached.
--
-- One row per reader and post is what makes the number a count of people rather than of
-- scrolls. It is never read back out as a list: nobody is told who saw what.
CREATE TABLE community_post_views (
    post_id    UUID        NOT NULL REFERENCES community_posts (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

-- Erasing an account walks its rows by user.
CREATE INDEX community_post_views_user_idx ON community_post_views (user_id);

-- Kept on the post so a feed page does not count the table above on every read, and so
-- the number stays what it was when a reader's account, and her row, is erased.
ALTER TABLE community_posts ADD COLUMN view_count INTEGER NOT NULL DEFAULT 0;
