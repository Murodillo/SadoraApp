# SADORA backend

Ktor (Netty) + PostgreSQL + Exposed + Flyway. Autentifikatsiya, profil va onboarding,
sikl/Mind/ovqatlanish/dorilar, ovqat skaneri, bildirishnomalar, wearable'lar,
maxfiy chat, AI chat,
entitlements/limitlar, feature flags va admin panel API'si.

## Modullar

| Modul | Nima |
|---|---|
| `:contract` | Mobil va backend bo'lishadigan DTO'lar (KMP: jvm + android + ios). Backend maydon nomini o'zgartirsa, mobil build sinadi — runtime'da emas |
| `:server` | Ktor ilovasi. `uz.sadora.server` |

## Muhitlar

Sozlamalar muhit fayllarida, har biri bitta muhitni to'liq ta'riflaydi:

| Fayl | Git'da | Nima uchun |
|---|---|---|
| `.env.dev` | ha | Dev. Ichida sir yo'q — hammasi ochiq standart qiymat yoki ataylab qo'yilgan qulaylik |
| `.env.prod.example` | ha | Prod shabloni. Bo'sh qoldirilgan har bir qator — sir |
| `.env.prod` | **yo'q** | Shablondan nusxa, prod hostida to'ldiriladi |

Ikkalasini bitta skript yuklaydi, shuning uchun muhitni adashtirib ishga tushirib
bo'lmaydi:

```bash
./tools/server_run.sh dev
```

```bash
./tools/server_run.sh prod
```

`dev` Gradle orqali ishga tushadi, `prod` esa `installDist` yasagan distributivni
ishlatadi — prod start Gradle demoniga ham, manba daraxtiga ham bog'liq bo'lmasligi
kerak.

**Prod o'zini himoya qiladi.** `SADORA_ENV=PROD` bo'lganda `AppConfig` uchta narsani
rad etadi va server umuman ko'tarilmaydi: dev JWT kaliti (yoki 32 belgidan qisqasi),
`OTP_EXPOSE_CODE=true`, va `OTP_FIXED_CODE` ning o'rnatilgani. Ya'ni dev sozlamalari
bilan prod ishga tushmaydi — buni eslab qolish shart emas.

Farqlar shu bilan tugamaydi: limitlar dev'da 20 barobar yumshoq (`configureRateLimit`),
chunki seed ham, demo ham bitta IP'dan keladi; `/docs` faqat prod'dan tashqarida
ochiladi.

## Ishga tushirish (dev)

```bash
docker compose up -d
```

```bash
./tools/server_run.sh dev
```

`.env.dev` bazani 5433-portda kutadi (`SADORA_DB_PORT`), chunki asosiy ishchi
kompyuterda 5432 ni boshqa loyiha egallagan. docker-compose ham shu o'zgaruvchini
o'qiydi, shuning uchun ikkalasi bir xil portda kelishadi.

Birinchi Owner hisobi jadval bo'sh bo'lganda `ADMIN_BOOTSTRAP_EMAIL` va
`ADMIN_BOOTSTRAP_PASSWORD` dan yaratiladi — `.env.dev` da ular allaqachon bor.

Test ma'lumotlari:

```bash
python3 ../tools/seed_demo.py
```

