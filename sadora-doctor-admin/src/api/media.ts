import { ApiFailure, messageOf } from './client'
import { limits } from './limits'
import type { MediaKind, MessageMedia } from './types'

/*
 * Photos and videos in a consultation: the small, pure rules behind the album in the
 * thread, the words in the list, and what the composer lets through before anything is
 * read or sent. The network side is in `upload.ts`; the canvas side in `image.ts` and
 * `video.ts`.
 */

/** `FlagKeys.CHAT_MEDIA`: with it a consultation message carries videos and albums; without, one photo. */
export const CHAT_MEDIA_FLAG = 'chat_media'

const MEBIBYTE = 1024 * 1024

// ---------------------------------------------------------------- sizes and lengths

/** "240 KB", "1,4 MB": under a megabyte in whole KB, above it in MB with one decimal. */
export function formatFileSize(bytes: number): string {
  const kilo = 1024
  if (bytes < kilo * kilo) return `${Math.max(1, Math.ceil(bytes / kilo))} KB`
  const tenths = Math.round((bytes * 10) / (kilo * kilo))
  return tenths % 10 === 0 ? `${tenths / 10} MB` : `${Math.floor(tenths / 10)},${tenths % 10} MB`
}

function clock(ms: number): { hours: number; minutes: number; seconds: number } {
  const total = Math.max(0, Math.round(ms / 1000))
  return { hours: Math.floor(total / 3600), minutes: Math.floor((total % 3600) / 60), seconds: total % 60 }
}

/** A video's length on its tile: `0:42`, `1:24`, `1:02:03`. */
export function formatDuration(ms: number): string {
  const { hours, minutes, seconds } = clock(ms)
  const ss = String(seconds).padStart(2, '0')
  return hours ? `${hours}:${String(minutes).padStart(2, '0')}:${ss}` : `${minutes}:${ss}`
}

/** The same length for a screen reader: `1 daqiqa 24 soniya`, `42 soniya`. */
export function durationWords(ms: number): string {
  const { hours, minutes, seconds } = clock(ms)
  const parts = [hours && `${hours} soat`, minutes && `${minutes} daqiqa`, seconds && `${seconds} soniya`].filter(Boolean)
  return parts.length ? parts.join(' ') : '0 soniya'
}

/** Under a video tile: `1:24 · 18 MB`; just the size when the length is unknown. */
export function videoMeta(item: Pick<MessageMedia, 'durationMs' | 'sizeBytes'>): string {
  return [item.durationMs ? formatDuration(item.durationMs) : null, formatFileSize(item.sizeBytes)].filter(Boolean).join(' · ')
}

// ---------------------------------------------------------------- the album

/** How a message's items are laid out, the way Telegram does it. */
export type AlbumShape = 'one' | 'two' | 'three' | 'grid'

export interface AlbumLayout {
  shape: AlbumShape
  /** Tiles drawn; the last drawn carries "+N" when `more` is above 0. */
  shown: number
  more: number
}

/** One tile at its own shape; two side by side; one big and two; four in a square, "+N" on the last. */
export function albumLayout(count: number): AlbumLayout {
  if (count <= 1) return { shape: 'one', shown: Math.max(0, count), more: 0 }
  if (count === 2) return { shape: 'two', shown: 2, more: 0 }
  if (count === 3) return { shape: 'three', shown: 3, more: 0 }
  return { shape: 'grid', shown: 4, more: count - 4 }
}

/** The album's width in the thread; a lone tile keeps its shape inside this and `ALBUM_SINGLE_MAX_HEIGHT`. */
export const ALBUM_WIDTH = 300
export const ALBUM_SINGLE_MAX_HEIGHT = 340
const SINGLE_MIN_WIDTH = 140
const SINGLE_MIN_HEIGHT = 90

/**
 * The box a lone photo or video is drawn in: its own proportions, as large as fits the
 * album's width and the height cap. A sliver of a panorama or a very tall screenshot is
 * held to a usable minimum and cropped to it rather than drawn as a line.
 */
