// Sadora as a Telegram Mini App: the bot's menu button opens this page.
//
// Sign-in: Telegram's signed initData goes to /v1/telegram/webapp/session. A Telegram account
// not linked yet signs in by phone code (the app's own endpoints), finishes a short
// onboarding if the account is new, and is then linked to the bot. Everything after that is
// the app's API with a Bearer token, so the server's checks are the app's own.

const tg = window.Telegram?.WebApp;
const API = apiBase();
const $app = document.getElementById('app');
const $tabs = document.getElementById('tabs');
const $sheet = document.getElementById('sheet');
const $toast = document.getElementById('toast');

function apiBase() {
  const override = new URLSearchParams(location.search).get('api');
  if (override) return override.replace(/\/$/, '');
  const host = location.hostname;
  if (host === 'sadora.app' || host === 'www.sadora.app') return 'https://api.sadora.app';
  if (host.startsWith('devv.') || host.startsWith('dev.')) return 'https://dev-api.sadora.app';
  return 'http://localhost:8080';
}

// ------------------------------------------------------------------ words

const WORDS = {
  uz: {
    tabToday: 'Bugun', tabCalendar: 'Kalendar', tabMeds: 'Dorilar',
    hello: (n) => n ? `Salom, ${n}` : 'Salom',
    openFromBot: 'Sadora\'ni Telegram ichida ochish uchun @sadoraapp_bot ga kiring va pastdagi «Sadora» tugmasini bosing.',
    openBot: 'Botni ochish',
    welcomeTitle: 'Sadora\'ga xush kelibsiz',
    welcomeText: 'Siklingiz, kayfiyatingiz va dorilaringiz — endi Telegram ichida ham. Kirish uchun telefon raqamingizni kiriting.',
    phone: 'Telefon raqam', getCode: 'Kod olish', codeSent: (p) => `${p} raqamiga SMS kod yubordik`,
    code: 'SMS kod', signIn: 'Kirish', changeNumber: 'Raqamni o\'zgartirish', resend: 'Qayta yuborish',
    resendIn: (s) => `Qayta yuborish: ${s} s`,
    phoneHint: '+998 va 9 ta raqam',
    setupTitle: 'Bir necha savol', setupText: 'Prognoz to\'g\'ri chiqishi uchun.',
    name: 'Ismingiz', lastPeriod: 'Oxirgi hayz qachon boshlangan?', cycleLength: 'Sikl davomiyligi (kun)',
    periodLength: 'Hayz davomiyligi (kun)', unsure: 'Bilmayman — 28 va 5 kun deb olamiz',
    consent: 'Sog\'liq ma\'lumotlarimni Sadora saqlashiga roziman.', privacy: 'Maxfiylik siyosati',
    consentNeeded: 'Davom etish uchun rozilik kerak', start: 'Boshlash',
    cycleDay: 'Sikl kuni', dayN: (n) => `${n}-kun`,
    periodDay: (n) => `Hayzning ${n}-kuni`,
    untilPeriod: (n) => n === 0 ? 'Hayz bugun kutilmoqda' : n < 0 ? `Hayz ${-n} kun kechikmoqda` : `Hayzgacha ${n} kun`,
    phases: { period: 'Hayz', follicular: 'Follikulyar faza', fertile: 'Unumdor kunlar', luteal: 'Luteal faza' },
    predicted: 'taxminiy',
    noCycle: 'Hali ma\'lumot yo\'q', noCycleText: 'Oxirgi hayz boshlangan kunni belgilang — Sadora keyingisini hisoblaydi.',
    stageNoPredict: 'Sizning bosqichingizda sikl prognozi ko\'rsatilmaydi. To\'liq ko\'rinish Sadora ilovasida.',
    periodStarted: 'Hayz boshlandi', periodEnded: 'Hayz tugadi', save: 'Saqlash',
    savedPeriodStart: 'Hayz bugundan belgilandi', savedPeriodEnd: 'Hayz tugadi deb belgilandi',
    mood: 'Bugungi kayfiyat', moodAny: 'Kayfiyat', symptoms: 'Belgilar', water: 'Suv', addWater: '+250 ml',
    saved: 'Saqlandi ✓', failed: 'Saqlab bo\'lmadi', offline: 'Internet bilan aloqa yo\'q',
    nextPeriod: 'Keyingi hayz', ovulation: 'Ovulyatsiya', avgCycle: 'O\'rtacha sikl', avgPeriod: 'O\'rtacha hayz',
    days: (n) => `${n} kun`, legendPeriod: 'Hayz', legendPred: 'Taxminiy hayz', legendFertile: 'Unumdor', legendLog: 'Yozuv bor',
    flow: 'Hayz', flows: { none: 'Yo\'q', spotting: 'Dog\'', light: 'Kam', medium: 'O\'rtacha', heavy: 'Ko\'p' },
    future: 'Kelajakdagi kunni belgilab bo\'lmaydi',
    medsToday: 'Bugungi dorilar', noMeds: 'Bugun dori yo\'q', noMedsText: 'Dori va vitaminlarni Sadora ilovasida qo\'shing — eslatmalar shu botga ham keladi.',
    taken: 'Ichdim', skip: 'O\'tkazdim', undo: 'Bekor qilish', stTaken: 'Ichildi', stSkipped: 'O\'tkazildi', stLate: 'Kechikdi',
    ofTaken: (a, b) => `${a} / ${b} ichildi`,
    appPromo: 'To\'liq Sadora ilovada', appPromoText: 'Homiladorlik, tahlillar, shifokor, Yaqinim va boshqalar',
    download: 'Yuklab olish', signOut: 'Chiqish',
    weekdays: ['Du', 'Se', 'Ch', 'Pa', 'Ju', 'Sh', 'Ya'],
    months: ['Yanvar', 'Fevral', 'Mart', 'Aprel', 'May', 'Iyun', 'Iyul', 'Avgust', 'Sentabr', 'Oktabr', 'Noyabr', 'Dekabr'],
    retry: 'Qayta urinish', somethingWrong: 'Nimadir xato ketdi',
  },
  ru: {
    tabToday: 'Сегодня', tabCalendar: 'Календарь', tabMeds: 'Лекарства',
    hello: (n) => n ? `Привет, ${n}` : 'Привет',
    openFromBot: 'Чтобы открыть Sadora в Telegram, зайдите в @sadoraapp_bot и нажмите кнопку «Sadora» внизу.',
    openBot: 'Открыть бота',
    welcomeTitle: 'Добро пожаловать в Sadora',
    welcomeText: 'Цикл, настроение и лекарства — теперь и в Telegram. Для входа введите номер телефона.',
    phone: 'Номер телефона', getCode: 'Получить код', codeSent: (p) => `Мы отправили SMS-код на ${p}`,
    code: 'Код из SMS', signIn: 'Войти', changeNumber: 'Изменить номер', resend: 'Отправить снова',
    resendIn: (s) => `Отправить снова: ${s} с`,
    phoneHint: '+998 и 9 цифр',
    setupTitle: 'Пара вопросов', setupText: 'Чтобы прогноз был точным.',
    name: 'Ваше имя', lastPeriod: 'Когда начались последние месячные?', cycleLength: 'Длина цикла (дней)',
    periodLength: 'Длина месячных (дней)', unsure: 'Не знаю — возьмём 28 и 5 дней',
    consent: 'Я согласна, чтобы Sadora хранила мои данные о здоровье.', privacy: 'Политика конфиденциальности',
    consentNeeded: 'Нужно согласие, чтобы продолжить', start: 'Начать',
    cycleDay: 'День цикла', dayN: (n) => `${n}-й день`,
    periodDay: (n) => `${n}-й день месячных`,
    untilPeriod: (n) => n === 0 ? 'Месячные ожидаются сегодня' : n < 0 ? `Задержка ${-n} дн.` : `До месячных ${n} дн.`,
    phases: { period: 'Месячные', follicular: 'Фолликулярная фаза', fertile: 'Фертильные дни', luteal: 'Лютеиновая фаза' },
    predicted: 'прогноз',
    noCycle: 'Пока нет данных', noCycleText: 'Отметьте день начала последних месячных — Sadora рассчитает следующие.',
    stageNoPredict: 'На вашем этапе прогноз цикла не показывается. Полная картина — в приложении Sadora.',
    periodStarted: 'Месячные начались', periodEnded: 'Месячные закончились', save: 'Сохранить',
    savedPeriodStart: 'Месячные отмечены с сегодня', savedPeriodEnd: 'Отмечено окончание месячных',
    mood: 'Настроение сегодня', moodAny: 'Настроение', symptoms: 'Симптомы', water: 'Вода', addWater: '+250 мл',
    saved: 'Сохранено ✓', failed: 'Не удалось сохранить', offline: 'Нет связи с интернетом',
    nextPeriod: 'Следующие месячные', ovulation: 'Овуляция', avgCycle: 'Средний цикл', avgPeriod: 'Средние месячные',
    days: (n) => `${n} дн.`, legendPeriod: 'Месячные', legendPred: 'Прогноз', legendFertile: 'Фертильные', legendLog: 'Есть запись',
    flow: 'Месячные', flows: { none: 'Нет', spotting: 'Мазня', light: 'Слабые', medium: 'Средние', heavy: 'Обильные' },
    future: 'Нельзя отметить будущий день',
    medsToday: 'Лекарства на сегодня', noMeds: 'На сегодня лекарств нет', noMedsText: 'Добавьте лекарства и витамины в приложении Sadora — напоминания придут и в этот бот.',
    taken: 'Приняла', skip: 'Пропустила', undo: 'Отменить', stTaken: 'Принято', stSkipped: 'Пропущено', stLate: 'Опаздывает',
    ofTaken: (a, b) => `Принято ${a} из ${b}`,
    appPromo: 'Полная Sadora — в приложении', appPromoText: 'Беременность, анализы, врач, Близкий и другое',
    download: 'Скачать', signOut: 'Выйти',
    weekdays: ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'],
    months: ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь'],
    retry: 'Повторить', somethingWrong: 'Что-то пошло не так',
  },
  en: {
    tabToday: 'Today', tabCalendar: 'Calendar', tabMeds: 'Medicines',
    hello: (n) => n ? `Hi, ${n}` : 'Hi',
    openFromBot: 'To open Sadora in Telegram, go to @sadoraapp_bot and tap the "Sadora" button at the bottom.',
    openBot: 'Open the bot',
    welcomeTitle: 'Welcome to Sadora',
    welcomeText: 'Your cycle, mood and medicines — now inside Telegram too. Enter your phone number to sign in.',
    phone: 'Phone number', getCode: 'Get code', codeSent: (p) => `We sent an SMS code to ${p}`,
    code: 'SMS code', signIn: 'Sign in', changeNumber: 'Change number', resend: 'Resend',
    resendIn: (s) => `Resend in ${s} s`,
    phoneHint: '+998 and 9 digits',
    setupTitle: 'A few questions', setupText: 'So the prediction is right.',
    name: 'Your name', lastPeriod: 'When did your last period start?', cycleLength: 'Cycle length (days)',
    periodLength: 'Period length (days)', unsure: 'Not sure — we\'ll use 28 and 5 days',
    consent: 'I agree to Sadora storing my health data.', privacy: 'Privacy policy',
    consentNeeded: 'Consent is needed to continue', start: 'Start',
    cycleDay: 'Cycle day', dayN: (n) => `Day ${n}`,
    periodDay: (n) => `Period day ${n}`,
    untilPeriod: (n) => n === 0 ? 'Period expected today' : n < 0 ? `Period ${-n} days late` : `${n} days until your period`,
    phases: { period: 'Period', follicular: 'Follicular phase', fertile: 'Fertile days', luteal: 'Luteal phase' },
    predicted: 'predicted',
    noCycle: 'No data yet', noCycleText: 'Mark the day your last period started — Sadora will work out the next one.',
    stageNoPredict: 'Cycle predictions are not shown for your stage. The full view is in the Sadora app.',
    periodStarted: 'Period started', periodEnded: 'Period ended', save: 'Save',
    savedPeriodStart: 'Period logged from today', savedPeriodEnd: 'Period marked as ended',
    mood: 'Today\'s mood', moodAny: 'Mood', symptoms: 'Symptoms', water: 'Water', addWater: '+250 ml',
    saved: 'Saved ✓', failed: 'Could not save', offline: 'No internet connection',
    nextPeriod: 'Next period', ovulation: 'Ovulation', avgCycle: 'Average cycle', avgPeriod: 'Average period',
    days: (n) => `${n} days`, legendPeriod: 'Period', legendPred: 'Predicted', legendFertile: 'Fertile', legendLog: 'Logged',
    flow: 'Period', flows: { none: 'None', spotting: 'Spotting', light: 'Light', medium: 'Medium', heavy: 'Heavy' },
    future: 'A future day cannot be logged',
    medsToday: 'Today\'s medicines', noMeds: 'No medicines today', noMedsText: 'Add medicines and vitamins in the Sadora app — reminders will come to this bot too.',
    taken: 'Taken', skip: 'Skip', undo: 'Undo', stTaken: 'Taken', stSkipped: 'Skipped', stLate: 'Late',
    ofTaken: (a, b) => `${a} of ${b} taken`,
    appPromo: 'The full Sadora is in the app', appPromoText: 'Pregnancy, lab results, doctors, Yaqinim and more',
    download: 'Download', signOut: 'Sign out',
    weekdays: ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'],
    months: ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'],
    retry: 'Try again', somethingWrong: 'Something went wrong',
  },
};

