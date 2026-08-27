/**
 * SADORA admin panel.
 *
 * Deliberately build-free: plain ES modules served by the same Ktor process as the
 * API. An internal panel with four sections does not need a bundler, and shipping it
 * inside the server jar means there is no second thing to deploy or keep in sync.
 *
 * Sections follow the design's admin chapters: users (16), subscriptions and limits
 * (16b), content flags and audit (16c).
 */

// The admin routes are mounted under the versioned API prefix, so the panel's own
// URL is /v1/admin/ui and its calls go to /v1/admin/*.
const API = "/v1/admin";

// ---------------------------------------------------------------- session

/**
 * The admin token lives in sessionStorage, not localStorage: closing the tab ends the
 * session, which is the behaviour you want for a console that can block accounts.
 */
const session = {
  get() {
    try {
      const raw = sessionStorage.getItem("sadora.admin");
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },
  set(value) {
    try {
      sessionStorage.setItem("sadora.admin", JSON.stringify(value));
    } catch {
      /* private mode — the session simply does not survive a reload */
    }
  },
  clear() {
    try {
      sessionStorage.removeItem("sadora.admin");
    } catch {
      /* ignore */
    }
  },
};

const ROLE_RIGHTS = {
  OWNER: { users: true, manageUsers: true, premium: true, content: true, audit: true, card: true },
  ADMIN: { users: true, manageUsers: true, premium: true, content: true, audit: false, card: true },
  SUPPORT: { users: true, manageUsers: true, premium: false, content: false, audit: false, card: true },
  ANALYST: { users: true, manageUsers: false, premium: false, content: false, audit: false, card: false },
};

const rights = () => ROLE_RIGHTS[session.get()?.role] ?? ROLE_RIGHTS.ANALYST;

// ---------------------------------------------------------------- api

class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function api(path, { method = "GET", body } = {}) {
  const current = session.get();
  const response = await fetch(API + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(current ? { Authorization: `Bearer ${current.accessToken}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401) {
    // The token is gone or expired; there is nothing to retry with.
    session.clear();
    render();
    throw new ApiError("Sessiya tugadi. Qaytadan kiring.", 401);
  }

  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const error = payload?.error ?? {};
    throw new ApiError(error.message || "Nimadir noto'g'ri ketdi", response.status, error.code);
  }
  return payload;
}

// ---------------------------------------------------------------- helpers

const el = (tag, props = {}, ...children) => {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (value === null || value === undefined || value === false) continue;
    if (key === "class") node.className = value;
    else if (key === "html") node.innerHTML = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2).toLowerCase(), value);
    else node.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
};

/**
 * Appends children, skipping conditionals that came out empty.
 *
 * `Node.append(null)` renders the text "null", so a `cond ? node : null` argument has
 * to be filtered rather than passed straight through.
 */
const append = (host, ...children) => {
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    host.append(child);
  }
  return host;
};

const clear = (node) => {
  while (node.firstChild) node.removeChild(node.firstChild);
  return node;
};

const date = (iso) => {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString("uz-UZ", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit",
  });
};

const badge = (text, kind) => el("span", { class: `badge ${kind ?? ""}`.trim() }, text);

const tierBadge = (tier) =>
  tier === "premium" ? badge("Premium", "premium") : badge("Bepul");

const statusBadge = (status) => {
  if (status === "active") return badge("Faol", "ok");
  if (status === "blocked") return badge("Bloklangan", "bad");
  return badge("O'chirilmoqda", "warn");
};

const LIFE_STAGES = {
  cycle: "Sikl", trying_to_conceive: "Rejalashtirish", pregnancy: "Homiladorlik",
  postpartum: "Tug'ruqdan keyin", perimenopause: "Perimenopauza", menopause: "Menopauza",
};

/** Runs an action, showing its failure where the user is looking rather than in a console. */
async function guard(host, action) {
  const previous = host.querySelector(".error");
  if (previous) previous.remove();
  try {
    await action();
  } catch (error) {
    host.prepend(el("div", { class: "error" }, error.message));
  }
}

// ---------------------------------------------------------------- sign in

function signInView() {
  const root = el("div", { class: "signin" });
  const card = el("div", { class: "signin-card" });

  const form = el("form", {
    onsubmit: async (event) => {
      event.preventDefault();
      const data = new FormData(form);
      const totp = String(data.get("totp") || "").trim();
      await guard(card, async () => {
        const result = await api("/auth/login", {
          method: "POST",
          body: {
            email: String(data.get("email") || "").trim(),
            password: String(data.get("password") || ""),
            // Only sent when filled: accounts without 2FA must not send an empty code.
            totpCode: totp === "" ? null : totp,
          },
        });
        session.set(result);
        location.hash = "#/users";
        render();
      });
    },
  });

  form.append(
    el("div", { class: "field" },
      el("div", { class: "label" }, "E-mail"),
      el("input", { type: "email", name: "email", required: true, autocomplete: "username" })),
    el("div", { class: "field" },
      el("div", { class: "label" }, "Parol"),
      el("input", { type: "password", name: "password", required: true, autocomplete: "current-password" })),
    el("div", { class: "field" },
      el("div", { class: "label" }, "2FA kodi (agar yoqilgan bo'lsa)"),
      el("input", { type: "text", name: "totp", inputmode: "numeric", autocomplete: "one-time-code", placeholder: "123456" })),
    el("button", { class: "btn", type: "submit", style: "width:100%" }, "Kirish"),
  );

  card.append(
    el("div", { class: "brand" },
      el("div", { class: "brand-mark" }, "✦"),
      el("div", {},
        el("div", { class: "brand-name" }, "SADORA"),
        el("div", { class: "dim" }, "Admin panel"))),
    form,
  );

  root.append(card);
  return root;
}

// ---------------------------------------------------------------- shell

const SECTIONS = [
  { hash: "#/users", label: "Foydalanuvchilar", glyph: "◉", right: "users" },
  { hash: "#/features", label: "Obuna va limitlar", glyph: "◎", right: "users" },
  { hash: "#/flags", label: "Kontent flaglari", glyph: "◫", right: "users" },
  { hash: "#/audit", label: "Audit jurnali", glyph: "◷", right: "audit" },
];

function shell(content, current) {
  const me = session.get();
  const can = rights();

  const nav = el("nav", { class: "nav" },
    SECTIONS.map((section) =>
      el("a", {
        href: section.hash,
        class: current === section.hash ? "active" : "",
        "aria-disabled": can[section.right] ? null : "true",
        title: can[section.right] ? null : "Sizning rolingizda ochiq emas",
      }, el("span", {}, section.glyph), section.label)),
  );

  const sidebar = el("aside", { class: "sidebar" },
    el("div", { class: "brand" },
      el("div", { class: "brand-mark" }, "✦"),
      el("div", {},
        el("div", { class: "brand-name" }, "SADORA"),
        el("div", { class: "dim" }, "Admin"))),
    nav,
    el("div", { class: "who" },
      el("div", { class: "who-name" }, me?.name ?? ""),
      el("div", { class: "who-role" }, me?.role ?? ""),
      el("button", {
        class: "btn secondary small",
        onclick: () => {
          session.clear();
          location.hash = "";
          render();
        },
      }, "Chiqish")),
  );

  return el("div", { class: "shell" }, sidebar, el("main", { class: "main" }, content));
}

function pageHead(title, subtitle, ...actions) {
  return el("div", { class: "page-head" },
    el("div", {}, el("h1", {}, title), el("p", { class: "sub" }, subtitle)),
    el("div", { class: "row" }, ...actions));
}

function denied() {
  return el("div", { class: "empty" }, "Bu bo'lim sizning rolingizda ochiq emas.");
}

function pager(page, onOffset) {
  const from = page.total === 0 ? 0 : page.offset + 1;
  const to = Math.min(page.offset + page.limit, page.total);
  return el("div", { class: "pager" },
    el("button", {
      class: "btn secondary small",
      disabled: page.offset === 0,
      onclick: () => onOffset(Math.max(0, page.offset - page.limit)),
    }, "‹ Oldingi"),
    el("span", { class: "dim" }, `${from}–${to} / ${page.total}`),
    el("button", {
      class: "btn secondary small",
      disabled: to >= page.total,
      onclick: () => onOffset(page.offset + page.limit),
    }, "Keyingi ›"),
  );
}

// ---------------------------------------------------------------- users

const usersState = { q: "", status: "", language: "", lifeStage: "", offset: 0 };

async function usersView() {
  if (!rights().users) return shell(denied(), "#/users");

  const host = el("div", {});
  const body = el("div", {});

  const filters = el("div", { class: "filters" },
    el("input", {
      type: "text", placeholder: "Ism, telefon yoki e-mail bo'yicha qidirish",
      value: usersState.q, style: "flex:1;min-width:280px",
      oninput: (e) => { usersState.q = e.target.value; },
      onkeydown: (e) => { if (e.key === "Enter") { usersState.offset = 0; load(); } },
    }),
    select("status", { "": "Barcha holat", active: "Faol", blocked: "Bloklangan", deletion_pending: "O'chirilmoqda" }),
    select("lifeStage", { "": "Barcha bosqich", ...LIFE_STAGES }),
    select("language", { "": "Barcha til", uz: "O'zbekcha", ru: "Русский", en: "English" }),
    el("button", { class: "btn", onclick: () => { usersState.offset = 0; load(); } }, "Qidirish"),
  );

  function select(key, options) {
    return el("select", {
      style: "width:auto",
      onchange: (e) => { usersState[key] = e.target.value; usersState.offset = 0; load(); },
    }, Object.entries(options).map(([value, label]) =>
      el("option", { value, selected: usersState[key] === value }, label)));
  }

  async function load() {
    await guard(host, async () => {
      const params = new URLSearchParams({ limit: "25", offset: String(usersState.offset) });
      for (const key of ["q", "status", "language", "lifeStage"]) {
        if (usersState[key]) params.set(key, usersState[key]);
      }
      const page = await api(`/users?${params}`);
      clear(body).append(usersTable(page, load));
    });
  }

  host.append(
    pageHead("Foydalanuvchilar", "Hisob holati, obuna va texnik ma'lumot."),
    el("div", { class: "note strong" },
      "Sog'liq ma'lumotlari — sikl, simptom, kayfiyat, dori va AI yozishmalari — " +
      "admin panelga umuman chiqarilmaydi. Bu tuzilma darajasida ta'minlangan."),
    filters,
    body,
  );

  await load();
  return shell(host, "#/users");
}

function usersTable(page, reload) {
  if (page.items.length === 0) {
    return el("div", { class: "card" }, el("div", { class: "empty" }, "Hech kim topilmadi."));
  }

  const rows = page.items.map((user) =>
    el("tr", {
      class: rights().card ? "clickable" : "",
      onclick: rights().card ? () => openUser(user.id, reload) : null,
    },
      el("td", {}, el("div", {}, user.name), el("div", { class: "dim mono" }, user.phone ?? user.email ?? "—")),
      el("td", {}, LIFE_STAGES[user.lifeStage] ?? user.lifeStage),
      el("td", {}, tierBadge(user.tier)),
      el("td", {}, statusBadge(user.status)),
      el("td", { class: "dim" }, date(user.registeredAt)),
      el("td", { class: "dim" }, date(user.lastActiveAt)),
    ));

  return el("div", {},
    el("div", { class: "card", style: "padding:20px 8px" },
      el("table", {},
        el("thead", {}, el("tr", {},
          ["Foydalanuvchi", "Bosqich", "Reja", "Holat", "Ro'yxatdan o'tgan", "Oxirgi faollik"]
            .map((h) => el("th", {}, h)))),
        el("tbody", {}, rows))),
    pager(page, (offset) => { usersState.offset = offset; reload(); }),
  );
}

// ---------------------------------------------------------------- user card

async function openUser(id, reload) {
  const scrim = el("div", {
    class: "scrim",
    onclick: (e) => { if (e.target === scrim) scrim.remove(); },
  });
  const drawer = el("div", { class: "drawer" }, el("div", { class: "empty" }, "Yuklanmoqda…"));
  scrim.append(drawer);
  document.body.append(scrim);

  const refresh = async () => {
    await guard(drawer, async () => {
      const card = await api(`/users/${id}`);
      clear(drawer).append(userCard(card, refresh, () => { scrim.remove(); reload(); }));
    });
  };
  await refresh();
}

function userCard(card, refresh, close) {
  const { general, subscription, technical } = card;
  const can = rights();
  const host = el("div", {});

  const head = el("div", { class: "drawer-head" },
    el("div", {},
      el("h2", { style: "margin-bottom:4px" }, general.name),
      el("div", { class: "row" }, tierBadge(general.tier), statusBadge(general.status))),
    el("button", { class: "btn ghost small", onclick: close }, "✕ Yopish"));

  const generalCard = el("div", { class: "card" },
    el("h3", {}, "Umumiy"),
    el("dl", { class: "kv" },
      kv("ID", el("span", { class: "mono" }, general.id)),
      kv("Telefon", general.phone ?? "—"),
      kv("E-mail", general.email ?? "—"),
      kv("Til", general.language),
      kv("Hayot bosqichi", LIFE_STAGES[general.lifeStage] ?? general.lifeStage),
      kv("Ro'yxatdan o'tgan", date(general.registeredAt)),
      kv("Oxirgi faollik", date(general.lastActiveAt)),
      kv("Vaqt mintaqasi", technical.timezone)));

  const subscriptionCard = el("div", { class: "card" },
    el("h3", {}, "Obuna"),
    el("dl", { class: "kv" },
      kv("Reja", tierBadge(subscription.tier)),
      kv("Manba", subscription.source ?? "—"),
      kv("Tugash sanasi", date(subscription.expiresAt)),
      kv("Grace davri", subscription.inGracePeriod ? badge("Ha", "warn") : "Yo'q")),
    subscription.history.length > 0
      ? el("div", { style: "margin-top:16px" },
          el("div", { class: "label" }, "Tarix"),
          el("table", {}, el("tbody", {}, subscription.history.map((item) =>
            el("tr", {},
              el("td", {}, item.source),
              el("td", { class: "dim" }, item.productId ?? "—"),
              el("td", { class: "dim" }, `${date(item.startedAt)} → ${date(item.expiresAt)}`))))))
      : null);

  const technicalCard = el("div", { class: "card" },
    el("h3", {}, "Texnik"),
    el("div", { class: "label" }, "Qurilmalar"),
    technical.devices.length === 0
      ? el("p", { class: "dim" }, "Qurilma yo'q.")
      : el("table", {}, el("tbody", {}, technical.devices.map((device) =>
          el("tr", {},
            el("td", {}, device.platform, " ", el("span", { class: "dim" }, device.model ?? "")),
            el("td", { class: "dim mono" }, device.appVersion ?? "—"),
            el("td", { class: "dim" }, date(device.lastSeenAt)))))),
    el("div", { class: "label", style: "margin-top:16px" }, "Limit ishlatilishi"),
    technical.featureUsage.length === 0
      ? el("p", { class: "dim" }, "Hozircha ishlatilmagan.")
      : el("table", {}, el("tbody", {}, technical.featureUsage.map((usage) =>
          el("tr", {},
            el("td", { class: "mono" }, usage.featureKey),
            el("td", { class: "dim" }, `bugun ${usage.usedToday}`),
            el("td", { class: "dim" }, `oyda ${usage.usedThisMonth}`))))));

  host.append(head, generalCard, subscriptionCard, technicalCard);

  if (can.manageUsers) host.append(blockCard(general, refresh));
  if (can.premium) host.append(premiumCard(general, refresh));
  if (can.content) host.append(overrideCard(general, refresh));

  return host;
}

const kv = (key, value) => el("div", { style: "display:contents" },
  el("dt", {}, key), el("dd", {}, value));

function blockCard(general, refresh) {
  const blocked = general.status === "blocked";
  const card = el("div", { class: "card" });
  const reason = el("input", { type: "text", placeholder: "Sabab (audit jurnaliga yoziladi)" });

  card.append(
    el("h3", {}, blocked ? "Blokdan chiqarish" : "Hisobni bloklash"),
    el("p", { class: "dim" }, blocked
      ? "Blokdan chiqarilsa foydalanuvchi qaytadan kira oladi."
      : "Bloklangan foydalanuvchining barcha sessiyalari darhol tugatiladi."),
    el("div", { class: "field" }, reason),
    el("button", {
      class: blocked ? "btn" : "btn danger",
      onclick: () => guard(card, async () => {
        if (!reason.value.trim()) throw new ApiError("Sabab ko'rsatilishi kerak", 400);
        await api(`/users/${general.id}/block`, {
          method: "POST",
          body: { blocked: !blocked, reason: reason.value.trim() },
        });
        await refresh();
      }),
    }, blocked ? "Blokdan chiqarish" : "Bloklash"));

  return card;
}

function premiumCard(general, refresh) {
  const card = el("div", { class: "card" });
  const expires = el("input", { type: "datetime-local" });
  const reason = el("input", { type: "text", placeholder: "Sabab" });

  card.append(
    el("h3", {}, "Premium berish"),
    el("p", { class: "dim" },
      "Qo'lda berilgan Premium. Sanani bo'sh qoldirsangiz muddatsiz bo'ladi."),
    el("div", { class: "grid-2" },
      el("div", { class: "field" }, el("div", { class: "label" }, "Tugash sanasi"), expires),
      el("div", { class: "field" }, el("div", { class: "label" }, "Sabab"), reason)),
    el("button", {
      class: "btn",
      onclick: () => guard(card, async () => {
        if (!reason.value.trim()) throw new ApiError("Sabab ko'rsatilishi kerak", 400);
        await api(`/users/${general.id}/premium`, {
          method: "POST",
          body: {
            expiresAt: expires.value ? new Date(expires.value).toISOString() : null,
            reason: reason.value.trim(),
          },
        });
        await refresh();
      }),
    }, "Premium berish"));

  return card;
}

function overrideCard(general, refresh) {
  const card = el("div", { class: "card" });
  const key = el("input", { type: "text", placeholder: "ai_chat" });
  const daily = el("input", { type: "number", min: "0", placeholder: "kunlik" });
  const monthly = el("input", { type: "number", min: "0", placeholder: "oylik" });
  const reason = el("input", { type: "text", placeholder: "Sabab" });
  const enabled = el("select", {},
    el("option", { value: "true" }, "Yoqilgan"),
    el("option", { value: "false" }, "O'chirilgan"));

  card.append(
    el("h3", {}, "Shaxsiy limit"),
    el("p", { class: "dim" },
      "Faqat shu foydalanuvchi uchun umumiy limitni bekor qiladi."),
    el("div", { class: "grid-2" },
      el("div", { class: "field" }, el("div", { class: "label" }, "Imkoniyat kaliti"), key),
      el("div", { class: "field" }, el("div", { class: "label" }, "Holat"), enabled),
      el("div", { class: "field" }, el("div", { class: "label" }, "Kunlik limit"), daily),
      el("div", { class: "field" }, el("div", { class: "label" }, "Oylik limit"), monthly)),
    el("div", { class: "field" }, el("div", { class: "label" }, "Sabab"), reason),
    el("div", { class: "row" },
      el("button", {
        class: "btn",
        onclick: () => guard(card, async () => {
          if (!key.value.trim()) throw new ApiError("Kalit ko'rsatilishi kerak", 400);
          if (!reason.value.trim()) throw new ApiError("Sabab ko'rsatilishi kerak", 400);
          await api(`/users/${general.id}/features/${encodeURIComponent(key.value.trim())}`, {
            method: "PUT",
            body: {
              enabled: enabled.value === "true",
              dailyLimit: daily.value === "" ? null : Number(daily.value),
              monthlyLimit: monthly.value === "" ? null : Number(monthly.value),
              reason: reason.value.trim(),
            },
          });
          await refresh();
        }),
      }, "Saqlash"),
      el("button", {
        class: "btn secondary",
        onclick: () => guard(card, async () => {
          if (!key.value.trim()) throw new ApiError("Kalit ko'rsatilishi kerak", 400);
          await api(`/users/${general.id}/features/${encodeURIComponent(key.value.trim())}`, {
            method: "DELETE",
          });
          await refresh();
        }),
      }, "Bekor qilish")));

  return card;
}

// ---------------------------------------------------------------- features

async function featuresView() {
  if (!rights().users) return shell(denied(), "#/features");

  const host = el("div", {});
  const body = el("div", {});
  const editable = rights().content;

  async function load() {
    await guard(host, async () => {
      const features = await api("/features");
      clear(body).append(
        features.length === 0
          ? el("div", { class: "card" }, el("div", { class: "empty" }, "Imkoniyat topilmadi."))
          : el("div", {}, features.map((feature) => featureCard(feature, editable, load))));
    });
  }

  append(
    host,
    pageHead("Obuna va limitlar", "Bepul va Premium rejalar nimani ochadi va qancha."),
    !editable ? el("div", { class: "note" }, "Sizning rolingiz faqat ko'rish uchun.") : null,
    body,
  );

  await load();
  return shell(host, "#/features");
}

function featureCard(feature, editable, reload) {
  const card = el("div", { class: "card" });
  const fields = {};
  const number = (name, value) => {
    const input = el("input", {
      type: "number", min: "0", value: value ?? "", placeholder: "cheksiz", disabled: !editable,
    });
    fields[name] = input;
    return input;
  };
  const toggle = (name, value) => {
    const input = el("select", { disabled: !editable },
      el("option", { value: "true", selected: value }, "Yoqilgan"),
      el("option", { value: "false", selected: !value }, "O'chirilgan"));
    fields[name] = input;
    return input;
  };

  card.append(
    el("div", { class: "between" },
      el("div", {},
        el("h3", { style: "margin-bottom:2px" }, el("span", { class: "mono" }, feature.key)),
        el("p", { class: "dim", style: "margin:0" }, feature.description)),
      editable ? el("button", {
        class: "btn small",
        onclick: () => guard(card, async () => {
          await api(`/features/${encodeURIComponent(feature.key)}`, {
            method: "PUT",
            body: {
              freeEnabled: fields.freeEnabled.value === "true",
              premiumEnabled: fields.premiumEnabled.value === "true",
              freeDailyLimit: numberOrNull(fields.freeDailyLimit),
              freeMonthlyLimit: numberOrNull(fields.freeMonthlyLimit),
              premiumDailyLimit: numberOrNull(fields.premiumDailyLimit),
              premiumMonthlyLimit: numberOrNull(fields.premiumMonthlyLimit),
            },
          });
          await reload();
        }),
      }, "Saqlash") : null),
    el("div", { class: "grid-2", style: "margin-top:16px" },
      el("div", {},
        el("div", { class: "label" }, "Bepul"),
        el("div", { class: "field" }, toggle("freeEnabled", feature.freeEnabled)),
        el("div", { class: "field" }, number("freeDailyLimit", feature.freeDailyLimit)),
        el("div", { class: "field" }, number("freeMonthlyLimit", feature.freeMonthlyLimit))),
      el("div", {},
        el("div", { class: "label" }, "Premium"),
        el("div", { class: "field" }, toggle("premiumEnabled", feature.premiumEnabled)),
        el("div", { class: "field" }, number("premiumDailyLimit", feature.premiumDailyLimit)),
        el("div", { class: "field" }, number("premiumMonthlyLimit", feature.premiumMonthlyLimit)))),
    el("p", { class: "dim", style: "margin:0" }, "Bo'sh limit — cheksiz."),
  );

  return card;
}

const numberOrNull = (input) => (input.value === "" ? null : Number(input.value));

// ---------------------------------------------------------------- flags

async function flagsView() {
  if (!rights().users) return shell(denied(), "#/flags");

  const host = el("div", {});
  const body = el("div", {});
  const editable = rights().content;

  async function load() {
    await guard(host, async () => {
      const flags = await api("/flags");
      clear(body).append(
        flags.length === 0
          ? el("div", { class: "card" }, el("div", { class: "empty" }, "Flag topilmadi."))
          : el("div", {}, flags.map((flag) => flagCard(flag, editable, load))));
    });
  }

  append(
    host,
    pageHead("Kontent flaglari", "Qaysi imkoniyat kimga ochiq — bosqichma-bosqich chiqarish bilan."),
    !editable ? el("div", { class: "note" }, "Sizning rolingiz faqat ko'rish uchun.") : null,
    body,
  );

  await load();
  return shell(host, "#/flags");
}

function flagCard(flag, editable, reload) {
  const card = el("div", { class: "card" });
  const enabled = el("select", { disabled: !editable },
    el("option", { value: "true", selected: flag.enabled }, "Yoqilgan"),
    el("option", { value: "false", selected: !flag.enabled }, "O'chirilgan"));
  const fallback = el("select", { disabled: !editable },
    el("option", { value: "true", selected: flag.defaultValue }, "true"),
    el("option", { value: "false", selected: !flag.defaultValue }, "false"));

  const rules = flag.rules.length === 0
    ? el("p", { class: "dim" }, "Qoida yo'q — hamma uchun standart qiymat amal qiladi.")
    : el("table", {},
        el("thead", {}, el("tr", {},
          ["Shart", "Chiqarish", "Qiymat", "Ustuvorlik", ""].map((h) => el("th", {}, h)))),
        el("tbody", {}, flag.rules.map((rule) =>
          el("tr", {},
            el("td", { class: "dim" }, describeRule(rule)),
            el("td", {}, `${rule.rolloutPercentage}%`),
            el("td", {}, rule.value ? badge("true", "ok") : badge("false")),
            el("td", { class: "dim" }, rule.priority),
            el("td", {}, editable ? el("button", {
              class: "btn ghost small",
              onclick: () => guard(card, async () => {
                await api(`/flags/${encodeURIComponent(flag.key)}/rules/${rule.id}`, { method: "DELETE" });
                await reload();
              }),
            }, "O'chirish") : null)))));

  card.append(
    el("div", { class: "between" },
      el("div", {},
        el("h3", { style: "margin-bottom:2px" }, el("span", { class: "mono" }, flag.key)),
        el("p", { class: "dim", style: "margin:0" }, flag.description)),
      el("div", { class: "row" },
        el("div", {}, el("div", { class: "label" }, "Holat"), enabled),
        el("div", {}, el("div", { class: "label" }, "Standart"), fallback),
        editable ? el("button", {
          class: "btn small",
          onclick: () => guard(card, async () => {
            await api(`/flags/${encodeURIComponent(flag.key)}`, {
              method: "PUT",
              body: { enabled: enabled.value === "true", defaultValue: fallback.value === "true" },
            });
            await reload();
          }),
        }, "Saqlash") : null)),
    el("div", { class: "label", style: "margin-top:20px" }, "Qoidalar"),
    rules,
    editable ? addRuleForm(flag, reload, card) : null,
  );

  return card;
}

function describeRule(rule) {
  const parts = [
    ["muhit", rule.environment], ["davlat", rule.country], ["til", rule.language],
    ["bosqich", rule.lifeStage], ["platforma", rule.platform], ["kohorta", rule.cohort],
  ].filter(([, value]) => value).map(([label, value]) => `${label}=${value}`);
  return parts.length === 0 ? "hamma uchun" : parts.join(" · ");
}

function addRuleForm(flag, reload, host) {
  const inputs = {};
  const text = (name, placeholder) => {
    const input = el("input", { type: "text", placeholder });
    inputs[name] = input;
    return el("div", { class: "field" }, el("div", { class: "label" }, placeholder), input);
  };
  const rollout = el("input", { type: "number", min: "0", max: "100", value: "100" });
  const value = el("select", {},
    el("option", { value: "true" }, "true"),
    el("option", { value: "false" }, "false"));
  const priority = el("input", { type: "number", value: "100" });

  return el("details", { style: "margin-top:16px" },
    el("summary", { class: "dim", style: "cursor:pointer" }, "Yangi qoida qo'shish"),
    el("div", { class: "grid-2", style: "margin-top:12px" },
      text("environment", "Muhit"), text("country", "Davlat"),
      text("language", "Til"), text("lifeStage", "Hayot bosqichi"),
      text("platform", "Platforma"), text("cohort", "Kohorta"),
      el("div", { class: "field" }, el("div", { class: "label" }, "Chiqarish %"), rollout),
      el("div", { class: "field" }, el("div", { class: "label" }, "Qiymat"), value),
      el("div", { class: "field" }, el("div", { class: "label" }, "Ustuvorlik"), priority)),
    el("button", {
      class: "btn",
      onclick: () => guard(host, async () => {
        const body = { rolloutPercentage: Number(rollout.value), value: value.value === "true", priority: Number(priority.value) };
        for (const [name, input] of Object.entries(inputs)) {
          body[name] = input.value.trim() === "" ? null : input.value.trim();
        }
        await api(`/flags/${encodeURIComponent(flag.key)}/rules`, { method: "POST", body });
        await reload();
      }),
    }, "Qo'shish"));
}

// ---------------------------------------------------------------- audit

const auditState = { action: "", entityType: "", offset: 0 };

async function auditView() {
  if (!rights().audit) return shell(denied(), "#/audit");

  const host = el("div", {});
  const body = el("div", {});

  const filters = el("div", { class: "filters" },
    el("input", {
      type: "text", placeholder: "Amal (masalan admin.user.blocked)", value: auditState.action,
      style: "flex:1;min-width:260px",
      oninput: (e) => { auditState.action = e.target.value; },
      onkeydown: (e) => { if (e.key === "Enter") { auditState.offset = 0; load(); } },
    }),
    el("input", {
      type: "text", placeholder: "Obyekt turi", value: auditState.entityType, style: "width:200px",
      oninput: (e) => { auditState.entityType = e.target.value; },
    }),
    el("button", { class: "btn", onclick: () => { auditState.offset = 0; load(); } }, "Filtrlash"));

  async function load() {
    await guard(host, async () => {
      const params = new URLSearchParams({ limit: "50", offset: String(auditState.offset) });
      if (auditState.action) params.set("action", auditState.action);
      if (auditState.entityType) params.set("entityType", auditState.entityType);
      const page = await api(`/audit?${params}`);

      clear(body).append(
        page.items.length === 0
          ? el("div", { class: "card" }, el("div", { class: "empty" }, "Yozuv topilmadi."))
          : el("div", {},
              el("div", { class: "card", style: "padding:20px 8px" },
                el("table", {},
                  el("thead", {}, el("tr", {},
                    ["Vaqt", "Kim", "Amal", "Obyekt", "Sabab"].map((h) => el("th", {}, h)))),
                  el("tbody", {}, page.items.map((entry) =>
                    el("tr", {},
                      el("td", { class: "dim" }, date(entry.createdAt)),
                      el("td", {},
                        el("div", {}, entry.actorLabel ?? entry.actorId ?? "—"),
                        el("div", { class: "dim" }, entry.actorType)),
                      el("td", { class: "mono" }, entry.action),
                      el("td", { class: "dim mono" },
                        entry.entityType ? `${entry.entityType}/${(entry.entityId ?? "").slice(0, 8)}` : "—"),
                      el("td", { class: "dim" }, entry.reason ?? "—")))))),
              pager(page, (offset) => { auditState.offset = offset; load(); })));
    });
  }

  host.append(
    pageHead("Audit jurnali", "Kim nima qilgani — o'zgartirib bo'lmaydigan yozuv."),
    filters,
    body,
  );

  await load();
  return shell(host, "#/audit");
}

// ---------------------------------------------------------------- router

const ROUTES = {
  "#/users": usersView,
  "#/features": featuresView,
  "#/flags": flagsView,
  "#/audit": auditView,
};

async function render() {
  const root = document.getElementById("root");

  // A drawer is parented to <body>, so changing section would otherwise leave it
  // hanging over the new page.
  document.querySelectorAll(".scrim").forEach((node) => node.remove());

  if (!session.get()) {
    clear(root).append(signInView());
    return;
  }

  const view = ROUTES[location.hash] ?? usersView;
  if (!ROUTES[location.hash]) location.hash = "#/users";

  // Render the shell first so navigation feels immediate on a slow request.
  clear(root).append(shell(el("div", { class: "empty" }, "Yuklanmoqda…"), location.hash));
  const rendered = await view();
  clear(root).append(rendered);
}

window.addEventListener("hashchange", render);
render();
