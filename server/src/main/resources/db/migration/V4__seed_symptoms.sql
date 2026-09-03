-- The symptom catalogue, matching the sheets in the design.
--
-- Stage-scoped where the design scopes them: a hot flush is offered in perimenopause and
-- menopause, not to someone tracking a cycle, and the sheet is shorter for it.

INSERT INTO symptom_definitions (key, label, category, sort_order) VALUES
    ('discharge',      'Ajralma',              'bleeding',  10),
    ('cramps',         'Og''riq',              'pain',      20),
    ('headache',       'Bosh og''rig''i',      'pain',      30),
    ('back_pain',      'Belda og''riq',        'pain',      40),
    ('joint_pain',     'Bo''g''im og''rig''i', 'pain',      50),
    ('breast_tender',  'Ko''krak sezgirligi',  'pain',      60),
    ('nausea',         'Ko''ngil aynishi',     'digestion', 70),
    ('bloating',       'Qorin dam bo''lishi',  'digestion', 80),
    ('swelling',       'Shish',                'digestion', 90),
    ('acne',           'Toshma',               'skin',     100),
    ('mood_swings',    'Kayfiyat o''zgarishi', 'mood',     110),
    ('anxiety',        'Xavotir',              'mood',     120),
    ('insomnia',       'Uyqusizlik',           'sleep',    130),
    ('night_sweats',   'Terlash',              'sleep',    140),
    ('hot_flush',      'Issiqlik to''lqini',   'energy',   150),
    ('fatigue',        'Charchoq',             'energy',   160),
    ('cravings',       'Ishtaha o''zgarishi',  'other',    170);

-- Stage scoping. A symptom with no rows here is offered in every stage.
INSERT INTO symptom_life_stages (symptom_key, life_stage) VALUES
    ('discharge',     'cycle'),
    ('discharge',     'trying_to_conceive'),
    ('discharge',     'pregnancy'),
    ('cramps',        'cycle'),
    ('cramps',        'trying_to_conceive'),
    ('breast_tender', 'cycle'),
    ('breast_tender', 'trying_to_conceive'),
    ('breast_tender', 'pregnancy'),
    ('back_pain',     'pregnancy'),
    ('back_pain',     'postpartum'),
    ('swelling',      'pregnancy'),
    ('hot_flush',     'perimenopause'),
    ('hot_flush',     'menopause'),
    ('night_sweats',  'perimenopause'),
    ('night_sweats',  'menopause'),
    ('joint_pain',    'perimenopause'),
    ('joint_pain',    'menopause'),
    ('insomnia',      'perimenopause'),
    ('insomnia',      'menopause'),
    ('insomnia',      'postpartum');
