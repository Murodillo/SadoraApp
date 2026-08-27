# SADORA

Ayollar salomatligi ilovasi — sikl, homiladorlik, menopauza, uyqu, ovqatlanish va
kayfiyat bir joyda. Kotlin Multiplatform + Compose Multiplatform, Android va iOS
uchun bitta umumiy UI.

> **Har bir ayol. Har bir lahza.**

Interfeys tili — o'zbekcha. Ilova ichida uch til nazarda tutilgan (UZ / RU / EN),
hozircha faqat o'zbekchasi yozilgan.

---

## Ishga tushirish

```bash
./gradlew :androidApp:assembleDebug
```

iOS uchun `iosApp/iosApp.xcodeproj` faylini Xcode'da oching va ishga tushiring.

Backend uchun Postgres va Redis'ni ko'taring, keyin serverni ishga tushiring —
batafsil [server/README.md](./server/README.md):

```bash
docker compose up -d
```

```bash
./gradlew :server:run
```

Admin panel server bilan birga ko'tariladi:
<http://localhost:8080/v1/admin/ui/>. Birinchi admin hisobini yaratish uchun
`ADMIN_BOOTSTRAP_EMAIL` va `ADMIN_BOOTSTRAP_PASSWORD` bering — batafsil
[server/README.md](./server/README.md).

Testlar:

```bash
./gradlew :shared:iosSimulatorArm64Test
```

```bash
./gradlew :server:test :contract:jvmTest
```

Android SDK yo'li `local.properties` faylida ko'rsatiladi (bu fayl git'ga
qo'shilmaydi):

```
sdk.dir=/Users/<siz>/Library/Android/sdk
```

---

## Arxitektura

Butun UI `:shared` modulining `commonMain` manbasida — Android va iOS bir xil
kodni ishlatadi. Platformaga xos qism juda kichik: ikkala tomonda ham faqat
`App()` ni chaqiradigan ingichka kirish nuqtasi.

```
androidApp/          MainActivity — App() ni chaqiradi
iosApp/              SwiftUI ContentView — App() ni chaqiradi
contract/            Mobil va backend bo'lishadigan DTO'lar (KMP)
server/              Ktor backend — server/README.md
  └ resources/admin/ Admin panel (build qadamisiz, server beradi)
shared/src/commonMain/kotlin/org/example/project/
├── App.kt           Ildiz: AppState va Navigator shu yerda yashaydi
├── design/          Dizayn tokenlari (ranglar, tipografika, o'lchamlar, mavzu)
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

**`model/`** — `AppState` butun ilova uchun bitta xotiradagi do'kon. Backend hali
yo'q, shuning uchun ekranlar to'g'ridan-to'g'ri shu yerdan o'qiydi va yozadi.
Hammasi Compose state, ya'ni har qanday o'zgarish tegishli ekranni qayta chizadi.

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

Hozircha bu to'liq ishlaydigan UI prototipi: barcha ekranlar chizilgan, oqimlar
bog'langan, holat real vaqtda o'zgaradi.

Backend'ning 1-sprint qamrovi yozilgan va ilova **unga ulangan**: ro'yxatdan o'tish
(telefon OTP, Apple/Google, email), kirish, profil va onboarding, roziliklar,
entitlements va feature flags. Sessiya qurilmada saqlanadi, shuning uchun ilova qayta
ishga tushganda foydalanuvchi kirgan holida qoladi.

Ulanish `data/SadoraController` orqali: ekranlar wire tiplarini bilmaydi, controller
esa `busy`/`error` holatini bir joyda boshqaradi. Backend bo'lmasa (`@Preview`,
testlar) hamma amal lokal bajariladi va ilova prototip sifatida ishlayveradi.

Hali yo'q:

- **Sog'liq ma'lumotlarining saqlanishi** — sikl, ovqat, kayfiyat va dorilar hozircha
  xotirada; ularning API'si 2–3-sprintda
- **Qolgan backend API'lari** — sikl, Mind, Nutrition, Meds, wearable va AI Gateway
- **To'lov** — paywall tugmasi hozir faqat entitlements'ni qayta so'raydi; App Store /
  Google Play billing SDK'si ulanmagan
- **Apple/Google kirish** — tugmalar bor, lekin platforma SDK'si idToken bermaydi
- **Haqiqiy AI** — javoblar namuna matn
- **Qurilma integratsiyasi** — Apple Health / Oura ma'lumotlari namuna
- **RU va EN tarjimalari** — matnlar hozircha kodda o'zbekcha

---

## Dizayn manbasi

Dizayn Claude Design'da, bir nechta faylga bo'lingan: poydevor va palitra,
onboarding, mobil yadro, modullar. Ekranlar har bir faylda ikki marta —
yorug' va qorong'i mavzu.
