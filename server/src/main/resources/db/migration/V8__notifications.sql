-- Notification delivery.
--
-- The outbox is the record of what the product decided to send and why — including what
-- it decided *not* to send. A suppressed row with its reason is the only way to answer
-- "why didn't she get her reminder", which is otherwise unanswerable after the fact.

CREATE TABLE user_notification_settings (
    user_id     UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    -- "med_reminder:true,water:false" — absent category means on.
    categories  TEXT        NOT NULL DEFAULT '',
    quiet_from  TIME,
    quiet_until TIME,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The admin panel's template list. Text lives here, not in the app, so wording can be
-- fixed without a release and translated without one either.
CREATE TABLE notification_templates (
    key        TEXT        NOT NULL,
    language   TEXT        NOT NULL,
    category   TEXT        NOT NULL,
    title      TEXT        NOT NULL,
    body       TEXT        NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (key, language)
);

CREATE TABLE notification_outbox (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category          TEXT        NOT NULL,
    title             TEXT        NOT NULL,
    body              TEXT        NOT NULL,
    scheduled_for     TIMESTAMPTZ NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'queued',
    sent_at           TIMESTAMPTZ,
    suppressed_reason TEXT,
    -- Stops the same reminder being queued twice when the scheduler ticks again, and is
    -- how a restart mid-run stays harmless.
    dedupe_key        TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, dedupe_key)
);

CREATE INDEX notification_outbox_due_idx ON notification_outbox (status, scheduled_for);
CREATE INDEX notification_outbox_user_idx ON notification_outbox (user_id, created_at DESC);

-- Global caps, editable from the admin panel. One row.
CREATE TABLE notification_caps (
    id           INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    max_per_day  INTEGER NOT NULL DEFAULT 6,
    max_per_week INTEGER NOT NULL DEFAULT 25,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO notification_caps (id) VALUES (1);

INSERT INTO notification_templates (key, language, category, title, body) VALUES
    ('med_reminder', 'uz', 'med_reminder', '{{name}}', 'Qabul vaqti — {{time}}'),
    ('med_reminder', 'ru', 'med_reminder', '{{name}}', 'Время приёма — {{time}}'),
    ('med_reminder', 'en', 'med_reminder', '{{name}}', 'Time to take it — {{time}}'),
    ('daily_check_in', 'uz', 'daily_check_in', 'Bugun qanday?', 'Kayfiyat va energiyani belgilab qo''ying'),
    ('daily_check_in', 'ru', 'daily_check_in', 'Как сегодня?', 'Отметьте настроение и энергию'),
    ('daily_check_in', 'en', 'daily_check_in', 'How is today?', 'Log your mood and energy'),
    ('period_soon', 'uz', 'cycle', 'Hayz yaqinlashmoqda', 'Taxminan {{days}} kundan keyin'),
    ('period_soon', 'ru', 'cycle', 'Скоро менструация', 'Примерно через {{days}} дн.'),
    ('period_soon', 'en', 'cycle', 'Your period is near', 'In about {{days}} days');
