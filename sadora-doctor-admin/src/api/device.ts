import { version } from '../../package.json'
import type { DeviceInfo } from './types'

const DEVICE_KEY = 'sadora.doctor.deviceId'

/** The one the sign-in went out with, when storage refuses to keep it. */
let fallbackId: string | null = null

/**
 * A random id for this browser, kept in `localStorage` so every sign-in from it lands on
 * the same device row — support can then answer "which computer is this?" the way it
 * does for a phone. It is not a secret and opens nothing, which is why it may outlive
 * the tab when the tokens may not.
 */
export function deviceId(): string {
  try {
    const stored = localStorage.getItem(DEVICE_KEY)
    if (stored) return stored
    const created = randomId()
    localStorage.setItem(DEVICE_KEY, created)
    return created
  } catch {
    // A locked-down browser without storage still signs in; it is simply a new device each time.
    fallbackId ??= randomId()
    return fallbackId
  }
}

/** What every sign-in sends about this browser. */
export function deviceInfo(): DeviceInfo {
  return {
    deviceId: deviceId(),
    platform: 'web',
    appVersion: `doctor-web ${version}`,
    timezone: timezone(),
  }
}

function timezone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined
  } catch {
    return undefined
  }
}

/**
 * A v4 UUID. `crypto.randomUUID` exists only in a secure context, and the panel opened
 * over plain http on a clinic's LAN address is not one; `getRandomValues` works in both.
 */
export function randomId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
