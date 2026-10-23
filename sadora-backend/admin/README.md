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

Menyu ish bo'yicha guruhlangan: **Odamlar**, **Kontent**, **Pul**, **Ilova**,
**Hisobot**, **Loyiha**. Sahifaning sarlavhasi menyudagi nomi bilan bir xil.

| Sahifa | Rol | Nima |
|---|---|---|
| Kirish | — | Email + parol, 2FA yoqilgan bo'lsa TOTP kodi |
| Bosh sahifa | hamma | Eng tepada **Bugun**: pulni qaytarish, shifokor arizalari, chat shikoyatlari, do'konda tugagan mahsulotlar — har biri soni va bir bosishda o'z sahifasi (`/admin/stats/todo`, rolga ko'ra). Keyin ko'rsatkichlar, faollik grafigi, bosqich va til donutlari, hodisalar lentasi |
| Analitika | Owner/Admin/Analyst | Kunlik faol foydalanuvchilar (o'lchangan), retention 1/7/30, hisobdan to'lovgacha voronka, tushum, platforma va versiyalar, streak taqsimoti, rozilik, kunlar jadvali va CSV |
| Foydalanuvchilar | hamma | Filtrlar, qidiruv, CSV, sahifalash |
| Kartochka | Owner/Admin/Support | Umumiy (ulanishlar: Yaqinim, Telegram, soatlar — faqat holati; operatorlar izohlari) · Tarix (hisob voqealari, bir xillari bitta qatorga yig'iladi) · Obuna · Gul · Texnik. Bloklash, Premium berish |
| Funksiyalar | Owner/Admin (Analyst o'qiydi) | Ilova qila oladigan har narsa — bitta kartada: ishlayaptimi (bitta kalit), kim uchun (Hamma / Faqat Premium), limitlar va 7 kunlik foydalanish. Foizli yoyish va qoidalar «Kengaytirilgan»da. Avvalgi «Feature flags» va «Entitlements» sahifalari shu yerga birlashdi |
| Do'kon | hamma (Gul katalogi Owner/Admin/Analyst) | Gul do'koni (obuna, qurilma, dori) va pulga sotiladigan hamroh hamda ramkalar narxi |
| Chat | hamma (Owner/Admin amal qiladi) | Moderatsiya navbati: postlar, izohlar, shikoyatlar. Yashirish, qaytarish, muallifni cheklash — hammasi taxallus ostida, foydalanuvchi ID'si ko'rinmaydi |
| Bildirishnomalar | Owner/Admin (Analyst o'qiydi) | Chastota chegaralari va shablonlar |
| Soat va qurilmalar | Owner/Admin (Analyst o'qiydi) | Provayderlar holati, metrika moslashtirish jadvali |
| Audit jurnali | faqat Owner | Filtr va sahifalash |
| Kampaniyalar | Owner/Admin (Analyst o'qiydi) | Push kampaniya: UZ (majburiy)/RU/EN matn, segment (hayot bosqichi, til, tarif, ayollar/Yaqinimlar), hozir yoki vaqtga, telefon ekranidagi ko'rinishi, jonli auditoriya hisobi, bitta telefonga sinov, yetkazilganlar statistikasi |
| Ilova versiyasi | Owner/Admin (Analyst o'qiydi) | Android/iOS uchun majburiy va taklif qilinadigan build, do'kon havolasi, texnik ishlar xabari (UZ/RU/EN) |
| Tizim holati | Owner/Admin | Server, ma'lumotlar bazasi va har bir tashqi xizmat (SMS, push, Gemini, Telegram, Payme, Click, Play, App Store) — bugungi muvaffaqiyat va xatolar, oxirgi xato matni |
| Operatorlar | faqat Owner | Operator qo'shish (vaqtinchalik parol bir marta ko'rsatiladi), rol, o'chirish/yoqish, parol va 2FA'ni tiklash |
| Loyiha → Doska | Owner/Admin/Dasturchi | Jamoaning tasklari (`SAD-12`): 5 ustunli Kanban (sudrab yoki karta ichidagi tanlov bilan ko'chiriladi), ro'yxat va arxiv; filtrlar URL'da. Task sahifasi: Markdown tavsif, subtasklar, izohlar, tarix, kalitni eslatgan commitlar va hujjatlar |
| Loyiha → Epic va relizlar | Owner/Admin/Dasturchi (arxivlash Owner/Admin) | Feature'lar (epic) va reliz sanalari, har birining bajarilish foizi |
| Loyiha → Hujjatlar | Owner/Admin/Dasturchi | Markdown wiki daraxti: chapda matn, o'ngda ko'rinish, rasm paste/drag, har saqlash — versiya, eski versiyani tiklash; ikki kishi bir vaqtda saqlasa ikkinchisiga aytiladi |
| Loyiha → API tokenlar | Owner/Admin/Dasturchi | `tools/pm` va Claude sessiyalari uchun token (bir marta ko'rsatiladi, faqat Loyiha'ni ochadi, «Tayyor»ga o'tkaza olmaydi, butunlay o'chira olmaydi); Owner uchun zaxirani yuklab olish |

**Ctrl+K (⌘K)** — har qanday sahifadan bitta qidiruv oynasi: sahifa nomi (yoki «bayroq», «payme»
kabi kalit so'z), telefon (bo'shliq bilan yozilsa ham), ism, email, shifokor ismi yoki litsenziyasi,
to'lov ID'si. Hisob, shifokor va to'lov qidiruvi Owner/Admin/Support uchun (`/admin/search`);
Analitik faqat sahifalarni topadi, chunki kartochkani ocha olmaydi. Loyiha'ga kira oladiganlar
uchun tasklar (`SAD-12` yoki so'z) va hujjatlar ham chiqadi (`/admin/pm/search`).

**Dasturchi** (`DEVELOPER`) roli faqat Loyiha bo'limi va o'z hisobini ko'radi: foydalanuvchilar,
pul va moderatsiya unga yopiq — server ham, panel ham. Jamoa a'zosi «Operatorlar» sahifasida shu rol
bilan qo'shiladi.

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
Ekranlar va seanslar alohida hodisalar jadvalini talab qiladi; u yo'q, shuning uchun ular
ko'rsatilmaydi — o'lchanmagan raqamni o'lchangandek ko'rsatish ko'rsatmaslikdan yomonroq.
AI xarajati esa o'z sahifasida, kunlik dinamikasi bilan.

**Analitika sahifasi faqat mahsulot o'z ishi uchun yozadigan qatorlarni sanaydi.**
"Ilovani ochganlar" — streak tizimining kunlik check-in yozuvi (har hisobga kuniga bir
marta) va audit'dagi kirishlar; retention — ro'yxatdan o'tgan sanaga nisbatan oxirgi
so'rov; voronka — hisob, onboarding, 30 kunlik faollik, Premium, to'lov. Grafik uchun
ilovaga hech qanday hodisa qo'shilmagan va birorta raqam sog'liq qiymati emas.

**Grafiklar qo'lda chizilgan SVG.** To'rtta shakl — kunlar bo'yicha chiziq, ustunlar,
donut va voronka — bir necha yuz qator, va ular ilovaning o'z egri chiziqlarida
animatsiya qiladi hamda qolgan hamma narsa bilan bir xil rang tokenlarini o'qiydi.
Bog'liqlik yo'q. Har bir grafik o'lchov, shuning uchun har birini hover qilib raqamni
ko'rish mumkin.

**Funksiya kartasi — bayroq va huquqning juftligi.** Serverda bitta narsa ko'pincha ikki
qator: hamma uchun o'chiradigan bayroq va kim olishini aytadigan huquq. Ularni
`pages/featureCatalog.ts` juftlaydi va kartaning bitta kaliti nima qilishini o'sha yerda
belgilaydi: o'chirish — umumiy o'chirgich (qolgan sozlama saqlanadi), yoqish — faqat
o'chiq qismni tiklaydi, hech kimga ochiq bo'lmagan huquq esa faqat Premium'ga ochiladi.
Katalog bilmagan yangi kalit «Boshqa» guruhida o'z tavsifi bilan chiqadi. Limit maydonlari
faqat server ishlatilishni sanaydigan funksiyalarda (yoki limit allaqachon qo'yilgan
joyda) ko'rinadi — boshqasida limit hech narsani cheklamaydi va karta shuni aytadi.

**Operatorni faqat Owner boshqaradi va hech kim o'zini emas.** O'zini pasaytirgan yoki
o'chirgan Owner panelni Owner'siz qoldirardi; o'z paroli va 2FA'si esa isbot so'raydigan
«Hisobim» sahifasida. Yangi operator ham, paroli tiklangan operator ham Owner ko'rgan
vaqtinchalik parol bilan kiradi: uni almashtirmaguncha server faqat «Hisobim»ni ochadi
(`password_change_required`), panel ham faqat shu sahifani ko'rsatadi. Har o'zgarish
operatorning keyingi so'rovidan kuchga kiradi — admin realmi hisobni har so'rovda o'qiydi.

**Kartochka tarixi chat moderatsiyasini ko'rsatmaydi.** Moderatsiya sahifasi taxallusni
hisobga bog'lamaydi; bir xil daqiqalardagi tarix bog'lab qo'yardi. Owner bo'lmaganlarga
har voqea dashboarddagidek — kim, qayerdan va nega qismisiz — boradi. Izohlar audit
jurnaliga matnisiz yoziladi va hisob bilan birga o'chadi.

**Kampaniya oddiy eslatma yo'lidan boradi.** Har bir push bildirishnomalar navbatiga
(`campaign:<id>:<user>` kaliti bilan, ikki marta ketmaydi) tushadi va undan oldin
foydalanuvchining umumiy kaliti, «Yangiliklar» kaliti, kunlik/haftalik chegara tekshiriladi;
sokin soatlarga tushsa, ular tugaguncha kutadi. Yashirin rejimdagi hisobga umuman
yuborilmaydi — «Sizda yangi eslatma bor» degan taklif jumboqqa aylanadi. Navbatdagi
kategoriya `system`: yangi kategoriya eski ilovalarning bildirishnomalar tarixini buzardi,
shuning uchun «Yangiliklar» sozlamalarda alohida maydon (`news`).

**Majburiy yangilash build raqami bo'yicha.** iOS'da ko'rinadigan versiya 1.0 bo'lib qoladi,
faqat build o'sadi; ilova `/v1/app/status`ga build raqamini yuboradi (kirishdan oldin, har
ochilganda va qaytganda). Javob bo'lmasa ilova ochilaveradi. Texnik ishlar API'ni to'xtatmaydi —
faqat ilova o'zini yopib xabarni ko'rsatadi; webhook'lar va panel ishlayveradi.

**Tizim holati faqat mavjud qatorlardan.** Push — navbatdan, Gemini — AI jurnalidan, to'lovlar —
tranzaksiyalardan; SMS va Telegram bot uchun alohida kunlik hisoblagich (`ops_counters`).
Bekor qilingan to'lov xato hisoblanmaydi.

**Saqlash toast bilan tasdiqlanadi.** Ilgari yagona tasdiq "Saqlash" tugmasining
kulrang bo'lishi edi — buni "hali saqlanmoqda"dan ajratib bo'lmasdi. Endi har bir
muvaffaqiyatli yozuv burchakda qisqa xabar chiqaradi; xato esa avvalgidek joyida.

## Buyruqlar

```bash
npm run typecheck
```

```bash
npm run build
```
