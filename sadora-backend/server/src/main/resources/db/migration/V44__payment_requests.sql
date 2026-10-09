-- "Ask Yaqinim to pay": she asks the person close to her to pay for Premium or a
-- consultation, and when they do, the thing goes to her.
--
-- Three pieces. A request row she makes, which is answered by a payment or closed by her,
-- by them, or by time. Gift plans, which are one-off purchases rather than subscriptions:
-- a store subscription belongs to the account that pays, so a present has to be a product
-- of its own. And a bank of gifted days for the woman who already pays the store herself,
-- spent the day her own subscription ends rather than thrown on top of a renewal the
-- store will overwrite.

ALTER TABLE billing_plans
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'subscription' CHECK (kind IN ('subscription', 'gift'));

INSERT INTO billing_plans
    (id, title, period, price_minor, trial_days, highlighted, position, kind,
     app_store_product_id, google_play_product_id)
VALUES
    ('gift_year',  'Sovg''a · 1 yil', 'year',  29900000, 0, FALSE, 11, 'gift', 'uz.sadora.gift.year',  'gift_year'),
    ('gift_month', 'Sovg''a · 1 oy',  'month',  3990000, 0, FALSE, 12, 'gift', 'uz.sadora.gift.month', 'gift_month');

-- The person who pays for her when it is not her. Null when she paid herself, and for a
-- browser payer, who has no account.
ALTER TABLE payment_transactions
    ADD COLUMN payer_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN refunded_at TIMESTAMPTZ;

-- Whether the person who follows her takes requests to pay. Theirs to switch, on by default.
ALTER TABLE partner_links
    ADD COLUMN accepts_payment_requests BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE payment_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind            TEXT        NOT NULL CHECK (kind IN ('premium', 'consultation')),
    -- A gift plan for Premium; the payer may change month to year and back.
    plan_id         TEXT REFERENCES billing_plans (id),
    -- The pending consultation window the money opens.
    consultation_session_id UUID REFERENCES consultation_sessions (id) ON DELETE CASCADE,
    doctor_id       UUID,
    amount_minor    BIGINT      NOT NULL CHECK (amount_minor > 0),
    note            TEXT CHECK (note IS NULL OR char_length(note) <= 140),
    -- The Yaqinim link it was pushed to, when there was one.
    partner_link_id UUID REFERENCES partner_links (id) ON DELETE SET NULL,
    -- The browser link's token, hashed. Rotated when she shares it again.
    web_token_hash  TEXT UNIQUE,
    status          TEXT        NOT NULL DEFAULT 'open'
        CHECK (status IN ('open', 'paid', 'declined', 'cancelled', 'expired')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL,
    reminded_at     TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    paid_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    transaction_id  UUID REFERENCES payment_transactions (id) ON DELETE SET NULL,
    CONSTRAINT payment_requests_purpose CHECK (
        (kind = 'premium' AND plan_id IS NOT NULL AND consultation_session_id IS NULL) OR
        (kind = 'consultation' AND consultation_session_id IS NOT NULL)
    )
);

-- One open request at a time.
CREATE UNIQUE INDEX payment_requests_one_open_idx ON payment_requests (owner_id) WHERE status = 'open';
CREATE INDEX payment_requests_link_idx ON payment_requests (partner_link_id, status);
CREATE INDEX payment_requests_due_idx ON payment_requests (expires_at) WHERE status = 'open';

ALTER TABLE payment_transactions
    ADD COLUMN payment_request_id UUID REFERENCES payment_requests (id) ON DELETE SET NULL;

-- Gifted days. Applied at once as a subscription when she has nothing that renews by
-- itself; banked otherwise, and spent when her own subscription is gone.
CREATE TABLE premium_gift_credits (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    days            INTEGER     NOT NULL CHECK (days > 0),
    transaction_id  UUID        NOT NULL UNIQUE REFERENCES payment_transactions (id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at      TIMESTAMPTZ,
    subscription_id UUID REFERENCES subscriptions (id) ON DELETE SET NULL,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX premium_gift_credits_pending_idx ON premium_gift_credits (user_id)
    WHERE applied_at IS NULL AND revoked_at IS NULL;
