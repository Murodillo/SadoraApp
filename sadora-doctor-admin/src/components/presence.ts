import { useEffect, useState } from 'react'
import { useReducedMotion } from './motion'

/** An item of a list as it is drawn: present, or on its way out. */
export interface Presence<T> {
  item: T
  leaving: boolean
}

/**
 * Merges the list as it was drawn with the list as the server has it now.
 *
 * Every current item comes in its current order. Every item that has just gone stays,
 * marked `leaving`, right after the item it used to follow — so it can play its exit
 * where the eye last saw it instead of the rows below jumping up at once.
 */
export function withLeaving<T>(shown: readonly Presence<T>[], next: readonly T[], keyOf: (item: T) => string): Presence<T>[] {
  const nextKeys = new Set(next.map(keyOf))
  const result: Presence<T>[] = next.map((item) => ({ item, leaving: false }))
  let anchor = -1
  for (const entry of shown) {
    const key = keyOf(entry.item)
    if (nextKeys.has(key)) {
      anchor = result.findIndex((candidate) => keyOf(candidate.item) === key)
    } else {
      anchor += 1
      result.splice(anchor, 0, { item: entry.item, leaving: true })
    }
  }
  return result
}

/** How long a leaving row stays drawn: the `--standard` duration its exit animation uses. */
export const LEAVE_MS = 300

/**
 * A list that lets its removed items animate out. The merge happens during render, not
 * in an effect, so the frame after new data never draws the old list — or, when the
 * last item goes, an empty state before the exit has played.
 *
 * `resetKey` names which list this is. When it changes — another filter, say — the new
 * list is drawn as it is: rows missing from it were not removed, they belong to a list
 * she is no longer looking at, and must not play an exit. A list still loading has
 * nothing to animate either. With reduced motion asked for, a removed item simply goes.
 */
export function usePresence<T>(
  items: readonly T[] | undefined,
  keyOf: (item: T) => string,
  { resetKey, leaveMs = LEAVE_MS }: { resetKey?: unknown; leaveMs?: number } = {},
): Presence<T>[] {
  const reduced = useReducedMotion()
  const [seen, setSeen] = useState({ items, resetKey })
  const [shown, setShown] = useState<Presence<T>[]>(() => present(items))

  if (items !== seen.items || resetKey !== seen.resetKey) {
    const fresh = reduced || !items || resetKey !== seen.resetKey
    setSeen({ items, resetKey })
    setShown(fresh ? present(items) : withLeaving(shown, items, keyOf))
  }

  const hasLeaving = shown.some((entry) => entry.leaving)
  useEffect(() => {
    if (!hasLeaving) return
    const timer = window.setTimeout(() => setShown((current) => current.filter((entry) => !entry.leaving)), leaveMs)
    return () => window.clearTimeout(timer)
  }, [hasLeaving, shown, leaveMs])

  return shown
}

function present<T>(items: readonly T[] | undefined): Presence<T>[] {
  return (items ?? []).map((item) => ({ item, leaving: false }))
}
