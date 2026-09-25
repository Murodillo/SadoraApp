import { describe, expect, it } from 'vitest'
import { compact, labelIndexes, movingAverage, niceCeiling, percentChange, share, shortDay, som } from './analytics'

describe('percentChange', () => {
  it('is a fraction of the previous value', () => {
    expect(percentChange(120, 100)).toBeCloseTo(0.2)
    expect(percentChange(50, 100)).toBeCloseTo(-0.5)
    expect(percentChange(100, 100)).toBe(0)
  })

  it('has nothing to say when the previous period was empty', () => {
    // A rise from zero is a first, not an infinite increase.
    expect(percentChange(7, 0)).toBeNull()
    expect(percentChange(0, 0)).toBeNull()
  })
})

describe('compact', () => {
  it('shortens thousands and millions and leaves small numbers alone', () => {
    expect(compact(0)).toBe('0')
    expect(compact(999)).toBe('999')
    expect(compact(1000)).toBe('1K')
    expect(compact(1250)).toBe('1.3K')
    expect(compact(12_345)).toBe('12K')
    expect(compact(1_500_000)).toBe('1.5M')
  })
})

describe('som', () => {
  it('drops the tiyin and groups the thousands', () => {
    expect(som(29_900_000).replace(/ /g, ' ')).toBe('299 000')
    expect(som(0)).toBe('0')
  })
})

describe('shortDay', () => {
  it('keeps the day and the month', () => {
    expect(shortDay('2026-09-22')).toBe('22.09')
    expect(shortDay('nonsense')).toBe('nonsense')
  })
})

describe('labelIndexes', () => {
  it('labels every point of a short series', () => {
    expect(labelIndexes(4)).toEqual([0, 1, 2, 3])
  })

  it('always keeps the first and the last and spaces the rest', () => {
    const picked = labelIndexes(90)
    expect(picked[0]).toBe(0)
    expect(picked[picked.length - 1]).toBe(89)
    expect(picked.length).toBeLessThanOrEqual(6)
    expect([...picked].sort((a, b) => a - b)).toEqual(picked)
  })
})

describe('niceCeiling', () => {
  it('rounds the axis top to 1, 2, 2.5, 5 or 10 times a power of ten', () => {
    expect(niceCeiling(0)).toBe(1)
    expect(niceCeiling(3)).toBe(5)
    expect(niceCeiling(37)).toBe(50)
    expect(niceCeiling(50)).toBe(50)
    expect(niceCeiling(210)).toBe(250)
    expect(niceCeiling(999)).toBe(1000)
  })
})

describe('share', () => {
  it('is a whole percentage and never divides by zero', () => {
    expect(share(1, 3)).toBe(33)
    expect(share(2, 3)).toBe(67)
    expect(share(0, 0)).toBe(0)
    expect(share(5, 0)).toBe(0)
  })
})

describe('movingAverage', () => {
  it('averages the window that exists so far, so the first days are not empty', () => {
    expect(movingAverage([2, 4, 6], 2)).toEqual([2, 3, 5])
    expect(movingAverage([1, 1, 1, 1], 7)).toEqual([1, 1, 1, 1])
  })
})
