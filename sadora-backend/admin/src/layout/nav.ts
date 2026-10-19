import type { AdminRole } from '../api/types'
import type { ArtName } from '../components/art'

export interface NavEntry {
  to: string
  label: string
  /** The clay icon the apps use for the same idea. */
  art: ArtName
  roles: AdminRole[]
  /** Other words an operator might type into Ctrl+K for this page. */
  keywords?: string[]
}

export interface NavGroup {
  title: string
  entries: NavEntry[]
}

const ALL: AdminRole[] = ['OWNER', 'ADMIN', 'SUPPORT', 'ANALYST']

/*
 * Grouped by the job an operator came to do, not by the server module behind the page:
 * people, content, money, the app's settings, and reports. A page's title in the
 * header is its name here, so the rail and the page always agree.
 */
export const groups: NavGroup[] = [
  {
    title: '',
    entries: [{ to: '/', label: 'Bosh sahifa', art: 'ic3d_home', roles: ALL }],
  },
  {
    title: 'Odamlar',
    entries: [
      { to: '/users', label: 'Foydalanuvchilar', art: 'ic3d_profile', roles: ALL, keywords: ['hisob', 'mijoz', 'telefon'] },
      { to: '/doctors', label: 'Shifokorlar', art: 'ic3d_doctor', roles: ['OWNER', 'ADMIN', 'SUPPORT'], keywords: ['doktor', 'ariza', 'tekshiruv'] },
      { to: '/consultations', label: 'Konsultatsiyalar', art: 'ic3d_message', roles: ['OWNER', 'ADMIN', 'SUPPORT'], keywords: ['qaytarish', 'refund'] },
    ],
  },
  {
    title: 'Kontent',
    entries: [
      { to: '/community', label: 'Chat', art: 'ic3d_chats', roles: ALL, keywords: ['shikoyat', 'moderatsiya', 'post'] },
      { to: '/content', label: 'Maqolalar', art: 'ic3d_book', roles: ALL, keywords: ['bilim', 'maqola'] },
    ],
  },
  {
    title: 'Pul',
    entries: [
      { to: '/billing', label: "Obunalar va to'lovlar", art: 'ic3d_crown', roles: ALL, keywords: ['premium', 'tushum', 'payme', 'click'] },
      { to: '/shop', label: "Do'kon", art: 'ic3d_shop', roles: ALL, keywords: ['hamroh', 'ramka', 'chegirma'] },
    ],
  },
  {
    title: 'Ilova',
    entries: [
      { to: '/features', label: 'Funksiyalar', art: 'ic3d_bulb', roles: ['OWNER', 'ADMIN', 'ANALYST'], keywords: ['bayroq', 'flag', 'limit', 'huquq'] },
      { to: '/rewards', label: 'Gul va ketma-ketlik', art: 'ic3d_trophy', roles: ALL, keywords: ['mukofot', 'streak', 'tanga'] },
      { to: '/notifications', label: 'Bildirishnomalar', art: 'ic3d_bell', roles: ['OWNER', 'ADMIN', 'ANALYST'], keywords: ['push', 'shablon'] },
      { to: '/wearables', label: 'Soat va qurilmalar', art: 'ic3d_watch', roles: ['OWNER', 'ADMIN', 'ANALYST'], keywords: ['garmin', 'oura', 'wearable'] },
      { to: '/promo-video', label: 'Reklama videosi', art: 'ic3d_globe', roles: ALL, keywords: ['video'] },
    ],
  },
  {
    title: 'Hisobot',
    entries: [
      { to: '/analytics', label: 'Analitika', art: 'ic3d_insights', roles: ['OWNER', 'ADMIN', 'ANALYST'], keywords: ['retention', 'dau', 'voronka'] },
      { to: '/ai', label: 'AI xarajati', art: 'ic3d_ai', roles: ['OWNER', 'ADMIN', 'ANALYST'], keywords: ['gemini'] },
      { to: '/audit', label: 'Audit jurnali', art: 'ic3d_notebook', roles: ['OWNER'], keywords: ['log', 'jurnal'] },
    ],
  },
  {
    title: 'Hisob',
    entries: [
      // Every role: 2FA is each operator's own to switch on, and one that cannot enrol
      // is one whose account stays a password.
      { to: '/security', label: 'Hisobim va 2FA', art: 'ic3d_lock', roles: ALL, keywords: ['parol', 'profil'] },
      { to: '/operators', label: 'Operatorlar', art: 'ic3d_shield', roles: ['OWNER'], keywords: ['xodim', 'admin', 'rol'] },
    ],
  },
]

export const titles: Record<string, string> = Object.fromEntries(
  groups.flatMap((group) => group.entries.map((entry) => [entry.to, entry.label])),
)
