import { limits } from './limits'
import type { FoodRelation, PrescriptionForm, PrescriptionItem, SendPrescriptionRequest } from './types'

/** The words for a prescription, in Uzbek like the rest of the panel. */
export const FORM_LABEL: Record<PrescriptionForm, string> = {
  tablet: 'Tabletka',
  capsule: 'Kapsula',
  syrup: 'Sirop',
  drops: 'Tomchi',
  injection: 'Ukol',
  ointment: 'Surtma',
  powder: 'Kukun',
  other: 'Boshqa',
}

/** The unit a form suggests; she can type her own over it. */
export const FORM_UNIT: Record<PrescriptionForm, string> = {
  tablet: 'tabletka',
  capsule: 'kapsula',
  syrup: 'ml',
  drops: 'tomchi',
  injection: 'ml',
  ointment: '',
  powder: 'paket',
  other: '',
}

export const FOOD_LABEL: Record<FoodRelation, string> = {
  before: 'Ovqatdan oldin',
  with: 'Ovqat bilan',
  after: 'Ovqatdan keyin',
  any: "Farqi yo'q",
}

export const FORMS = Object.keys(FORM_LABEL) as PrescriptionForm[]
export const FOODS: FoodRelation[] = ['before', 'with', 'after', 'any']

/** The usual hours for N doses a day. */
export function presetTimes(count: number): string[] {
  switch (count) {
    case 1:
      return ['09:00']
    case 2:
      return ['09:00', '21:00']
    case 3:
      return ['08:00', '14:00', '20:00']
    default:
      return ['08:00', '12:00', '17:00', '21:00']
  }
}

/** One medicine as the form holds it. Food starts unset: it is required and must be read. */
export interface ItemDraft {
  name: string
  form: PrescriptionForm
  dose: string
  /** Null while she has not typed one: the form's word stands in. */
  unit: string | null
  times: string[]
  /** Null is every day. */
  everyDays: number | null
  food: FoodRelation | null
  startDay: number
  days: string
  ongoing: boolean
  note: string
}

export const emptyItem = (): ItemDraft => ({
  name: '',
  form: 'tablet',
  dose: '',
  unit: null,
  times: presetTimes(2),
  everyDays: null,
  food: null,
  startDay: 1,
  days: '',
  ongoing: false,
  note: '',
})

export function itemFrom(item: PrescriptionItem): ItemDraft {
  return {
    name: item.name,
    form: item.form ?? 'tablet',
    dose: item.dose,
    unit: item.unit ?? '',
    times: [...item.schedule.times],
    everyDays: item.schedule.kind === 'interval' ? (item.schedule.intervalDays ?? null) : null,
    food: item.foodRelation,
    startDay: item.startDay ?? 1,
    days: item.days != null ? String(item.days) : '',
    ongoing: item.days == null,
    note: item.note ?? '',
  }
}

export type ItemProblem = 'name' | 'dose' | 'food' | 'days' | 'times'

export function problemsOf(item: ItemDraft): Set<ItemProblem> {
  const problems = new Set<ItemProblem>()
  if (!item.name.trim()) problems.add('name')
  if (!item.dose.trim()) problems.add('dose')
  if (!item.food) problems.add('food')
  const days = Number(item.days)
  if (!item.ongoing && !(Number.isInteger(days) && days >= 1 && days <= limits.prescriptionDaysMax)) problems.add('days')
  const valid = item.times.every((time) => /^([01]\d|2[0-3]):[0-5]\d$/.test(time))
  if (!item.times.length || !valid || new Set(item.times).size !== item.times.length) problems.add('times')
  return problems
}

export function toRequest(items: ItemDraft[], note: string): SendPrescriptionRequest {
  return {
    items: items.map((item) => ({
      name: item.name.trim(),
      form: item.form,
      dose: item.dose.trim(),
      unit: (item.unit ?? FORM_UNIT[item.form]).trim() || null,
      schedule: {
        kind: item.everyDays ? 'interval' : 'daily',
        times: [...item.times].sort(),
        intervalDays: item.everyDays,
      },
      foodRelation: item.food ?? 'any',
      startDay: item.startDay,
      days: item.ongoing ? null : Number(item.days),
      note: item.note.trim() || null,
    })),
    note: note.trim() || null,
  }
}

/** "1 tabletka · kuniga 2 marta: 09:00, 21:00 · ovqatdan keyin · 5 kun" */
export function summaryOf(item: PrescriptionItem): string {
  const times = item.schedule.times.join(', ')
  const daily = `kuniga ${item.schedule.times.length} marta`
  const when =
    item.schedule.kind === 'interval' ? `har ${item.schedule.intervalDays} kunda, ${daily}: ${times}` : `${daily}: ${times}`
  const start = (item.startDay ?? 1) > 1 ? `${item.startDay}-kundan ` : ''
  const length = item.days != null ? `${item.days} kun` : 'doimiy'
  return [[item.dose, item.unit].filter(Boolean).join(' '), when, FOOD_LABEL[item.foodRelation].toLowerCase(), start + length].join(
    ' · ',
  )
}
