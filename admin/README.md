# SADORA admin panel

React + TypeScript + Vite. Xuddi shu Ktor backend'ga ulanadi.

## Ishga tushirish

Backend ishlab turgan bo'lishi kerak (`docker compose up -d` va `./gradlew :server:run`).

```bash
npm install
```

```bash
npm run dev
```

<http://localhost:5173> ochiladi. Boshqa portdagi backend uchun:

```bash
SADORA_API=http://localhost:9000 npm run dev
```

Yig'ilgan bundle'ni ko'rish uchun (mijozga ko'rsatish va tunnel uchun shu ishlatiladi —
HMR soketi tunnel ustida ishonchsiz):

```bash
npm run build && npm run preview
```

<http://localhost:4173> ochiladi.

**Panel API'ni proksi orqali chaqiradi**, to'g'ridan-to'g'ri emas: `/v1` so'rovlari Vite
tomonidan backendga uzatiladi, shuning uchun brauzer uchun bitta origin bo'ladi va CORS
umuman qatnashmaydi. `server` ham, `preview` ham `allowedHosts: true` bilan ishlaydi —
tunnel panelni oldindan bilib bo'lmaydigan nom ostida ochadi, aks holda Vite so'rovni
rad etardi.

## Muhitlar

Panelning o'z sozlamasi yo'q — u qaysi backend'ga ulansa, o'sha muhitda ishlaydi
(`SADORA_API`). Dev backend uchun [server/README.md](../server/README.md) ga qarang;
prod panel `https://admin.sadora.app` da turadi va faqat prod backend'ga ulanadi.

Birinchi Owner hisobi backend birinchi marta ko'tarilganda yaratiladi —
`ADMIN_BOOTSTRAP_EMAIL` va `ADMIN_BOOTSTRAP_PASSWORD` ga qarang
([server/README.md](../server/README.md)).

## Tayyor sahifalar

| Sahifa | Rol | Nima |
|---|---|---|
| Kirish | — | Email + parol, 2FA yoqilgan bo'lsa TOTP kodi |
| Dashboard | hamma | 6 ko'rsatkich, 14 kunlik ro'yxatdan o'tish grafigi, hodisalar lentasi |
| Foydalanuvchilar | hamma | Filtrlar, qidiruv, CSV, sahifalash |
| Kartochka | Owner/Admin/Support | Umumiy · Obuna · Texnik. Bloklash, Premium berish |
| Entitlements va limitlar | Owner/Admin (Analyst o'qiydi) | Jadvalni joyida tahrirlash |
| Feature flags | Owner/Admin (Analyst o'qiydi) | Kill switch, standart qiymat, foizli yoyish qoidalari |
| Maxfiy chat | hamma (Owner/Admin amal qiladi) | Moderatsiya navbati: postlar, izohlar, shikoyatlar. Yashirish, qaytarish, muallifni cheklash — hammasi taxallus ostida, foydalanuvchi ID'si ko'rinmaydi |
| Bildirishnomalar | Owner/Admin (Analyst o'qiydi) | Chastota chegaralari va shablonlar |
| Wearable providerlar | Owner/Admin (Analyst o'qiydi) | Provayderlar holati, metrika moslashtirish jadvali |
| Audit log | faqat Owner | Filtr va sahifalash |

Barcha sahifalarning backend'i bor — o'chirilgan punkt qolmadi. Alohida
"qo'llab-quvvatlash" sahifasi ataylab yo'q: operatorning ish oqimi (hisobni topish,
kartochkani ochish, obunani yoki blokni o'zgartirish) foydalanuvchi kartochkasining
o'zida, va bo'sh ikkinchi nusxasi faqat chalg'itardi.

## Qarorlar

**2FA — har bir operatorning o'zida, va QR yo'q.** "Hisobim va 2FA" sahifasi kalit
yaratadi, uni ko'rsatadi va faqat autentifikator to'g'ri kod bergandan keyin yoqadi:
skanerlanmagan kalit bilan yoqish — panelga o'zini qamab qo'yish. O'chirish parolni ham
so'raydi, chunki o'g'irlangan seans — 2FA to'sib turgan narsaning o'zi. QR chizilmaydi:
bu yo bog'liqlik, yo bir marta ro'yxatdan o'tadigan besh-o'nta odam uchun uch yuz qator
kodlagich; kalit ham, `otpauth://` havolasi ham nusxa olish uchun ekranda turadi.

**Token `sessionStorage` da, `localStorage` da emas.** Operator tokeni mahsulotdagi
har bir obunani ochadi; taxta yopilganda u ham o'chishi kerak.

**401 — hamma joyda chiqishga olib keladi.** Admin realmida refresh token yo'q, bu
ataylab: token tugasa 2FA kodi bilan qaytadan kirasiz.

**Rol bo'yicha yashirish — qulaylik, himoya emas.** Haqiqiy tekshiruv server tomonida
(`requireAdminRole`); panel shunchaki API rad etadigan sahifani ko'rsatmaydi.

**CSV eksporti — ekrandagi sahifa.** Butun filtrlangan to'plamni yuklab olish — hisob
ma'lumotlarining ommaviy chiqarilishi va u o'zining alohida audit qilinadigan
endpoint'i ortida turishi kerak, tugmadek ko'rinadigan qulaylik ortida emas.

**Moderatsiya sahifasi foydalanuvchiga olib bormaydi.** Postdan hisob kartochkasiga
havola yo'q va API uni bera olmaydi. "Muallifni cheklash" post orqali qo'llanadi —
moderator kimligini bilmaydi. Bu maxfiy chatning butun mazmuni.

**DAU/MAU — hisobning oxirgi so'rovi bo'yicha, seans emas.** Auth qatlami har
so'rovda `last_active_at` ni yangilaydi, shuning uchun bu raqamni halol o'lchash mumkin.
Seanslar, ekranlar va voronkalar alohida hodisalar jadvalini talab qiladi; u yo'q,
shuning uchun ular ko'rsatilmaydi — o'lchanmagan raqamni o'lchangandek ko'rsatish
ko'rsatmaslikdan yomonroq. AI xarajati esa o'z sahifasida, kunlik dinamikasi bilan.

## Buyruqlar

```bash
npm run typecheck
```

```bash
npm run build
```
