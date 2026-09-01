# SADORA backend

Ktor (Netty) + PostgreSQL + Exposed + Flyway. Tijorat taklifining **1-sprint** backend
qamrovi: autentifikatsiya, profil va onboarding, entitlements/limitlar servisi, feature
flags va admin panel API'si.

## Modullar

| Modul | Nima |
|---|---|
| `:contract` | Mobil va backend bo'lishadigan DTO'lar (KMP: jvm + android + ios). Backend maydon nomini o'zgartirsa, mobil build sinadi — runtime'da emas |
| `:server` | Ktor ilovasi. `uz.sadora.server` |

## Ishga tushirish

```bash
docker compose up -d
```

```bash
./gradlew :server:run
```

Boshqa loyihaning Postgres'i 5432 ni band qilgan bo'lsa:

```bash
SADORA_DB_PORT=5433 docker compose up -d
```

```bash
DB_URL=jdbc:postgresql://localhost:5433/sadora ./gradlew :server:run
```

Birinchi admin hisobini yaratish (jadval bo'sh bo'lgandagina ishlaydi):

```bash
ADMIN_BOOTSTRAP_EMAIL=owner@sadora.uz ADMIN_BOOTSTRAP_PASSWORD=changeme123 ./gradlew :server:run
```

Sozlamalar — `.env.example`. Hammasida dev qiymati bor, shuning uchun hech narsa
bermasdan ham ko'tariladi. `AppConfig` prod'da ikki narsani rad etadi: dev JWT kaliti va
`OTP_EXPOSE_CODE=true`.

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

## Nima hali yo'q (3-sprint)
bildirishnoma scheduler'i · AI Gateway va uning xarajat logi · App Store / Google Play va
Payme/Click webhook'lari · hisobni haqiqiy o'chirish job'i · SMS provayderi
(`OtpSender` interfeysi tayyor, hozircha log'ga yozadi) · admin 2FA enrolment ekrani.
