-- The Owner adds operators from the panel now. A new operator, and one whose password the
-- Owner reset, signs in with a temporary password the Owner has seen; until she sets her
-- own, the panel shows her nothing but her account page and the API refuses the rest.
ALTER TABLE admin_users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;

-- What one operator leaves for the next about an account: "asked for a refund on the 3rd,
-- promised an answer". Account matters only — the panel says so above the field — and the
-- notes go with the account when it is erased.
CREATE TABLE user_support_notes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    admin_id   UUID        REFERENCES admin_users (id) ON DELETE SET NULL,
    body       TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX user_support_notes_user_idx ON user_support_notes (user_id, created_at DESC);
