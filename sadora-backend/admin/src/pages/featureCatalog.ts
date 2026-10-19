import type { AdminFlag, FeatureDefinition } from '../api/types'
import type { ArtName } from '../components/art'

/*
 * One card per thing the app does, in words an operator uses. On the server the same
 * thing can be two rows — a flag that switches it off for everyone, and an entitlement
 * that says who gets it and how often — and the two used to live on two pages under
 * their raw keys. The catalogue below is the only place that pairs them; the rules of
 * what a switch means are here too, kept out of the page so they can be tested.
 */

export interface CatalogEntry {
  title: string
  about: string
  art: ArtName
  /** The kill switch: off closes the feature for everyone at once. */
  flag?: string
  /** Who gets it (Free / Premium) and how many times a day or a month. */
  entitlement?: string
  /**
   * The server counts each use of this feature, so a limit on it holds and its usage
   * can be shown. A limit on any other feature counts nothing.
   */
  metered?: boolean
  /** Separate switches for one feature, one per phone platform. */
  platforms?: { flag: string; label: string }[]
}

export interface CatalogGroup {
  title: string
  entries: CatalogEntry[]
}

export const catalog: CatalogGroup[] = [
  {
    title: 'AI',
    entries: [
      { title: 'AI chat', about: 'SADORA AI bilan yozishma', art: 'ic3d_ai', flag: 'ai_chat_enabled', entitlement: 'ai_chat', metered: true },
      { title: 'Kunlik AI xulosasi', about: '"Bugun" ekranidagi qisqa xulosa', art: 'ic3d_sun', flag: 'ai_summary_enabled', entitlement: 'ai_daily_summary' },
      { title: 'AI tahlillar', about: 'Haftalik va oylik tahlillar', art: 'ic3d_insights', flag: 'ai_insights_enabled', entitlement: 'ai_insights' },
      { title: 'Tahlillar tarixi', about: '30 va 90 kunlik tahlillar', art: 'ic3d_calendar', entitlement: 'insights_history' },
      { title: 'AI ovqatlanish rejasi', about: 'Bosqichga mos ovqat rejasi', art: 'ic3d_nutrition', entitlement: 'ai_meal_plan' },
      { title: 'Ovoz bilan belgilash', about: 'Gapiradi — AI eshitganini belgilaydi', art: 'ic3d_message', entitlement: 'voice_log', metered: true },
      { title: 'AI hamroh maslahatlari', about: 'Hamroh (pet) gapiradigan maslahatlar', art: 'ic3d_star', entitlement: 'ai_pet' },
      {
        title: 'Gemini modeli',
        about: "O'chirilsa AI javoblarini model emas, oddiy qoidalar yozadi",
        art: 'ic3d_bulb',
        flag: 'ai_model_enabled',
      },
    ],
  },
  {
    title: "Sog'liq va kuzatuv",
    entries: [
      { title: 'Sikl prognozi', about: 'Prognoz va kalendar', art: 'ic3d_cycle', entitlement: 'cycle_prediction' },
      { title: 'Ovqat skaneri', about: 'Rasmdan ovqatni taniydi', art: 'ic3d_camera', flag: 'food_scan', entitlement: 'food_scan', metered: true },
      { title: 'Tahlil skaneri', about: "Laboratoriya varag'ini o'qiydi", art: 'ic3d_lab', entitlement: 'lab_scan', metered: true },
      { title: 'Ovqatlanish kundaligi', about: 'Ovqat va suv', art: 'ic3d_apple', entitlement: 'nutrition_log' },
      { title: 'Dorilar va eslatmalar', about: 'Dori kursi va eslatmalar', art: 'ic3d_meds', entitlement: 'meds_reminders' },
      { title: 'Kayfiyat kundaligi', about: 'Mind check-in va kundalik', art: 'ic3d_mood', entitlement: 'mind_journal' },
      { title: 'Kegel — yuqori darajalar', about: '3-darajadan yuqori mashqlar', art: 'ic3d_kegel', entitlement: 'kegel_advanced' },
      { title: "Chaqaloq o'sishi", about: 'WHO persentil grafigi', art: 'ic3d_baby', entitlement: 'baby_growth_chart' },
      { title: 'PCOS va endometrioz hisoboti', about: '3–6 oylik holat hisoboti', art: 'ic3d_record', entitlement: 'condition_report' },
      { title: 'Shifokor uchun PDF', about: '3–6 oylik hisobot', art: 'ic3d_doctor', entitlement: 'doctor_report_pdf', metered: true },
      { title: "Ma'lumotlarni eksport qilish", about: "Hamma ma'lumot bitta faylda", art: 'ic3d_share', entitlement: 'data_export', metered: true },
      { title: 'Bilim — Premium maqolalar', about: 'Premium materiallar', art: 'ic3d_book', entitlement: 'learn_premium' },
      {
        title: "Sog'liq ilovalari sinxroni",
        about: 'Apple HealthKit va Android Health Connect',
        art: 'ic3d_watch',
        entitlement: 'wearable_sync',
        platforms: [
          { flag: 'healthkit_sync', label: 'iPhone' },
          { flag: 'health_connect_sync', label: 'Android' },
        ],
      },
    ],
  },
  {
    title: 'Muloqot va xavfsizlik',
    entries: [
      { title: 'Chat', about: 'Lenta, izohlar va xabarlar', art: 'ic3d_chats', flag: 'community' },
      { title: 'SOS', about: "Yaqinlarga signal. O'chirilsa ham 103/112 tugmalari qoladi", art: 'ic3d_shield', flag: 'sos' },
      { title: 'Nishonni taqish', about: 'Ism yonida nishon', art: 'ic3d_trophy', entitlement: 'badge_wear' },
    ],
  },
  {
    title: "To'lov va sotuv",
    entries: [
      { title: 'App Store va Google Play', about: "Do'kon orqali obuna", art: 'ic3d_crown', flag: 'store_iap' },
      { title: 'Payme', about: "Sayt orqali to'lov", art: 'ic3d_gem', flag: 'payme_checkout' },
      { title: 'Click', about: "Sayt orqali to'lov", art: 'ic3d_gem', flag: 'click_checkout' },
      { title: 'Humo hamrohi sotuvda', about: 'Pullik legendar hamroh', art: 'ic3d_star', flag: 'pet_humo_sale' },
      { title: 'Avatar ramkalari sotuvda', about: 'Pullik ramkalar', art: 'ic3d_gift', flag: 'frame_sale' },
    ],
  },
]

