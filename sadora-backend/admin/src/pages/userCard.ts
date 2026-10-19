import type { AuditEntry } from '../api/types'

export const partnerStatusLabels: Record<string, string> = {
  invited: 'Taklif yuborilgan',
  pending: 'Tasdiq kutilmoqda',
  active: 'Ulangan',
  paused: "To'xtatilgan",
}

export const relationLabels: Record<string, string> = {
  husband: "Turmush o'rtog'i",
  mother: 'Onasi',
  sister: 'Opa-singlisi',
  friend: "Do'sti",
  other: 'Boshqa',
}

export const wearableStatusLabels: Record<string, { text: string; tone: string }> = {
  active: { text: 'Ishlayapti', tone: 'ok' },
  expired: { text: 'Kirish muddati tugagan', tone: 'warn' },
  error: { text: 'Xato', tone: 'danger' },
}

const providerNames: Record<string, string> = {
  oura: 'Oura',
  garmin: 'Garmin',
  whoop: 'WHOOP',
  fitbit: 'Fitbit',
  huawei: 'Huawei',
}

export function providerName(provider: string): string {
  return providerNames[provider] ?? provider.charAt(0).toUpperCase() + provider.slice(1)
}

/** A run of the same event, newest first: five sign-ins in a row read as one line. */
export interface TimelineRun {
  key: string
  action: string
  actorType: AuditEntry['actorType']
  count: number
  /** The newest event of the run. */
  latest: string
  /** The oldest event of the run; equal to [latest] for a single one. */
  earliest: string
  /** Only one event can carry a reason or an actor; a run of several shows neither. */
  entry: AuditEntry
}

/**
 * Folds consecutive events with the same action and actor type. The list arrives newest
 * first and stays so; nothing is reordered, only neighbours merge.
 */
export function collapseTimeline(entries: AuditEntry[]): TimelineRun[] {
  const runs: TimelineRun[] = []
  for (const entry of entries) {
    const last = runs[runs.length - 1]
    if (last && last.action === entry.action && last.actorType === entry.actorType) {
      last.count += 1
      last.earliest = entry.createdAt
      continue
    }
    runs.push({
      key: entry.id,
      action: entry.action,
      actorType: entry.actorType,
      count: 1,
      latest: entry.createdAt,
      earliest: entry.createdAt,
      entry,
    })
  }
  return runs
}

export const actorLabels: Record<AuditEntry['actorType'], string> = {
  user: "O'zi",
  admin: 'Operator',
  system: 'Tizim',
}
