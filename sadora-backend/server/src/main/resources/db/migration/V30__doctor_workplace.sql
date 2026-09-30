-- The doctor's working day, and paid consultations (2026-09-30).
--
--   consultation_sessions   each 24-hour window, with its price, payment, first reply,
--                           the doctor's summary and the patient's rating
--   doctor hours and busy   when she answers, and a switch for "not now"
--   quick replies, notes    her own shortcuts and her own notes on a patient
--   app settings            numbers an operator changes without a deploy: the commission
--   doctor payouts          what Sadora has paid her out, so a balance can be kept
--   push routing            which app a device is, which app a push is for, and where a
--                           tap on it should land

-- ---------------------------------------------------------------- consultation sessions

CREATE TABLE consultation_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id     UUID        NOT NULL REFERENCES community_conversations (id) ON DELETE CASCADE,
    doctor_id           UUID        NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    patient_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Set when the window opens: at once when free, on payment when paid.
    opened_at           TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    -- 'doctor' when she ended it, 'expired' when the window ran out, 'refund' when it
    -- ended unanswered and the money is owed back.
    closed_reason       TEXT,
    -- In tiyin, as billing keeps money; 0 is a free consultation.
    price_minor         BIGINT      NOT NULL DEFAULT 0 CHECK (price_minor >= 0),
    commission_percent  INTEGER     NOT NULL DEFAULT 0 CHECK (commission_percent BETWEEN 0 AND 100),
    -- 'free', 'pending', 'paid', 'refund_due', 'refunded'
    payment_state       TEXT        NOT NULL DEFAULT 'free',
    first_reply_at      TIMESTAMPTZ,
    summary             TEXT CHECK (char_length(summary) <= 2000),
    summary_at          TIMESTAMPTZ,
    rating              SMALLINT CHECK (rating BETWEEN 1 AND 5),
    review              TEXT CHECK (char_length(review) <= 1000),
    rated_at            TIMESTAMPTZ,
    refunded_at         TIMESTAMPTZ,
    refunded_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX consultation_sessions_conversation_idx ON consultation_sessions (conversation_id, created_at DESC);
CREATE INDEX consultation_sessions_doctor_idx ON consultation_sessions (doctor_id, created_at DESC);
CREATE INDEX consultation_sessions_payment_idx ON consultation_sessions (payment_state) WHERE payment_state <> 'free';

-- Every consultation opened before sessions existed becomes its first, free, session.
INSERT INTO consultation_sessions (conversation_id, doctor_id, patient_id, opened_at, expires_at, closed_at, closed_reason, created_at)
SELECT c.id,
       c.doctor_id,
       CASE WHEN c.user_a = d.user_id THEN c.user_b ELSE c.user_a END,
       c.opened_at,
       c.expires_at,
       c.closed_at,
       CASE WHEN c.closed_at IS NOT NULL THEN 'doctor' END,
       COALESCE(c.opened_at, c.created_at)
FROM community_conversations c
JOIN doctor_profiles d ON d.id = c.doctor_id
WHERE c.doctor_id IS NOT NULL;

-- A payment is for a plan or for a consultation, never both and never neither.
ALTER TABLE payment_transactions
    ALTER COLUMN plan_id DROP NOT NULL,
    ADD COLUMN consultation_session_id UUID REFERENCES consultation_sessions (id) ON DELETE SET NULL,
    ADD CONSTRAINT payment_transactions_one_purpose CHECK (
        (plan_id IS NOT NULL AND consultation_session_id IS NULL) OR
        (plan_id IS NULL AND consultation_session_id IS NOT NULL)
    );

-- ---------------------------------------------------------------- the doctor's day

ALTER TABLE doctor_profiles
    -- Her price for one consultation, in tiyin; 0 keeps it free.
    ADD COLUMN consultation_price_minor BIGINT NOT NULL DEFAULT 0 CHECK (consultation_price_minor >= 0),
    -- "Not now": she is still taking consultations, but patients are told she is away.
    ADD COLUMN busy BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN timezone TEXT NOT NULL DEFAULT 'Asia/Tashkent';

-- Her hours, per weekday (1 Monday … 7 Sunday), in minutes from midnight. A day with no
-- row is a day off; a doctor with no rows at all has not set hours and is not shown any.
CREATE TABLE doctor_hours (
    doctor_id    UUID    NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    weekday      INTEGER NOT NULL CHECK (weekday BETWEEN 1 AND 7),
    start_minute INTEGER NOT NULL CHECK (start_minute BETWEEN 0 AND 1439),
    end_minute   INTEGER NOT NULL CHECK (end_minute BETWEEN 1 AND 1440),
    CHECK (end_minute > start_minute),
    PRIMARY KEY (doctor_id, weekday)
);

CREATE TABLE doctor_quick_replies (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id  UUID        NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    title      TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 60),
    body       TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 1000),
    position   INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX doctor_quick_replies_doctor_idx ON doctor_quick_replies (doctor_id, position);

-- Hers alone: never shown to the patient, never to staff.
CREATE TABLE doctor_patient_notes (
    doctor_id  UUID        NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    patient_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       TEXT        NOT NULL CHECK (char_length(body) <= 4000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (doctor_id, patient_id)
);

-- ---------------------------------------------------------------- money

CREATE TABLE app_settings (
    key        TEXT PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID
);

INSERT INTO app_settings (key, value) VALUES ('consultation_commission_percent', '20');

CREATE TABLE doctor_payouts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id    UUID        NOT NULL REFERENCES doctor_profiles (id) ON DELETE CASCADE,
    amount_minor BIGINT      NOT NULL CHECK (amount_minor > 0),
    note         TEXT CHECK (char_length(note) <= 500),
    paid_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   UUID        NOT NULL
);

CREATE INDEX doctor_payouts_doctor_idx ON doctor_payouts (doctor_id, paid_at DESC);

-- ---------------------------------------------------------------- push routing

-- Which app a device is: a doctor has both on one phone, and a patient's message must
-- ring in the doctor app, her own reminders in the women's app.
ALTER TABLE devices ADD COLUMN app TEXT NOT NULL DEFAULT 'client';

ALTER TABLE notification_outbox
    ADD COLUMN target_app TEXT NOT NULL DEFAULT 'client',
    -- Where a tap lands, e.g. sadora://conversation/<id>; null opens the app.
    ADD COLUMN link TEXT;