/** A catalogue entry with the server's rows it names; a row the server lacks is absent. */
export interface FeatureCard {
  id: string
  entry: CatalogEntry
  flag?: AdminFlag
  feature?: FeatureDefinition
  platforms: { label: string; flag: AdminFlag }[]
}

export interface FeatureCardGroup {
  title: string
  cards: FeatureCard[]
}

/**
 * Pairs the catalogue with what the server has. A key the server added after this panel
 * shipped still shows up — under "Boshqa", by its own description — so nothing it can
 * switch is ever out of reach of the panel.
 */
export function buildFeatureGroups(flags: AdminFlag[], features: FeatureDefinition[]): FeatureCardGroup[] {
  const flagByKey = new Map(flags.map((flag) => [flag.key, flag]))
  const featureByKey = new Map(features.map((feature) => [feature.key, feature]))
  const claimedFlags = new Set<string>()
  const claimedFeatures = new Set<string>()

  const groups: FeatureCardGroup[] = catalog.map((group) => ({
    title: group.title,
    cards: group.entries.flatMap((entry) => {
      const flag = entry.flag ? flagByKey.get(entry.flag) : undefined
      const feature = entry.entitlement ? featureByKey.get(entry.entitlement) : undefined
      const platforms = (entry.platforms ?? []).flatMap((platform) => {
        const found = flagByKey.get(platform.flag)
        return found ? [{ label: platform.label, flag: found }] : []
      })
      if (flag) claimedFlags.add(flag.key)
      if (feature) claimedFeatures.add(feature.key)
      platforms.forEach((platform) => claimedFlags.add(platform.flag.key))
      if (!flag && !feature && !platforms.length) return []
      return [{ id: entry.flag ?? entry.entitlement ?? entry.title, entry, flag, feature, platforms }]
    }),
  }))

  const rest: FeatureCard[] = [
    ...flags
      .filter((flag) => !claimedFlags.has(flag.key))
      .map((flag) => ({
        id: flag.key,
        entry: { title: flag.description || flag.key, about: flag.key, art: 'ic3d_bulb' as ArtName, flag: flag.key },
        flag,
        platforms: [],
      })),
    ...features
      .filter((feature) => !claimedFeatures.has(feature.key))
      .map((feature) => ({
        id: feature.key,
        entry: { title: feature.description || feature.key, about: feature.key, art: 'ic3d_bulb' as ArtName, entitlement: feature.key },
        feature,
        platforms: [],
      })),
  ]
  if (rest.length) groups.push({ title: 'Boshqa', cards: rest })
  return groups.filter((group) => group.cards.length > 0)
}

