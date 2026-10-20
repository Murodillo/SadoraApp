import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { API_BASE, ApiFailure, messageOf } from '../api/client'
import { formatFileSize, mediaFileName, tileLabel, videoMeta } from '../api/media'
import { usePhotoState } from '../api/photos'
import { mediaLink, mediaThumbPath } from '../api/upload'
import type { MessageMedia } from '../api/types'
import { useToast } from './toast'
import { Spinner } from './ui'

/** How long a saved file's object URL is kept for the browser to finish writing it. */
const OBJECT_URL_KEPT_MS = 60_000

const FOCUSABLE = 'button:not([disabled]), [href], video[controls], textarea, input, [tabindex]:not([tabindex="-1"])'

/**
 * A fresh signed address for one item, asked each time the item is shown: an address is
 * good for 15 minutes, and one kept from an earlier look may have run out. `retry` asks
 * again — a player that lost its address mid-way, or a failed first try.
 */
function useMediaLink(id: string): { url?: string; failed?: string; retry: () => void } {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<{ id: string; attempt: number; url?: string; failed?: string }>({ id, attempt })
  useEffect(() => {
    const controller = new AbortController()
    mediaLink(id, controller.signal).then(
      (link) => {
        if (!controller.signal.aborted) setState({ id, attempt, url: API_BASE + link.url })
      },
      (error: unknown) => {
        if (!controller.signal.aborted) setState({ id, attempt, failed: messageOf(error) })
      },
    )
    return () => controller.abort()
  }, [id, attempt])
  const current = state.id === id && state.attempt === attempt ? state : null
  return { url: current?.url, failed: current?.failed, retry: () => setAttempt((count) => count + 1) }
}

/** Saves a blob under a name, the way a download link would. */
function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_KEPT_MS)
}

/**
 * A message's photos and videos, full screen. ← and → (or the arrows) walk the album,
 * Escape closes it, and Tab stays inside it; the focus returns to the tile it was opened
 * from. A photo opens from its thumbnail and sharpens when the full picture arrives; a
 * video plays at once. Both come from a signed address asked for as the item is shown.
 */
