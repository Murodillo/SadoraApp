-- Profile photos (2026-10-01).
--
-- A woman's photo is hers and her doctors': it shows in her own app and, in a
-- consultation, to the doctor she wrote to — never in the anonymous room, where she is
-- an alias. A doctor's photo is public, like her name: her page, the directory, her
-- bylines, her consultations.
--
-- Stored as the server made them — a square JPEG of at most 512 px, re-encoded, so no
-- EXIF (and no location) survives from the phone. users.avatar_url, unused since V1, is
-- the served path of her photo, versioned by the upload time so caches refresh.

CREATE TABLE user_photos (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    content    BYTEA       NOT NULL,
    size_px    INTEGER     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctor_photos (
    doctor_id  UUID PRIMARY KEY REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    content    BYTEA       NOT NULL,
    size_px    INTEGER     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Read with every byline, so the versioned URL needs no second query.
ALTER TABLE doctor_profiles ADD COLUMN photo_updated_at TIMESTAMPTZ;
