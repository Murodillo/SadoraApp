import type { ReactNode } from 'react'
import { albumLayout, singleTileSize, tileLabel, videoMeta } from '../api/media'
import type { PendingItem, PendingSend } from '../api/mediaSend'
import { usePhotoState } from '../api/photos'
import { mediaThumbPath, overallProgress } from '../api/upload'
import type { MediaKind, MessageMedia } from '../api/types'

/*
 * Photos and videos in the thread, laid out the way Telegram does it: one tile at its own
 * shape, two side by side, one big beside two, or four in a square with "+N" on the
 * last. A sent item's thumbnail is fetched with her token and cached in memory; one still
 * on its way shows the picture the tab already has.
 */

/** One tile: a sent item, or one on its way with its local preview. */
export interface AlbumItem {
  key: string
  kind: MediaKind
  width: number
  height: number
  durationMs?: number | null
  sizeBytes: number
  /** A sent item: its thumbnail is `/v1/media/{id}/thumb`. */
  mediaId?: string
  hasThumbnail?: boolean
  /** An item on its way: an object URL already in the tab, or null for a video without a poster. */
  localUrl?: string | null
}

export function albumItemOf(media: MessageMedia): AlbumItem {
  return { ...media, key: media.id, mediaId: media.id }
}

export function pendingAlbumItem(item: PendingItem): AlbumItem {
  return {
    key: item.key,
    kind: item.kind,
    width: item.width,
    height: item.height,
    durationMs: item.durationMs,
    sizeBytes: item.blob.size,
    localUrl: item.previewUrl,
  }
}

/**
 * The grid. With `onOpen` every tile is a button that opens the viewer at its index;
 * without, the tiles are pictures only (a send still on its way). `overlay` is drawn
 * over the whole grid.
 */
export function MediaAlbum({
  items,
  onOpen,
  overlay,
}: {
  items: AlbumItem[]
  onOpen?: (index: number) => void
  overlay?: ReactNode
}) {
  const layout = albumLayout(items.length)
  const shown = items.slice(0, layout.shown)
  const single = layout.shape === 'one' && shown[0] ? singleTileSize(shown[0].width, shown[0].height) : null
  return (
    <div className={`album ${layout.shape}`} style={single ? { width: single.width, height: single.height } : undefined}>
      {shown.map((item, index) => {
        const more = index === shown.length - 1 ? layout.more : 0
        return (
          <AlbumTile
            key={item.key}
            item={item}
            label={tileLabel(item, index, items.length, more)}
            more={more}
            onOpen={onOpen ? () => onOpen(index) : undefined}
          />
        )
      })}
      {overlay}
    </div>
  )
}

function AlbumTile({ item, label, more, onOpen }: { item: AlbumItem; label: string; more: number; onOpen?: () => void }) {
  const fetchable = item.mediaId && item.hasThumbnail !== false && item.localUrl === undefined
  const thumb = usePhotoState(fetchable ? mediaThumbPath(item.mediaId!) : null)
  const src = item.localUrl ?? thumb.url
  const blank = !src && (thumb.failed || !fetchable)
  const inner = (
    <>
      {src ? <img src={src} alt="" draggable={false} /> : blank ? <span className="album-blank" /> : <span className="skeleton" />}
      {item.kind === 'video' && (
        <span className="album-play" aria-hidden="true">
          ▶
        </span>
      )}
      {item.kind === 'video' && more === 0 && (
        <span className="album-meta" aria-hidden="true">
          {videoMeta(item)}
        </span>
      )}
      {more > 0 && (
        <span className="album-more" aria-hidden="true">
          +{more}
        </span>
      )}
    </>
  )
  if (!onOpen) {
    return (
      <div className="album-tile" role="img" aria-label={label}>
        {inner}
      </div>
    )
  }
  return (
    <button type="button" className="album-tile" aria-label={label} onClick={onOpen}>
      {inner}
    </button>
  )
}

const RING_RADIUS = 18
const RING_LENGTH = 2 * Math.PI * RING_RADIUS

/** The share of the files the server holds, drawn as a ring. */
export function ProgressRing({ value }: { value: number }) {
  const percent = Math.round(Math.min(1, Math.max(0, value)) * 100)
  return (
    <svg
      className="progress-ring"
      viewBox="0 0 44 44"
      width={44}
      height={44}
      role="progressbar"
      aria-label="Yuklanmoqda"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={percent}
    >
      <circle className="track" cx="22" cy="22" r={RING_RADIUS} />
      <circle
        className="bar"
        cx="22"
        cy="22"
        r={RING_RADIUS}
        strokeDasharray={RING_LENGTH}
        strokeDashoffset={RING_LENGTH * (1 - percent / 100)}
      />
    </svg>
  )
}

/**
 * Her photos and videos while they go: the album from what the tab already has, a ring
 * for the bytes the server holds, and a cancel in its middle. A send that failed says so
 * and offers to try again — from where each upload stopped — or to drop it.
 */
export function PendingMediaBubble({
  send,
  onRetry,
  onDiscard,
}: {
  send: PendingSend
  onRetry: () => void
  onDiscard: () => void
}) {
  const progress = overallProgress(send.items.map((item) => ({ sizeBytes: item.blob.size, sentBytes: item.sentBytes })))
  const failed = send.status === 'failed'
  const overlay = failed ? null : (
    <div className="album-progress">
      <ProgressRing value={progress} />
      {send.status === 'uploading' && (
        <button type="button" className="album-cancel" aria-label="Yuborishni bekor qilish" onClick={onDiscard}>
          ✕
        </button>
      )}
    </div>
  )
  return (
    <li className={`msg mine media pending${failed ? ' failed' : ''}`}>
      <div className="msg-bubble">
        <MediaAlbum items={send.items.map(pendingAlbumItem)} overlay={overlay} />
        {send.caption && <p className="msg-text">{send.caption}</p>}
        {failed ? (
          <div className="pending-failed" role="alert">
            <span>
              <b>Yuborilmadi</b>
              {send.error ? ` · ${send.error}` : ''}
            </span>
            <span className="pending-actions">
              <button type="button" className="btn small" onClick={onRetry}>
                Qayta yuborish
              </button>
              <button type="button" className="btn small ghost danger" onClick={onDiscard}>
                Bekor qilish
              </button>
            </span>
          </div>
        ) : (
          <span className="msg-stamp">{send.status === 'sending' ? 'Yuborilmoqda…' : `Yuklanmoqda · ${Math.round(progress * 100)}%`}</span>
        )}
      </div>
    </li>
  )
}
