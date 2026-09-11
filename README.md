# SADORA

Ayollar salomatligi ilovasi — sikl, homiladorlik, menopauza, uyqu, ovqatlanish va
kayfiyat bir joyda. Kotlin Multiplatform + Compose Multiplatform, Android va iOS
uchun bitta umumiy UI.

> **Har bir ayol. Har bir lahza.**

Interfeys uch tilda — o'zbek, rus va ingliz. O'zbekchasi asl, qolgan ikkitasi undan
tarjima; til `Profil → Til` da tanlanadi va AI javoblari ham o'sha tilda keladi.

---

## Muhitlar

Ikkita muhit bor va ular alohida sozlamalar fayllaridan yashaydi:

| | **dev** | **prod** |
|---|---|---|
| Sozlamalar | `server/.env.dev` — git'da, ichida sir yo'q | `server/.env.prod` — git'ga qo'shilmaydi, `.env.prod.example` dan nusxa |
| Baza | `docker compose` ko'targan lokal Postgres | boshqariladigan alohida instansiya |
| SMS kodi | har doim `123456` (`OTP_FIXED_CODE`) | haqiqiy SMS provayder (hali ulanmagan) |
| JWT kaliti | ochiq, repozitoriyda | generatsiya qilingan, kamida 32 belgi |
| Swagger `/docs` | ochiq | yopiq |
| Limitlar | 20 barobar yumshoq — seed va demo bir IP'dan keladi | to'liq qattiq |
| Ilova | debug build | release build |

`AppConfig.verifyProductionSafety()` prod'ni himoya qiladi: dev JWT kaliti, javobda
qaytariladigan OTP yoki doimiy OTP kodi bilan server **ko'tarilmaydi**. Ya'ni dev
sozlamalari bilan prod'ni tasodifan ishga tushirib bo'lmaydi.

Hozircha faqat **dev** ishlaydi. Prod Play Market'ga chiqishdan oldin ko'tariladi.

---

## Ishga tushirish (dev)

Postgres va Redis'ni ko'taring, so'ng serverni — sozlamalar `server/.env.dev` dan
o'qiladi:

```bash
docker compose up -d
```

```bash
./tools/server_run.sh dev
```

Admin panel (backend ishlab turganda) — <http://localhost:5173>, batafsil
[admin/README.md](./admin/README.md):

```bash
npm --prefix admin install && npm --prefix admin run dev
```

Test ma'lumotlari — 4 ta demo hisob, 10 ta fon hisobi va Bilim kutubxonasi:

```bash
python3 tools/seed_demo.py
```

Android ilovasi:

```bash
./gradlew :androidApp:assembleDebug
```

iOS uchun `iosApp/iosApp.xcodeproj` faylini Xcode'da oching va ishga tushiring. Imzolash
uchun `iosApp/Configuration/Config.xcconfig` faylidagi `TEAM_ID` ni to'ldiring — u
hisobga bog'liq, shuning uchun repozitoriyda bo'sh turadi.

Haqiqiy telefonda sinash uchun APK'ni shu kompyuterning nomiga qaratib yig'ing —
emulyatordagi `10.0.2.2` telefonda mavjud emas:

```bash
./gradlew :androidApp:assembleDebug -Psadora.devHost=$(scutil --get LocalHostName).local
```

IP o'rniga nom: DHCP ijarasi yangilanganda manzil o'zgaradi va telefondagi ilova
yo'q bo'lgan manzilga murojaat qilib, "Internetga ulanib bo'lmadi" deb yozadi.
`-Psadora.devHost` to'liq URL ham qabul qiladi (`https://...`), bu tunnel orqali
ishlaganda kerak bo'ladi.

