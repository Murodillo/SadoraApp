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
  /** `Limits.MESSAGE_MAX`: a message, or the caption under photos and videos. */
  messageMax: 1000,
  /** `Limits.MESSAGE_MEDIA_MAX`: photos and videos in one consultation message. */
  messageMediaMax: 10,
  /** `Limits.MEDIA_PHOTO_PX`: the longest edge the server keeps of a photo. */
  mediaPhotoPx: 2048,
  /** `Limits.MEDIA_PHOTO_MAX_BYTES` and `MEDIA_VIDEO_MAX_BYTES`: what one upload may weigh. */
  mediaPhotoMaxBytes: 15 * 1024 * 1024,
  mediaVideoMaxBytes: 100 * 1024 * 1024,
  /** `Limits.MEDIA_VIDEO_MAX_SECONDS`. */
  mediaVideoMaxSeconds: 180,
  /** `Limits.MEDIA_POSTER_MAX_BYTES`: a video's poster frame, decoded. */
  mediaPosterMaxBytes: 1_000_000,
  /** `Limits.REPORT_NOTE_MAX`. */
  reportNoteMax: 500,
  /** `Limits.MEDICATION_NAME_MAX` and the `PRESCRIPTION_*` limits. */
  medicationNameMax: 120,
  medicationTimesPerDayMax: 8,
  prescriptionItemsMax: 10,
  prescriptionDoseMax: 40,
  prescriptionUnitMax: 24,
  prescriptionNoteMax: 1000,
  prescriptionItemNoteMax: 300,
  prescriptionStartDayMax: 90,
  prescriptionDaysMax: 365,
  prescriptionCancelReasonMax: 300,
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
