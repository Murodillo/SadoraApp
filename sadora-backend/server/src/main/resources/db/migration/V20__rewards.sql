-- Streaks, Nur, the shop, referrals, and the home layout.
--
-- None of this is health data. It is engagement bookkeeping, and it deliberately lives
-- apart from the tables V3, V5, V7 and V17 protect: an operator working the shop has no
-- business reading a cycle, and the reverse join does not exist here to be written.
--
-- The one rule that shapes the whole file: nothing in it pays for a health *outcome*.
-- Coins are earned for opening the app and for logging, never for sleeping well or for
-- a number going the right way. Paying for outcomes would put a price on her body, and
-- it would teach the log to lie.

-- The run of consecutive days. One row per user, rewritten on each first open of a day.
CREATE TABLE user_streaks (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- Includes today once she has opened the app today. A missed day resets it to 1 on
    -- her return, never to 0: the day she comes back is day one, not a failure.
    current_days INTEGER     NOT NULL DEFAULT 0 CHECK (current_days >= 0),
    longest_days INTEGER     NOT NULL DEFAULT 0 CHECK (longest_days >= 0),
    -- The calendar day in *her* timezone, which is why this is a DATE and not a
    -- timestamp: a streak resets at her midnight, not the server's.
    last_open_on DATE,
    total_days   INTEGER     NOT NULL DEFAULT 0 CHECK (total_days >= 0),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- What each action pays. Editable from the panel, which is the point: the economy is
-- tuned without an app release, and the app reads the rates rather than hard-coding them.
CREATE TABLE coin_rules (
    reason      TEXT PRIMARY KEY,
    amount      INTEGER     NOT NULL CHECK (amount >= 0),
    -- How many times a day the reason may pay. NULL means once, enforced by the unique
    -- index on the ledger below; a number above 1 allows a counted award.
    daily_cap   INTEGER CHECK (daily_cap IS NULL OR daily_cap > 0),
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    description TEXT        NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every coin that has ever moved. The balance is SUM(amount) over this table and is
-- deliberately not cached in a column: a cached balance drifts, and the row that would
-- explain the drift is the one nobody wrote.
CREATE TABLE coin_ledger (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Positive earns, negative spends. One column, so the sum is the balance.
    amount     INTEGER     NOT NULL CHECK (amount <> 0),
    reason     TEXT        NOT NULL,
    -- Her calendar day, for the once-a-day rules. NULL for awards that are not daily.
    earned_on  DATE,
    -- What the row is about: a redemption id, an invited user id, a streak length.
    reference  TEXT,
    -- Only ever set by an operator's manual adjustment; it lands on the audit row too.
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX coin_ledger_user_idx ON coin_ledger (user_id, created_at DESC);

-- The once-a-day guarantee, in the schema rather than in a service that could forget it:
-- two opens racing each other cannot both award the daily coin.
CREATE UNIQUE INDEX coin_ledger_once_daily_idx
    ON coin_ledger (user_id, reason, earned_on)
    WHERE earned_on IS NOT NULL AND reference IS NULL;

-- Counted awards — doses, meals — are unique per day *and* per thing, so the cap is a
-- count of rows rather than a flag.
CREATE UNIQUE INDEX coin_ledger_once_per_reference_idx
    ON coin_ledger (user_id, reason, reference)
    WHERE reference IS NOT NULL;

INSERT INTO coin_rules (reason, amount, daily_cap, description) VALUES
    ('daily_open',        10,  NULL, 'Ilovani kunda birinchi marta ochish'),
    ('streak_milestone',  50,  NULL, 'Streak bosqichi — 3, 7, 14, 30, 60, 100, 365 kun'),
    ('check_in',          5,   NULL, 'Kunlik kayfiyat belgisi'),
    ('water_goal',        10,  NULL, 'Suv maqsadiga yetish'),
    ('dose_taken',        3,   4,    'Dori qabulini tasdiqlash'),
    ('meal_logged',       4,   3,    'Ovqat qo''shish'),
    ('journal_entry',     6,   1,    'Kundalikka yozuv'),
    ('practice',          6,   2,    'Nafas yoki meditatsiya mashg''uloti'),
    -- Paid per article rather than per open: the ledger row is keyed by the slug, so
    -- the same piece never pays twice however often it is reopened.
    ('article_read',      5,   3,    'Bilim maqolasini ochish'),
    ('referral_joined',   200, NULL, 'Do''st taklif kodi bilan ro''yxatdan o''tdi'),
    ('referral_welcome',  100, NULL, 'Taklif kodi bilan kelgan foydalanuvchiga sovg''a');

-- Her invite code. One per account, generated on first read rather than at sign-up, so
-- an account that never opens the referral screen never takes a code out of the space.
CREATE TABLE referral_codes (
    code       TEXT PRIMARY KEY CHECK (char_length(code) BETWEEN 4 AND 16),
    user_id    UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Who came in on whose code. The invited account is the primary key: a code can be used
-- by many people, but a person arrives once, and self-invites are refused by the CHECK.
CREATE TABLE referral_claims (
    invited_user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    inviter_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code            TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (invited_user_id <> inviter_user_id)
);

CREATE INDEX referral_claims_inviter_idx ON referral_claims (inviter_user_id);

-- What Nur buys. Premium is granted by the server; a vitamin or a device is a partner's
-- product and what the coins buy is a discount on it — which is why price and percentage
-- sit next to the coin cost rather than instead of it.
CREATE TABLE shop_products (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug             TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9-]{2,64}$'),
    kind             TEXT        NOT NULL CHECK (kind IN ('premium', 'vitamin', 'device')),
    title            TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 120),
    brand            TEXT,
    description      TEXT,
    emoji            TEXT,
    -- Retail price in so'm. NULL for Premium, where there is nothing to discount.
    price_uzs        BIGINT CHECK (price_uzs IS NULL OR price_uzs >= 0),
    discount_percent INTEGER     NOT NULL DEFAULT 0 CHECK (discount_percent BETWEEN 0 AND 100),
    coin_cost        INTEGER     NOT NULL DEFAULT 0 CHECK (coin_cost >= 0),
    -- Days of Premium granted. Only ever set on a premium row.
    premium_days     INTEGER CHECK (premium_days IS NULL OR premium_days > 0),
    -- NULL is unlimited. A number is decremented as codes are issued.
    stock            INTEGER CHECK (stock IS NULL OR stock >= 0),
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    position         INTEGER     NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- A premium row grants days; a partner row discounts a price. Neither shape makes
    -- sense as the other, and a row that is both is a pricing bug on a live shop.
    CHECK (
        (kind = 'premium' AND premium_days IS NOT NULL)
        OR (kind <> 'premium' AND price_uzs IS NOT NULL)
    )
);

CREATE INDEX shop_products_visible_idx ON shop_products (active, kind, position);

-- What she was given for her coins. The code is the whole product for a partner item.
CREATE TABLE shop_redemptions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Restricted rather than cascaded: deleting a product must not quietly delete the
    -- codes people are holding. A product that has been redeemed is deactivated, not
    -- dropped.
    product_id       UUID        NOT NULL REFERENCES shop_products (id) ON DELETE RESTRICT,
    coin_cost        INTEGER     NOT NULL CHECK (coin_cost >= 0),
    discount_percent INTEGER     NOT NULL DEFAULT 0,
    code             TEXT        NOT NULL UNIQUE,
    status           TEXT        NOT NULL DEFAULT 'issued'
                     CHECK (status IN ('issued', 'used', 'expired', 'cancelled')),
    expires_at       TIMESTAMPTZ,
    used_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX shop_redemptions_user_idx ON shop_redemptions (user_id, created_at DESC);

-- The starting shop. Real partners and real prices are the operator's to enter; these
-- are the shape of the thing, so the screen has something to draw on a fresh database.
INSERT INTO shop_products (slug, kind, title, brand, description, emoji, price_uzs, discount_percent, coin_cost, premium_days, position) VALUES
    ('premium-7',    'premium', 'Premium — 7 kun',  NULL, 'Bir hafta to''liq kirish: AI suhbat, tahlillar tarixi, Bilim.', '✨', NULL, 0,  700,  7,    0),
    ('premium-30',   'premium', 'Premium — 30 kun', NULL, 'Bir oy to''liq kirish.', '💎', NULL, 0, 2500, 30,   1),
    ('vitamin-d3',   'vitamin', 'D3 vitamini 2000 IU', 'Solgar',    'Quyosh kam bo''lgan oylarda eng ko''p so''raladigan qo''shimcha.', '☀️', 145000, 15, 300, NULL, 10),
    ('vitamin-k2',   'vitamin', 'K2 vitamini MK-7',    'Now Foods', 'Ko''pincha D3 bilan birga olinadi.', '🦴', 165000, 15, 320, NULL, 11),
    ('vitamin-zinc', 'vitamin', 'Rux (zinc) 25 mg',    'Nature''s Bounty', 'Immunitet va teri uchun.', '🛡️', 98000, 20, 260, NULL, 12),
    ('vitamin-biotin','vitamin','Biotin 5000 mcg',     'Solgar',    'Soch va tirnoq uchun.', '💇', 132000, 15, 300, NULL, 13),
    ('vitamin-iron', 'vitamin', 'Temir + C vitamini',  'Solgar',    'Gemoglobin past bo''lganda shifokor tavsiyasi bilan.', '🩸', 158000, 15, 320, NULL, 14),
    ('vitamin-omega','vitamin', 'Omega-3 1000 mg',     'Now Foods', 'Yurak va teri uchun.', '🐟', 210000, 10, 380, NULL, 15),
    ('vitamin-mg',   'vitamin', 'Magniy B6',           'Doppelherz','Uyqu va mushak tirishishi uchun.', '🌙', 120000, 15, 280, NULL, 16),
    ('vitamin-folate','vitamin','Folat 400 mcg',       'Solgar',    'Homiladorlikni rejalashtirayotganlar uchun.', '🌱', 115000, 20, 280, NULL, 17),
    ('device-whoop',  'device', 'WHOOP 4.0',           'WHOOP',     'Tiklanish va uyqu kuzatuvi. Ekransiz bilaguzuk.', '⌚', 3900000, 10, 4000, NULL, 20),
    ('device-apple-watch','device','Apple Watch SE',   'Apple',     'Pulse, uyqu, faollik — Apple Health orqali ulanadi.', '⌚', 3200000, 8, 3500, NULL, 21),
    ('device-galaxy-watch','device','Galaxy Watch 7',  'Samsung',   'Samsung Health orqali ulanadi.', '⌚', 3100000, 10, 3400, NULL, 22),
    ('device-huawei-band','device','Huawei Band 9',    'Huawei',    'Arzon va uzoq quvvat — birinchi qurilma uchun.', '⌚', 590000, 15, 900, NULL, 23),
    ('device-mi-band','device', 'Xiaomi Smart Band 9', 'Xiaomi',    'Qadam, uyqu va pulse.', '⌚', 620000, 15, 950, NULL, 24),
    ('device-fitbit','device',  'Fitbit Inspire 3',    'Fitbit',    'Uyqu bosqichlari va kunlik faollik.', '⌚', 1450000, 12, 1800, NULL, 25),
    ('device-garmin','device',  'Garmin Vivosmart 5',  'Garmin',    'Stress va uyqu kuzatuvi.', '⌚', 1900000, 10, 2200, NULL, 26),
    ('device-oura', 'device',   'Oura Ring Gen 3',     'Oura',      'Barmoqdagi uzuk — uyqu va tiklanish uchun eng aniqlaridan.', '💍', 4200000, 8, 4500, NULL, 27);

-- How Today is arranged. A preference, not health data: no consent gates it, and it
-- follows the account to a new phone, which is the reason it is here and not on device.
CREATE TABLE home_widgets (
    user_id    UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    widget_key TEXT    NOT NULL,
    position   INTEGER NOT NULL DEFAULT 0,
    visible    BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (user_id, widget_key)
);

-- Asked at sign-up: owning a watch or a band changes where the flow ends. NULL means
-- the question was skipped or predates it, which stays different from "no".
ALTER TABLE users ADD COLUMN has_wearable BOOLEAN;
