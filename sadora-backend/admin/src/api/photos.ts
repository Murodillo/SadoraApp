/**
 * The admin photo paths the server hands out — `/v1/admin/doctors/{id}/photo?v=…` — are
 * fetched with the operator's bearer token, so only a path on the panel's own API is
 * accepted. Anything else (an absolute URL, a protocol-relative `//host`, a blank) is
 * treated as "no photo" and the initials stand in.
 */
export function adminPhotoPath(photoUrl: string | null | undefined): string | null {
  const path = photoUrl?.trim()
  if (!path || !path.startsWith('/v1/') || path.startsWith('//')) return null
  return path
}