Server JVM 21 ga kompilyatsiya qilinadi. Gradle o'z toolchain'ini yuklab oladi, lekin
`./gradlew :server:installDist` yasagan skript `PATH` dagi `java` ni ishlatadi — eskiroq
JDK bo'lsa `UnsupportedClassVersionError` beradi. Shuning uchun distributivni
ko'tarishdan oldin `JAVA_HOME` ni 21 ga qo'ying (Docker obrazi buni o'zi hal qiladi).

## Hujjatlar va tekshirish

```bash
./gradlew :server:test :contract:jvmTest
```

* Admin panel alohida ilova: [`admin/`](../admin) (<http://localhost:5173>)
* Swagger UI (faqat dev/stage): <http://localhost:8080/docs>
* OpenAPI: [`openapi.yaml`](src/main/resources/openapi/openapi.yaml)
* `GET /health/live` — jarayon tirikmi (bazaga tegmaydi)
* `GET /health/ready` — trafikka tayyormi (bazani ham tekshiradi)

## Arxitektura qarorlari

**Sog'liq ma'lumotlari admin'ga ko'rinmaydi — bu tuzilma darajasida.** TZ 17-bo'limi
talabi. `AdminService` faqat `UserRepository`, `EntitlementRepository` va
`SubscriptionRepository` ga bog'liq; ularning hech biri sikl, simptom, kayfiyat, dori yoki
AI yozishmasiga yeta olmaydi. `AdminUserSummary` va `AdminUserCard` tiplarida bunday
maydon uchun joy yo'q. Ya'ni keyinchalik "operator uchun istisno" qo'shish uchun shu
bog'liqliklarni o'zgartirish kerak bo'ladi — bu ko'rinadigan qaror.

**Entitlement uchta qatlamdan yig'iladi:** tarif bo'yicha `feature_definitions` →
foydalanuvchi `user_entitlement_overrides` → `feature_usage_daily` dagi sarf. Definitions
60 soniya keshlanadi (admin o'zgartirsa darhol invalidatsiya qilinadi), sarf esa
keshlanmaydi — keshlangan "3 tadan 3 tasi qoldi" bir limitni ikki marta sarflashga yo'l
ochadi.

**Kunlik limit foydalanuvchining vaqt mintaqasida hisoblanadi.** Toshkent UTC+5, ya'ni
server yarim tunida hisoblansa limit har kuni besh soat kech yangilanadi.

**Refresh tokenlar aylanadi va oila bo'lib bekor qilinadi.** Har ishlatishda eskisi bekor
qilinib, o'rniga yangisi beriladi. Allaqachon ishlatilgan token qayta kelsa — yo o'g'irlangan
nusxa, yo noto'g'ri retry; ikkalasida ham butun oila bekor qilinadi va hodisa audit log'ga
tushadi. Foydalanuvchi foydasiga xato qilish o'g'rini tizimda qoldirish demakdir.

**Rollout barqaror hash bo'yicha bo'linadi.** `hash(flagKey + userId) % 100`. 5% dan 20% ga
kengaytirish faqat yangi foydalanuvchi qo'shadi, hech kimni chiqarib yubormaydi; ikki turli
bayroq bir xil odamlarni tanlamaydi.

**Vaqt: baza `timestamptz`, domen `kotlin.time.Instant`, JVM UTC ga qadab qo'yilgan.**
Konvertatsiya `core/Time.kt` da, repository chegarasida.

**Foydalanuvchilarda parol yo'q.** Kirish — telefonga kelgan kodni tasdiqlash: mavjud
raqam uchun u hisobni qaytaradi, yangisi uchun ochadi, shuning uchun «ro'yxatdan o'tish»
va «kirish» bitta amal. Bu ataylab: parol bilan ochilgan hisobga qaytib kirib
bo'lmasdi, hech bir mijoz ishlatmaydigan parol yo'li esa hech kim kuzatmaydigan yo'l
bo'lardi. Admin panelning o'z paroli bor — alohida realm, 2FA ortida.

**Parol — bcrypt (cost 12), refresh token va OTP — SHA-256.** Parol past entropiyali va
taxmin qilinadi, shuning uchun ataylab sekin; tasodifiy 256-bitli token uchun sekin hash
foyda bermaydi.

**Flyway sxemaning egasi.** `SchemaUtils.create` hech qachon chaqirilmaydi. Exposed
jadval obyektlari faqat query qurish uchun; ular migratsiyadan farq qilsa — bug
obyektda, migratsiyada emas.

**Sog'liq ma'lumotlari alohida chegara ortida.** `db/HealthTables.kt` — sikl, kunlik
yozuvlar, simptomlar — `AdminService` yetib bora olmaydigan joyda; u faqat hisob
repozitoriylariga bog'liq. Yozishdan oldin ikki darvoza: `store_health` roziligi va
tarif bo'yicha funksiya yoqilganligi. O'qish rozilikka bog'liq emas — saqlashga
rozilikni qaytarib olish o'z ma'lumotini ko'rish huquqini bekor qilmaydi.

**Prognoz taxmin qilmaydi.** Ma'lumot yetarli bo'lmasa `confidence: none` qaytadi va
sana berilmaydi; 28 kun o'ylab topilmaydi. Sikl bashorat qilmaydigan bosqichlar
(homiladorlik, menopauza) umuman prognoz olmaydi. Sikl kuni va faza bitta manbadan
hisoblanadi, shuning uchun ular hech qachon bir-biriga zid javob bermaydi.

**Kunlik yozuv — bitta qator.** Sikl kundaligi ham, Mind check-in'i ham `daily_logs`
ning o'sha qatoriga yozadi: 14-sentyabrdagi kayfiyat — bitta fakt, uni qaysi ekran
qayd etganidan qat'i nazar. Check-in faqat uchta maydonni almashtiradi, shuning uchun
u bir soat oldin kiritilgan simptomni o'chirib yubormaydi.

**Ovqatlanish jamlanmasi hisoblagichda saqlanmaydi.** Har safar qayd etilgan
ovqatlardan yig'iladi — aks holda o'chirilgan ovqat kunlik raqamni buzib qoldirardi.
Makrolar yeyilgan holicha saqlanadi: katalog tuzatilsa, o'tgan oyda nima yeganini
jimgina o'zgartirmasligi kerak.

**Rasmni model o'qiydi, lekin yozuv qilmaydi.** `POST /v1/nutrition/scan` bitta suratni
oladi va bitta baho qaytaradi — hech narsa saqlanmaydi. Kundalikka yozish alohida
so'rov (`POST /v1/nutrition/meals`), chunki porsiyani foydalanuvchi to'g'rilaydi va u
rozi bo'lmaguncha taxmin kundalikka tushmasligi kerak. Model qaytargan raqamlar
ishonilmasdan chegaralanadi: 90 000 kkal — bu katta baho emas, bu xato. `isFood: false`
to'liq javob sifatida qaytadi.

**Dori qabullari oldindan yozilmaydi.** Ular jadvaldan o'qish paytida hisoblanadi;
saqlanadigan yagona narsa — foydalanuvchi qilgan ish. Bir yillik qatorlarni oldindan
yaratish kursni tahrirlaganda eskilarini qoldirar va jadval o'zgarishi tarixni jimgina
qayta yozardi. Arxivlash o'chirish emas: `endedOn` qo'yiladi va tarix joyida qoladi.

**Dorilar bo'yicha maslahat berilmaydi.** Tarix faqat sanoq qaytaradi — nechta qabul
qilingan, o'tkazilgan, kutilmoqda. Ball ham, tavsiya matni ham yo'q: ilova o'tkazib
yuborilgan qabul haqida ko'rsatma bermaydi, retsept yoki farmatsevtga yo'naltiradi.

**Bildirishnomalar outbox orqali ketadi.** Scheduler har daqiqada tiklaydi, jadvaldan
nomzodlarni topadi va qarorini — jumladan **to'xtatish qarorini sababi bilan** —
outbox'ga yozadi. «Nega eslatma kelmadi» degan savolga javob beradigan yagona narsa
shu. Har bir nomzodda dedupe kalit bor, shuning uchun har daqiqada ishlash ham,
tick o'rtasida qayta ishga tushish ham xavfsiz.

**Dori eslatmasi sokin soatlarni va chegaralarni chetlab o'tadi.** U reklama trafigi
emas — foydalanuvchi o'zi qo'ygan vaqt. Ammo uning o'z kalitini o'chirishi baribir
ustun turadi: bu byudjet emas, qaror.

**Provayderdagi farqlar moslik jadvalida tugaydi.** Metrika nomi, birligi va
ko'paytiruvchisi — `provider_metric_mappings` dagi qatorlar, kod emas. Yangi provayder
qo'shish yoki maydonini o'zgartirgan provayderga ergashish admin panelidagi bitta
qator; sinovda Oura shu yo'l bilan, kod o'zgartirmasdan ulandi.

**Kunlik jamlanma qayta hisoblanadi, oshirilmaydi.** Qayta sinxronlangan namuna
qiymatni joyida o'zgartiradi — oshirib boriladigan jami esa namunalardan uzoqlashib
ketardi va buni hech narsa sezmasdi. Ikki manba bir metrikani bersa, biri tanlanadi:
telefon va soat qadamlarini qo'shish kunni ikkilantiradi.

**Tahlillar o'lchanmagan raqamni qaytarmaydi.** `GET /v1/insights` bo'sh kunni `null`
qilib qaytaradi, nol qilib emas — aks holda dam olingan hafta qulagandek ko'rinadi — va
hech narsadan o'rtacha hisoblamaydi. Oldingi oyna xuddi shu uzunlikda, shuning uchun
"+12 daqiqa" doim bir xil sonli kunga nisbatan. Bog'liqlik kamida sakkiz kunlik yozuvdan
va median bo'yicha ikki yarimning sezilarli farqidan chiqadi; server nima o'lchaganini
aytadi, gapni mijoz yozadi. Chuqurlik `insights_history`, hikoya `ai_insights` ortida —
ilovadagi qulf endi chizma emas, serverning qarori (V13).

**Bilim kutubxonasi ilovada emas, bazada.** Maqolalar admin panelida yoziladi
(`content_articles`), ilova o'zining birorta maqolasini olib yurmaydi. Tanasi — tipli
bloklar ro'yxati (`heading`, `paragraph`, `bullets`, `note`), HTML emas: o'quvchi ikki
platformadagi Compose, va yopiq ro'yxat unga har bir blokni to'g'ri joylashtirish imkonini
beradi. Chop etish saqlashdan alohida amal — qoralama ilovada umuman ko'rinmaydi (`404`),
muharrir esa yarim yozilgan matnni bemalol qoldiradi. Premium maqola hammaga ro'yxatda
turadi va faqat tanasi yopiladi: `truncated: true` bilan birinchi xatboshi beriladi,
chunki bo'sh sahifa ustidagi paywall nima sotilayotganini ko'rsatmaydi. Qulfni
`learn_premium` hal qiladi, ilova esa faqat kelgan `locked` bayrog'ini chizadi.

**Maxfiy chat taxallus ostida, va admin tomonida ham shunday.** Har hisobga bir marta
ikki so'zli taxallus beriladi (`community_identities`) — postdan hisobga qaytadigan
yagona bog'lanish shu jadval, va admin API uni o'qimaydi. Moderatsiya javoblarida
foydalanuvchi ID'si yo'q; muallifni cheklash post orqali qo'llanadi, moderator kimligini
bilmaydi. Shikoyat — har o'quvchidan bir marta, o'z matniga emas; beshta ochiq shikoyat
matnni o'zi yashiradi, moderator qaytarishi mumkin. Bo'lim `community` bayrog'i ortida.

**AI chat limitni javobdan oldin sarflaydi.** `POST /v1/ai/chat` avval `ai_chat`
entitlement'ini `consume` qiladi — `429 limit_reached` yoki `402 entitlement_required` —
keyin javob beradi, shuning uchun javob olgan har bir so'rov hisoblangan. Foydalanuvchi
ma'lumotlari faqat `ai_insights` roziligi bilan o'qiladi; roziliksiz javob umumiy va
`basedOn` bo'sh.

**Javobni model yozadi, lekin savol javobsiz qolmaydi.** `AiGateway` Gemini'ga murojaat
qiladi; kalit bo'lmasa, `ai_model_enabled` bayrog'i o'chirilgan bo'lsa yoki model
qoqilsa (timeout, kvota, bloklangan javob) — o'sha savolga qoidalar javob beradi. Uchala
holat ham xato emas, javobdir; farqni faqat log biladi, va o'rni ham aynan shu.
`thinkingLevel: minimal` bilan yuboriladi: o'ylash tokenlari `maxOutputTokens` ichidan
yeyiladi va bitta javobni 3 soniyadan 16 soniyaga cho'zgan edi.

**Javob foydalanuvchi tilida keladi.** `AiPhrases` — ilovadagi `Strings` bilan bir xil
shakl: interfeys va uchta implementatsiya, ya'ni yangi jumla qo'shilsa, unga javob
bermagan til kompilyatsiya xatosi bo'ladi. Model uchun bu prompt tili; qoidalar dvigateli
uchun esa undan ham muhimi — u mavzuni savolning ichidagi so'zlardan topadi, va o'sha
so'zlar tilga bog'liq. Ilgari ruscha "почему я устала" birorta o'zbekcha o'zakka
tushmagani uchun umumiy javobga tushib ketardi. "Nimaga tayandim" qatori ham o'sha
tilda yoziladi.

**Suhbat saqlanmaydi, xarajat esa saqlanadi.** `ai_usage_log` — model, tokenlar, narx
(USD mikro), kechikish, natija. Savol ham, javob ham yo'q, va jadvalda ularni qo'yadigan
ustun ham yo'q: "xarajat logi" — bu va'da sezdirmay buziladigan eng ehtimolli joy.
Admin paneldagi "AI xarajati" sahifasi shu jadvalni o'qiydi va modelni o'sha yerdan
o'chirish mumkin.

**Narx bazada, obuna esa faqat provayder tasdig'idan keyin.** `billing_plans` narxni
tiyinda saqlaydi va ilova uni `GET /v1/billing/plans` orqali o'qiydi — narxni o'zgartirish
uchun yangi ilova versiyasi kerak emas. `POST /v1/billing/checkout` faqat `pending` yozuv
va havola yaratadi; obunani hech kim so'rab ololmaydi. Uni `activate` beradi, va unga
faqat tekshirilgan callback yetadi: Payme — Basic `Paycom:<key>` bilan, Click — MD5
imzosi bilan (imzo doimiy vaqtda solishtiriladi). To'lov — jurnal yozuvi: har ikkala
provayder ham qayta so'raydi, shuning uchun `PerformTransaction` va `Complete` ikkinchi
marta kelganda yangi obuna emas, o'sha javob qaytadi (`payment_transactions.state` shu
uchun bor). Bekor qilish callback'i to'langan obunani olib qo'ymaydi — bu operator
qarori, va provayderning xabari bilan jimgina qaytarib olish odam sotib olgan narsani
yo'qotishi demakdir.

**Store cheki mijozdan emas, store'dan tasdiqlanadi.** `POST /v1/billing/store/verify`
`StoreVerifier` orqali o'tadi; kalitlar hali yo'q, shuning uchun standart implementatsiya
rad etadi (`UnconfiguredStoreVerifier`). Bu ataylab: hammaga "ha" deydigan zaglushka
testda ishlaydi va productionda paywall'ni butunlay ochib yuboradi.

**"Hisobni o'chirish" ikki qadam, va ikkinchisini kimdir bajaradi.** `DELETE /v1/me`
hisobni belgilaydi va barcha qurilmalarni darhol chiqaradi — refresh token o'ladi, ya'ni
hech bir seans o'zini yangilay olmaydi. `AccountErasureJob` esa
`ACCOUNT_ERASURE_GRACE_DAYS` (standart 30) o'tgach qatorni haqiqatan o'chiradi. Bitta
`DELETE FROM users`: sxemada unga tegishli har bir jadval `ON DELETE CASCADE` bilan
`users(id)` ga bog'langan, shuning uchun sikl, ovqat, kundalik, dorilar, postlar,
qurilmalar va tokenlar u bilan birga ketadi — va kelasi sprintda qo'shilgan jadval ham
o'z tashqi kaliti bilan qamrab olinadi. Ataylab qolgan ikki istisno `ON DELETE SET NULL`:
audit jurnali va AI xarajat jurnali — tarix javob beradi, odam esa ichida qolmaydi.
Muhlat "har ehtimolga qarshi" saqlash emas: u tugagach ma'lumot yo'q va uni ilova ichida
qaytarib bo'lmaydi.

**Bildirishnoma FCM orqali ketadi, sozlanmagan bo'lsa log'ga.** `FCM_PROJECT_ID` va
`FCM_SERVICE_ACCOUNT_FILE` berilgan bo'lsa `FcmPushSender` ishlaydi — bitta Firebase
loyihasi ikkala platformani ham qamraydi (Android to'g'ridan-to'g'ri, iOS o'sha loyihaga
yuklangan APNs kaliti orqali). Berilmagan bo'lsa `LoggingPushSender`: bu store
tekshiruvchisidan farqli o'laroq rad javob emas — faqat log'ga yozilgan bildirishnoma
hech kimga zarar qilmaydi, faqat log'ga yozilgan to'lov esa mahsulotni bepul qilib
qo'yadi. Kalit har bir token uchun alohida so'rov: FCM `UNREGISTERED` desa, o'sha token
o'chiriladi — qurilma o'chirib tashlangan, va uni cheksiz sinash har bir
bildirishnomaga kafolatlangan bitta xatolik qo'shadi. Bitta qurilmaning yiqilishi
boshqasida yetib borgan eslatmani muvaffaqiyatsiz deb belgilamaydi.

**Onboarding'dagi birinchi check-in health-gate ortida.** `firstCheckIn` profil bilan
birga keladi, lekin `HealthService` orqali, `store_health` roziligi bo'lgandagina
yoziladi — roziliksiz jimgina tashlab yuboriladi, so'rov muvaffaqiyatsiz bo'lmaydi.
`UserService` sog'liq ma'lumotiga tegmaydi.

## Integratsiya testi

`ApiIntegrationTest` butun API'ni haqiqiy Postgres ustida yuritadi — OTP → onboarding →
maxfiy chat → moderatsiya → AI limiti. `TEST_DB_URL` berilmasa o'tkazib yuboriladi:

```bash
docker exec sadora-postgres psql -U sadora -d sadora -c "CREATE DATABASE sadora_test"
```

```bash
TEST_DB_URL=jdbc:postgresql://localhost:5433/sadora_test ./gradlew :server:test
```

CI'da u job'ning o'z Postgres'iga qarshi ishlaydi.

## Serverga qo'yish (deploy)

Backend — JVM jarayoni (Ktor) va u **PostgreSQL** talab qiladi: sxemada 91 ta
`TIMESTAMPTZ`, 28 ta `gen_random_uuid()`, `jsonb`, regex `~` operatori va beshta
qisman (`partial`) unikal indeks bor. Aynan o'sha qisman indekslar bir tangani ikki
marta to'lab yuborishdan saqlaydi, va MySQL'da ularning ekvivalenti yo'q.

Shuning uchun **umumiy (shared) cPanel xosting bunga yaramaydi**: u PHP uchun, uzluksiz
JVM jarayonini ko'tarmaydi va MySQL beradi. Kerak bo'ladigan narsa — Docker o'rnatilgan
VPS yoki shunga o'xshash xizmat. Statik narsalar (landing sahifasi, admin panelning
yig'ilgan `dist`i) esa istalgan xostingda yashayveradi.

`docker-compose.prod.yml` butun backendni bitta serverga ko'taradi — Postgres, Redis va
API. Serverda Docker'dan boshqa hech narsa kerak emas: JDK ham, Gradle ham, manba
daraxti ham API konteynerning ichida yig'iladi.

```bash
cp server/.env.prod.example server/.env.prod   # so'ng bo'sh qatorlarni to'ldiring
docker compose -f docker-compose.prod.yml --env-file server/.env.prod up -d --build
```

`POSTGRES_PASSWORD` majburiy: usiz compose ishga tushmaydi, chunki namunadagi parol
bilan jimgina ko'tarilgan prod bazasi — parolini kimdir baribir topadigan baza.

Postgres va Redis hech qanday portni tashqariga chiqarmaydi; ular faqat compose tarmog'i
ichidan ko'rinadi. API esa `127.0.0.1:8080` da turadi, ya'ni unga faqat shu mashinadagi
proxy yetadi. Domen tayyor bo'lgach, TLS'ni Caddy oladi (sertifikatni o'zi yangilaydi):

```bash
SADORA_DOMAIN=api.sadora.uz docker compose -f docker-compose.prod.yml \
  --env-file server/.env.prod --profile tls up -d
```

Ikki holatni farqlash kerak:

| | **doimiy test serveri** | **haqiqiy prod** |
|---|---|---|
| `SADORA_ENV` | `DEV` | `PROD` |
| SMS kodi | `123456` (`OTP_FIXED_CODE`) | haqiqiy provayder |
| Ishlaydimi? | bugun | **yo'q — SMS provayder ulanmagan** |

`AppConfig.verifyProductionSafety()` ataylab yo'l bermaydi: dev JWT kaliti, javobda
qaytariladigan OTP yoki doimiy OTP kodi bilan `SADORA_ENV=PROD` **ko'tarilmaydi**. Ya'ni
haqiqiy prod SMS provayderni kutadi; doimiy test serveri esa hozir ham ishlayveradi va
tunnel bilan bog'liq muammolarni butunlay yo'q qiladi.

Deploy'dan keyin tekshiruv — `/health/ready` bazani ham tekshiradi, `/health/live` esa
yo'q (bazadagi qisqa uzilish konteynerni o'ldirmasligi uchun):

```bash
curl https://api.sadora.uz/health/ready
```

## Nima hali yo'q
App Store / Google Play cheklarini haqiqiy tekshirish (`StoreVerifier` interfeysi va
grant yo'li tayyor, kalitlar yo'q) va ilovadagi billing SDK · SMS provayderi (`OtpSender` interfeysi
tayyor, hozircha log'ga yozadi) · Health Connect /
HealthKit o'qish qatlami (server tomon `POST /v1/health-data/samples` tayyor, ilovada
namuna yig'uvchi hali yo'q, shuning uchun uyqu va qadam ekranlari bo'sh holatini
ko'rsatadi).