export function MediaViewer({
  items,
  start,
  caption,
  onClose,
}: {
  items: MessageMedia[]
  start: number
  caption?: string
  onClose: () => void
}) {
  const [index, setIndex] = useState(() => Math.min(Math.max(0, start), items.length - 1))
  const item = items[index]!
  const dialog = useRef<HTMLDivElement>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose
  const link = useMediaLink(item.id)
  const thumb = usePhotoState(item.hasThumbnail !== false ? mediaThumbPath(item.id) : null)
  // The address a failed picture or player was given: asked again once, then it is a failure.
  const retried = useRef<string | null>(null)
  const [broken, setBroken] = useState<string | null>(null)
  const [downloading, setDownloading] = useState(false)
  // The full photo's address once it has drawn: the blurred thumbnail under it then goes.
  const [drawn, setDrawn] = useState<string | null>(null)
  const { notify } = useToast()
  const count = items.length
  const label = tileLabel(item, index, count)

  const go = (step: number) => setIndex((current) => Math.min(count - 1, Math.max(0, current + step)))
  const goRef = useRef(go)
  goRef.current = go

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        closeRef.current()
        return
      }
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        // A focused player seeks with the arrows; it keeps them.
        if (event.target instanceof HTMLVideoElement) return
        event.preventDefault()
        goRef.current(event.key === 'ArrowLeft' ? -1 : 1)
        return
      }
      if (event.key === 'Tab' && dialog.current) {
        const focusable = [...dialog.current.querySelectorAll<HTMLElement>(FOCUSABLE)]
        if (!focusable.length) return
        const first = focusable[0]!
        const last = focusable[focusable.length - 1]!
        const active = document.activeElement
        if (!dialog.current.contains(active) || (event.shiftKey && active === first) || (!event.shiftKey && active === last)) {
          event.preventDefault()
          ;(event.shiftKey ? last : first).focus()
        }
      }
    }
    document.addEventListener('keydown', onKey)
    const previous = document.activeElement as HTMLElement | null
    dialog.current?.focus()
    const { overflow } = document.body.style
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      previous?.focus?.()
    }
  }, [])

  // An arrow that went disabled at the album's end drops the focus to the page; it is
  // brought back into the dialog so the keys keep working.
  useEffect(() => {
    setBroken(null)
    const active = document.activeElement
    if (!active || active === document.body || (active instanceof HTMLButtonElement && active.disabled)) dialog.current?.focus()
  }, [index])

  function failedToShow() {
    if (!link.url) return
    if (retried.current !== link.url) {
      retried.current = link.url
      link.retry()
    } else {
      setBroken("Faylni ochib bo'lmadi")
    }
  }

  async function download() {
    setDownloading(true)
    try {
      const fresh = await mediaLink(item.id)
      // The address is signed: no token goes with it, as with the player itself.
      const response = await fetch(API_BASE + fresh.url)
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const blob = await response.blob()
      saveBlob(blob, mediaFileName(item, blob.type || response.headers.get('Content-Type')))
    } catch (error) {
      notify(error instanceof ApiFailure ? messageOf(error) : "Faylni yuklab bo'lmadi", 'error')
    } finally {
      setDownloading(false)
    }
  }

  const problem = broken ?? link.failed
  let stage
  if (problem) {
    stage = (
      <div className="viewer-failed" role="alert">
        <span>{problem}</span>
        <button
          type="button"
          className="btn small"
          onClick={() => {
            retried.current = null
            setBroken(null)
            link.retry()
          }}
        >
          Qayta urinish
        </button>
      </div>
    )
  } else if (item.kind === 'video') {
    stage = link.url ? (
      <video
        key={link.url}
        className="viewer-media"
        src={link.url}
        poster={thumb.url ?? undefined}
        controls
        autoPlay
        playsInline
        aria-label={label}
        onError={failedToShow}
      />
    ) : (
      <span className="viewer-wait" aria-label="Yuklanmoqda">
        <Spinner />
      </span>
    )
  } else {
    const sharp = Boolean(link.url) && drawn === link.url
    stage = (
      <span className="viewer-photo">
        {thumb.url && !sharp && <img className="viewer-under" src={thumb.url} alt="" aria-hidden="true" />}
        {link.url && (
          <img
            key={link.url}
            className="viewer-media"
            src={link.url}
            alt={caption ? `${label}: ${caption}` : label}
            onLoad={() => setDrawn(link.url ?? null)}
            onError={failedToShow}
          />
        )}
        {!link.url && !thumb.url && (
          <span className="viewer-wait" aria-label="Yuklanmoqda">
            <Spinner />
          </span>
        )}
      </span>
    )
  }

  return createPortal(
    <div
      ref={dialog}
      className="viewer"
      role="dialog"
      aria-modal="true"
      aria-label={label}
      tabIndex={-1}
      onClick={(event) => {
        if (event.target === event.currentTarget) closeRef.current()
      }}
    >
      <div className="viewer-bar">
        {count > 1 && (
          <span className="viewer-count" aria-live="polite">
            {index + 1} / {count}
          </span>
        )}
        <span className="viewer-meta">{item.kind === 'video' ? videoMeta(item) : formatFileSize(item.sizeBytes)}</span>
        <span className="viewer-actions">
          <button type="button" className="btn small" onClick={() => void download()} disabled={downloading}>
            {downloading && <Spinner />}
            Yuklab olish
          </button>
          <button type="button" className="btn small viewer-close" onClick={() => closeRef.current()} aria-label="Yopish">
            ✕
          </button>
        </span>
      </div>
      <div
        className="viewer-stage"
        onClick={(event) => {
          if (event.target === event.currentTarget) closeRef.current()
        }}
      >
        {count > 1 && (
          <button type="button" className="viewer-nav prev" onClick={() => go(-1)} disabled={index === 0} aria-label="Oldingi">
            ‹
          </button>
        )}
        {stage}
        {count > 1 && (
          <button type="button" className="viewer-nav next" onClick={() => go(1)} disabled={index === count - 1} aria-label="Keyingi">
            ›
          </button>
        )}
      </div>
      {caption && <p className="viewer-caption">{caption}</p>}
    </div>,
    document.body,
  )
}
