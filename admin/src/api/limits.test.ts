import { describe, expect, it } from 'vitest'
import { acceptSlug, limits, slugPattern } from './limits'

describe('acceptSlug', () => {
  it('lowercases and replaces what a slug cannot hold', () => {
    expect(acceptSlug('Hayz Davri: 5 Savol!')).toBe('hayz-davri-5-savol-')
  })

  it('collapses runs of hyphens', () => {
    expect(acceptSlug('a---b  c')).toBe('a-b-c')
  })

  it('never exceeds the server limit', () => {
    expect(acceptSlug('x'.repeat(500))).toHaveLength(limits.article.slugMax)
  })

  it('always produces something the server pattern accepts', () => {
    const pattern = new RegExp(`^${slugPattern}$`)
    for (const raw of ['Ўзбек тили', 'Привет мир', 'emoji 🌸 slug', 'UPPER_case.dots']) {
      expect(acceptSlug(raw)).toMatch(pattern)
    }
  })
})
