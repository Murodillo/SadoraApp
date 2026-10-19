import { describe, expect, it } from 'vitest'
import type { ServiceHealth } from '../api/types'
import { ago, serviceCopy, sortServices, uptime } from './system'

const now = new Date('2026-10-20T10:00:00Z')

describe('system helpers', () => {
  it('says how long ago, in the panel’s words', () => {
    expect(ago(null, now)).toBe("hali yo'q")
    expect(ago('2026-10-20T09:59:40Z', now)).toBe('hozirgina')
    expect(ago('2026-10-20T09:45:00Z', now)).toBe('15 daq oldin')
    expect(ago('2026-10-20T07:00:00Z', now)).toBe('3 soat oldin')
    expect(ago('2026-10-18T10:00:00Z', now)).toBe('2 kun oldin')
  })

  it('reads the uptime', () => {
    expect(uptime('2026-10-20T09:48:00Z', now)).toBe('12 daq')
    expect(uptime('2026-10-20T07:45:00Z', now)).toBe('2 soat 15 daq')
    expect(uptime('2026-10-17T06:00:00Z', now)).toBe('3 kun 4 soat')
  })

  it('puts the broken services first', () => {
    const service = (key: string, status: ServiceHealth['status']): ServiceHealth => ({ key, status, configured: status !== 'off' })
    expect(sortServices([service('a', 'ok'), service('b', 'off'), service('c', 'error'), service('d', 'warn')]).map((s) => s.key)).toEqual(['c', 'd', 'a', 'b'])
  })

  it('names known services and keeps an unknown one by its key', () => {
    expect(serviceCopy('sms').name).toBe('SMS · Play Mobile')
    expect(serviceCopy('telegram').okLabel).toBeNull()
    expect(serviceCopy('newthing').name).toBe('newthing')
  })
})
