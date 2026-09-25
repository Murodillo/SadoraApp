import { describe, expect, it, vi } from 'vitest'
import { deviceId, deviceInfo, randomId } from './device'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

describe('device', () => {
  it('makes one id per browser and keeps it', () => {
    const first = deviceId()
    expect(first).toMatch(UUID)
    expect(deviceId()).toBe(first)
    expect(localStorage.getItem('sadora.doctor.deviceId')).toBe(first)
  })

  it('describes itself as the web panel', () => {
    const info = deviceInfo()
    expect(info.platform).toBe('web')
    expect(info.deviceId).toBe(deviceId())
    expect(info.appVersion).toMatch(/^doctor-web \d+\.\d+\.\d+/)
    expect(typeof info.timezone).toBe('string')
  })

  it('still makes a v4 id where randomUUID is missing (plain http)', () => {
    const own = Object.getOwnPropertyDescriptor(crypto, 'randomUUID')
    // Shadow the prototype's method the way an insecure context leaves it: absent.
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true })
    const getRandomValues = vi.spyOn(crypto, 'getRandomValues')
    try {
      expect(randomId()).toMatch(UUID)
      expect(getRandomValues).toHaveBeenCalledOnce()
    } finally {
      if (own) Object.defineProperty(crypto, 'randomUUID', own)
      else delete (crypto as { randomUUID?: unknown }).randomUUID
    }
  })
})
