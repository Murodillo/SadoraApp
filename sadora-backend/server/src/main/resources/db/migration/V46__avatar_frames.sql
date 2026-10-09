-- Avatar frames: a ring around her photo and her alias.
--
-- The catalogue lives in the contract (AvatarFrames); this is what the panel edits about
-- it — a Gul price, a money price, whether it is offered at all — plus what she owns and
-- what she wears. A badge's frame has no row of ownership: owning the badge's tier is
-- owning the frame, so a woman who reached it before frames existed has it already.

CREATE TABLE frame_products (
    frame                  TEXT PRIMARY KEY,
    -- A Gul frame's price; null for the others.
    coin_cost              INTEGER CHECK (coin_cost IS NULL OR coin_cost > 0),
    -- A paid frame's price in tiyin, one price everywhere. The stores keep their own copy.
    price_minor            BIGINT CHECK (price_minor IS NULL OR price_minor > 0),
    currency               TEXT        NOT NULL DEFAULT 'UZS',
    app_store_product_id   TEXT,
    google_play_product_id TEXT,
    -- Off: not offered to anyone new. Whoever owns it keeps it.
    active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO frame_products (frame, coin_cost, price_minor, app_store_product_id, google_play_product_id) VALUES
    ('tulip',       300,  NULL, NULL, NULL),
    ('lavender',    500,  NULL, NULL, NULL),
    ('sakura',      800,  NULL, NULL, NULL),
    ('moon',        1200, NULL, NULL, NULL),
    ('rose',        1800, NULL, NULL, NULL),
    -- 29 000 and 49 000 so'm.
    ('rainbow',     NULL, 2900000, 'uz.sadora.frame.rainbow', 'uz.sadora.frame.rainbow'),
    ('humo_wing',   NULL, 4900000, 'uz.sadora.frame.humo_wing', 'uz.sadora.frame.humo_wing');

-- A frame she bought, with Gul or money, or was given by an operator. A refund marks the
-- row revoked rather than deleting it, so the panel can still see what happened.
CREATE TABLE user_frames_owned (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    frame          TEXT        NOT NULL,
    source         TEXT        NOT NULL CHECK (source IN ('coins', 'paid', 'admin')),
    -- The payment that bought it. Unique, so a provider's retry cannot grant it twice.
    transaction_id UUID UNIQUE REFERENCES payment_transactions (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at     TIMESTAMPTZ
);

-- One live copy of a frame per woman.
CREATE UNIQUE INDEX user_frames_owned_live_idx ON user_frames_owned (user_id, frame) WHERE revoked_at IS NULL;

-- The one she wears. Only the key: whether she still owns it is read live, so a refund or
-- a lost badge takes it off everywhere without touching this row.
CREATE TABLE user_worn_frame (
    user_id    UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    frame      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A paid frame is a fourth thing a one-off payment can buy, and still exactly one of them.
ALTER TABLE payment_transactions ADD COLUMN frame TEXT;
ALTER TABLE payment_transactions DROP CONSTRAINT payment_transactions_one_purpose;
ALTER TABLE payment_transactions ADD CONSTRAINT payment_transactions_one_purpose CHECK (
    (plan_id IS NOT NULL AND consultation_session_id IS NULL AND pet IS NULL AND frame IS NULL) OR
    (plan_id IS NULL AND consultation_session_id IS NOT NULL AND pet IS NULL AND frame IS NULL) OR
    (plan_id IS NULL AND consultation_session_id IS NULL AND pet IS NOT NULL AND frame IS NULL) OR
    (plan_id IS NULL AND consultation_session_id IS NULL AND pet IS NULL AND frame IS NOT NULL)
);

-- Yaqinim can give her one.
ALTER TABLE payment_requests ADD COLUMN frame TEXT;
ALTER TABLE payment_requests DROP CONSTRAINT payment_requests_kind_check;
ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_kind_check
    CHECK (kind IN ('premium', 'consultation', 'pet', 'frame'));
ALTER TABLE payment_requests DROP CONSTRAINT payment_requests_purpose;
ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_purpose CHECK (
    (kind = 'premium' AND plan_id IS NOT NULL AND consultation_session_id IS NULL) OR
    (kind = 'consultation' AND consultation_session_id IS NOT NULL) OR
    (kind = 'pet' AND pet IS NOT NULL AND plan_id IS NULL AND consultation_session_id IS NULL) OR
    (kind = 'frame' AND frame IS NOT NULL AND plan_id IS NULL AND consultation_session_id IS NULL)
);

-- The paid frames' sale, off until the store products exist. Gul and badge frames do not
-- wait for it.
INSERT INTO feature_flags (key, description, enabled, default_value)
VALUES ('frame_sale', 'Pullik avatar ramkalari sotuvda', TRUE, FALSE)
ON CONFLICT (key) DO NOTHING;
