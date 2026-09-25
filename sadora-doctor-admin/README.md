# SADORA — shifokor paneli

Tasdiqlangan shifokorlar kompyuterda ishlaydigan veb-panel: chatdagi javobsiz savollarga
javob berish, o'z postlarini yozish va ochiq sahifasidagi ma'lumotlarni yangilash.
Xodimlar panelining (`sadora-backend/admin`) ukasi — o'sha stek (React 18 + TypeScript +
Vite + TanStack Query), o'sha ko'rinish, tokenlar, animatsiyalar va test uslubi; kod
nusxalab moslashtirilgan, papkalar o'rtasida import yo'q.

Ariza bu yerda topshirilmaydi — u **Sadora Doctor** ilovasida (telefon kamerasi diplom va
litsenziyani suratga oladi). Panel faqat `approved` holatdagi shifokorga ochiladi; qolganlar
holat sahifasini ko'radi.

## Ishga tushirish

Backend `:8080` da ishlab turgan bo'lishi kerak (`docker compose up -d` va
`./gradlew :server:run`, [server/README.md](../sadora-backend/server/README.md) ga qarang).

```bash
npm install
```

```bash
npm run dev
```

<http://localhost:5174> ochiladi (5173 — xodimlar paneli, ikkalasi yonma-yon ishlaydi).
Boshqa portdagi backend uchun:

```bash
SADORA_API=http://localhost:9000 npm run dev
```

Panel API'ni **proksi orqali** chaqiradi: `/v1` so'rovlari Vite tomonidan backendga
uzatiladi, brauzer uchun bitta origin bo'ladi va serverning CORS ro'yxatiga hech narsa
qo'shish shart emas. Prod'da ham panel API bilan bitta origin ortida (yoki `/v1` ni
backendga uzatadigan reverse proxy ortida) turishi kerak — xodimlar paneli kabi.

Dev va stage serverda `OTP_EXPOSE_CODE` (standart `true`, prod'da taqiqlangan) kodni
javobda qaytaradi (`devCode`) va panel uni kod maydoniga o'zi yozib qo'yadi.
`OTP_FIXED_CODE` o'rnatilgan bo'lsa, kod har doim o'sha.

Yig'ilgan bundle'ni ko'rish uchun:

```bash
npm run build && npm run preview
```

<http://localhost:4174> ochiladi.

## Buyruqlar

```bash
npm run typecheck        # tsc -b --noEmit
npm test                 # vitest run
npm run test:coverage    # qamrov va chegaralar (client.ts, phone.ts, AuthContext.tsx)
npm run build            # tsc -b && vite build -> dist/
```

## Sahifalar

| Sahifa | Nima |
|---|---|
| Kirish | Telefon (+998, operator kodi tekshiriladi) → SMS kod. Qadamlar karta ichida siljib almashadi |
| Holat | `none` / `pending` / `rejected` / `suspended`: qayerda turgani va keyingi qadam; rad etish yoki to'xtatish izohi |
| Savollar | Javob kutayotganlar soni, javoblari va postlari (sanab chiqadi); bo'lim filtri; chapda ro'yxat, o'ngda savol, izohlar va javob maydoni (≤1000, Ctrl/⌘+Enter). Javobdan keyin savol ro'yxatdan chiqib ketadi, izohlar yangilanadi |
| Postlarim | Ochiq sahifadagi hisoblagichlar, so'nggi postlar va izohlari; yangi post — chatda qanday ko'rinishi dialogda ko'rsatiladi, keyin chop etiladi |
| Profil | Tekshirilgan ma'lumotlar (o'qish uchun); ish joyi (≤160) va "O'zim haqimda" (≤500) tahriri |

## Qarorlar

**Kirish — ilovaning o'z telefon kodi bilan.** Shifokor oddiy Sadora hisobi, shuning uchun
panel xodimlar panelining email/parol kirishini emas, `/v1/auth/otp/*` ni ishlatadi.
Brauzer uchun tasodifiy `deviceId` bir marta yaratilib `localStorage` da saqlanadi — u sir
emas va hech narsani ochmaydi. E'tibor bering: yangi raqam bilan kirish (ilovadagi kabi)
oddiy hisob yaratadi; u `none` holat sahifasini ko'radi.

**Ikkala token ham `sessionStorage` da.** Shifokor klinikadagi umumiy kompyuterdan kiradi va
uning nomidan, ✓ belgisi bilan yoziladi; tab yopilganda sessiya ham tugaydi. Bu ataylab.

**401 — bir marta yangilash, keyin chiqish.** Access token tugasa, `/v1/auth/refresh` bir
marta chaqiriladi (bir vaqtda kelgan 401'lar bitta yangilashni kutadi — server refresh
tokenni har safar almashtiradi) va so'rov qaytariladi. Server rad etsa, sessiya hamma joyda
tugaydi. Tarmoq xatosi yoki 5xx esa sessiyani tugatmaydi. "Chiqish" tokenlarni darhol
unutadi va refresh tokenni serverda bekor qiladi.

**Holat har bir 403 da qayta o'qiladi.** Admin shifokorni panel ochiq turganda to'xtatsa,
keyingi rad javobi `/v1/doctor/me` ni yangilaydi va panel holat sahifasiga o'tadi.

**Javob berilgan savol birdan yo'qolmaydi.** Ro'yxatdan chiqib ketish animatsiyasini
o'ynaydi, ochiq savol esa javob izohlarda paydo bo'lguncha ko'rinib turadi; "Keyingi
savol" navbatdagisini ochadi. Har bir savolning qoralamasi alohida saqlanadi.

**Harakat — xodimlar panelining lug'ati.** Sahifa kirishi, qatorlarning ketma-ket chiqishi,
toast, dialog, skeleton, sanab chiqadigan raqamlar va tugma bosilishi o'sha kalit kadrlar
bilan. `prefers-reduced-motion` so'ralsa, CSS animatsiyalari o'chadi, raqamlar darhol
ko'rinadi va chiqib ketayotgan qator kutilmaydi.

**Mavzu** — xodimlar panelidagi kabi: standart qorong'i, tanlov `localStorage` da. Kirish va
holat sahifalari ham tanlovga amal qiladi.

**Matnlar ilova bilan bir xil.** Bo'lim nomlari `StringsUz.topic` dan (Sikl, Homiladorlik,
Kayfiyat, Tana), mutaxassisliklar `DoctorStringsUz.specialty` dan. Uzunlik chegaralari
`contract/Limits.kt` bilan, telefon qoidalari `contract/UzbekPhone.kt` bilan bir xil —
birini o'zgartirsangiz, ikkinchisini ham o'zgartiring.