const MOODS = [['bad', '😣'], ['low', '😕'], ['ok', '😐'], ['good', '🙂'], ['great', '😄']];
const FLOWS = ['spotting', 'light', 'medium', 'heavy'];

let lang = pickLanguage(tg?.initDataUnsafe?.user?.language_code);
let t = WORDS[lang];

function pickLanguage(code) {
  const c = (code || '').slice(0, 2);
  return c === 'ru' ? 'ru' : c === 'en' ? 'en' : 'uz';
}
function setLanguage(code) {
  lang = ['uz', 'ru', 'en'].includes(code) ? code : lang;
  t = WORDS[lang];
  document.documentElement.lang = lang;
  document.querySelectorAll('[data-t]').forEach((el) => { el.textContent = t[el.dataset.t]; });
}

// ------------------------------------------------------------------ state

const tgUser = tg?.initDataUnsafe?.user;
const deviceId = `tg-${tgUser?.id ?? 'web'}`;
const STORE_KEY = `sadora.tg.${tgUser?.id ?? 'web'}`;
let session = null; // { accessToken, refreshToken }
let me = null;      // UserProfile
let tab = 'today';
let today = null;   // the server's "today" in her time zone
let calMonth = null;

const store = {
  get() { try { return JSON.parse(localStorage.getItem(STORE_KEY) || 'null'); } catch { return null; } },
  set(v) { try { v ? localStorage.setItem(STORE_KEY, JSON.stringify(v)) : localStorage.removeItem(STORE_KEY); } catch { /* private mode */ } },
};

