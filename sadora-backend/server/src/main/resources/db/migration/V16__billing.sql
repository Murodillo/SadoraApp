-- Plans and payments.
--
-- Two rules run through this file. Money is stored in the currency's smallest unit as an
-- integer — tiyin for UZS — because that is what Payme and Click count in and a rounding
-- error in a price is not a rounding error to the person paying. And a payment is a
-- ledger row, never an update in place: a provider that asks the same question twice
-- must get the same answer, so state moves forward through recorded transitions.

CREATE TABLE billing_plans (
    id            TEXT PRIMARY KEY CHECK (id ~ '^[a-z][a-z0-9_]{1,40}$'),
    title         TEXT        NOT NULL,
    period        TEXT        NOT NULL CHECK (period IN ('month', 'year')),
    price_minor   BIGINT      NOT NULL CHECK (price_minor > 0),
    currency      TEXT        NOT NULL DEFAULT 'UZS',
    trial_days    INTEGER     NOT NULL DEFAULT 0 CHECK (trial_days >= 0),
    highlighted   BOOLEAN     NOT NULL DEFAULT FALSE,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    position      INTEGER     NOT NULL DEFAULT 0,
    app_store_product_id   TEXT,
    google_play_product_id TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_transactions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id       TEXT        NOT NULL REFERENCES billing_plans (id),
    provider      TEXT        NOT NULL,
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency      TEXT        NOT NULL DEFAULT 'UZS',
    state         TEXT        NOT NULL DEFAULT 'pending'
        CHECK (state IN ('pending', 'paid', 'cancelled', 'failed')),
    -- The provider's own id for this payment. Unique per provider so a webhook replayed
    -- twice cannot create a second transaction or grant a second subscription.
    external_id   TEXT,
    -- Provider-side timestamps, in their own units, kept for reconciliation.
    provider_created_at BIGINT,
    paid_at       TIMESTAMPTZ,
    cancelled_at  TIMESTAMPTZ,
    cancel_reason INTEGER,
    subscription_id UUID REFERENCES subscriptions (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX payment_transactions_external_idx
    ON payment_transactions (provider, external_id)
    WHERE external_id IS NOT NULL;
CREATE INDEX payment_transactions_user_idx ON payment_transactions (user_id, created_at DESC);
CREATE INDEX payment_transactions_state_idx ON payment_transactions (state, created_at DESC);

-- Every provider callback, as it arrived. Not for the product — for the argument with a
-- provider six weeks later about what they actually sent.
CREATE TABLE payment_callbacks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider      TEXT        NOT NULL,
    method        TEXT,
    transaction_id UUID REFERENCES payment_transactions (id) ON DELETE SET NULL,
    payload       TEXT        NOT NULL,
    outcome       TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX payment_callbacks_day_idx ON payment_callbacks (created_at DESC);

-- The two plans the paywall has been drawing as constants. 299 000 and 39 900 so'm,
-- in tiyin.
INSERT INTO billing_plans
    (id, title, period, price_minor, trial_days, highlighted, position,
     app_store_product_id, google_play_product_id)
VALUES
    ('premium_year',  'Yillik',  'year',  29900000, 7, TRUE,  1, 'uz.sadora.premium.year',  'premium_year'),
    ('premium_month', 'Oylik',   'month',  3990000, 0, FALSE, 2, 'uz.sadora.premium.month', 'premium_month');
