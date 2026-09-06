# SADORA

Ayollar salomatligi ilovasi — sikl, homiladorlik, menopauza, uyqu, ovqatlanish va
kayfiyat bir joyda. Kotlin Multiplatform + Compose Multiplatform, Android va iOS
uchun bitta umumiy UI.

> **Har bir ayol. Har bir lahza.**

Interfeys tili — o'zbekcha. Ilova ichida uch til nazarda tutilgan (UZ / RU / EN),
hozircha faqat o'zbekchasi yozilgan.

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

iOS uchun `iosApp/iosApp.xcodeproj` faylini Xcode'da oching va ishga tushiring.

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

## CI

Har bir push GitHub Actions'da tekshiriladi: backend testlari va migratsiyalarning
haqiqiy Postgres ustida ko'tarilishi, shared modul testlari, Android APK yig'ilishi,
admin panelning typecheck va build'i. Kotlin/Native (iOS) faqat `main` ga PR va push'da
— macOS runner'lari o'n barobar qimmat.

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
shared/src/commonMain/kotlin/org/example/project/
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

Sog'liq yozuvlari serverda saqlanadi: sikl, ovqat, suv, kayfiyat va dorilar qurilma
almashsa ham qoladi. Sessiya qurilmada saqlanadi, shuning uchun ilova qayta ishga
tushganda foydalanuvchi kirgan holida qoladi.

Ulanish `data/SadoraController` va `data/HealthController` orqali: ekranlar wire
tiplarini bilmaydi, controller esa `busy`/`error` holatini bir joyda boshqaradi. Backend
bo'lmasa (`@Preview`, testlar) hamma amal lokal bajariladi va ilova prototip sifatida
ishlayveradi.

Hali yo'q:

- **App Store / Google Play billing** — Payme va Click ulangan (narxlar serverda,
  to'lovni server tasdiqlaydi), do'kon ichidagi xarid esa hali yo'q: `StoreVerifier`
  sozlanmagan holda har qanday chekni rad etadi
- **Apple/Google kirish** — tugmalar bor va server `idToken`ni tekshiradi, lekin
  platforma SDK'si hali o'sha tokenni bermaydi
- **Qurilma integratsiyasi** — Apple Health / Oura ma'lumotlari namuna
- **RU va EN tarjimalari** — `i18n/` qatlami qo'yildi va til
  sozlamasi ishlaydi, lekin hozircha faqat birinchi bo'lak ko'chirilgan: tab yorliqlari,
  xush kelibsiz ekrani, Profil va sozlamalar. Qolgan ekranlar hali kodda o'zbekcha
- **AI javoblari faqat o'zbekcha** — prompt til so'ramaydi, shuning uchun rus yoki
  ingliz tilini tanlagan foydalanuvchi ham o'zbekcha javob oladi

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