function device() {
  return {
    deviceId,
    platform: 'web',
    app: 'client',
    appVersion: 'telegram-miniapp-1',
    osVersion: tg ? `${tg.platform} ${tg.version}` : undefined,
    model: 'Telegram',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
  };
}

// ------------------------------------------------------------------ API

class ApiError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

async function api(path, { method = 'GET', body, auth = true, retried = false } = {}) {
  const headers = { 'Accept-Language': lang, 'X-Device-Id': deviceId };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && session) headers.Authorization = `Bearer ${session.accessToken}`;
  let response;
  try {
    response = await fetch(`${API}/v1${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'offline', t.offline);
  }
  if (response.status === 401 && auth && !retried && session) {
    if (await renew()) return api(path, { method, body, auth, retried: true });
  }
  if (!response.ok) {
    const json = await response.json().catch(() => null);
    const err = json?.error ?? json ?? {};
    throw new ApiError(response.status, err.code, err.message || t.somethingWrong);
  }
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

function adopt(auth) {
  session = { accessToken: auth.tokens.accessToken, refreshToken: auth.tokens.refreshToken };
  me = auth.user;
  store.set({ refreshToken: session.refreshToken });
  setLanguage(me?.language || lang);
}

/** A new access token from the stored refresh token, or a fresh Telegram sign-in. */
async function renew() {
  const saved = session?.refreshToken || store.get()?.refreshToken;
  if (saved) {
    try {
      adopt(await api('/auth/refresh', { method: 'POST', body: { refreshToken: saved }, auth: false }));
      return true;
    } catch (e) {
      if (e.code === 'offline') throw e;
      store.set(null);
    }
  }
  try {
    adopt(await api('/telegram/webapp/session', { method: 'POST', body: { initData: tg.initData, device: device() }, auth: false }));
    return true;
  } catch (e) {
    if (e.code === 'telegram_not_linked') return false;
    throw e;
  }
}

// ------------------------------------------------------------------ helpers

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const haptic = (kind = 'light') => { try { kind === 'success' || kind === 'error' ? tg?.HapticFeedback?.notificationOccurred(kind) : tg?.HapticFeedback?.impactOccurred(kind); } catch { /* old client */ } };

function toast(text) {
  $toast.textContent = text;
  $toast.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { $toast.hidden = true; }, 2200);
}

// Dates stay 'YYYY-MM-DD' strings; arithmetic in UTC so no time zone shifts a day.
const parse = (d) => { const [y, m, dd] = d.split('-').map(Number); return new Date(Date.UTC(y, m - 1, dd)); };
const fmt = (date) => date.toISOString().slice(0, 10);
const addDays = (d, n) => { const x = parse(d); x.setUTCDate(x.getUTCDate() + n); return fmt(x); };
const daysBetween = (a, b) => Math.round((parse(b) - parse(a)) / 86400000);
function nice(d) {
  const x = parse(d);
  return `${x.getUTCDate()} ${t.months[x.getUTCMonth()].toLowerCase()}`;
}

function setScreen(html, { tabs = true } = {}) {
  $app.innerHTML = html;
  $app.classList.toggle('no-tabs', !tabs);
  $tabs.hidden = !tabs;
  window.scrollTo(0, 0);
}

function busy(button, on) {
  if (!button) return;
  button.disabled = on;
  if (on) { button.dataset.label = button.innerHTML; button.innerHTML = '<span class="spinner" style="width:20px;height:20px;border-width:2px"></span>'; }
  else if (button.dataset.label) button.innerHTML = button.dataset.label;
}

function failScreen(error, retry) {
  setScreen(`
    <div class="welcome">
      <img src="../img/ic_shield.webp" alt="" width="88" height="88">
      <h1>${esc(t.somethingWrong)}</h1>
      <p>${esc(error?.message || '')}</p>
      <button class="btn block" id="retry">${esc(t.retry)}</button>
    </div>`, { tabs: false });
  document.getElementById('retry').onclick = retry;
}

// ------------------------------------------------------------------ boot

async function boot() {
  setLanguage(lang);
  applyTheme();
  if (!tg || !tg.initData) return outsideTelegram();
  tg.ready();
  tg.expand();
  try { tg.disableVerticalSwipes?.(); } catch { /* older client */ }
  tg.onEvent('themeChanged', applyTheme);
  try {
    if (await renew()) return afterSignIn();
    phoneScreen();
  } catch (e) {
    failScreen(e, boot);
  }
}

function applyTheme() {
  const dark = tg?.colorScheme === 'dark' || (!tg && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  const bg = dark ? '#0F0D24' : '#F7F5FF';
  try { tg?.setHeaderColor(bg); tg?.setBackgroundColor(bg); tg?.setBottomBarColor?.(bg); } catch { /* older client */ }
}

function outsideTelegram() {
  setScreen(`
    <div class="welcome">
      <img src="../favicon.svg" alt="" width="88" height="88">
      <h1>Sadora</h1>
      <p>${esc(t.openFromBot)}</p>
      <a class="btn block" href="https://t.me/sadoraapp_bot">${esc(t.openBot)}</a>
    </div>`, { tabs: false });
}

async function afterSignIn() {
  if (!me?.onboardingCompleted) return setupScreen();
  showTab(tab);
}

// ------------------------------------------------------------------ phone sign-in

function phoneScreen(prefill = '') {
  setScreen(`
    <div class="welcome">
      <img src="../favicon.svg" alt="" width="84" height="84">
      <h1>${esc(t.welcomeTitle)}</h1>
      <p>${esc(t.welcomeText)}</p>
    </div>
    <div class="card">
      <form id="f" novalidate>
        <label class="field"><span>${esc(t.phone)}</span>
          <input id="phone" type="tel" inputmode="tel" autocomplete="tel" placeholder="+998 90 123 45 67" value="${esc(prefill || '+998 ')}">
        </label>
        <div class="error" id="err"></div>
        <button class="btn block" id="go">${esc(t.getCode)}</button>
      </form>
    </div>`, { tabs: false });
  const input = document.getElementById('phone');
  input.focus();
  input.setSelectionRange(input.value.length, input.value.length);
  document.getElementById('f').onsubmit = async (ev) => {
    ev.preventDefault();
    const digits = input.value.replace(/\D/g, '');
    const local = digits.startsWith('998') ? digits.slice(3) : digits;
    const err = document.getElementById('err');
    if (local.length !== 9) { err.textContent = t.phoneHint; haptic('error'); return; }
    const phone = `+998${local}`;
    const button = document.getElementById('go');
    busy(button, true);
    try {
      const challenge = await api('/auth/otp/request', { method: 'POST', body: { phone, language: lang }, auth: false });
      codeScreen(phone, challenge);
    } catch (e) {
      err.textContent = e.message; haptic('error');
      busy(button, false);
    }
  };
}

function codeScreen(phone, challenge) {
  const pretty = phone.replace(/^\+998(\d{2})(\d{3})(\d{2})(\d{2})$/, '+998 $1 $2 $3 $4');
  setScreen(`
    <div class="welcome">
      <img src="../img/ic_message.webp" alt="" width="84" height="84">
      <h1>${esc(t.code)}</h1>
      <p>${esc(t.codeSent(pretty))}</p>
    </div>
    <div class="card">
      <form id="f" novalidate>
        <input id="code" class="code" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" value="${esc(challenge.devCode || '')}" aria-label="${esc(t.code)}">
        <div class="error" id="err" style="margin-top:10px"></div>
        <button class="btn block" id="go">${esc(t.signIn)}</button>
      </form>
      <div class="row" style="justify-content:space-between;margin-top:12px">
        <button class="btn ghost small" id="back">${esc(t.changeNumber)}</button>
        <button class="btn ghost small" id="again" disabled></button>
      </div>
    </div>`, { tabs: false });
  const input = document.getElementById('code');
  const err = document.getElementById('err');
  input.focus();

  const again = document.getElementById('again');
  let left = challenge.resendAfterSeconds || 60;
  const tick = () => {
    if (!document.body.contains(again)) return clearInterval(timer);
    if (left <= 0) { again.disabled = false; again.textContent = t.resend; clearInterval(timer); return; }
    again.textContent = t.resendIn(left--);
  };
  const timer = setInterval(tick, 1000); tick();
  again.onclick = async () => {
    again.disabled = true;
    try { codeScreen(phone, await api('/auth/otp/request', { method: 'POST', body: { phone, language: lang }, auth: false })); }
    catch (e) { err.textContent = e.message; again.disabled = false; }
  };
  document.getElementById('back').onclick = () => phoneScreen(pretty);

  const submit = async (ev) => {
    ev?.preventDefault();
    const code = input.value.replace(/\D/g, '');
    if (code.length < 4) return;
    const button = document.getElementById('go');
    busy(button, true);
    try {
      adopt(await api('/auth/otp/verify', { method: 'POST', body: { challengeId: challenge.challengeId, code, device: device() }, auth: false }));
      await api('/telegram/webapp/link', { method: 'POST', body: { initData: tg.initData } });
      haptic('success');
      afterSignIn();
    } catch (e) {
      err.textContent = e.message; haptic('error');
      busy(button, false);
    }
  };
  document.getElementById('f').onsubmit = submit;
  input.oninput = () => { if (input.value.replace(/\D/g, '').length === 6) submit(); };
}

// ------------------------------------------------------------------ short onboarding

function setupScreen() {
  const max = new Date().toISOString().slice(0, 10);
  setScreen(`
    <div class="head"><div><h1>${esc(t.setupTitle)}</h1><div class="sub">${esc(t.setupText)}</div></div></div>
    <div class="card">
      <form id="f" novalidate>
        <label class="field"><span>${esc(t.name)}</span><input id="name" type="text" autocomplete="given-name" value="${esc(me?.name || tgUser?.first_name || '')}" maxlength="40"></label>
        <label class="field"><span>${esc(t.lastPeriod)}</span><input id="last" type="date" max="${max}"></label>
        <div class="row">
          <label class="field grow"><span>${esc(t.cycleLength)}</span><input id="cycle" type="number" inputmode="numeric" min="15" max="90" value="28"></label>
          <label class="field grow"><span>${esc(t.periodLength)}</span><input id="period" type="number" inputmode="numeric" min="1" max="15" value="5"></label>
        </div>
        <div class="muted small" style="margin:-6px 0 14px">${esc(t.unsure)}</div>
        <label class="check"><input id="consent" type="checkbox"><span>${esc(t.consent)} <a href="https://sadora.app/privacy" id="pp">${esc(t.privacy)}</a></span></label>
        <div class="error" id="err"></div>
        <button class="btn block" id="go">${esc(t.start)}</button>
      </form>
    </div>`, { tabs: false });
  document.getElementById('pp').onclick = (ev) => { ev.preventDefault(); tg.openLink(`https://sadora.app/privacy.${lang}`); };
  document.getElementById('f').onsubmit = async (ev) => {
    ev.preventDefault();
    const err = document.getElementById('err');
    if (!document.getElementById('consent').checked) { err.textContent = t.consentNeeded; haptic('error'); return; }
    const button = document.getElementById('go');
    busy(button, true);
    const last = document.getElementById('last').value || null;
    const clamp = (v, lo, hi, d) => { const n = parseInt(v, 10); return Number.isFinite(n) ? Math.min(hi, Math.max(lo, n)) : d; };
    try {
      me = await api('/me/onboarding', {
        method: 'POST',
        body: {
          name: document.getElementById('name').value.trim() || tgUser?.first_name || 'Sadora',
          language: lang,
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
          lifeStage: 'cycle',
          cycle: {
            lastPeriodStart: last,
            averageCycleLength: clamp(document.getElementById('cycle').value, 15, 90, 28),
            averagePeriodLength: clamp(document.getElementById('period').value, 1, 15, 5),
          },
          consents: { storeHealth: true },
        },
      });
      haptic('success');
      showTab('today');
    } catch (e) {
      err.textContent = e.message; haptic('error');
      busy(button, false);
    }
  };
}

// ------------------------------------------------------------------ tabs

$tabs.querySelectorAll('button').forEach((b) => { b.onclick = () => { haptic('light'); showTab(b.dataset.tab); }; });

function showTab(name) {
  tab = name;
  $tabs.querySelectorAll('button').forEach((b) => b.setAttribute('aria-current', b.dataset.tab === name ? 'page' : 'false'));
  closeSheet();
  const render = { today: renderToday, calendar: renderCalendar, meds: renderMeds }[name];
  setScreen('<div class="splash" style="min-height:60vh"><div class="spinner"></div></div>');
  render().catch((e) => failScreen(e, () => showTab(name)));
}

// ------------------------------------------------------------------ Today

let symptomCatalogue = null;

async function renderToday() {
  const status = await api('/cycle/status');
  today = status.today;
  const [log, water, symptoms] = await Promise.all([
    api(`/days/${today}`).catch(() => null),
    api('/nutrition/today').catch(() => null),
    symptomCatalogue ? Promise.resolve(symptomCatalogue) : api('/symptoms').catch(() => []),
  ]);
  symptomCatalogue = symptoms;
  if (tab !== 'today') return;

  const hero = heroHtml(status);
  const mood = log?.mood;
  const chosen = new Set((log?.symptoms || []).map((s) => s.key));
  const top = (symptoms || []).slice(0, 12);
  const goal = water?.waterGoalMl || 2000;
  const ml = water?.waterMl || 0;

  setScreen(`
    <div class="head">
      <div><h1>${esc(t.hello(me?.name))}</h1><div class="sub">${esc(nice(today))}</div></div>
      <button class="btn ghost small" id="out">${esc(t.signOut)}</button>
    </div>
    ${hero}
    <div class="card">
      <h2>${esc(t.mood)}</h2>
      <div class="moods">${MOODS.map(([k, face]) => `<button class="mood" data-mood="${k}" aria-pressed="${mood === k}" aria-label="${k}">${face}</button>`).join('')}</div>
    </div>
    ${top.length ? `<div class="card"><h2>${esc(t.symptoms)}</h2><div class="chips">${top.map((s) => `<button class="chip" data-sym="${esc(s.key)}" aria-pressed="${chosen.has(s.key)}">${esc(s.label)}</button>`).join('')}</div></div>` : ''}
    <div class="card">
      <div class="row">
        <div class="icon"><img src="../img/ic_water.webp" alt=""></div>
        <div class="grow"><div style="font-weight:700">${esc(t.water)}</div><div class="muted small" id="wtext">${ml} / ${goal} ml</div></div>
        <button class="btn soft small" id="water">${esc(t.addWater)}</button>
      </div>
      <div class="water-bar"><i id="wbar" style="width:${Math.min(100, (ml / goal) * 100)}%"></i></div>
    </div>
    ${promoHtml()}`);

  bindHero(status);
  bindPromo();
  document.getElementById('out').onclick = signOut;

  // Taps save one after another, each on top of the answer to the one before, so a quick
  // mood-then-symptom never sends the day without the mood.
  let current = log || {};
  let queue = Promise.resolve();
  const saveDay = (change) => {
    queue = queue.then(async () => {
      const body = { flow: current.flow ?? null, mood: current.mood ?? null, energy: current.energy ?? null, stress: current.stress ?? null, symptoms: current.symptoms || [], note: current.note ?? null, fetalMovement: current.fetalMovement ?? null, ...change(current) };
      try { current = await api(`/days/${today}`, { method: 'PUT', body }); haptic('light'); toast(t.saved); }
      catch (e) { toast(e.message || t.failed); haptic('error'); }
    });
    return queue;
  };

  $app.querySelectorAll('[data-mood]').forEach((b) => {
    b.onclick = () => {
      const value = b.getAttribute('aria-pressed') === 'true' ? null : b.dataset.mood;
      $app.querySelectorAll('[data-mood]').forEach((x) => x.setAttribute('aria-pressed', String(x.dataset.mood === value)));
      saveDay(() => ({ mood: value }));
    };
  });
  $app.querySelectorAll('[data-sym]').forEach((b) => {
    b.onclick = () => {
      const on = b.getAttribute('aria-pressed') !== 'true';
      b.setAttribute('aria-pressed', String(on));
      const key = b.dataset.sym;
      saveDay((day) => {
        const rest = (day.symptoms || []).filter((s) => s.key !== key);
        return { symptoms: on ? [...rest, { key, severity: 'moderate' }] : rest };
      });
    };
  });
  document.getElementById('water').onclick = async (ev) => {
    const b = ev.currentTarget; b.disabled = true;
    try {
      const w = await api('/nutrition/water', { method: 'POST', body: { ml: 250, clientId: crypto.randomUUID?.() } });
      document.getElementById('wtext').textContent = `${w.waterMl} / ${w.waterGoalMl} ml`;
      document.getElementById('wbar').style.width = `${Math.min(100, (w.waterMl / w.waterGoalMl) * 100)}%`;
      haptic('light');
    } catch (e) { toast(e.message); haptic('error'); }
    b.disabled = false;
  };
}

function heroHtml(s) {
  const p = s.prediction || {};
  if (p.reason === 'stage_does_not_predict') {
    return `<div class="hero"><img class="ring" src="../img/ic_cycle_ring.webp" alt=""><div class="label">Sadora</div><div class="line" style="margin-top:10px;max-width:70%">${esc(t.stageNoPredict)}</div></div>`;
  }
  if (!s.cycleDay && !s.lastPeriodStart) {
    return `<div class="hero"><img class="ring" src="../img/ic_cycle_ring.webp" alt="">
      <div class="label">${esc(t.cycleDay)}</div><div class="big num">—</div>
      <div class="line" style="max-width:70%">${esc(t.noCycleText)}</div>
      <div class="hero-btn"><button class="btn light" id="pstart">${esc(t.periodStarted)}</button></div></div>`;
  }
  const inPeriod = !!s.currentPeriod;
  const phase = s.phase ? t.phases[s.phase] + (s.phaseIsPredicted ? ` · ${t.predicted}` : '') : '';
  const line = inPeriod
    ? t.periodDay(daysBetween(s.currentPeriod.startedOn, s.today) + 1)
    : s.daysUntilNextPeriod != null ? t.untilPeriod(s.daysUntilNextPeriod) : '';
  return `<div class="hero ${inPeriod ? 'phase-period' : ''}"><img class="ring" src="../img/ic_cycle_ring.webp" alt="">
    <div class="label">${esc(t.cycleDay)}</div>
    <div class="big num">${s.cycleDay ?? '—'}</div>
    <div class="line">${esc(phase)}</div>
    <div class="label" style="margin-top:4px">${esc(line)}</div>
    <div class="hero-btn">${inPeriod
      ? `<button class="btn light" id="pend">${esc(t.periodEnded)}</button>`
      : `<button class="btn light" id="pstart">${esc(t.periodStarted)}</button>`}</div></div>`;
}

function bindHero(s) {
  const start = document.getElementById('pstart');
  if (start) start.onclick = () => periodSheet(s);
  const end = document.getElementById('pend');
  if (end) end.onclick = async () => {
    busy(end, true);
    try {
      await api(`/cycle/periods/${s.currentPeriod.id}`, { method: 'PATCH', body: { endedOn: s.today } });
      haptic('success'); toast(t.savedPeriodEnd); showTab('today');
    } catch (e) { toast(e.message); haptic('error'); busy(end, false); }
  };
}

/** "Period started" asks which day: today by default, or the day she actually noticed. */
function periodSheet(s) {
  openSheet(`
    <h3>${esc(t.periodStarted)}</h3>
    <div class="section"><label class="field"><input id="pday" type="date" max="${s.today}" value="${s.today}"></label></div>
    <div class="error" id="err"></div>
    <button class="btn block" id="psave">${esc(t.save)}</button>`);
  document.getElementById('psave').onclick = async (ev) => {
    const day = document.getElementById('pday').value || s.today;
    busy(ev.currentTarget, true);
    try {
      await api('/cycle/periods', { method: 'POST', body: { startedOn: day } });
      haptic('success'); closeSheet(); toast(t.savedPeriodStart); showTab(tab);
    } catch (e) { document.getElementById('err').textContent = e.message; haptic('error'); busy(ev.currentTarget, false); }
  };
}

function promoHtml() {
  return `<div class="card flat promo">
    <div class="icon"><img src="../img/ic_star.webp" alt=""></div>
    <div class="grow"><div>${esc(t.appPromo)}</div><div class="muted small">${esc(t.appPromoText)}</div></div>
    <button class="btn soft small" id="dl">${esc(t.download)}</button></div>`;
}
function bindPromo() {
  const b = document.getElementById('dl');
  if (b) b.onclick = () => tg.openLink('https://sadora.app/download');
}

async function signOut() {
  try { await api('/auth/logout', { method: 'POST', body: { refreshToken: session?.refreshToken } }); } catch { /* signed out anyway */ }
  try { await api('/telegram', { method: 'DELETE' }); } catch { /* not linked */ }
  store.set(null); session = null; me = null;
  phoneScreen();
}

// ------------------------------------------------------------------ Calendar

async function renderCalendar() {
  if (!today) today = (await api('/cycle/status')).today;
  if (!calMonth) calMonth = today.slice(0, 8) + '01';
  const first = calMonth;
  const lead = (parse(first).getUTCDay() + 6) % 7; // Monday first
  const gridStart = addDays(first, -lead);
  const gridEnd = addDays(gridStart, 41);
  const cal = await api(`/cycle/calendar?from=${gridStart}&to=${gridEnd}`);
  if (tab !== 'calendar') return;
  const byDate = Object.fromEntries((cal.days || []).map((d) => [d.date, d]));
  const p = cal.prediction || {};
  const month = parse(first).getUTCMonth();

  let cells = t.weekdays.map((w) => `<div class="wd">${w}</div>`).join('');
  for (let i = 0; i < 42; i++) {
    const d = addDays(gridStart, i);
    const info = byDate[d] || {};
    const cls = ['day'];
    if (parse(d).getUTCMonth() !== month) cls.push('out');
    const bleeding = !!info.flow || info.phase === 'period';
    if (bleeding) cls.push('period');
    if (bleeding && info.isPredicted && !info.flow) cls.push('pred');
    if (!bleeding && info.phase === 'fertile') cls.push(p.ovulationOn === d ? 'ovulation' : 'fertile');
    if (d === today) cls.push('today');
    const dot = info.hasNote || info.symptomCount > 0 ? '<i class="dot"></i>' : '';
    cells += `<button class="${cls.join(' ')}" data-day="${d}">${parse(d).getUTCDate()}${dot}</button>`;
  }

  const stat = (label, value) => value ? `<div class="stat"><div class="num">${esc(value)}</div><div class="muted">${esc(label)}</div></div>` : '';
  const next = p.nextPeriodEarliest && p.nextPeriodLatest && p.nextPeriodEarliest !== p.nextPeriodLatest
    ? `${nice(p.nextPeriodEarliest)} – ${nice(p.nextPeriodLatest)}`
    : p.nextPeriodStart ? nice(p.nextPeriodStart) : null;

  setScreen(`
    <div class="card">
      <div class="cal-head">
        <button id="prev" aria-label="‹">‹</button>
        <h2 style="margin:0">${esc(t.months[month])} ${parse(first).getUTCFullYear()}</h2>
        <button id="next" aria-label="›">›</button>
      </div>
      <div class="cal-grid">${cells}</div>
      <div class="legend">
        <span><i style="background:var(--ph-period)"></i>${esc(t.legendPeriod)}</span>
        <span><i style="border:2px dashed var(--ph-period)"></i>${esc(t.legendPred)}</span>
        <span><i style="background:color-mix(in srgb,var(--ph-fert) 35%,transparent)"></i>${esc(t.legendFertile)}</span>
        <span><i style="background:var(--primary);width:6px;height:6px"></i>${esc(t.legendLog)}</span>
      </div>
    </div>
    <div class="card"><div class="stats">
      ${stat(t.nextPeriod, next)}
      ${stat(t.ovulation, p.ovulationOn ? nice(p.ovulationOn) : null)}
      ${stat(t.avgCycle, p.averageCycleLength ? t.days(p.averageCycleLength) : null)}
      ${stat(t.avgPeriod, p.averagePeriodLength ? t.days(p.averagePeriodLength) : null)}
    </div></div>
    ${promoHtml()}`);
  bindPromo();

  const shift = (n) => { const x = parse(calMonth); x.setUTCMonth(x.getUTCMonth() + n); calMonth = fmt(x); haptic('light'); renderCalendar().catch((e) => toast(e.message)); };
  document.getElementById('prev').onclick = () => shift(-1);
  document.getElementById('next').onclick = () => shift(1);
  $app.querySelectorAll('[data-day]').forEach((b) => { b.onclick = () => daySheet(b.dataset.day); });
}

async function daySheet(date) {
  if (date > today) { toast(t.future); haptic('error'); return; }
  haptic('light');
  const [log, symptoms] = await Promise.all([
    api(`/days/${date}`).catch(() => ({})),
    symptomCatalogue ? Promise.resolve(symptomCatalogue) : api('/symptoms').catch(() => []),
  ]);
  symptomCatalogue = symptoms;
  const state = { flow: log?.flow ?? null, mood: log?.mood ?? null, symptoms: new Map((log?.symptoms || []).map((s) => [s.key, s])) };

  openSheet(`
    <h3>${esc(nice(date))}</h3>
    <div class="section"><div>${esc(t.flow)}</div><div class="chips">
      ${['none', ...FLOWS].map((f) => `<button class="chip flow" data-flow="${f}" aria-pressed="${(state.flow ?? 'none') === f}">${esc(t.flows[f])}</button>`).join('')}
    </div></div>
    <div class="section"><div>${esc(t.moodAny)}</div><div class="moods">
      ${MOODS.map(([k, face]) => `<button class="mood" data-mood="${k}" aria-pressed="${state.mood === k}" aria-label="${k}">${face}</button>`).join('')}
    </div></div>
    ${(symptoms || []).length ? `<div class="section"><div>${esc(t.symptoms)}</div><div class="chips">
      ${symptoms.map((s) => `<button class="chip" data-sym="${esc(s.key)}" aria-pressed="${state.symptoms.has(s.key)}">${esc(s.label)}</button>`).join('')}
    </div></div>` : ''}
    <div class="error" id="err" style="margin-top:14px"></div>
    <button class="btn block" id="dsave">${esc(t.save)}</button>`);

  const card = $sheet.querySelector('.sheet-card');
  card.querySelectorAll('[data-flow]').forEach((b) => { b.onclick = () => {
    state.flow = b.dataset.flow === 'none' ? null : b.dataset.flow;
    card.querySelectorAll('[data-flow]').forEach((x) => x.setAttribute('aria-pressed', String(x === b)));
    haptic('light');
  }; });
  card.querySelectorAll('[data-mood]').forEach((b) => { b.onclick = () => {
    state.mood = state.mood === b.dataset.mood ? null : b.dataset.mood;
    card.querySelectorAll('[data-mood]').forEach((x) => x.setAttribute('aria-pressed', String(x.dataset.mood === state.mood)));
    haptic('light');
  }; });
  card.querySelectorAll('[data-sym]').forEach((b) => { b.onclick = () => {
    const k = b.dataset.sym;
    state.symptoms.has(k) ? state.symptoms.delete(k) : state.symptoms.set(k, { key: k, severity: 'moderate' });
    b.setAttribute('aria-pressed', String(state.symptoms.has(k)));
    haptic('light');
  }; });
  document.getElementById('dsave').onclick = async (ev) => {
    busy(ev.currentTarget, true);
    try {
      await api(`/days/${date}`, {
        method: 'PUT',
        body: { flow: state.flow, mood: state.mood, energy: log?.energy ?? null, stress: log?.stress ?? null, symptoms: [...state.symptoms.values()], note: log?.note ?? null, fetalMovement: log?.fetalMovement ?? null },
      });
      haptic('success'); closeSheet(); toast(t.saved);
      renderCalendar().catch(() => {});
    } catch (e) { document.getElementById('err').textContent = e.message; haptic('error'); busy(ev.currentTarget, false); }
  };
}

// ------------------------------------------------------------------ Medicines

async function renderMeds() {
  const day = await api('/meds/today');
  if (tab !== 'meds') return;
  const doses = day.doses || [];
  const taken = doses.filter((d) => d.status === 'taken').length;
  const hhmm = (s) => s.slice(0, 5);
  const state = (d) => d.status === 'taken' ? `<span class="state taken">${esc(t.stTaken)}</span>`
    : d.status === 'skipped' ? `<span class="state skipped">${esc(t.stSkipped)}</span>`
    : d.isLate ? `<span class="state late">${esc(t.stLate)}</span>` : '';

  setScreen(`
    <div class="head"><div><h1>${esc(t.medsToday)}</h1>${doses.length ? `<div class="sub">${esc(t.ofTaken(taken, doses.length))}</div>` : ''}</div></div>
    ${doses.length ? `<div class="card">${doses.map((d, i) => `
      <div class="dose">
        <div class="time num">${esc(hhmm(d.dueAt))}</div>
        <div class="grow"><div style="font-weight:700">${esc(d.emoji ? d.emoji + ' ' : '')}${esc(d.name)}</div>
          <div class="muted small">${esc(d.dosage || '')}</div>${state(d)}</div>
        ${d.status === 'pending'
          ? `<div class="row" style="gap:6px"><button class="btn small" data-i="${i}" data-s="taken">${esc(t.taken)}</button><button class="btn ghost small" data-i="${i}" data-s="skipped" aria-label="${esc(t.skip)}">⏭</button></div>`
          : `<button class="btn ghost small" data-i="${i}" data-s="pending">${esc(t.undo)}</button>`}
      </div>`).join('')}</div>`
    : `<div class="card center" style="padding:28px 18px"><img src="../img/ic_med_bottle.webp" alt="" width="84" height="84" style="margin:0 auto 10px"><h2>${esc(t.noMeds)}</h2><div class="muted">${esc(t.noMedsText)}</div></div>`}
    ${promoHtml()}`);
  bindPromo();
  $app.querySelectorAll('[data-i]').forEach((b) => {
    b.onclick = async () => {
      const d = doses[Number(b.dataset.i)];
      busy(b, true);
      try {
        await api(`/meds/${d.medicationId}/doses`, { method: 'POST', body: { dueOn: d.dueOn, dueAt: d.dueAt, status: b.dataset.s } });
        haptic(b.dataset.s === 'taken' ? 'success' : 'light');
        renderMeds().catch((e) => toast(e.message));
      } catch (e) { toast(e.message); haptic('error'); busy(b, false); }
    };
  });
}

// ------------------------------------------------------------------ sheet

function openSheet(html) {
  $sheet.querySelector('.sheet-card').innerHTML = html;
  $sheet.hidden = false;
  try { tg.BackButton.show(); } catch { /* older client */ }
}
function closeSheet() {
  $sheet.hidden = true;
  try { tg?.BackButton.hide(); } catch { /* older client */ }
}
$sheet.onclick = (ev) => { if (ev.target === $sheet) closeSheet(); };
tg?.BackButton?.onClick?.(closeSheet);

boot();
