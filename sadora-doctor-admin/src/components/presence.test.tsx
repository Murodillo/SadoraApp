import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LEAVE_MS, usePresence, withLeaving } from './presence'
import type { Presence } from './presence'

const key = (item: string) => item
const shown = (...items: string[]): Presence<string>[] =>
  items.map((item) => ({ item: item.replace('*', ''), leaving: item.endsWith('*') }))
const drawn = (list: Presence<string>[]) => list.map((entry) => `${entry.item}${entry.leaving ? '*' : ''}`)

describe('withLeaving', () => {
  it('keeps a removed item where it was, marked leaving', () => {
    expect(drawn(withLeaving(shown('a', 'b', 'c'), ['a', 'c'], key))).toEqual(['a', 'b*', 'c'])
  })

  it('puts new items where the server has them and leaves the gone ones after their neighbour', () => {
    expect(drawn(withLeaving(shown('a', 'b', 'c'), ['d', 'a', 'c'], key))).toEqual(['d', 'a', 'b*', 'c'])
  })

  it('keeps an item that was first on the list at the top while it leaves', () => {
    expect(drawn(withLeaving(shown('a', 'b'), ['b'], key))).toEqual(['a*', 'b'])
  })

  it('lets the last item leave rather than jumping to an empty list', () => {
    expect(drawn(withLeaving(shown('a'), [], key))).toEqual(['a*'])
  })

  it('brings back an item that returns while it was leaving', () => {
    expect(drawn(withLeaving(shown('a*', 'b'), ['a', 'b'], key))).toEqual(['a', 'b'])
  })

  it('draws the list as it is when nothing was drawn before', () => {
    expect(drawn(withLeaving([], ['a', 'b'], key))).toEqual(['a', 'b'])
  })
})

describe('usePresence', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('holds a removed item for its exit, then drops it', () => {
    const { result, rerender } = renderHook(({ items }) => usePresence(items, key), {
      initialProps: { items: ['a', 'b'] as string[] | undefined },
    })
    expect(drawn(result.current)).toEqual(['a', 'b'])

    rerender({ items: ['b'] })
    expect(drawn(result.current)).toEqual(['a*', 'b'])

    act(() => {
      vi.advanceTimersByTime(LEAVE_MS)
    })
    expect(drawn(result.current)).toEqual(['b'])
  })

  it('draws the first data at once, never an empty frame', () => {
    const { result, rerender } = renderHook(({ items }) => usePresence(items, key), {
      initialProps: { items: undefined as string[] | undefined },
    })
    expect(result.current).toEqual([])
    rerender({ items: ['a'] })
    expect(drawn(result.current)).toEqual(['a'])
  })

  it('draws another list afresh rather than letting the old rows leave', () => {
    const { result, rerender } = renderHook(({ items, resetKey }) => usePresence(items, key, { resetKey }), {
      initialProps: { items: ['a', 'b', 'c'] as string[] | undefined, resetKey: 'all' },
    })
    // A cached filter answers at once: b and c are not gone, they are another list.
    rerender({ items: ['b'], resetKey: 'pregnancy' })
    expect(drawn(result.current)).toEqual(['b'])
    // And a list still loading has nothing to animate.
    rerender({ items: undefined, resetKey: 'body' })
    expect(result.current).toEqual([])
  })

  it('skips the exit when reduced motion is asked for', () => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('reduce'),
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }))
    const { result, rerender } = renderHook(({ items }) => usePresence(items, key), {
      initialProps: { items: ['a', 'b'] },
    })
    rerender({ items: ['b'] })
    expect(drawn(result.current)).toEqual(['b'])
  })
})
