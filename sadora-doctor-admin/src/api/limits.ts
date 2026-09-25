/**
 * How long a field may be. These mirror `uz.sadora.contract.Limits` — the server refuses
 * what is outside them, and the panel should not let a doctor type it in the first place.
 * TypeScript cannot read the Kotlin object, so the numbers are written down in both
 * places under the same names; change one and change the other.
 */
export const limits = {
  /** Posts and comments share the minimum: `CommunityService.MIN_BODY_LENGTH`. */
  postMin: 2,
  postMax: 2000,
  commentMax: 1000,
  doctorWorkplaceMax: 160,
  doctorBioMax: 500,
} as const

/**
 * Why a trimmed text cannot be sent, in the words the server would use, or null when it
 * can. The server trims too, so a body of spaces is as empty there as it is here.
 */
export function lengthProblem(text: string, min: number, max: number): string | null {
  const length = text.trim().length
  if (length < min) return min <= 1 ? "To'ldirilishi shart" : `Kamida ${min} ta belgi`
  if (length > max) return `Eng ko'pi ${max} belgi`
  return null
}
