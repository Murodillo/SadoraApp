/**
 * The arithmetic behind the charts and the stat tiles, kept out of the components so
 * it can be tested without rendering anything.
 */

/**
 * Change from one period to the next, as a fraction. `null` when there is nothing to
 * compare against — a rise from zero is not "infinity percent", it is a first.
 */
export function percentChange(current: number, previous: number): number | null {
  if (previous === 0) return null
  return (current - previous) / previous
}

/** `1234567` → `1.2M`, `12345` → `12.3K`, `999` → `999`. For axes and tile hints. */
export function compact(value: number): string {
  const abs = Math.abs(value)
  if (abs >= 1_000_000) return `${trimZero((value / 1_000_000).toFixed(1))}M`
  if (abs >= 10_000) return `${trimZero((value / 1_000).toFixed(0))}K`
  if (abs >= 1_000) return `${trimZero((value / 1_000).toFixed(1))}K`
  return String(value)
}

function trimZero(text: string): string {
  return text.endsWith('.0') ? text.slice(0, -2) : text
}

/** Tiyin to "299 000" — the panel never shows a minor unit to a person. */
export function som(minor: number): string {
  return Math.round(minor / 100).toLocaleString('ru-RU')
}

/** `2026-09-22` → `22.09`, the axis label for a day. */
export function shortDay(iso: string): string {
  const [, month, day] = iso.split('-')
  return month && day ? `${day}.${month}` : iso
}

/**
 * Which points along a series get an axis label: the first, the last, and evenly spaced
 * ones between, at most `max` in total. Labelling every day of a 90-day series would
 * turn the axis into a grey bar.
 */
export function labelIndexes(length: number, max = 6): number[] {
  if (length <= max) return Array.from({ length }, (_, index) => index)
  const step = (length - 1) / (max - 1)
  const picked = new Set<number>()
  for (let index = 0; index < max; index += 1) picked.add(Math.round(index * step))
  return [...picked].sort((a, b) => a - b)
}

/**
 * A "nice" ceiling for an axis: the smallest of 1, 2, 5 × 10ⁿ that is ≥ the peak,
 * so the top gridline is a round number rather than 37.
 */
export function niceCeiling(peak: number): number {
  if (peak <= 0) return 1
  const magnitude = Math.pow(10, Math.floor(Math.log10(peak)))
  for (const step of [1, 2, 2.5, 5, 10]) {
    const candidate = step * magnitude
    if (candidate >= peak) return candidate
  }
  return 10 * magnitude
}

/** Share of a part in a whole, as a whole-number percentage; 0 when the whole is 0. */
export function share(part: number, whole: number): number {
  return whole > 0 ? Math.round((part / whole) * 100) : 0
}

/** A simple moving average, so the trend line can be drawn under the daily noise. */
export function movingAverage(values: number[], window = 7): number[] {
  return values.map((_, index) => {
    const start = Math.max(0, index - window + 1)
    const slice = values.slice(start, index + 1)
    return slice.reduce((sum, value) => sum + value, 0) / slice.length
  })
}
