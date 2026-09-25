-- The Bilim library: articles written in the admin panel instead of compiled into the app.
--
-- Nothing here is health data — it is editorial content, the same for every reader — so
-- it has no user column and the admin API reads and writes it directly. What is per-user
-- is only whether the body is unlocked, and that is resolved from `learn_premium` at
-- read time rather than stored.

CREATE TABLE content_categories (
    key        TEXT PRIMARY KEY CHECK (key ~ '^[a-z][a-z0-9_]{1,30}$'),
    label      TEXT        NOT NULL CHECK (char_length(label) BETWEEN 1 AND 60),
    -- The order the chips are drawn in; ties fall back to the label.
    position   INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE content_articles (
    -- The slug is the identity in every link, so it is chosen once and never edited.
    slug         TEXT PRIMARY KEY CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,80}$'),
    kind         TEXT        NOT NULL DEFAULT 'article',
    category_key TEXT        NOT NULL REFERENCES content_categories (key) ON UPDATE CASCADE,
    title        TEXT        NOT NULL CHECK (char_length(title) BETWEEN 1 AND 200),
    excerpt      TEXT        NOT NULL DEFAULT '' CHECK (char_length(excerpt) <= 400),
    -- The body as an ordered list of typed blocks; the reader lays each type out itself.
    body         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    read_minutes INTEGER     NOT NULL DEFAULT 1 CHECK (read_minutes BETWEEN 1 AND 240),
    premium      BOOLEAN     NOT NULL DEFAULT FALSE,
    -- A draft is invisible to the app entirely; publishing is a separate, deliberate act.
    published    BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    reviewed_by  TEXT,
    author       TEXT,
    author_role  TEXT,
    disclaimer   TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX content_articles_feed_idx ON content_articles (published, published_at DESC);
CREATE INDEX content_articles_category_idx ON content_articles (category_key, published, published_at DESC);

-- ---------------------------------------------------------------- seed

INSERT INTO content_categories (key, label, position) VALUES
    ('cycle',     'Sikl',         1),
    ('hormones',  'Gormonlar',    2),
    ('nutrition', 'Ovqatlanish',  3),
    ('sleep',     'Uyqu',         4),
    ('mind',      'Ong',          5);

-- The three cards the app used to carry as constants, now rows an editor can change,
-- plus bodies so the reader has something real to open. Wording is deliberately
-- general-health: none of it tells anyone to start or stop a treatment.
INSERT INTO content_articles
    (slug, kind, category_key, title, excerpt, body, read_minutes, premium, published,
     published_at, reviewed_by, author, author_role, disclaimer)
VALUES
    (
        'temirga-boy-taomlar', 'article', 'nutrition',
        'Temirga boy taomlar: nima yeyish va nima bilan qo''shish',
        'Hayz davrida yo''qotilgan temirni ovqat bilan qoplash — odatiy amaliyot. Qaysi taomlarda temir ko''p va nima uni yaxshiroq so''rilishiga yordam beradi.',
        '[
          {"type":"paragraph","text":"Temir qonda kislorod tashuvchi gemoglobin uchun kerak. Hayz davrida temirning bir qismi yo''qoladi, shuning uchun bu kunlarda ovqatga biroz ko''proq e''tibor beriladi."},
          {"type":"heading","text":"Nimalarda ko''p"},
          {"type":"bullets","items":["Qora jigar, mol go''shti, tovuq jigari","Yasmiq, no''xat, loviya","Ismaloq, ko''kat, quruq o''rik"]},
          {"type":"heading","text":"Nima bilan qo''shiladi"},
          {"type":"paragraph","text":"O''simlik taomlaridagi temir C vitamini bilan birga yaxshiroq so''riladi: yasmiq sho''rvasiga limon, salatga bulg''or qalampiri qo''shish yetadi."},
          {"type":"note","text":"Choy va qahva temir so''rilishini kamaytiradi — ularni ovqatdan bir soat keyin ichgan ma''qul."}
        ]'::jsonb,
        6, FALSE, TRUE, now(), 'Dr. S. Aliyeva ko''rib chiqqan', 'Nilufar Karimova', 'Muallif · nutritsiolog',
        'Ushbu material umumiy salomatlik ma''lumoti. Temir preparatlarini shifokor tavsiyasisiz boshlash tavsiya etilmaydi.'
    ),
    (
        'kechki-tartib', 'video', 'sleep',
        'Kechki tartib: 30 daqiqalik amal',
        'Uxlashdan oldingi yarim soatni bir xil o''tkazish — uyquga tushishni osonlashtiradigan eng oddiy odat.',
        '[
          {"type":"paragraph","text":"Tana takrorlanadigan signallarga o''rganadi. Har kecha bir xil tartib uyqu vaqti yaqinlashganini bildiradi."},
          {"type":"heading","text":"Yarim soat qanday o''tadi"},
          {"type":"bullets","items":["Yorug''likni pasaytiring va ekranni qo''ying","Ertangi kunni bir varaqqa yozib qo''ying","Sekin nafas: to''rt sanab olish, olti sanab chiqarish"]},
          {"type":"note","text":"Uyqu kelmasa, yotoqda kutmang — yorug''i past xonada 10-15 daqiqa o''tirib, keyin qayting."}
        ]'::jsonb,
        9, FALSE, TRUE, now(), NULL, 'Sadora jamoasi', 'Muallif',
        'Uyqu muammosi bir necha hafta davom etsa, shifokorga murojaat qiling.'
    ),
    (
        'siklni-tushunish', 'course', 'cycle',
        'Siklni tushunish: gormonlar va kayfiyat',
        'Besh darsda sikl fazalari, ular bilan birga o''zgaradigan energiya va kayfiyat — va bularni kundalikda qanday kuzatish.',
        '[
          {"type":"paragraph","text":"Sikl to''rt fazadan iborat, va har birida gormonlar darajasi boshqacha. Energiya, uyqu va kayfiyatdagi o''zgarishlar ko''pincha shu bilan birga keladi."},
          {"type":"heading","text":"Kurs nimalardan iborat"},
          {"type":"bullets","items":["Fazalar va ularning davomiyligi","Estrogen va progesteron nima qiladi","Kayfiyat va energiyani qayd etish","Kundalikdagi belgilarni o''qish","Shifokorga nima bilan borish"]},
          {"type":"paragraph","text":"Har bir darsdan keyin kundalikka nimani yozish kerakligi ko''rsatiladi — kurs oxirida o''z siklingiz haqida o''lchangan tasavvur qoladi."}
        ]'::jsonb,
        25, TRUE, TRUE, now(), 'Dr. N. Karimova ko''rib chiqqan', 'Sadora jamoasi', 'Muallif',
        'Kurs tashxis qo''ymaydi va davolash tayinlamaydi.'
    );
