import { describe, expect, it } from 'vitest'
import { accept, display, format, isIncomplete, isValid, NATIONAL_LENGTH, OPERATOR_CODES, parse, toE164 } from './phone'

/*
 * A port of `UzbekPhoneTest` in sadora-backend/contract, case for case, so the panel
 * refuses exactly what the server refuses. The last block covers what only the panel has.
 */
describe('UzbekPhone (contract port)', () => {
  it('the field never holds more than nine digits', () => {
    expect(accept('9012345678901234')).toBe('901234567')
    expect(accept('90 123 45 67 89')).toBe('901234567')
    expect(accept('1234567890123456789')).toHaveLength(NATIONAL_LENGTH)
  })

  it('a pasted number loses its country code but a real 99 code does not', () => {
    expect(accept('+998 90 123 45 67')).toBe('901234567')
    expect(accept('998901234567')).toBe('901234567')
    expect(accept('99')).toBe('99')
    expect(accept('998')).toBe('998')
    expect(accept('998123456')).toBe('998123456')
  })

  it('the mask groups the digits as they arrive', () => {
    expect(format('')).toBe('')
    expect(format('9')).toBe('9')
    expect(format('90')).toBe('90')
    expect(format('901')).toBe('90 1')
    expect(format('90123')).toBe('90 123')
    expect(format('901234')).toBe('90 123 4')
    expect(format('9012345')).toBe('90 123 45')
    expect(format('901234567')).toBe('90 123 45 67')
    // Re-formatting what is already formatted must not move anything.
    expect(format('90 123 45 67')).toBe('90 123 45 67')
  })

  it('a complete number behind a real operator code is valid', () => {
    for (const code of OPERATOR_CODES) {
      expect(isValid(`${code}1234567`), `rejected: ${code}`).toBe(true)
      expect(toE164(`${code}1234567`)).toBe(`+998${code}1234567`)
    }
  })

  it.each([
    ['', 'nothing'],
    ['90123456', 'eight digits'],
    ['9012345678', 'ten'],
    ['9012345678901234', 'a paste that went wrong'],
    ['70 123 45 67', 'no operator uses 70'],
    ['71 123 45 67', 'a Tashkent landline; the code comes by SMS'],
    ['10 123 45 67', 'no such code'],
  ])('%j is not a number (%s)', (input) => {
    expect(isValid(input)).toBe(false)
    expect(toE164(input)).toBeNull()
  })

  it('a half-typed number is incomplete rather than wrong', () => {
    expect(isIncomplete('90')).toBe(true)
    expect(isIncomplete('90 123 45 6')).toBe(true)
    expect(isIncomplete('90 123 45 67')).toBe(false)
    // Complete but wrong: nine digits, no such operator. That one is worth saying.
    expect(isIncomplete('70 123 45 67')).toBe(false)
    expect(isValid('70 123 45 67')).toBe(false)
  })
})

describe('UzbekPhone (panel only)', () => {
  it('knows the same operator codes as the contract', () => {
    expect([...OPERATOR_CODES].sort()).toEqual(
      ['20', '33', '50', '55', '77', '88', '90', '91', '93', '94', '95', '97', '98', '99'].sort(),
    )
  })

  it('parses the national and the international form alike', () => {
    expect(parse('901234567')).toBe('901234567')
    expect(parse('+998 33 123 45 67')).toBe('331234567')
    expect(parse('+7 901 234 56 78')).toBeNull()
  })

  it('shows a wire number the way she typed it', () => {
    expect(display('+998901234567')).toBe('+998 90 123 45 67')
    expect(display('not a number')).toBe('not a number')
  })
})
