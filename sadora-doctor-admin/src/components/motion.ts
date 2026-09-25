import { useEffect, useRef, useState } from 'react'

/**
 * A number that counts up to its value the first time it is shown, and again whenever
 * it changes. A figure that simply appears reads as decoration; one that is counted
 * reads as a measurement, which is what a stat tile is for.
 */
export function useCountUp(target: number, durationMs = 720): number {
  const reduced = useReducedMotion()
  const [value, setValue] = useState(reduced ? target : 0)
  const from = useRef(reduced ? target : 0)
  useEffect(() => {
    if (reduced) {
      setValue(target)
      from.current = target
      return
    }
    const start = performance.now()
    const begin = from.current
    let frame = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs)
      // The app's Emphasized curve: quick to leave, slow to settle.
      const eased = 1 - Math.pow(1 - t, 3)
      const next = Math.round(begin + (target - begin) * eased)
      setValue(next)
      if (t < 1) frame = requestAnimationFrame(tick)
      else from.current = target
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, durationMs, reduced])
  return value
}

/** Whether the operator asked the OS for less motion. Counting and drawing then skip to the end. */
export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() => matchReduced())
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const query = window.matchMedia('(prefers-reduced-motion: reduce)')
    const handler = () => setReduced(query.matches)
    query.addEventListener?.('change', handler)
    return () => query.removeEventListener?.('change', handler)
  }, [])
  return reduced
}

function matchReduced(): boolean {
  return typeof window !== 'undefined' && Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)
}

/**
 * `true` for one frame after `value` changes — for a class that plays a short
 * "this just updated" animation on a cell, without keeping the class on forever.
 */
export function useJustChanged(value: unknown, ms = 600): boolean {
  const [flag, setFlag] = useState(false)
  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    setFlag(true)
    const timer = window.setTimeout(() => setFlag(false), ms)
    return () => window.clearTimeout(timer)
  }, [value, ms])
  return flag
}
