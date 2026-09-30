import { describe, expect, it } from 'vitest'
import type { QuickReply } from './types'
import {
  durationLabel,
  endFromInput,
  endToInput,
  formatSom,
  groupDigits,
  hoursProblem,
  insertReply,
  matchQuickReplies,
  minutesToTime,
  parseSom,
  priceProblem,
  ratingLabel,
  slashQuery,
  timeToMinutes,
} from './work'

const reply = (id: string, title: string, body: string, position = 0): QuickReply => ({ id, title, body, position })

describe('money', () => {
  it("writes tiyin as so'm with plain-space groups", () => {
    expect(formatSom(5_000_000)).toBe("50 000 so'm")
    expect(formatSom(0)).toBe("0 so'm")
    expect(formatSom(123_456_789)).toBe("1 234 567,89 so'm")
    expect(formatSom(-750_000)).toBe("−7 500 so'm")
    expect(formatSom(undefined)).toBe("0 so'm")
    expect(groupDigits(2_000_000)).toBe('2 000 000')
  })

  it('reads the price box and says what is wrong with it', () => {
    expect(parseSom('50 000')).toBe(50_000)
    expect(parseSom('0')).toBe(0)
    expect(parseSom('')).toBeNull()
    expect(parseSom('12a')).toBeNull()
    expect(priceProblem(0)).toBeNull()
    expect(priceProblem(1_000)).toBeNull()
    expect(priceProblem(2_000_000)).toBeNull()
    expect(priceProblem(999)).toMatch(/1 000–2 000 000/)
    expect(priceProblem(2_000_001)).not.toBeNull()
    expect(priceProblem(null)).toBe('Faqat raqam kiriting')
  })
})

describe('hours', () => {
  it('turns minutes into a time and back', () => {
    expect(minutesToTime(540)).toBe('09:00')
    expect(minutesToTime(1095)).toBe('18:15')
    expect(timeToMinutes('09:30')).toBe(570)
    expect(timeToMinutes('24:00')).toBe(1440)
    expect(timeToMinutes('25:00')).toBeNull()
    expect(timeToMinutes('nine')).toBeNull()
  })

  it('reads an end of 00:00 as midnight at the end of the day', () => {
    expect(endToInput(1440)).toBe('00:00')
    expect(endFromInput('00:00')).toBe(1440)
    expect(endFromInput('18:00')).toBe(1080)
    expect(hoursProblem({ weekday: 1, startMinute: 600, endMinute: 540 })).not.toBeNull()
    expect(hoursProblem({ weekday: 1, startMinute: 0, endMinute: 1440 })).toBeNull()
  })

  it('words a duration and a rating', () => {
    expect(durationLabel(null)).toBe('—')
    expect(durationLabel(0)).toBe('1 daqiqadan kam')
    expect(durationLabel(12)).toBe('12 daqiqa')
    expect(durationLabel(65)).toBe('1 soat 5 daqiqa')
    expect(durationLabel(120)).toBe('2 soat')
    expect(durationLabel(60 * 27)).toBe('1 kun 3 soat')
    expect(ratingLabel(4.75)).toBe('4.8')
    expect(ratingLabel(null)).toBe('—')
  })
})

describe('quick replies', () => {
  const replies = [
    reply('a', 'Salomlashish', 'Assalomu alaykum! Savolingizni yozing.', 1),
    reply('b', 'Bel og‘rig‘i', "Yotib dam oling, og'ir ko'tarmang.", 0),
    reply('c', 'Tahlil', "Umumiy qon tahlilini topshiring, salom bilan.", 2),
  ]

  it('opens a search only for a slash at the very start of one line', () => {
    expect(slashQuery('/')).toBe('')
    expect(slashQuery('/sal')).toBe('sal')
    expect(slashQuery('Salom /sal')).toBeNull()
    expect(slashQuery('/sal\nikkinchi')).toBeNull()
  })

  it('lists by position with nothing typed, and titles that start with the query first', () => {
    expect(matchQuickReplies(replies, '').map((r) => r.id)).toEqual(['b', 'a', 'c'])
    expect(matchQuickReplies(replies, 'sal').map((r) => r.id)).toEqual(['a', 'c'])
    expect(matchQuickReplies(replies, 'QON').map((r) => r.id)).toEqual(['c'])
    expect(matchQuickReplies(replies, 'yo‘q')).toEqual([])
  })

  it('puts a reply in place of the slash search, or at the caret with spacing kept', () => {
    expect(insertReply('/sal', 4, 'Assalomu alaykum!')).toEqual({ text: 'Assalomu alaykum!', caret: 17 })
    expect(insertReply('', 0, 'Salom')).toEqual({ text: 'Salom', caret: 5 })
    expect(insertReply('Hurmatli bemor,', 15, 'dam oling.')).toEqual({ text: 'Hurmatli bemor, dam oling.', caret: 26 })
    expect(insertReply('AB', 1, 'x')).toEqual({ text: 'A x B', caret: 3 })
  })
})
