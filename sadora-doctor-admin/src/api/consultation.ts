import type { Conversation } from './types'

/** Open means the server said so and the window has not run out since it did. */
export function consultationOpen(conversation: Conversation, now: number = Date.now()): boolean {
  const consultation = conversation.consultation
  if (!consultation) return !conversation.blocked
  return consultation.open && !consultation.closedAt && Date.parse(consultation.expiresAt) > now
}

/** `5 soat 12 daqiqa` until a moment, never below zero. */
export function timeLeft(until: string, now: number = Date.now()): string {
  const minutes = Math.max(0, Math.ceil((Date.parse(until) - now) / 60_000))
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return hours ? `${hours} soat ${rest} daqiqa` : `${rest} daqiqa`
}
