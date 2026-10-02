import { useEffect, useState } from 'react'
import { requestBlob } from './client'

/**
 * Profile photos, fetched with her token and kept as object URLs.
 *
 * Every photo endpoint wants the `Authorization` header — a patient's photo belongs to
 * one consultation, and even a doctor's public one is served to signed-in people only —
 * so an `<img src>` pointing at the API would be refused. The bytes are fetched through
 * the client instead (which renews the token on a 401 like any other call) and shown
 * through a `blob:` URL.
 *
 * The cache is keyed by the photo's URL. That is safe because the server versions every
 * photo URL with `?v=<upload time>`: a new photo is a new key, never a stale hit. A photo
 * shown in three places at once (the list row, the chat header, the patient panel) is
 * fetched once. Entries are counted by who shows them; one nobody shows any more waits in
 * a short idle line, so going back to a page does not fetch it again, and its object URL
 * is revoked when it falls off the end of that line — or at once when the session ends.
 */

interface Entry {
  /** Resolves to the object URL, or null when the photo could not be had. */
  promise: Promise<string | null>
  objectUrl: string | null
  refs: number
}

/** How many photos nobody is showing are kept before the oldest is revoked. */
export const IDLE_PHOTOS = 40

const entries = new Map<string, Entry>()
/** Unshown entries, oldest first. */
const idle: string[] = []

function load(url: string): Entry {
  const entry: Entry = { promise: Promise.resolve(null), objectUrl: null, refs: 0 }
  entry.promise = (async () => {
    try {
      const blob = await requestBlob(url)
      // Cleared (signed out) while it was on the wire: nothing is kept.
      if (entries.get(url) !== entry) return null
      entry.objectUrl = URL.createObjectURL(blob)
      return entry.objectUrl
    } catch {
      // A failure is not remembered: whoever shows it next asks again.
      if (entries.get(url) === entry) forget(url)
      return null
    }
  })()
  return entry
}

function forget(url: string): void {
  const entry = entries.get(url)
  entries.delete(url)
  const at = idle.indexOf(url)
  if (at >= 0) idle.splice(at, 1)
  if (entry?.objectUrl) URL.revokeObjectURL(entry.objectUrl)
}

/** The object URL of a photo already fetched, for a first render without a flash of initials. */
export function peekPhoto(url: string): string | null {
  return entries.get(url)?.objectUrl ?? null
}

/** Starts showing a photo: fetched once, shared by everyone showing it. Pair with `releasePhoto`. */
export function acquirePhoto(url: string): Promise<string | null> {
  let entry = entries.get(url)
  if (!entry) {
    entry = load(url)
    entries.set(url, entry)
  }
  entry.refs += 1
  const at = idle.indexOf(url)
  if (at >= 0) idle.splice(at, 1)
  return entry.promise
}

/** Stops showing a photo. The last one out puts it in the idle line; the line's overflow is revoked. */
export function releasePhoto(url: string): void {
  const entry = entries.get(url)
  if (!entry) return
  entry.refs = Math.max(0, entry.refs - 1)
  if (entry.refs > 0) return
  idle.push(url)
  while (idle.length > IDLE_PHOTOS) forget(idle[0]!)
}

/**
 * Revokes every photo. Called when the session ends: a patient's face must not outlive
 * the doctor's session on a shared clinic computer.
 */
export function clearPhotoCache(): void {
  for (const url of [...entries.keys()]) forget(url)
}

/** For tests: how many photos are held, and how many of those nobody shows. */
export function photoCacheSize(): { held: number; idle: number } {
  return { held: entries.size, idle: idle.length }
}

/**
 * The object URL to draw for a photo path, or null — while it loads, when it fails, and
 * when there is no photo at all. The caller draws the initial for null.
 */
export function usePhoto(url: string | null | undefined): string | null {
  const [state, setState] = useState<{ url: string | null | undefined; objectUrl: string | null }>(() => ({
    url,
    objectUrl: url ? peekPhoto(url) : null,
  }))

  useEffect(() => {
    if (!url) return
    let live = true
    acquirePhoto(url).then((objectUrl) => {
      if (live) setState({ url, objectUrl })
    })
    return () => {
      live = false
      releasePhoto(url)
    }
  }, [url])

  // A new URL shows its own photo or none — never the previous one while it loads.
  if (!url) return null
  return state.url === url ? state.objectUrl : peekPhoto(url)
}
