import type { AppReleasePolicy } from '../api/types'

/**
 * What a save would stop for people right now, said before it is saved: a minimum raised
 * shuts out every older build at once, and maintenance closes the app for everyone.
 */
export function releaseWarnings(saved: AppReleasePolicy, draft: AppReleasePolicy): string[] {
  const warnings: string[] = []
  for (const [key, name] of [['android', 'Android'], ['ios', 'iOS']] as const) {
    const before = saved[key].minBuild ?? 0
    const after = draft[key].minBuild ?? 0
    if (after > before) warnings.push(`${name}: ${after} dan past build'lar ilovani ochmay, faqat «Yangilang» ekranini ko'radi.`)
    if (after > 0 && !draft[key].storeUrl?.trim()) warnings.push(`${name}: do'kon havolasi yo'q — «Yangilash» tugmasi hech qayerga olib bormaydi.`)
  }
  if (draft.maintenance.active && !saved.maintenance.active) {
    warnings.push("Texnik ishlar yoqiladi: ilova hamma uchun yopiladi va faqat xabar ko'rinadi.")
  }
  return warnings
}
