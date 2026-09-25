/**
 * How long a field may be, and what range a number may sit in.
 *
 * These mirror `uz.sadora.contract.Limits` — the server refuses what is outside them,
 * and the panel should not let an editor type it in the first place. TypeScript cannot
 * read the Kotlin object, so the one thing keeping the two honest is that the numbers
 * are written down in both places under the same names; change one and change the other.
 */
export const limits = {
  article: {
    slugMax: 80,
    titleMax: 160,
    excerptMax: 400,
    personMax: 120,
    disclaimerMax: 500,
    readMinutes: { min: 1, max: 120 },
  },
  /** Every destructive admin action asks why, and the answer goes into the audit log. */
  reasonMax: 500,
} as const

/** A slug is lowercase letters, digits and hyphens — the same rule the server applies. */
export const slugPattern = '[a-z0-9-]+'

/** Strips anything a slug cannot contain, so the field can never hold an invalid one. */
export function acceptSlug(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, '-')
    .replace(/-{2,}/g, '-')
    .slice(0, limits.article.slugMax)
}
