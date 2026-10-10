# UX matn tekshiruvi — 2026-10-10

Qamrov: sadora-client (UZ/RU/EN, barcha i18n fayllari), sadora-doctor (i18n + qattiq yozilgan matnlar),
server ErrorText + push/SMS/AI matnlari, iOS ruxsat matnlari, ikkala React panel.
Faqat hisobot — kod o'zgartirilmagan. Huquqiy matnlar (LegalTexts*) qamrovdan tashqarida.

Qatorlar — `file:line`. Qisqartmalar: **Uz/Ru/En** = `sadora-client/shared/src/commonMain/kotlin/uz/sadora/app/i18n/Strings{Uz,Ru,En}.kt`,
**D/** = `sadora-doctor/shared/src/commonMain/kotlin/uz/sadora/doctor/i18n/`, **C/** = mijoz `i18n/` papkasi,
**ET** = `sadora-backend/server/src/main/kotlin/uz/sadora/server/i18n/ErrorText.kt`.

---

## 0. Birinchi navbatda (release'dan oldin)

| # | Muammo | Joy | Nima qilish |
|---|---|---|---|
| 1 | Health ruxsatlarining **hammasi majburiy** — Apple 5.1.3 / Health Connect qoidasiga zid, dark pattern | C/HealthGateStrings.kt:47–48 (Ru 78, En 109) | "Qancha ko'p tur — shuncha aniq. Keyin sozlamalardan ham berasiz" + "Shu bilan davom etish" tugmasi (oqim ham o'zgaradi) |
| 2 | **Homiladorlikni yo'qotish** yo'li yo'q — faqat "Farzandingiz tug'ildimi?" | C/StageToolsUz.kt:27–30 | "Homiladorligim boshqacha yakunlandi" varianti + yumshoq ekran (oqim masalasi) |
| 3 | iOS ruxsat matnlari **faqat o'zbekcha**, `InfoPlist.strings` yo'q, `knownRegions = (en, Base)` | ikkala iosApp | uz/ru/en `.lproj` + `CFBundleLocalizations` |
| 4 | Doctor `NSLocalNetworkUsageDescription` — dasturchi matni release'da | sadora-doctor iosApp Info.plist | Release konfiguratsiyasidan olib tashlash |
| 5 | FAQ "Payme yoki Click orqali" — iOS'da tashqi to'lovni tilga olish App Store qoidasini buzadi; store to'lovi tushib qolgan | Uz:1771, Ru:1774, En:1767 | Platformaga qarab matn; iOS'da faqat App Store |
| 6 | Tekshirib bo'lmaydigan da'volar: "minglab ayol…", "ginekologlar bilan tayyorlanadi", "uchinchi shaxslarga berilmaydi" (AI tashqi modelga ketadi) | Uz:242–243, Ru:245–246/104/279, En:242–243/101/276 | "Ayollar uchun yaratilgan", "Ma'lumotingizni sotmaymiz va reklama uchun bermaymiz" |
| 7 | `BLEEDING` toifasi "Ajralma / Выделения / Discharge" — qonni yashiradi | Uz:1010, Ru:1013, En:1007 | "Qon va ajralma / Кровотечение и выделения / Bleeding & discharge" |
| 8 | Retsept kartasi ikki ilovada **boshqacha**: shifokor "Farqi yo'q", bemor "Ovqatdan qat'i nazar"; "3-kundan 5 kun" noaniq | D/PrescriptionStrings.kt:133/220/307, 153/240/327; C/PrescriptionStrings.kt:58 | "Ovqatdan qat'i nazar / Независимо от еды / With or without food"; "3-kundan boshlab, 5 kun". Summary matnini contract'ga umumlashtirish |
| 9 | `FoodRelation.ANY` = "Vaqtidan qat'i nazar" — dori qabulini noto'g'ri tushuntiradi | Uz:1360 | "Ovqatdan qat'i nazar" |
| 10 | Yaqinim veb-havolasini **olgan har kim** ko'ra olishi aytilmagan | C/PartnerUz.kt:234–235 (Ru/En shu qator) | "Havolani olgan har kim ko'ra oladi — faqat ishongan odamga yuboring. N kun ishlaydi, istalgan payt o'chirasiz" |
| 11 | Qulflangan ekranda sog'liq ma'lumoti: "$name: hayz boshlandi", dori nomi, DM matni | server PartnerPhrases.kt:44,51; V8__notifications.sql:61; DM/konsultatsiya pushlari | Neytral matn sukut bo'yicha ("Yaqinim: yangi holat", "Dori vaqti") |
| 12 | O'z-o'ziga zarar (EPDS) xabari sovuq, 103 oxirida; AI promptda favqulodda qoida yo'q | C/StageToolsUz.kt:119–120; AiPhrases*.instruction | "Aytganingiz — muhim qadam. Siz yolg'iz emassiz… xavf his qilsangiz — darhol 103" + qo'ng'iroq tugmasi; AI'ga 103 qoidasi |
| 13 | Ingliz ko'plik: 20+ joyda "1 days", "1 times", "1 feeds" (seriya ekrani ham) | En:1543–1548 va ro'yxat §2 | `en(n, "day", "days")` helper + plural test |
| 14 | Ruscha valyuta: "гул/гулей/Gul" uch xil va turlanmaydi ("Не хватает гул") | Ru:1548, 1632–33; C/FrameStrings.kt:119–125; BadgeStrings.kt:246–247; ET:274 | Bitta qaror: lotincha **Gul** (turlanmaydi) — "Недостаточно Gul" |
| 15 | Push'lar faqat o'zbekcha (konsultatsiya, retsept, DM, shifokor, foto) | ConsultationService.kt:290–328, PrescriptionService.kt:88, CommunityService.kt:328, MessagingService.kt:470, PhotoService.kt:144, DoctorService.kt:299, NotificationScheduler.kt:170 | `PartnerPhrases` uslubida `ConsultationPhrases`/`DoctorPhrases` |
| 16 | Validation xabarlari maydon nomisiz: "Bo'sh bo'lishi mumkin emas" | ET:66, 80–83, 163, 222 | Xabarga mavzu qo'shish yoki ilovada maydon tagida ko'rsatish |
| 17 | Shifokor pul matnlari: kim kimga to'lashi aniq emas ("To'lanishi kerak bo'lgan qoldiq", "Jami tushum") | D/WorkStrings.kt:166/274/382, 167/383, 172/280/388, 131/135 | "Sizga o'tkazilishi kutilayotgan summa", "Bemorlar to'lagan jami", narx "24 soatlik konsultatsiya uchun", "Sadora ulushidan keyin: $amount" |
| 18 | Yaqinim to'lov so'rovi: "yordam so'rayapti" (favqulodda tuyuladi), narx/bir martaligi yo'q | C/PartnerUz.kt:273–274, 251–252 | "$name sizdan sovg'a so'rayapti 💝 · $what · $price. Bir martalik, avtomatik yangilanmaydi" |

---

## 1. Atamalar lug'ati (bitta tushuncha — bitta so'z)

| Tushuncha | Hozir aralash | Tavsiya UZ | RU | EN |
|---|---|---|---|---|
| Symptom | simptom (15), belgi, alomat | **alomat** (yoki simptom — bittasi) | симптом | symptom |
| "belgi" | simptom, badge, yozuv, fe'l | faqat fe'l "belgilash" | — | — |
| Badge | Belgilar, nishon | **nishon** | значок | badge |
| Appointment | Tadbir, uchrashuv, ko'rik, qabul | **ko'rik** | визит | visit |
| Prediction | bashorat, prognoz | **bashorat** | прогноз | forecast |
| Journal | Jurnal, Kundalik | **Kundalik** | Дневник | Journal |
| Mind tab | Ong / Разум / Mind | **Ruhiyat** | Настроение | Mood |
| Medicine | dori, tabletka, препарат | **dori** ("Dorilarim") | лекарство | medication |
| Period | hayz; RU менструация/месячные/периоды | hayz | месячные (UI) | period |
| Fertile | Ovulyatsiya davri / Unumdor | **Unumdor kunlar** | Фертильное окно | Fertile window |
| Consultation | konsultatsiya, suhbat, yozishma | **konsultatsiya** | консультация | consultation |
| Patient record | Tibbiy karta, Bemor kartasi, yozuv | **Bemor kartasi** | Карта пациентки | Patient record |
| Quick replies | Tayyor/Быстрые/Готовые/Ready | Tayyor javoblar | Быстрые ответы | Quick replies |
| Rating | baho / отзыв / review | baho | оценка | rating |
| Busy | Band / Занята / Перерыв | Band | Перерыв (jinssiz) | Busy |
| Partner | Yaqinim, Мой близкий, My person, Your person | **Yaqinim** (brend, 3 tilda) | близкий человек | someone close |
| Currency | Gul/gul, гул/гулей | **Gul** | Gul | Gul |
| Hot flushes | issiq toshish / issiqlik to'lqinlari | **Issiq toshishlar** | приливы | hot flushes |
| AI | AI chat, AI suhbat, AI yordamchi, ИИ/AI | **AI suhbat**; brend **Sadora AI** | ИИ-чат | AI chat |
| Brend | SADORA / Sadora | bitta qaror | | |
| Streak | streak (server, admin) / ketma-ketlik | **ketma-ketlik** | серия | streak |
| Session | seans / sessiya | **seans** | сеанс | session |
| Back | Ortga / Orqaga | **Orqaga** | Назад | Back |
| Dose pending | Kutilmoqda / Kechiktirilgan | **Kutilmoqda** | Ожидается | Pending |
| Store | Play Market, Play Store, store | **Google Play / App Store** | | |
| Birliklar | "s" = soat va soniya; "d"/"daq"; "son" | soat **s**, daqiqa **daq**, soniya **son** emas → **sek** | ч / мин / с | h / min / s |
| Sana + qo'shimcha | "$date gacha", "$date dan beri" | **"${date}gacha"** (qo'shib) | | |
| Qo'shtirnoq | "…" va «…» | **«»** | «» | “” |
| Apostrof | hammasi ASCII `'` (admin ShopPage'da `‘` aralash) | loyiha qarori: ASCII qoldirish yoki `ʻ/ʼ` ga skript bilan o'tish | | |
| Valyuta EN | UZS / so'm | **UZS** | сум | UZS |

Tavsiya: shu jadvalni `StringsTest`ga taqiqlangan so'zlar ro'yxati sifatida qo'shish (masalan "Tadbir", "Play Market", "периодов").

---

## 2. Mijoz ilovasi — asosiy matnlar (UZ)

**Tibbiy xavfsizlik**
- Uz:1116 moodWatchBody — 2 haftalik mezon va 103 yo'q → "Tushkunlik yoki xavotir ikki haftadan ko'p davom etsa, shifokor yoki psixologga murojaat qiling. O'zingizga zarar yetkazish fikri kelsa — darhol 103. SADORA tashxis qo'ymaydi."
- Uz:624 chat qoidasi chala → "Bu shifokor maslahati emas. Kuchli og'riq, ko'p qon ketish yoki isitma bo'lsa, shifokorga murojaat qiling."
- Uz:227 (Ru 230, En 227) TTC — 35+ uchun 6 oy → "Bir yildan (35 yoshdan oshgan bo'lsangiz — 6 oydan) ko'p bo'lsa…"
- Uz:460 hayz paytida "Homiladorlik ehtimoli past" → "…pastroq (kafolat emas)"
- Uz:999 "Og'riq darajasi" barcha alomatlar uchun → "Kuchliligi"

**Ma'no / grammatika**
- Uz:382 mavjud bo'lmagan "Yo'l" bo'limi → "Bosqichni o'zgartirsangiz, bosqich bo'limi yangilanadi. Yozuvlaringiz saqlanadi."
- Uz:718, 722, 725 "konsultatsiya ochiq ekan" → "konsultatsiya davomida ko'ra oladi"; "boshqa ocha olmaydi" → "qayta ocha olmaydi"
- Uz:1033–1075, 65 "Tadbir(lar)" → "Ko'rik(lar)", "Ko'rik qo'shish"
- Uz:842, 838 "Birinchi belgini qo'shish" → "Bugungi holatni yozish"; Uz:1149 tavtologiya → "Quyida bugungi alomatlarni tanlang."
- Uz:583 "Har kunlik" → "Kunlik"; Uz:582 "ma'lumotlar kontekstida" → "ma'lumotlaringizni bilgan holda"
- Uz:106 "Yosh bashoratlarni…" → "Yoshingiz…"; Uz:127 → "keyin ilova o'zi aniqlaydi"; Uz:141 → "istalgan vaqtda o'chirishingiz mumkin"
- Uz:944 → "Keyingi hayzgacha $days kun"; Uz:974 "Yaxshi" → "Muntazam"; Uz:1014 → "Hazm qilish"; Uz:1494 → "Bachadon bo'yni shilliqi"
- Uz:1028/231 → ikkalasi "Taxminiy tug'ish sanasi"
- Uz:1847 qo'sh "-ga"; Uz:1225 "va" oldida vergul; Uz:446–448 "Muvozanat uchun oddiy kun" → "Odatiy kun — bu ham yaxshi."
- Uz:789 har kungi "…ajoyib kun 🌸" → "$greeting! O'zingizga bir daqiqa ajrating 🌸"

**Xato / bo'sh holat / CTA**
- Uz:763–766, 1080, 1342 validatsiya → "Sanani kun.oy.yil ko'rinishida yozing, masalan 27.08.2026", "Vaqtni 20:00 ko'rinishida yozing"
- Uz:774–775 limitlar → "Bu oylik limit tugadi. Yangi oy boshida yangilanadi." / "Bugungi limit tugadi. Ertaga yana…"
- Uz:771 bloklangan hisob — kanal yo'q → "Profil → SADORA haqida bo'limidan biz bilan bog'laning"
- Uz:1056 "Ro'yxat bo'sh" → "Hozircha ko'rik yo'q — birinchisini qo'shing."
- Uz:287 "Hammasiga rozilik" → "Hammasiga roziman"; Uz:1074 "Kerak emas" → "Eslatmasiz"
- Uz:1712, 1707, 1708 → "Havolani yuborish", "Yangi kod yaratish", "Havolani o'chirish"
- Uz:1320 "✓ Ko'rib chiqqan" → "✓ Shifokor ko'rib chiqqan"

**Dasturchi tili / Russianizmlar**
- Uz:1302 "Katalog serverdan keladi" → "O'zbek taomlari ro'yxat boshida."
- Uz:1440 "HealthKit … ruxsat bergach" → "Apple Salomatlik yoki Health Connect'ga ruxsat bersangiz…"
- "makrolar" (Uz:907, 892, 927 + iOS kamera matni) → "oqsil, yog' va uglevod"
- Uz:618 "Anonim" (taxallus ko'rinadi) → "Taxallus bilan — ismingiz ko'rinmaydi"
- Lenta → Postlar, Kalendar → Taqvim, Play Market → Google Play, bpm → zarba/daq, Bio → O'zim haqimda, Konturli → Chiziqli, praktika → mashqlar, trend → dinamika

**Mayda**: tugma/chip >22 belgi — Uz:95, 719, 1667, 1845, 1038, 842, 1423, 1060; `"  HAFTA"` oldidagi bo'shliqlar (Uz:1025, 1102 va Ru/En) layoutga o'tsin; "•" va "·" aralash (Uz:874, 1412).

---

## 3. Mijoz ilovasi — RU va EN

**RU**
- Ru:1864 "Спросите позже" → "Напомнить позже"
- Ru/En:387 inbox "Отправленные/Sent" (Uz "Yuborilganlar") → "Ранее/Earlier"
- Ru:854 umumiy `levels` "Энергия: Высокий" → "Низко / Средне / Высоко"
- Ru:1854 "Добавлено периодов" → "Добавлено месячных: $count"
- Ru:1133, 1136 "до $longest дней" → `ru(longest, "дня","дней","дней")`; Ru:1450 "$count записей" → plural; ~15 joyda "дн." → `ru()`
- Ru:40 "Разум" → "Настроение"; Ru:48 "Беременна" → "Беременность"
- Ru:1056/1037/1039 События/визиты → "Визиты"; Ru:1340, 1374–75 "препарат" → "лекарство"
- Ru:778 "Месячный лимит" (hayz bilan adashadi) → "Лимит на этот месяц исчерпан. Обновится 1-го числа."
- Ru:773/776/787 xatolar → "Проверьте выделенные поля…", "Это действие недоступно для вашего аккаунта.", "Консультация платная. Оплатите, чтобы начать чат."
- Ru:1752 "это глубже аналитика" → "более глубокая аналитика"; Ru:1511–13 "есть небольшой запас" (teskari ma'no) → "можно немного добавить"
- Ru:1584 "Близкий подключён" → "Yaqinim подключён(а)"; C/PartnerRu.kt:8 "Мой близкий" → "Близкие"/"Yaqinim"; PartnerRu:33 ты vs :263 вы — bittasi
- C/BadgeStrings.kt:222–245 "Прочитайте статей: $target", "3 дней" → `ru()`
- Ru:111 vergul; "8ч" → "8 ч"; Ru:1636 "Выполняем…" → "Обмениваем…"; 9 ta tasdiq sarlavhasida "?" yo'q

**EN**
- Plural (critical): En:941, 974, 979, 1026, 1062, 1122, 1149, 1196, 1204, 1253–54, 1303, 1377–78, 1443, 1543–48, 1557, 1592, 1621, 1713, 1716, 264, 1433; PartnerEn:146, 180, 231, 239; PrescriptionEn:139, 141; D/PrescriptionStrings.kt:299; D/DoctorStrings.kt:191–192, 273–274
- En:110 "Where are you right now?" → "Which stage of life are you in?"
- En:196, 207, 212, 184, 1420 "I do not know" → "I don't know" (qisqartmalar izchil)
- Tab va sarlavha mos emas: Health/Wellbeing, Food/Nutrition, Recovery/After birth
- En:1761 "so'm" → "UZS"; En:1576 "Your person connected" → "Yaqinim connected"; PartnerEn:249 va :8 ("Ask Yaqinim" / "My person")
- En:1244 "Taken" tugma → "Mark as taken"; En:888 → "No meals logged today"; En:926 → "You're lowest on $macro today"; En:1434–35 "samples" → "data"
- En:1440 mavjud bo'lmagan "priority settings"; En:1081 "Republican centre" → kalka
- Tugmalardagi ortiqcha artikl ("Send the code", "Clear the conversation") ~8 ta; qo'shtirnoq aralash 6 ta
- UK imlosi izchil (colour, centre) — saqlansin. Doctor ilovasi esa US/UK aralash (Gynecologist + Licence, foetal) — bittasi.

---

## 4. Mijoz ilovasi — bo'limlar (Partner, Pet, Badge, Frame, Prescription, StageTools, Weeks, HealthGate, Doctor)

**Pullik oqimlar halolligi**
- C/PetStrings.kt:110, 116 — Humo Premiumsiz uxlaydi, bu so'rovda (askBody, requestWhat) aytilmagan → "Eslatma: Humo faqat Premium bilan gapiradi"
- C/FrameStrings.kt:78, 82; PetStrings.kt:109 "abadiy sizniki" → "Hisobingizda doim qoladi"
- C/DoctorStrings.kt:221 (Ru 341, En 462) qaytarish — qachon/qayerga → "24 soat ichida javob bo'lmasa, pul N ish kunida kartangizga qaytadi" (server ConsultationService:320 "3 ish kuni" bilan moslang)
- C/DoctorStrings.kt:268 "24 soat ichida javob beradi" kafolati → "konsultatsiya 24 soat ochiq turadi"

**Tibbiy**
- C/PregnancyWeeksUz.kt:193 (30-hafta) — muddatidan oldingi tug'ruq belgilari; :207 (37-hafta) probka vs qon ketish; 14–28-haftalarda qon ketish triggeri yo'q (20-haftaga qo'shish)
- C/StageToolsUz.kt:113–114 "kayfiyat o'zgarsa 2–4 haftadan keyin" (teskari) → "yomonlashsa — istalgan vaqtda qayta o'ting yoki shifokorga yozing"
- C/StageToolsUz.kt:117–118 "depressiya ehtimoli yuqori" → "Javoblaringiz … belgilariga mos keladi"
- C/StageToolsUz.kt:82–93 EPDS savollari aralash shaklda; :107–108 "ko'pincha"/"ancha tez-tez" deyarli sinonim → "deyarli doim"/"ancha tez-tez"
- C/PartnerUz.kt:203–204 tug'ruq xabari → "Bu tez yordam chaqirmaydi — kerak bo'lsa 103"
- C/PartnerUz.kt:167–172 postpartum maslahatlarida yordam triggeri yo'q
- C/PrescriptionStrings.kt:74, 87 → "Dozani o'zingiz o'zgartirmang"
- C/StageToolsUz.kt:41–42 → "Menopauzadan keyin qon ketish qayd etilgan"; :148–149 5-1-1 qoidasi aniqroq; :153 "son" → "s"
- C/PetStrings.kt:103–104 AI pet intro'da "Men shifokor emasman"

**Maxfiylik**
- C/HealthGateStrings.kt:32–34, 54 — "o'qiydi" vs "yuborilmoqda" → "o'qiydi va himoyalangan hisobingizga saqlaydi"
- C/PartnerUz.kt:55–56 — intim alomatlar ko'rinadimi aniq emas; :53–54 energiya; :75–76 uzilganda xabar boradimi

**Izchillik / mayda**
- C/PrescriptionStrings.kt:66, 67, 73, 83 "Tabletkalarim" → "Dorilarim"
- C/PetStrings.kt:95/97 "Ulash"/"Ulashish" bir ro'yxatda → "Yaqinni ulash"/"Shifokorga ko'rsatish"; :106 "Legendar" → "Afsonaviy"; "Humo'ni" → "Humoni"
- C/DoctorStrings.kt:183–184, 225 "Suhbat" → "Konsultatsiya"
- C/PartnerUz.kt:130, 136, 146 grammatika/kalka; :30, 238, 269 "$date gacha"
- C/HealthGateStrings.kt:51 "Play Market" → "Google Play"
- C/FrameStrings.kt:98 (Ru), :176 (En "her profile photo")

---

## 5. Shifokor ilovasi

**Retsept** (yuqoridagi #8 dan tashqari)
- D/PrescriptionStrings.kt:302 EN maydon "With food" variant bilan bir xil → "Food timing"
- :135/222/309 "Nechanchi kundan" → "Kursning nechanchi kunidan"; :136/310 → "Davomiyligi (kun)"
- :160–163 tasdiq — "Bekor qilish" ikki ma'noda → sarlavha "Retsept bekor qilinsinmi?", tugma "Retseptni bekor qilish", body "Bemorga xabar boradi va dorilar to'xtatiladi. Qaytarib bo'lmaydi." (doctor-admin `Prescription.tsx:367–372` ham)
- :148 "Maslahat retsepti" → "Shifokor tavsiyasi — rasmiy retsept emas" (ikkala ilova)
- :107, 122 "Ukol" → "In'eksiya", "Qachon ichiladi" → "Qabul vaqti"; :227 RU "повторный приём" → "повторный визит"; :126–127 "erta/kech" → "Ertaroq/Kechroq"

**Jins (RU)**: D/WorkStrings.kt:244, 247 "Занята" → "Перерыв"; D/StringsRu.kt:86 "Ответьте первой" → "…ваш ответ будет первым"; TabStrings.kt:397, 438, 444 "пациента" → "пациентки"; ET:223–225 "самой себе" → "себе"; ET:192 "отключите его"

**Boshqa**
- D/TabStrings.kt:210 vs :632 — UZ "ochilgan paytdagi" va EN "when you open it" zid
- D/TabStrings.kt:241/448/663 — ro'yxat qachon o'chishi 3 tilda har xil (maxfiylik va'dasi)
- D/TabStrings.kt:268 "$date dan" → "${date}dan beri"; :291, 300 "Bola harakati" → "Homila harakati"; :281–283 "O'rtacha hayz", "Diapazon" → aniq nomlar; :585 RU "ms" → "мс"
- D/TabStrings.kt:228 → "Yangi konsultatsiyalarni qabul qilish"; :204 qayta to'lov
- D/WorkStrings.kt:124, 208 → "Pul qaytariladi"
- D/DoctorStrings.kt:139 savollar/postlar; :113 shaxs aralash
- D/StringsUz.kt:42, 44; StringsEn.kt:43, 46 xato matnlari yechimsiz
- "Chat" tabi aslida ommaviy lenta — shifokor uchun "Hamjamiyat" aniqroq

**Qattiq yozilgan**: SignInKit.kt:159 "SADORA DOCTOR", SignInScreen.kt:81 "DOCTOR", Brand.kt:175 tagline, ScanScreen.kt:121 placeholder, PatientScreen.kt:275 `"$filled / 5"` (TalkBack), Overlays.kt:270 "⚠", PrescriptionScreens.kt:436–438 / WorkSettingsScreen.kt:246–254 TalkBack matni; androidApp `values/strings.xml` push kanali faqat UZ.

---

## 6. Server

**ErrorText** (ET)
- :258 "Allaqachon baholangansiz" (teskari ma'no) → "Siz bu konsultatsiyaga baho qo'ygansiz"
- :113 telefon → "Raqamni +998 bilan, 9 ta raqam qilib kiriting"
- :119–121 kod xatolari → keyingi qadam ("Yangi kod so'rang")
- :127 o'chirish so'rovi — bekor qilish yo'li; :147 shifokor doza soni; :132 "apple yoki google" → "Apple yoki Google"; :133 `{1}` ichki konfiguratsiya foydalanuvchiga chiqadi
- :200 "kuzatish/следить" → "holatini ko'ra olasiz"; :211 chat cheklovi sababsiz; :199 yechimsiz
- :250, 264 xom holat nomi `({0})`; :302, 310–311 "store"; :274 RU "гулей"
- :70–76, 42–48, 129–131 jargon (UUID, YYYY-MM-DD, endpoint, token) — Yaqinim veb sahifasida ko'rinadi
- :151, 235, 294 "sabab" uch xil

**Push/SMS/AI**
- Faqat UZ pushlar — §0 #15. `PrescriptionService.kt:88` "Dr X ✓: retsept bekor qilindi" → sarlavha "Retsept bekor qilindi", matn "{doctor}: {reason}. Dori eslatmalari to'xtatildi."
- `RewardPhrases.kt:25` "streak" → "ketma-ketlik"
- SMS (`AppConfig.kt:200`) yaxshi; "Sadora xodimlari ham so'ramaydi" + SMS Retriever/iOS domain qatori (Eskiz qayta tasdiq)
- `PartnerInvitePage.kt:34` — faqat Google Play, App Store havolasi yo'q
- Android client: bitta "Eslatmalar" kanali DM va "tug'ruq boshlandi" uchun ham — alohida "Xabarlar" kanali kerak

---

## 7. Admin panellar

- `admin/src/pages/BillingPage.tsx:231` — tugma "Qaytarish" pul qaytarmaydi, faqat belgilaydi → "Qaytarildi deb belgilash"
- `ShopPage.tsx:250`, `ContentPage.tsx:119`, `BillingPage.tsx:231` — brauzer `confirm()` (OK/Cancel, brauzer tilida) → `Modal` (namuna: `ConsultationsPage.tsx:336` RefundDialog)
- `FlagsPage.tsx:52–73, 136` — kill-switch va o'chirish tasdiqsiz
- `UserCardPage.tsx:368, 384` sessiya/seans; `:117` "Grace period"
- `ReportContext.tsx:92` "Rad etish" → "Shikoyatni yopish (xabar qoladi)"
- `layout/Shell.tsx:40–100` til aralash (Dashboard, Feature flags, Audit log, streak)
- `ShopPage.tsx:237, 248` `‘` va `'` aralash
- `api/client.ts:127` (ikkala panel) "Server xatosi (502)" → "Hozir saqlab bo'lmadi. Birozdan keyin qayta urinib ko'ring."
- `doctor-admin/src/api/client.ts:214` `Accept-Language: 'uz'` qattiq — RU shifokor uchun
- `DoctorMoney.tsx:437` summa qoldiqdan ko'p bo'lsa ham tugma ochiq, tasdiqsiz
- "Yuborilmoqda…" barcha xavfli amallarda; "Operator tokeni", "Slug" jargon; `StatusPage.tsx:131` kontakt yo'q

---

## 8. Umumiy xulosa

**Yaxshi tomonlar**: UZ ohangi iliq, "siz" izchil; RU "вы" izchil, ayol jinsi to'g'ri, «» va ё to'g'ri; EN UK imlosi va sentence case izchil; disclaimerlar ehtiyotkor; bo'sh holatlar ko'pincha yaxshi yozilgan; Yaqinim'ning "hech qachon ko'rinmaydi" ro'yxati, RefundDialog — namunali.

**Tizimli muammolar**
1. Atamalar tarqoqligi — §1 lug'atini qotirib, test bilan himoyalash.
2. Xato matnlari "nima bo'ldi"ni aytadi, "qanday tuzatish"ni emas (limitlar, validatsiya, kod, blok).
3. Ko'plik shakllari (RU `дн.`, EN "1 days") — `StringsPluralTest` kerak.
4. i18n ikki yo'lda: push, iOS plist, Android kanal, doctor-admin — hali faqat UZ.
5. Tibbiy nozik joylar (yo'qotish, EPDS, qon ketish, 103) — matn bilan birga oqim ham.

**Tuzatish tartibi tavsiyasi**: §0 (release blokerlar) → §1 lug'at + plural helper + testlar → retsept/pul matnlari (ikkala ilova birga) → qolgan major → minor.