Android SDK yo'li `local.properties` faylida ko'rsatiladi (bu fayl git'ga
qo'shilmaydi):

```
sdk.dir=/Users/<siz>/Library/Android/sdk
```

### Reliz uchun yig'ish

Do'kon identifikatori — `uz.sadora.app`, ikkala platformada ham bir xil va serverning
`APPLE_BUNDLE_IDS` sozlamasi ham shuni kutadi. U bir marta chiqqandan keyin
o'zgartirilmaydi.

Imzo kaliti `androidApp/keystore.properties` dan o'qiladi (git'ga qo'shilmaydi;
`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Fayl bo'lmasa release turi
imzosiz yig'iladi — Play baribir imzosiz yuklamani qabul qilmaydi, lekin toza klonda
build buzilmaydi.

Versiya buyruq qatoridan beriladi, ya'ni reliz uchun commit shart emas; Play bir marta
ko'rgan `versionCode` ni ikkinchi marta qabul qilmaydi:

```bash
./gradlew :androidApp:bundleRelease -Psadora.versionCode=2 -Psadora.versionName=1.0.1
```

Release build R8 bilan qisqartiriladi va obfuskatsiya qilinadi. Wire format aks ettirish
orqali topiladi, shuning uchun `androidApp/proguard-rules.pro` `:contract` DTO'larini va
ularning serializatorlarini saqlaydi — bu qoidalarsiz ilova yig'iladi, o'rnatiladi va
birinchi so'rovda yiqiladi. `bundleRelease` — o'sha qoidalarni tekshiradigan yagona narsa.

Testlar:

```bash
./gradlew :server:test :contract:jvmTest
```

```bash
./gradlew :shared:testAndroidHostTest
```

---

## Mijozga ko'rsatish

Butun demoni internetga chiqaradi: Postgres, backend, admin panel, ikkita Cloudflare
tunnel va o'sha tunnelga qaratilgan yangi APK.

```bash
./tools/demo_up.sh
```

Ikkala havolani chop etadi va `build/demo/urls.txt` ga yozadi. Yopish:

```bash
./tools/demo_down.sh
```

Tunnel manzillari har safar yangilanadi, shuning uchun skript APK'ni ham qayta yig'adi —
mijozga havola va APK birga beriladi. Demo davomida server internetdan ochiq turadi va
doimiy SMS kodi yoqilgan, shuning uchun tugagach yopib qo'ying.

---

## Shoxlar

| Shox | Nima uchun |
|---|---|
| `dev` | Kundalik ish shu yerda. Barcha o'zgarishlar shu shoxga tushadi |
| `main` | Prod. Play Market'ga chiqqanda `dev` shu yerga merge qilinadi |

---

## CI/CD

`dev` yoki `main` ga ochilgan har bir pull request va ularga har bir push GitHub
Actions'da tekshiriladi. **`dev` ga ochilgan PR** — va merge'dan keyin `dev` ning o'zi —
barcha tekshiruvlar o'tgach staging serverga avtomatik yetkaziladi. `main` ga PR faqat
tekshiriladi: `main` do'kon relizi.

```
PR → dev                                                     (.github/workflows/ci.yml)
 ├─ Backend          testlar, haqiqiy Postgres'da migratsiya, Kover coverage (pastki chegara 58%)
 ├─ Android/shared   shared modul testlari
 ├─ iOS shared       Kotlin/Native testlari — yetkazishni kutdirmaydi
 ├─ Admin panel      typecheck, vitest + coverage chegaralari, build, npm audit
 ├─ Deploy tooling   shellcheck, actionlint, gate testlari, compose va Caddyfile tekshiruvi
 └─ Staging (hammasi o'tsa)                                  (.github/workflows/stage.yml)
     ├─ API image     bir marta yig'iladi → GHCR, digest bo'yicha; SBOM va provenance bilan
     ├─ Web bundle    admin panel + landing
     ├─ Deploy        DB backup → almashtirish → /health/ready shu commit'ni aytguncha kutadi,
     │                aytmasa o'zi oldingi relizga qaytadi
     ├─ APK           staging URL bilan yig'iladi, imzosi tekshiriladi, landing'dagi
     │                /download.html ga chiqadi (serverda saqlangani bayt-baayt solishtiriladi)
     └─ Smoke test    tashqaridan: health va reliz, admin, CORS, landing, taklif sahifasi, APK
```

PR'da bitta izoh turadi va har run'da yangilanadi: joblar, testlar soni, coverage va
staging natijasi.

### Xavfsizlik

- **CI kaliti shell olmaydi.** Serverda u faqat `/usr/local/bin/sadora-ci` ni ishga
  tushiradi (`authorized_keys` da `command=`), jump host'da esa faqat serverning 22-portiga
  ulana oladi. Gate buyruqlari: `url`, `deploy`, `publish-apk`, `rollback`, `status`.
- **Infratuzilma CI'dan o'zgarmaydi.** Gate compose fayllari, Caddyfile va o'zini
  o'zgartirmaydi — ular `tools/deploy_stage.sh` bilan qo'lda qo'llanadi. PR ularni
  o'zgartirsa, run'da ogohlantirish chiqadi.
- Host kalitlari secret'da qotirilgan (`StrictHostKeyChecking yes`); GHCR tokeni serverda
  faqat bir martalik docker config'da yashaydi.
- Repo public, loglari ham: server manzillari va tunnel URL'lari yashiriladi, staging
  APK artifact sifatida yuklanmaydi. Fork PR'lari secret olmaydi va yetkazilmaydi.
  Action'lar commit SHA bilan qotirilgan.

### Bir martalik sozlash

```bash
cp deploy/stage/hosts.env.example deploy/stage/hosts.env   # manzillarni yozing
./tools/deploy_stage.sh                                     # gate + CI kalitini bog'laydi
./tools/ci_secrets.sh                                       # `staging` environment secret'lari
```

`ci_secrets.sh` kalit va keystore'ni fayldan o'qiydi, hech narsa terilmaydi va
chiqarilmaydi. Staging APK shu kompyuterning `~/.android/debug.keystore` bilan
imzolanadi — telefondagi eski yig'ma ustidan yangilanadi.

### Qo'lda

- **Qayta deploy yoki rollback:** Actions → Stage → Run workflow. `rollback_to` bo'sh
  bo'lsa — oldingi sog'lom relizga.
- **Smoke test:** `tools/ci/smoke.sh <app url> <landing url> <commit sha>`
- **Testlar lokal:**
  - gate — `docker run --rm -v "$PWD":/src -w /src ubuntu:24.04 bash deploy/stage/test/sadora-ci.test.sh`
  - admin — `npm --prefix admin run test:coverage`
  - server coverage — `TEST_DB_URL=… ./gradlew :server:koverHtmlReport`
  - hisobot generatori — `python3 -m unittest discover -s tools/ci`

### Cheklovlar

- Staging bitta. `dev` ga ochiq ikki PR bir-birining ustiga deploy qiladi — oxirgisi
  turadi; merge'dan keyin `dev` yana deploy bo'ladi.
- Quick tunnel manzili cloudflared qayta ishga tushganda (masalan, server reboot)
  o'zgaradi. Keyingi deploy CORS'ni va APK'ni yangi manzilga moslaydi, lekin eski APK'lar
  ishlamay qoladi. Domen bu muammoni yo'qotadi.
- Migratsiya orqaga qaytmaydi: rollback image'ni qaytaradi, schema'ni emas. Har
  deploydan oldingi dump serverda `/opt/sadora/backups` da (oxirgi 10 ta).

## Arxitektura

Butun UI `:shared` modulining `commonMain` manbasida — Android va iOS bir xil
kodni ishlatadi. Platformaga xos qism juda kichik: ikkala tomonda ham faqat
`App()` ni chaqiradigan ingichka kirish nuqtasi.

```
androidApp/          MainActivity — App() ni chaqiradi
iosApp/              SwiftUI ContentView — App() ni chaqiradi
admin/               React + TS admin panel — admin/README.md
contract/            Mobil va backend bo'lishadigan DTO'lar (KMP)
server/              Ktor backend — server/README.md
shared/src/commonMain/kotlin/uz/sadora/app/
├── App.kt           Ildiz: AppState va Navigator shu yerda yashaydi
├── design/          Dizayn tokenlari (ranglar, tipografika, o'lchamlar, mavzu)
├── i18n/            Uch tildagi matnlar (UZ — asl, RU, EN)
├── model/           Domen modeli va namuna ma'lumotlar
├── nav/             Navigatsiya holati (Tab, Route, Navigator)
└── ui/
    ├── components/  Komponentlar kutubxonasi
    ├── onboarding/  14 ta ekran: splash → kirish
    ├── core/        5 ta asosiy tab
    ├── journey/     "Yo'l" tabi va hayot bosqichlari
    ├── modules/     Modullar (skaner, ong, dorilar, uyqu, bilim…)
    └── settings/    Profil ichidagi sozlama ekranlari
```

### Qatlamlar

Bog'liqlik bir tomonga oqadi — `design` → `model` → `components` → ekranlar → `App`.
Hech bir ekran boshqa ekranni to'g'ridan-to'g'ri chaqirmaydi; ular faqat
`onOpen(Route)` orqali gaplashadi, shuning uchun har bir ekranni alohida ko'rish
va ko'chirish mumkin.

**`design/`** — dizayn tizimining yagona manbasi. `SadoraColors` ikkala mavzu
uchun ham *barcha* tokenlarni belgilaydi, shuning uchun ekranlar `if (dark)` yozmaydi;
ular `Sadora.colors.primary` deb yozadi va mavzu o'zi hal qiladi. Tipografika ettita
qadamdan iborat, radius va masofalar 8pt panjarasiga bog'langan.

**`i18n/`** — matnlarning yagona manbasi. `Strings` — interfeys, ya'ni yangi qator
qo'shilsa, unga javob bermagan til kompilyatsiya xatosi bo'ladi: bo'sh joy ekranga
chiqmaydi. Ekran tilni bilmaydi, `val t = strings` deb yozadi va `App` butun daraxtni
bitta `ProvideStrings` ichiga oladi. Til `Profil → Til` da tanlanadi, darhol qo'llanadi
va serverga yoziladi.

**`model/`** — `AppState` butun ilova uchun bitta xotiradagi do'kon. Ekranlar
to'g'ridan-to'g'ri shu yerdan o'qiydi va yozadi; controller'lar uni server javobi bilan
to'ldiradi. Hammasi Compose state, ya'ni har qanday o'zgarish tegishli ekranni qayta
chizadi.

**`nav/`** — navigatsiya kutubxonasi qo'shilmagan. `Navigator` joriy fazani
(splash / onboarding / kirish / asosiy), joriy tabni va route'lar stekini saqlaydi.
`replaceTop` chiziqli oqimlar uchun — masalan kamera → tahlil → natija, bu yerda
orqaga qadam tashlash noto'g'ri bo'lardi.

### Hayot bosqichi — eng katta shox

`LifeStage` ilovadagi eng katta tarmoqlanish. U "Yo'l" tabini butunlay
almashtiradi va tab yorlig'ini ham o'zgartiradi — homilador foydalanuvchi "Sikl"
emas, "Homilador" deb ko'radi.

Muhim jihat: homiladorlik, tug'ruqdan keyingi davr va menopauza uchun sikl
bashorati **umuman ko'rsatilmaydi**. Bu "o'chirilgan Sikl ekrani" emas — har biri
o'z maketiga, o'z asosiy ko'rsatkichiga va o'z tiliga ega.

| Bosqich | Asosiy ko'rsatkich | Bashorat |
|---|---|---|
| Sikl / Rejalashtirish | Sikl kuni | Bor, "Taxminiy" belgisi bilan |
| Homiladorlik | Hafta | Yo'q |
| Tug'ruqdan keyin | Tiklanish haftasi | Yo'q |
| Perimenopauza | Muntazamlik grafigi | Yo'q |
| Menopauza | Balans balli | Yo'q |

---

## Dizayn qoidalari

Bular shunchaki uslub emas — kodda ataylab saqlangan qarorlar.

**Gradient faqat to'rt joyda.** Hero, AI, Premium CTA va markaziy FAB. Boshqa
hech qayerda. Shuning uchun `AiSummaryCard` va `PremiumCtaButton` gradientni
o'zida saqlaydi, `SadoraCard` esa yo'q.

**Rang hech qachon yagona indikator emas.** Kalendarda qayd etilgan kunlar
to'ldirilgan, bashorat qilinganlari faqat konturli; afsona ikkalasini so'z bilan
ham yozadi. Dori qabul panjarasida ham xuddi shunday.

**Har bir bashorat belgilanadi.** "Taxminiy" nishoni bashorat ko'rsatilgan har bir
joyda turadi.

**Sabab-natija da'vo qilinmaydi.** Tahlillar "ko'pincha birga kuzatilgan" deb
yozadi, "sabab bo'lgan" demaydi.

**Tibbiy ko'rsatma berilmaydi.** Dorilar ekrani o'tkazib yuborilgan qabul haqida
maslahat bermaydi — retsept yoki farmatsevtga yo'naltiradi. Yagona istisno:
homiladorlikda bola harakati sezilarli kamaysa, ilova kechiktirmasdan shifokorga
murojaat qilishni aytadi.

**Premium bloklovchi emas.** Bepul rejadagi hamma narsa qoladi. Qulflangan bloklar
yashirilmaydi, xiralashgan holda ko'rinadi — foydalanuvchi nima qo'shilishini
ko'radi. Paywall'da "Hozir emas" tugmasi "Premium'ni ko'rish" bilan bir xil
vaznda.

**Teginish maydoni ≥ 44×44.** Kontrast AA darajasida — shuning uchun yorug'
mavzuda matn uchun `primary` emas, quyuqroq `textAccent` ishlatiladi.

---

## Holat va keyingi qadamlar

Barcha ekranlar chizilgan, oqimlar bog'langan, va ekranlarning ortida endi haqiqiy
backend turadi.

Ilova **unga ulangan**: ro'yxatdan o'tish (telefon OTP, Apple/Google, email), kirish,
profil va onboarding, roziliklar, entitlements va feature flags; ustiga sikl, Mind,
Nutrition, Meds, wearable, maxfiy chat, tahlillar (`GET /v1/insights`), Premium to'lovi
(Payme va Click) va AI Gateway — javobni haqiqiy model (Gemini) yozadi, kalit bo'lmasa
yoki `ai_model_enabled` o'chirilgan bo'lsa qoidalar javob beradi.

Sog'liq yozuvlari serverda saqlanadi: sikl, ovqat, suv, kayfiyat, kundalik, dorilar va
tadbirlar (ko'rik, UTT, tahlil) qurilma almashsa ham qoladi.

Ovqat skaneri ham serverda: telefon suratni 1024 px JPEG qilib yuboradi, Gemini uni
o'qiydi va bitta JSON qaytaradi. Raqamlar ishonilmasdan chegaralanadi, `isFood: false`
— to'liq javob (kamera stolga qaratilsa, shuni aytadi). Gate boshqa pulli AI
chaqiruvlari bilan bir xil: rozilik, Premium, keyin operator qo'ygan kunlik limit;
har bir chaqiruv AI xarajat jadvaliga yoziladi. Sessiya qurilmada saqlanadi, shuning uchun ilova qayta ishga
tushganda foydalanuvchi kirgan holida qoladi.

Ulanish `data/SadoraController` va `data/HealthController` orqali: ekranlar wire
tiplarini bilmaydi, controller esa `busy`/`error` holatini bir joyda boshqaradi. Backend
bo'lmasa (`@Preview`, testlar) hamma amal lokal bajariladi va ilova prototip sifatida
ishlayveradi.

Nur — streak, tanga, do'kon va taklif — ham ulangan; batafsil pastdagi bo'limda.

Hali yo'q:

- **App Store / Google Play billing** — Payme va Click ulangan (narxlar serverda,
  to'lovni server tasdiqlaydi), do'kon ichidagi xarid esa hali yo'q: `StoreVerifier`
  sozlanmagan holda har qanday chekni rad etadi
- **Apple/Google kirish** — tugmalar bor va server `idToken`ni tekshiradi, lekin
  platforma SDK'si hali o'sha tokenni bermaydi
- **Qurilma integratsiyasi** — Apple Health / Oura ma'lumotlari namuna. Onboarding
  endi "aqlli soat bormi?" deb so'raydi va "ha" javobi oxirida ulash ekraniga olib
  boradi, lekin ekranning ortidagi SDK hali yo'q
- **iOS ikonkasi** — Androidda streakka qarab almashadi, iOS'da `AppIcons.None`

---

## Nur — streak, tanga va do'kon

Ilovaning o'z valyutasi bor: **Nur**. Nomi bitta i18n tokenida
(`RewardStrings.coinName`) turadi, ya'ni uni o'zgartirish uch qatorlik ish.

Bitta qoida hamma narsani belgilaydi: **nur salomatlik natijasi uchun berilmaydi**.
Sakkiz soat uxlash to'rt soat uxlash bilan bir xil to'laydi. Aks holda ilova ayolga
tanasi uchun pul taklif qilgan bo'lardi — va jurnal yolg'on gapirishni o'rganardi.
Nur faqat *harakat* uchun: ilovani ochish, belgilash, o'qish.

| Nima | Qayerda |
|---|---|
| Streak — ketma-ket kunlar | `POST /v1/rewards/check-in`, har ishga tushishda |
| Hamyon va harakatlar tarixi | `GET /v1/rewards` |
| Do'kon: Premium, vitamin, qurilma | `GET /v1/shop`, `POST /v1/shop/redeem` |
| Taklif kodi va havola | `GET /v1/rewards/referral` |
| Bosh ekran tartibi | `GET/PUT/DELETE /v1/me/home-layout` |

Balansni server hisoblaydi va u har doim `SUM(coin_ledger.amount)` — keshlangan ustun
yo'q, chunki keshlangan balans o'zini tushuntiruvchi qatorlardan uzoqlashadi. Kunlik
mukofotlar `UNIQUE` indeks bilan qo'riqlanadi, ya'ni ikkita bir vaqtda kelgan so'rov
ikki marta to'lay olmaydi. Streak esa foydalanuvchining **o'z vaqt mintaqasidagi**
kunni sanaydi, serverning yarim tunini emas.

Tariflar va do'kon admin panelda: `Nur → Mukofotlar va streak` va `Nur → Do'kon`.
O'zgarish keyingi mukofotdan kuchga kiradi va relizni talab qilmaydi; allaqachon
berilgan nur qayta hisoblanmaydi.

**Premium** — serverning o'zi beradigan yagona narsa: nur yechiladi va obuna o'sha
zahoti uzayadi. **Vitamin va qurilma** hamkorlarniki: nur *chegirma* sotib oladi va
ilova `SDR-XXXX-XXXX` ko'rinishidagi kodni beradi. Ilova hech qachon mahsulotni sotdim
yoki yetkazdim demaydi.

**Taklif.** Havola — `https://sadora.uz/r/KOD`. Uni bosgan telefonda ilova bo'lsa,
`landing/404.html` (GitHub Pages'da rewrite yo'q, shuning uchun 404 sahifa kodni
yo'lning o'zidan o'qiydi) kodni ko'rsatadi va `sadora://invite/KOD` ni ochadi;
onboardingdagi "Taklif kodi" qadami esa uni allaqachon to'ldirilgan holda oladi. Kod
ro'yxatdan o'tish so'rovi bilan birga ketadi — hisob paydo bo'lgan lahzada ikkala
tomon ham nur oladi, va bir kod bir hisob uchun bir marta ishlaydi.

**Ilova ikonkasi streakka qarab o'zgaradi.** Androidda buni faqat `activity-alias`
almashtirish orqali qilib bo'ladi (ikonkani "bo'yash" API'si yo'q), shuning uchun
manifestda uchta alias bor va bir vaqtda bittasi yoqilgan: `Launcher` (issiq, streak
≥ 7), `LauncherCalm` (streak davom etyapti), `LauncherCold` (uch kundan beri
ochilmagan). Ikkita sovuqroq variant issiqdan generatsiya qilinadi — qo'lda ikkinchi
nusxa vaqt o'tib brenddan uzoqlashadi:

```bash
python3 tools/gen_icon_moods.py
```

iOS'da `setAlternateIconName` bor, lekin ikonkalar Info.plist'da e'lon qilinishi
kerak; hozircha `AppIcons.None` — hech narsa qilmaydi.

**Bosh ekran o'ziniki.** Qaysi bloklar ko'rinishi va tartibi `Profil → Bosh ekran
tartibi` da (yoki Bugun ekranining oxiridagi havolada) sozlanadi va serverda saqlanadi
— yangi telefon o'sha tartib bilan ochiladi. Ilova katalogni, server esa joylashuvni
biladi: yangi blok qo'shilgan reliz migratsiya talab qilmaydi, eski ilova tanimagan
kalitni esa saqlashda tushirib qoldiradi.

**Salomlashuv.** Ism ostidagi jumla har ochilishda boshqacha. Model bir chaqiruvda
bir nechta variant yozadi, ular kesh'da turadi va bittalab beriladi; kalit bo'lmasa
yoki `ai_model_enabled` o'chirilgan bo'lsa `GreetingPhrases` yozadi — u ham har safar
boshqacha, ya'ni zaxira varianti "buzilgan" ko'rinmaydi. Jumla bepul, limitsiz va
hech qachon maslahat yoki tashxis bermaydi.

## Huquqiy matnlar

Foydalanish shartlari va Maxfiylik siyosati `shared/.../i18n/LegalTexts*.kt` da, uch
tilda. Asl matn o'zbekcha va yuridik kuchga ega bo'lgani ham o'sha — qolgan ikkitasi
tarjima, va ekranning o'zi buni aytadi. Matn ilova ichida yashaydi, chunki onboarding
rozilikni hisob paydo bo'lishidan oldin so'raydi: foydalanuvchi nimaga rozi
bo'layotganini o'qish uchun oqimdan chiqmasligi yoki internetga ulanmasligi kerak.

Do'konlar bir xil matnni ochiq havolada ham so'raydi. Ular o'sha Kotlin manbadan
generatsiya qilinadi — qo'lda yozilgan ikkinchi nusxa vaqt o'tib boshqacha bo'lib
qoladi, va aynan o'sha nusxani tekshiruvchi o'qiydi:

```bash
python3 tools/gen_legal_pages.py
```

`landing/privacy.html` va `landing/terms.html` — Play Console va App Store Connect'ga
beriladigan manzillar (o'zbekcha); yonida `.ru` va `.en` variantlari.

Ekrandagi sana va serverning `POLICY_VERSION` sozlamasi bitta kun bo'lishi shart:
rozilik yozuvi versiyani saqlaydi, ya'ni ekran versiyadan kechroq sana ko'rsatsa,
yozuv foydalanuvchi ko'rmagan matnga rozi bo'lgan deb turadi.

---

## Logotip va harakat

Logotip brend faylidan olingan va kodda chiziladi — bitmap yo'q, shuning uchun u har
qanday o'lchamda tiniq. `ui/components/Brand.kt` ichida to'rt narsa bor: belgi (S),
so'z belgisi, AI orbi va yuklagich.

Harakat ham brend faylining o'zi: S bitta qalam zarbida 1,5 soniyada chiziladi, nuqta
chiziq to'xtamasdan oldin "sakraydi" (1,25 s), harflar 80 ms oralab ko'tariladi
(1,2 s dan), shior oxirida chiqadi (1,9 s). Splash aynan shu tugaguncha turadi, kirish
ekrani ham xuddi shu ochilishni o'ynatadi.

Yuklanish holati — o'sha orbning kichigi, aylanuvchi halqa bilan: skaner tahlil
qilayotganda va splash sessiyani kutib qolganda ko'rinadi.

Ikonkalar `design/logo/*.svg` dan yig'iladi: Android uchun adaptiv vektor (fon, old
plan va Android 13 temali qatlam) hamda eski telefonlar uchun PNG'lar, iOS uchun
1024 px yorug', qorong'i va tinted variantlari. Ilova ichida belgi gradientni ikkala
mavzuda ham saqlaydi, so'z belgisi va shior esa mavzuning matn ranglarini oladi.

---

## Dizayn manbasi

Dizayn Claude Design'da, bir nechta faylga bo'lingan: poydevor va palitra,
onboarding, mobil yadro, modullar. Ekranlar har bir faylda ikki marta —
yorug' va qorong'i mavzu.
