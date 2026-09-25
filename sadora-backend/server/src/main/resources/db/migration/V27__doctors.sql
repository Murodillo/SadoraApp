-- Doctors: accounts that write in the chat under their real name, with a check mark.
--
-- A doctor is an ordinary account that applied and was approved by an admin. The
-- profile has its own id, and that id — never the account's — is what the feed and
-- the doctor's public page carry: a doctor is public, the account behind her is not.
--
-- The alias rule still holds for everything she wrote before. Her doctor posts carry
-- `doctor_id`; her alias posts do not, and no query joins the two for a reader.

CREATE TABLE doctor_profiles (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    full_name        TEXT        NOT NULL CHECK (char_length(full_name) BETWEEN 5 AND 120),
    specialty        TEXT        NOT NULL,
    workplace        TEXT        NOT NULL CHECK (char_length(workplace) BETWEEN 1 AND 160),
    experience_years INTEGER     NOT NULL CHECK (experience_years BETWEEN 0 AND 70),
    license_number   TEXT        NOT NULL CHECK (char_length(license_number) BETWEEN 1 AND 64),
    bio              TEXT CHECK (char_length(bio) <= 500),
    -- 'pending', 'approved', 'rejected' or 'suspended'. Only 'approved' is shown.
    status           TEXT        NOT NULL DEFAULT 'pending',
    review_note      TEXT,
    reviewed_by      UUID,
    reviewed_at      TIMESTAMPTZ,
    -- Set on the first approval and kept through a suspension: "verified since".
    verified_at      TIMESTAMPTZ,
    submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX doctor_profiles_status_idx ON doctor_profiles (status, submitted_at DESC);

-- The proof an admin reviews. Small JPEGs from the phone, kept in the database: there
-- are a handful per doctor, only an admin ever reads them, and a separate file store
-- would be one more thing to back up and secure for four pictures.
CREATE TABLE doctor_documents (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id  UUID        NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    kind       TEXT        NOT NULL,
    mime_type  TEXT        NOT NULL,
    content    BYTEA       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX doctor_documents_doctor_idx ON doctor_documents (doctor_id, created_at);

-- A post or comment written as a doctor. Cascades with the profile: were the byline
-- to fall back to her alias, it would tie her alias to her name.
ALTER TABLE community_posts
    ADD COLUMN doctor_id UUID REFERENCES doctor_profiles (id) ON DELETE CASCADE;
ALTER TABLE community_comments
    ADD COLUMN doctor_id UUID REFERENCES doctor_profiles (id) ON DELETE CASCADE;

CREATE INDEX community_posts_doctor_idx ON community_posts (doctor_id, created_at DESC)
    WHERE doctor_id IS NOT NULL;
CREATE INDEX community_comments_doctor_idx ON community_comments (doctor_id, post_id)
    WHERE doctor_id IS NOT NULL;
