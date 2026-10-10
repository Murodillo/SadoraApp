-- A medication reminder is read on the lock screen by whoever holds the phone, so its
-- title no longer names the medicine. The app shows which dose it is once she opens it.
-- Only rows still carrying the shipped default are changed: an operator's own wording
-- stays hers.
UPDATE notification_templates SET title = 'Dori vaqti', updated_at = now()
    WHERE key = 'med_reminder' AND language = 'uz' AND title = '{{name}}';
UPDATE notification_templates SET title = 'Время лекарства', updated_at = now()
    WHERE key = 'med_reminder' AND language = 'ru' AND title = '{{name}}';
UPDATE notification_templates SET title = 'Medication time', updated_at = now()
    WHERE key = 'med_reminder' AND language = 'en' AND title = '{{name}}';

-- The Russian app says «месячные», not «менструация».
UPDATE notification_templates SET title = 'Скоро месячные', updated_at = now()
    WHERE key = 'period_soon' AND language = 'ru' AND title = 'Скоро менструация';
