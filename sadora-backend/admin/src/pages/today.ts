import type { AdminTodo } from '../api/types'
import type { ArtName } from '../components/art'

export interface TodayItem {
  key: keyof Omit<AdminTodo, 'generatedAt'>
  label: string
  hint: string
  count: number
  to: string
  art: ArtName
}

/*
 * The dashboard's "Bugun" list, most urgent first: money owed back to a patient, then a
 * doctor waiting to be let in, then reports, then a shop shelf that ran out. A queue the
 * server left out (null) is one this role's pages cannot work, and an empty one is not a
 * task — both are dropped, so the list is exactly what is waiting on this operator.
 */
const queues: Omit<TodayItem, 'count'>[] = [
  {
    key: 'refundsDue',
    label: 'Pulni qaytarish',
    hint: 'Shifokor javob bermagan pullik konsultatsiyalar',
    to: '/consultations',
    art: 'ic3d_gem',
  },
  {
    key: 'doctorsPending',
    label: 'Shifokor arizalari',
    hint: 'Hujjatlarni tekshirib, tasdiqlash yoki rad etish',
    to: '/doctors',
    art: 'ic3d_doctor',
  },
  {
    key: 'openReports',
    label: 'Chat shikoyatlari',
    hint: "Ko'rib chiqilmagan shikoyatlar",
    to: '/community?tab=reports',
    art: 'ic3d_chats',
  },
  {
    key: 'shopSoldOut',
    label: "Do'konda tugagan mahsulotlar",
    hint: "Qoldiqni to'ldiring yoki mahsulotni yashiring",
    to: '/shop',
    art: 'ic3d_shop',
  },
]

export function todayItems(todo: AdminTodo | undefined): TodayItem[] {
  if (!todo) return []
  return queues.flatMap((queue) => {
    const count = todo[queue.key]
    return typeof count === 'number' && count > 0 ? [{ ...queue, count }] : []
  })
}