export function singleTileSize(
  width: number,
  height: number,
  maxWidth: number = ALBUM_WIDTH,
  maxHeight: number = ALBUM_SINGLE_MAX_HEIGHT,
): { width: number; height: number } {
  const w = width > 0 ? width : 4
  const h = height > 0 ? height : 3
  const scale = Math.min(maxWidth / w, maxHeight / h)
  return {
    width: Math.min(maxWidth, Math.max(SINGLE_MIN_WIDTH, Math.round(w * scale))),
    height: Math.min(maxHeight, Math.max(SINGLE_MIN_HEIGHT, Math.round(h * scale))),
  }
}

/**
 * A tile's name for a screen reader: "Rasm 1 / 3", "Video, 1 daqiqa 24 soniya", and
 * ", yana 3 ta" on the tile that hides the rest of the album.
 */
export function tileLabel(item: Pick<MessageMedia, 'kind' | 'durationMs'>, index: number, count: number, more = 0): string {
  const place = count > 1 ? ` ${index + 1} / ${count}` : ''
  const base =
    item.kind === 'video' ? `Video${place}${item.durationMs ? `, ${durationWords(item.durationMs)}` : ''}` : `Rasm${place}`
  return more > 0 ? `${base}, yana ${more} ta` : base
}

// ---------------------------------------------------------------- words for a line

/** "📷 Rasm", "📷 3 ta rasm", "🎥 Video · 0:42", "🎥 2 ta video", "📷 2 ta rasm, 1 ta video". */
export function mediaSummary(items: readonly Pick<MessageMedia, 'kind' | 'durationMs'>[]): string {
  const photos = items.filter((item) => item.kind === 'photo').length
  const videos = items.length - photos
  if (videos === 0) return photos > 1 ? `📷 ${photos} ta rasm` : '📷 Rasm'
  if (photos === 0) {
    if (videos > 1) return `🎥 ${videos} ta video`
    const length = items[0]?.durationMs
    return length ? `🎥 Video · ${formatDuration(length)}` : '🎥 Video'
  }
  return `📷 ${photos} ta rasm, ${videos} ta video`
}

/**
 * The conversation list's line for a `media` last message, its caption after " · ". The
 * list carries the first item, the count and how many were videos: enough to word a
 * single item exactly and an album by what it holds.
 */
export function mediaPreview(
  first: MessageMedia | null | undefined,
  count: number | undefined,
  caption?: string | null,
  videos = 0,
): string {
  const total = Math.max(count ?? 0, first ? 1 : 0)
  const words =
    total > 1
      ? mediaSummary([
          ...Array.from({ length: total - Math.min(videos, total) }, () => ({ kind: 'photo' as const })),
          ...Array.from({ length: Math.min(videos, total) }, () => ({ kind: 'video' as const })),
        ])
      : mediaSummary(first ? [first] : [])
  const text = caption?.trim()
  return text ? `${words} · ${text}` : words
}

// ---------------------------------------------------------------- what she picks

/** The video types the server keeps: `MediaService.VIDEO_MIME`. */
export type VideoMime = 'video/mp4' | 'video/quicktime'

export type FileChoice = { kind: 'photo' } | { kind: 'video'; mimeType: VideoMime } | { problem: string }

const PHOTO_EXTENSIONS = ['jpg', 'jpeg', 'png', 'heic', 'heif', 'webp', 'gif', 'bmp']

/**
 * What a picked file will be sent as. Any picture the browser can draw goes as a photo
 * (it is redrawn as a JPEG); a video only as MP4 or MOV, the two the server reads. A
 * browser that names no type — some do for `.mov` — is judged by the extension.
 */
