import { describe, expect, it } from 'vitest'
import { emptySegment, localInputToIso, segmentSummary, sumValues, textProblems, toggle, writtenTexts } from './campaigns'

describe('textProblems', () => {
  it('asks for Uzbek first', () => {
    expect(textProblems({})[0]).toBe("O'zbekcha sarlavha va matn kerak")
    expect(textProblems({ uz: { title: 'Salom', body: '' } })).toContain("O'zbekcha sarlavha va matn kerak")
  })

  it('wants both halves of every language started, within the limits', () => {
    expect(textProblems({ uz: { title: 'a', body: 'b' }, ru: { title: 'Привет', body: '' } })).toEqual(['Ruscha: sarlavha va matn birga yozilsin'])
    expect(textProblems({ uz: { title: 'a'.repeat(66), body: 'b' } })).toEqual(["O'zbekcha: sarlavha 65 belgidan oshmasin"])
    expect(textProblems({ uz: { title: 'a', body: 'b' }, en: { title: '', body: '' } })).toEqual([])
  })
})

describe('writtenTexts', () => {
  it('sends only the languages actually written, trimmed', () => {
    expect(writtenTexts({ uz: { title: ' Salom ', body: 'Matn ' }, ru: { title: '', body: ' ' } })).toEqual({ uz: { title: 'Salom', body: 'Matn' } })
  })
})

describe('segment helpers', () => {
  it('toggles a value and keeps the options order', () => {
    expect(toggle(['pregnancy'], 'cycle', ['cycle', 'pregnancy', 'menopause'])).toEqual(['cycle', 'pregnancy'])
    expect(toggle(['cycle', 'pregnancy'], 'cycle', ['cycle', 'pregnancy'])).toEqual(['pregnancy'])
  })

  it('says the segment in one line, with empty parts as "all"', () => {
    expect(segmentSummary(emptySegment)).toBe('Ayollar · Barcha bosqichlar · Barcha tillar · Hamma tariflar')
    expect(segmentSummary({ lifeStages: ['pregnancy'], languages: ['ru'], tier: 'premium', accountKinds: ['self', 'partner'] })).toBe(
      'Ayollar va Yaqinimlar · Homiladorlik · Ruscha · Premium',
    )
  })

  it('reads a datetime-local value and sums counts', () => {
    expect(localInputToIso('')).toBeNull()
    expect(localInputToIso('2026-10-21T09:30')).toBe(new Date('2026-10-21T09:30').toISOString())
    expect(sumValues({ a: 2, b: 3 })).toBe(5)
    expect(sumValues(undefined)).toBe(0)
  })
})
