-- Things a stage asks her to note as they happen: a feed, a count of the baby's kicks,
-- a contraction, a hot flush, a mood questionnaire. One table because they are one kind
-- of fact — something happened at a time, perhaps for a while, perhaps with a number —
-- and what each column means for each kind is written down on StageEvent in :contract.
CREATE TABLE stage_events (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind             TEXT        NOT NULL CHECK (kind IN ('feeding', 'kick_count', 'contraction', 'hot_flush', 'mood_screen')),
    started_at       TIMESTAMPTZ NOT NULL,
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds BETWEEN 0 AND 21600),
    value            INTEGER CHECK (value IS NULL OR value BETWEEN 0 AND 2000),
    detail           TEXT CHECK (detail IS NULL OR char_length(detail) <= 40),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every read is one woman's events of one kind over a stretch of time.
CREATE INDEX stage_events_user_kind_time ON stage_events (user_id, kind, started_at DESC);

-- The catalogue spoke Uzbek to everyone: a woman with the app in Russian picked her
-- symptoms from Uzbek words. Each label now has all three languages.
ALTER TABLE symptom_definitions ADD COLUMN label_ru TEXT;
ALTER TABLE symptom_definitions ADD COLUMN label_en TEXT;

UPDATE symptom_definitions s SET label_ru = t.ru, label_en = t.en
FROM (VALUES
    ('discharge',     'Выделения',              'Discharge'),
    ('cramps',        'Боль',                   'Cramps'),
    ('headache',      'Головная боль',          'Headache'),
    ('back_pain',     'Боль в пояснице',        'Back pain'),
    ('joint_pain',    'Боль в суставах',        'Joint pain'),
    ('breast_tender', 'Чувствительность груди', 'Tender breasts'),
    ('nausea',        'Тошнота',                'Nausea'),
    ('bloating',      'Вздутие',                'Bloating'),
    ('swelling',      'Отёки',                  'Swelling'),
    ('acne',          'Высыпания',              'Acne'),
    ('mood_swings',   'Перепады настроения',    'Mood swings'),
    ('anxiety',       'Тревога',                'Anxiety'),
    ('insomnia',      'Бессонница',             'Insomnia'),
    ('night_sweats',  'Потливость',             'Night sweats'),
    ('hot_flush',     'Приливы',                'Hot flushes'),
    ('fatigue',       'Усталость',              'Fatigue'),
    ('cravings',      'Изменение аппетита',     'Appetite changes')
) AS t (key, ru, en)
WHERE s.key = t.key;

-- What each stage most often brings that the catalogue had no word for.
INSERT INTO symptom_definitions (key, label, label_ru, label_en, category, sort_order) VALUES
    ('heartburn',       'Jig''ildon qaynashi',  'Изжога',              'Heartburn',        'digestion',  95),
    ('constipation',    'Qabziyat',             'Запор',               'Constipation',     'digestion',  96),
    ('leg_cramps',      'Oyoq tortishishi',     'Судороги в ногах',    'Leg cramps',       'pain',       55),
    ('lochia',          'Tug''ruqdan keyingi ajralma', 'Лохии',        'Lochia',           'bleeding',   15),
    ('wound_pain',      'Chok og''rig''i',      'Боль в области шва',  'Stitches pain',    'pain',       57),
    ('vaginal_dryness', 'Quruqlik',             'Сухость',             'Vaginal dryness',  'other',     175),
    ('brain_fog',       'Diqqat tarqoqligi',    'Рассеянность',        'Brain fog',        'mood',      125),
    ('palpitations',    'Yurak tez urishi',     'Учащённое сердцебиение', 'Palpitations',  'energy',    155);

INSERT INTO symptom_life_stages (symptom_key, life_stage) VALUES
    ('heartburn',       'pregnancy'),
    ('constipation',    'pregnancy'),
    ('constipation',    'postpartum'),
    ('leg_cramps',      'pregnancy'),
    ('lochia',          'postpartum'),
    ('wound_pain',      'postpartum'),
    ('vaginal_dryness', 'perimenopause'),
    ('vaginal_dryness', 'menopause'),
    ('brain_fog',       'perimenopause'),
    ('brain_fog',       'menopause'),
    ('palpitations',    'perimenopause'),
    ('palpitations',    'menopause');
