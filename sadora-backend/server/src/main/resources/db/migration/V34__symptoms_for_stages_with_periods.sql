-- Perimenopause still has periods, with the cramps and breast tenderness that come with
-- them; V4 offered both only to the cycling stages, so a woman in perimenopause could
-- not note the commonest thing her month brings. Breastfeeding makes breasts tender, and
-- postpartum was left out of that one as well.
INSERT INTO symptom_life_stages (symptom_key, life_stage) VALUES
    ('cramps',        'perimenopause'),
    ('breast_tender', 'perimenopause'),
    ('breast_tender', 'postpartum')
ON CONFLICT DO NOTHING;
