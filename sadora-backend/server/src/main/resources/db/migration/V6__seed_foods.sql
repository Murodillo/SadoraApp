-- Starter catalogue. Per 100 g unless per_piece is set.

INSERT INTO food_items (key, name, kcal, protein_g, fat_g, carbs_g, per_piece) VALUES
    ('osh_meat',      'Osh (go''shtli)',       248,  9, 12, 26, FALSE),
    ('mastava',       'Mastava',                92,  4,  3, 12, FALSE),
    ('somsa_pumpkin', 'Oshqovoqli somsa',      276,  7, 14, 30, TRUE),
    ('somsa_meat',    'Go''shtli somsa',       318, 12, 18, 28, TRUE),
    ('lagman',        'Lag''mon',              135,  7,  5, 16, FALSE),
    ('manti',         'Manti',                 210,  9, 10, 22, TRUE),
    ('shurpa',        'Shorva',                 78,  5,  4,  6, FALSE),
    ('chuchvara',     'Chuchvara',             185,  8,  7, 22, FALSE),
    ('non_patir',     'Non (patir)',           270,  8,  4, 51, FALSE),
    ('chicken_salad', 'Tovuqli salat',         145, 12,  7,  8, FALSE),
    ('yogurt',        'Yog''urt (tabiiy)',      60,  4,  3,  5, FALSE),
    ('granola',       'Granola',               420, 10, 15, 60, FALSE),
    ('egg',           'Tuxum',                  78,  6,  5,  1, TRUE),
    ('apple',         'Olma',                   52,  0,  0, 14, FALSE),
    ('rice_boiled',   'Guruch (qaynatilgan)',  130,  3,  0, 28, FALSE),
    ('beef',          'Mol go''shti',          250, 26, 15,  0, FALSE),
    ('chicken_breast','Tovuq ko''kragi',       165, 31,  4,  0, FALSE),
    ('cottage_cheese','Tvorog',                 98, 11,  4,  3, FALSE),
    ('walnut',        'Yong''oq',              654, 15, 65, 14, FALSE),
    ('green_tea',     'Ko''k choy',              1,  0,  0,  0, FALSE);
