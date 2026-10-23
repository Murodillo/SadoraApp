import { describe, expect, it } from 'vitest'
import type { AdminOperator } from '../api/types'
import { looksLikeEmail, operatorStatus, roleHints, roleLabels, roleOrder } from './operators'

function operator(patch: Partial<AdminOperator> = {}): AdminOperator {
  return {
    id: 'a',
    email: 'a@sadora.uz',
    name: 'Ali',
    role: 'SUPPORT',
    active: true,
    totpEnabled: false,
    passwordChangeRequired: false,
    createdAt: '2026-10-01T00:00:00Z',
    ...patch,
  }
}

const now = new Date('2026-10-20T10:00:00Z')

describe('operatorStatus', () => {
  it('reads a working account as active', () => {
    expect(operatorStatus(operator(), now)).toEqual({ text: 'Faol', tone: 'ok' })
  })

  it('puts a switched-off account first, whatever else is true', () => {
    expect(operatorStatus(operator({ active: false, passwordChangeRequired: true }), now).text).toBe("O'chirilgan")
  })

  it('shows a lockout only while it lasts', () => {
    expect(operatorStatus(operator({ lockedUntil: '2026-10-20T10:05:00Z' }), now).tone).toBe('danger')
    expect(operatorStatus(operator({ lockedUntil: '2026-10-20T09:55:00Z' }), now).text).toBe('Faol')
  })

  it('flags an operator still on the temporary password', () => {
    expect(operatorStatus(operator({ passwordChangeRequired: true }), now)).toEqual({ text: 'Parolini almashtirmagan', tone: 'warn' })
  })
})

describe('roles', () => {
  it('names and explains every role in the picker', () => {
    roleOrder.forEach((role) => {
      expect(roleLabels[role]).toBeTruthy()
      expect(roleHints[role]).toBeTruthy()
    })
    expect(roleOrder).toEqual(['OWNER', 'ADMIN', 'SUPPORT', 'ANALYST', 'DEVELOPER'])
  })
})

describe('looksLikeEmail', () => {
  it('takes an address and refuses a typo', () => {
    expect(looksLikeEmail(' nodira@sadora.uz ')).toBe(true)
    expect(looksLikeEmail('nodira@sadora')).toBe(false)
    expect(looksLikeEmail('nodira sadora.uz')).toBe(false)
  })
})
