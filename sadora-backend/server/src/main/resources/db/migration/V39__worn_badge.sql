-- The one badge she wears beside her name and after her alias. Only the key is kept:
-- the tier shown is always the highest she has reached of it, read from user_badges,
-- so the medal others see grows with her without her choosing it again.
CREATE TABLE user_worn_badge (
    user_id    UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    badge      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
