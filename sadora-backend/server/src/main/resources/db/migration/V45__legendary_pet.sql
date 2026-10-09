-- Humo, the legendary pet: bought once for real money and hers from then on.
--
-- It is not a plan. A plan is time — a month, a year — and this is a thing she keeps, so
-- it has its own price row, and a payment says which pet it buys the way a consultation's
-- payment says which window it opens. Ownership is a row per purchase: a refund marks it
-- revoked rather than deleting it, so the panel can still see what happened.

CREATE TABLE pet_products (
    pet                    TEXT PRIMARY KEY,
    -- One price everywhere, in tiyin. The stores carry their own copy, set in their consoles.
    price_minor            BIGINT      NOT NULL CHECK (price_minor > 0),
    currency               TEXT        NOT NULL DEFAULT 'UZS',
    app_store_product_id   TEXT,
    google_play_product_id TEXT,
    active                 BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 499 000 so'm.
INSERT INTO pet_products (pet, price_minor, app_store_product_id, google_play_product_id)
VALUES ('humo', 49900000, 'uz.sadora.pet.humo', 'uz.sadora.pet.humo');

-- What a one-off payment buys, when it buys a pet: a third purpose beside a plan and a
-- consultation window, and still exactly one of them.
ALTER TABLE payment_transactions ADD COLUMN pet TEXT;
ALTER TABLE payment_transactions DROP CONSTRAINT payment_transactions_one_purpose;
ALTER TABLE payment_transactions ADD CONSTRAINT payment_transactions_one_purpose CHECK (
    (plan_id IS NOT NULL AND consultation_session_id IS NULL AND pet IS NULL) OR
    (plan_id IS NULL AND consultation_session_id IS NOT NULL AND pet IS NULL) OR
    (plan_id IS NULL AND consultation_session_id IS NULL AND pet IS NOT NULL)
);

CREATE TABLE user_pets_owned (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    pet            TEXT        NOT NULL,
    -- The payment that bought it. Unique, so a provider's retry cannot grant it twice.
    transaction_id UUID UNIQUE REFERENCES payment_transactions (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at     TIMESTAMPTZ
);

-- One live copy of a pet per woman.
CREATE UNIQUE INDEX user_pets_owned_live_idx ON user_pets_owned (user_id, pet) WHERE revoked_at IS NULL;

-- The pet she had before a bought one replaced it, so a refund can give it back. And the
-- one-off offer: shown once, ever.
ALTER TABLE user_pet ADD COLUMN previous_pet TEXT;

CREATE TABLE pet_offers_seen (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    pet     TEXT        NOT NULL,
    seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, pet)
);

-- Yaqinim can give it to her.
ALTER TABLE payment_requests ADD COLUMN pet TEXT;
ALTER TABLE payment_requests DROP CONSTRAINT payment_requests_kind_check;
ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_kind_check
    CHECK (kind IN ('premium', 'consultation', 'pet'));
ALTER TABLE payment_requests DROP CONSTRAINT payment_requests_purpose;
ALTER TABLE payment_requests ADD CONSTRAINT payment_requests_purpose CHECK (
    (kind = 'premium' AND plan_id IS NOT NULL AND consultation_session_id IS NULL) OR
    (kind = 'consultation' AND consultation_session_id IS NOT NULL) OR
    (kind = 'pet' AND pet IS NOT NULL AND plan_id IS NULL AND consultation_session_id IS NULL)
);

-- The sale itself, off until the store products exist. An operator turns it on.
INSERT INTO feature_flags (key, description, enabled, default_value)
VALUES ('pet_humo_sale', 'Humo (legendar hamroh) sotuvda', TRUE, FALSE)
ON CONFLICT (key) DO NOTHING;
