import { describe, expect, it } from 'vitest'
import type { AdminDoctorQuality } from '../api/types'
import {
  canEditCommission,
  canManageMoney,
  canMarkRefunded,
  firstReplyMinutes,
  formatMinutes,
  formatSom,
  nextSort,
  parsePercent,
  parseSomToMinor,
  sortQuality,
} from './consultations'

describe('formatSom', () => {
  it('turns tiyin into grouped so‘m', () => {
    expect(formatSom(5_000_000)).toBe("50 000 so'm")
    expect(formatSom(0)).toBe("0 so'm")
    expect(formatSom(123_456_789_00)).toBe("123 456 789 so'm")
  })

  it('shows tiyin only when there are some', () => {
    expect(formatSom(1_234_550)).toBe("12 345,50 so'm")
    expect(formatSom(105)).toBe("1,05 so'm")
  })

  it('handles a negative balance and a missing amount', () => {
    expect(formatSom(-250_000)).toBe("−2 500 so'm")
    expect(formatSom(undefined)).toBe('—')
    expect(formatSom(Number.NaN)).toBe('—')
  })
})

describe('parseSomToMinor', () => {
  it('reads what an operator types', () => {
    expect(parseSomToMinor('50000')).toBe(5_000_000)
    expect(parseSomToMinor('50 000')).toBe(5_000_000)
    expect(parseSomToMinor(' 1 250,5 ')).toBe(125_050)
    expect(parseSomToMinor('10.05')).toBe(1_005)
  })

  it('refuses zero, negatives and junk', () => {
    expect(parseSomToMinor('')).toBeNull()
    expect(parseSomToMinor('0')).toBeNull()
    expect(parseSomToMinor('-100')).toBeNull()
    expect(parseSomToMinor('12abc')).toBeNull()
    expect(parseSomToMinor('1,234')).toBeNull()
  })
})

describe('parsePercent', () => {
  it('takes a whole percent from 0 to 100', () => {
    expect(parsePercent('0')).toBe(0)
    expect(parsePercent(' 20 ')).toBe(20)
    expect(parsePercent('100')).toBe(100)
  })

  it('refuses anything else', () => {
    expect(parsePercent('101')).toBeNull()
    expect(parsePercent('-1')).toBeNull()
    expect(parsePercent('12.5')).toBeNull()
    expect(parsePercent('')).toBeNull()
  })
})

describe('roles', () => {
  it('lets Owner and Admin move money, and Support read only', () => {
    expect(canManageMoney('OWNER')).toBe(true)
    expect(canManageMoney('ADMIN')).toBe(true)
    expect(canManageMoney('SUPPORT')).toBe(false)
    expect(canManageMoney(undefined)).toBe(false)
  })

  it('keeps the commission the Owner’s', () => {
    expect(canEditCommission('OWNER')).toBe(true)
    expect(canEditCommission('ADMIN')).toBe(false)
  })

  it('marks a refund only on money still held', () => {
    expect(canMarkRefunded('refund_due', 'ADMIN')).toBe(true)
    expect(canMarkRefunded('paid', 'OWNER')).toBe(true)
    expect(canMarkRefunded('refunded', 'OWNER')).toBe(false)
    expect(canMarkRefunded('refund_due', 'SUPPORT')).toBe(false)
  })
})

describe('first reply', () => {
  it('counts whole minutes from the window opening', () => {
    expect(firstReplyMinutes('2026-09-30T10:00:00Z', '2026-09-30T10:12:40Z')).toBe(13)
    expect(firstReplyMinutes('2026-09-30T10:00:00Z', null)).toBeNull()
    expect(firstReplyMinutes('2026-09-30T10:00:00Z', '2026-09-30T09:00:00Z')).toBeNull()
  })

  it('reads as minutes or hours', () => {
    expect(formatMinutes(7)).toBe('7 daq')
    expect(formatMinutes(60)).toBe('1 soat')
    expect(formatMinutes(75)).toBe('1 soat 15 daq')
    expect(formatMinutes(null)).toBe('—')
  })
})

function doctor(fullName: string, patch: Partial<AdminDoctorQuality> = {}): AdminDoctorQuality {
  return {
    doctorId: fullName,
    fullName,
    specialty: 'gynecologist',
    status: 'approved',
    priceMinor: 0,
    busy: false,
    onlineNow: false,
    consultationsTotal: 0,
    consultationsMonth: 0,
    openNow: 0,
    unansweredTotal: 0,
    ratingCount: 0,
    grossMinor: 0,
    netMinor: 0,
    paidOutMinor: 0,
    balanceMinor: 0,
    refundDueMinor: 0,
    ...patch,
  }
}

describe('sortQuality', () => {
  const rows = [
    doctor('Bahor', { rating: 4.2, balanceMinor: 100, avgFirstReplyMinutes: 30 }),
    doctor('Aziza', { rating: null, balanceMinor: 500 }),
    doctor('Dilnoza', { rating: 4.9, balanceMinor: 100, avgFirstReplyMinutes: 5 }),
  ]

  it('sorts by a number either way, ties by name', () => {
    expect(sortQuality(rows, { key: 'balanceMinor', descending: true }).map((row) => row.fullName)).toEqual([
      'Aziza',
      'Bahor',
      'Dilnoza',
    ])
    expect(sortQuality(rows, { key: 'balanceMinor', descending: false }).map((row) => row.fullName)).toEqual([
      'Bahor',
      'Dilnoza',
      'Aziza',
    ])
  })

  it('puts a missing value last in both directions', () => {
    expect(sortQuality(rows, { key: 'rating', descending: true }).map((row) => row.fullName)).toEqual([
      'Dilnoza',
      'Bahor',
      'Aziza',
    ])
    expect(sortQuality(rows, { key: 'avgFirstReplyMinutes', descending: false }).map((row) => row.fullName)).toEqual([
      'Dilnoza',
      'Bahor',
      'Aziza',
    ])
  })

  it('does not reorder the array it was given', () => {
    sortQuality(rows, { key: 'fullName', descending: false })
    expect(rows.map((row) => row.fullName)).toEqual(['Bahor', 'Aziza', 'Dilnoza'])
  })

  it('flips the sorted column and starts a new one in its own direction', () => {
    expect(nextSort({ key: 'rating', descending: true }, 'rating')).toEqual({ key: 'rating', descending: false })
    expect(nextSort({ key: 'rating', descending: true }, 'fullName')).toEqual({ key: 'fullName', descending: false })
    expect(nextSort({ key: 'fullName', descending: false }, 'avgFirstReplyMinutes')).toEqual({
      key: 'avgFirstReplyMinutes',
      descending: false,
    })
  })
})