export function classifyFile(name: string, type: string): FileChoice {
  const mime = type.trim().toLowerCase()
  const extension = name.toLowerCase().split('.').pop() ?? ''
  if (mime.startsWith('image/')) return { kind: 'photo' }
  if (mime === 'video/mp4' || mime === 'video/x-m4v' || (!mime && (extension === 'mp4' || extension === 'm4v'))) {
    return { kind: 'video', mimeType: 'video/mp4' }
  }
  if (mime === 'video/quicktime' || (!mime && extension === 'mov')) return { kind: 'video', mimeType: 'video/quicktime' }
  if (mime.startsWith('video/')) return { problem: 'Faqat MP4 yoki MOV video' }
  if (!mime && PHOTO_EXTENSIONS.includes(extension)) return { kind: 'photo' }
  return { problem: 'Faqat rasm yoki video yuborish mumkin' }
}

/**
 * Why a video cannot go, in the server's words, or null. The length is checked only when
 * the browser could read it; the server checks it again either way, with the same second
 * of grace (`MediaService.DURATION_GRACE_MS`).
 */
export function videoProblem(sizeBytes: number, durationMs: number | null | undefined): string | null {
  if (sizeBytes > limits.mediaVideoMaxBytes) return `Video juda katta — eng ko'pi ${limits.mediaVideoMaxBytes / MEBIBYTE} MB`
  if (durationMs != null && durationMs >= (limits.mediaVideoMaxSeconds + 1) * 1000) {
    return `Video ${limits.mediaVideoMaxSeconds / 60} daqiqadan uzun bo'lmasin`
  }
  return null
}

export const FEATURE_OFF = 'Video va albom hozircha yoqilmagan'

/**
 * Which of the files she just picked fit the message beside the `already` chosen, and
 * why the rest stay out. With `chat_media` off a message carries one photo and no video;
 * with it on, up to 10 of either. Decided before any file is read, so nothing is
 * redrawn only to be dropped.
 */
export function admitFiles<T extends { kind: MediaKind }>(
  already: number,
  picked: readonly T[],
  chatMedia: boolean,
): { take: T[]; problems: string[] } {
  if (!chatMedia) {
    const photos = picked.filter((item) => item.kind === 'photo')
    const room = Math.max(0, 1 - already)
    const take = photos.slice(0, room)
    return { take, problems: take.length < picked.length ? [`${FEATURE_OFF}: bitta rasm yuborish mumkin`] : [] }
  }
  const room = Math.max(0, limits.messageMediaMax - already)
  const take = picked.slice(0, room)
  return {
    take,
    problems: take.length < picked.length ? [`Bitta xabarda eng ko'pi ${limits.messageMediaMax} ta rasm yoki video`] : [],
  }
}

// ---------------------------------------------------------------- refusals and files

/**
 * The words for a failed upload or send. The day's allowance and the switched-off flag
 * get the panel's own; a 400 says what was wrong with the file, which the server puts in
 * the field's detail under a general headline; a 503 and the rest carry the server's.
 */
export function mediaErrorText(error: unknown): string {
  if (error instanceof ApiFailure) {
    if (error.status === 429 && error.code !== 'rate_limited') return 'Bugungi limit tugadi'
    if (error.status === 403 && error.code === 'feature_disabled') return FEATURE_OFF
    if (error.status === 400) return Object.values(error.fields)[0] || error.message
  }
  return messageOf(error)
}

const EXTENSIONS: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'video/mp4': 'mp4',
  'video/quicktime': 'mov',
}

/** What a downloaded item is saved as: `sadora-rasm-1a2b3c4d.jpg`, `sadora-video-1a2b3c4d.mp4`. */
export function mediaFileName(item: Pick<MessageMedia, 'id' | 'kind'>, mimeType?: string | null): string {
  const extension = EXTENSIONS[(mimeType ?? '').split(';')[0]!.trim().toLowerCase()] ?? (item.kind === 'video' ? 'mp4' : 'jpg')
  const short = item.id.replace(/[^0-9a-z]/gi, '').slice(0, 8) || 'fayl'
  return `sadora-${item.kind === 'video' ? 'video' : 'rasm'}-${short}.${extension}`
}