export type SwitchState = 'on' | 'partial' | 'off'

/**
 * What a flag does for a user right now. Off at the kill switch is off whatever the
 * rules say; rules that all agree with the default change nothing; rules that disagree
 * open it for some users and not others.
 */
export function flagState(flag: AdminFlag): SwitchState {
  if (!flag.enabled) return 'off'
  if (flag.rules.some((rule) => rule.value !== flag.defaultValue)) return 'partial'
  return flag.defaultValue ? 'on' : 'off'
}

export type Audience = 'all' | 'premium' | 'free' | 'none'

export function audienceOf(feature: Pick<FeatureDefinition, 'freeEnabled' | 'premiumEnabled'>): Audience {
  if (feature.freeEnabled && feature.premiumEnabled) return 'all'
  if (feature.premiumEnabled) return 'premium'
  if (feature.freeEnabled) return 'free'
  return 'none'
}

/** The card's one switch: off if any part of it is off, partial if a flag is split. */
export function cardState(card: Pick<FeatureCard, 'flag' | 'feature'>): SwitchState {
  const parts: SwitchState[] = []
  if (card.flag) parts.push(flagState(card.flag))
  if (card.feature) parts.push(audienceOf(card.feature) === 'none' ? 'off' : 'on')
  if (!parts.length || parts.includes('off')) return 'off'
  return parts.includes('partial') ? 'partial' : 'on'
}

/** The writes one flip of the card's switch makes: a flag body, an entitlement, or both. */
export interface SwitchPlan {
  flag?: { key: string; enabled: boolean; defaultValue: boolean }
  feature?: FeatureDefinition
}

/**
 * Off: the kill switch when there is one — it overrides every rule and leaves the rest
 * of the setup as it was, so switching back on restores it. A feature with only an
 * entitlement is closed to both tiers.
 */
export function planSwitchOff(card: Pick<FeatureCard, 'flag' | 'feature'>): SwitchPlan {
  if (card.flag) return { flag: { key: card.flag.key, enabled: false, defaultValue: card.flag.defaultValue } }
  if (card.feature) return { feature: { ...card.feature, freeEnabled: false, premiumEnabled: false } }
  return {}
}

/**
 * On: only the parts that are off are touched. A flag whose default is "no" and that no
 * rule opens gets its default turned to "yes" — otherwise switching it on would change
 * nothing. A feature open to nobody opens to Premium, the narrower choice; making it
 * free for everyone is a separate, deliberate step.
 */
export function planSwitchOn(card: Pick<FeatureCard, 'flag' | 'feature'>): SwitchPlan {
  const plan: SwitchPlan = {}
  if (card.flag && flagState(card.flag) === 'off') {
    const ruleOpens = card.flag.rules.some((rule) => rule.value)
    plan.flag = { key: card.flag.key, enabled: true, defaultValue: card.flag.defaultValue || !ruleOpens }
  }
  if (card.feature && audienceOf(card.feature) === 'none') {
    plan.feature = { ...card.feature, premiumEnabled: true }
  }
  return plan
}

/** Whether the card shows its limit fields: only where a limit counts, or one is set. */
export function showsLimits(entry: CatalogEntry, feature: FeatureDefinition): boolean {
  return Boolean(
    entry.metered ||
      feature.freeDailyLimit != null ||
      feature.freeMonthlyLimit != null ||
      feature.premiumDailyLimit != null ||
      feature.premiumMonthlyLimit != null,
  )
}

/** Case- and apostrophe-blind match on what the operator reads, and on the raw keys. */
export function matchesSearch(card: FeatureCard, text: string): boolean {
  const needle = normalise(text)
  if (!needle) return true
  const haystack = [
    card.entry.title,
    card.entry.about,
    card.flag?.key,
    card.feature?.key,
    ...card.platforms.map((platform) => platform.flag.key),
  ]
  return haystack.some((value) => value && normalise(value).includes(needle))
}

function normalise(value: string): string {
  return value.toLowerCase().replace(/[ʻʼ‘’`']/g, '').trim()
}
