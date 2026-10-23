import type { PmPriority, PmStatus, PmTaskEvent, PmTaskSummary, PmTaskType } from './types'

/*
 * The board's words and its small arithmetic. The server keeps the rules — which fields,
 * who may finish a task — and this file only names things and works out where a dropped
 * card goes.
 */

export const statusOrder: PmStatus[] = ['backlog', 'todo', 'in_progress', 'review', 'done']

export const statusLabels: Record<PmStatus, string> = {
  backlog: 'Backlog',
  todo: 'Navbatda',
  in_progress: 'Jarayonda',
  review: "Ko'rib chiqish",
  done: 'Tayyor',
}

export const typeLabels: Record<PmTaskType, { text: string; tone: string }> = {
  bug: { text: 'Bug', tone: 'danger' },
  feature: { text: 'Feature', tone: 'premium' },
  task: { text: 'Ish', tone: 'free' },
}

export const typeOrder: PmTaskType[] = ['feature', 'bug', 'task']

export const priorityLabels: Record<PmPriority, { text: string; tone: string }> = {
  critical: { text: 'Kritik', tone: 'danger' },
  high: { text: 'Yuqori', tone: 'warn' },
  medium: { text: "O'rta", tone: 'free' },
  low: { text: 'Past', tone: 'free' },
}

export const priorityOrder: PmPriority[] = ['critical', 'high', 'medium', 'low']

/** The fixed component keys the server accepts, in its order. */
export const componentLabels: Record<string, string> = {
  android: 'Ilova (Android)',
  ios: 'Ilova (iOS)',
  doctor_app: 'Shifokor ilovasi',
  backend: 'Backend',
  admin: 'Admin panel',
  doctor_admin: 'Shifokor paneli',
  landing: 'Landing',
  infra: 'CI/Infra',
  design: 'Dizayn',
}

export const componentLabel = (key: string) => componentLabels[key] ?? key

/** Where a card lands: halfway between its neighbours, or one step past the only one. */
export function positionBetween(before: number | undefined, after: number | undefined): number {
  if (before !== undefined && after !== undefined) return (before + after) / 2
  if (before !== undefined) return before + 1
  if (after !== undefined) return after - 1
  return 1
}

/** Every column, top to bottom. */
export function columns(tasks: PmTaskSummary[]): Record<PmStatus, PmTaskSummary[]> {
  const grouped = Object.fromEntries(statusOrder.map((status) => [status, [] as PmTaskSummary[]])) as Record<PmStatus, PmTaskSummary[]>
  for (const task of tasks) grouped[task.status].push(task)
  for (const status of statusOrder) grouped[status].sort((a, b) => a.position - b.position || a.key.localeCompare(b.key))
  return grouped
}

/**
 * The position for [draggedKey] dropped at slot [index] of [column] (0 = top). Slots are
 * counted with the dragged card still in place, the way the column looked under the cursor.
 */
export function dropPosition(column: PmTaskSummary[], draggedKey: string, index: number): number {
  const before = column.slice(0, index).filter((task) => task.key !== draggedKey).at(-1)
  const after = column.slice(index).find((task) => task.key !== draggedKey)
  return positionBetween(before?.position, after?.position)
}

/** "2/3" when a task has subtasks; nothing otherwise. */
export function subtaskProgress(task: Pick<PmTaskSummary, 'subtasks' | 'subtasksDone'>): string | null {
  return task.subtasks > 0 ? `${task.subtasksDone}/${task.subtasks}` : null
}

/** Is the due date before today, for a task not yet done? */
export function overdue(task: Pick<PmTaskSummary, 'dueDate' | 'status'>, today: Date = new Date()): boolean {
  if (!task.dueDate || task.status === 'done') return false
  const local = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return new Date(`${task.dueDate}T00:00:00`) < local
}

/** One line of a task's history, after the actor's name. */
export function describeEvent(event: PmTaskEvent, nameOf: (id: string) => string): string {
  switch (event.kind) {
    case 'created':
      return `yaratdi (${statusLabels[event.to as PmStatus] ?? event.to})`
    case 'status':
      return `${statusLabels[event.from as PmStatus] ?? event.from} → ${statusLabels[event.to as PmStatus] ?? event.to}`
    case 'assignee':
      return event.to ? `ijrochi: ${nameOf(event.to)}` : 'ijrochini olib tashladi'
    case 'archived':
      return 'arxivladi'
    case 'restored':
      return 'arxivdan qaytardi'
    default:
      return event.kind
  }
}
