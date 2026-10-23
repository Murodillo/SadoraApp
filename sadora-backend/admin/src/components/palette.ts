import type { AdminRole, AdminSearchResults } from '../api/types'
import type { PmSearchResults } from '../pm/types'
import type { NavGroup } from '../layout/nav'
import type { ArtName } from './art'
import { formatSom } from '../pages/consultations'
import { doctorStatusLabels, specialtyLabels } from '../pages/doctorReview'

/** One line in the Ctrl+K list: where Enter goes, and what the line says. */
export interface PaletteItem {
  key: string
  section: 'Sahifalar' | 'Foydalanuvchilar' | 'Shifokorlar' | "To'lovlar" | 'Tasklar' | 'Hujjatlar'
  title: string
  subtitle?: string
  to: string
  art?: ArtName
}

/** Case- and apostrophe-blind, the way an operator types Uzbek on a Russian keyboard. */
export function normalise(value: string): string {
  return value.toLowerCase().replace(/[ʻʼ‘’`']/g, '').trim()
}

/** The pages this role can open whose name, group or keywords contain the text. */
export function matchPages(groups: NavGroup[], text: string, can: (roles: AdminRole[]) => boolean): PaletteItem[] {
  const needle = normalise(text)
  return groups.flatMap((group) =>
    group.entries
      .filter((entry) => can(entry.roles))
      .filter((entry) => {
        if (!needle) return true
        return [entry.label, group.title, ...(entry.keywords ?? [])].some((word) => normalise(word).includes(needle))
      })
      .map((entry) => ({
        key: `page:${entry.to}`,
        section: 'Sahifalar' as const,
        title: entry.label,
        subtitle: group.title || undefined,
        to: entry.to,
        art: entry.art,
      })),
  )
}

const paymentStates: Record<string, string> = {
  pending: 'kutilmoqda',
  paid: "to'langan",
  cancelled: 'bekor qilingan',
  failed: 'muvaffaqiyatsiz',
}

/** The server's finds as lines; a doctor opens on her own status tab, a payment on its owner's card. */
export function resultItems(results: AdminSearchResults | undefined): PaletteItem[] {
  if (!results) return []
  return [
    ...results.users.map((user) => ({
      key: `user:${user.id}`,
      section: 'Foydalanuvchilar' as const,
      title: user.name || 'Ismsiz',
      subtitle: [user.phone, user.email].filter(Boolean).join(' · ') || undefined,
      to: `/users/${user.id}`,
      art: 'ic3d_profile' as const,
    })),
    ...results.doctors.map((doctor) => ({
      key: `doctor:${doctor.id}`,
      section: 'Shifokorlar' as const,
      title: doctor.fullName,
      subtitle: [
        specialtyLabels[doctor.specialty as keyof typeof specialtyLabels] ?? doctor.specialty,
        doctorStatusLabels[doctor.status as keyof typeof doctorStatusLabels]?.text ?? doctor.status,
      ].join(' · '),
      to: `/doctors?status=${encodeURIComponent(doctor.status)}&id=${encodeURIComponent(doctor.id)}`,
      art: 'ic3d_doctor' as const,
    })),
    ...results.payments.map((payment) => ({
      key: `payment:${payment.id}`,
      section: "To'lovlar" as const,
      title: `${formatSom(payment.amountMinor)} · ${payment.provider}`,
      subtitle: paymentStates[payment.state] ?? payment.state,
      to: `/users/${payment.userId}`,
      art: 'ic3d_gem' as const,
    })),
  ]
}

/** The Loyiha section's finds: a task opens on its page, a doc in the docs tree. */
export function pmResultItems(results: PmSearchResults | undefined): PaletteItem[] {
  if (!results) return []
  return [
    ...results.tasks.map((task) => ({
      key: `task:${task.key}`,
      section: 'Tasklar' as const,
      title: `${task.key} · ${task.title}`,
      to: `/pm/${task.key}`,
      art: 'ic3d_target' as const,
    })),
    ...results.docs.map((doc) => ({
      key: `doc:${doc.id}`,
      section: 'Hujjatlar' as const,
      title: doc.title,
      subtitle: doc.snippet,
      to: `/pm/docs/${doc.id}`,
      art: 'ic3d_notebook' as const,
    })),
  ]
}

/** Arrow keys wrap around the list; an empty list has nowhere to go. */
export function moveIndex(current: number, delta: number, length: number): number {
  if (length === 0) return 0
  return (current + delta + length) % length
}
