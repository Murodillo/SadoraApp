import { ApiFailure, query, request } from './client'
import type {
  Ack,
  CompleteMediaUploadRequest,
  MediaKind,
  MediaLink,
  MediaUpload,
  MessageMedia,
  StartMediaUploadRequest,
} from './types'

/*
 * A photo or video on its way up, a piece at a time, so a dropped connection costs one
 * piece and not the file. Pieces go one after another, each `chunkBytes` long but the
 * last, at an offset that is a whole number of pieces; the server answers each with how
 * much it now holds, and that answer — not a count kept here — decides the next piece.
 */

/** `MediaStore.CHUNK_BYTES`. The server names it on every answer, and its word is what is used. */
export const CHUNK_BYTES = 1024 * 1024

export interface Piece {
  offset: number
  /** Exclusive. */
  end: number
}

/**
 * The next piece to send, or null when the server holds the whole file. It starts at
 * the last piece boundary at or before what the server holds: sending a piece it already
 * has again is harmless, and starting mid-piece is not allowed.
 */
export function nextPiece(sizeBytes: number, receivedBytes: number, chunkBytes: number): Piece | null {
  if (sizeBytes <= 0 || receivedBytes >= sizeBytes) return null
  const chunk = chunkBytes > 0 ? chunkBytes : CHUNK_BYTES
  const offset = Math.floor(Math.max(0, receivedBytes) / chunk) * chunk
  return { offset, end: Math.min(sizeBytes, offset + chunk) }
}

/** Every piece still to send after `receivedBytes`, in order: what a resumed upload will do. */
export function remainingPieces(sizeBytes: number, receivedBytes: number, chunkBytes: number): Piece[] {
  const pieces: Piece[] = []
  let piece = nextPiece(sizeBytes, receivedBytes, chunkBytes)
  while (piece) {
    pieces.push(piece)
    piece = nextPiece(sizeBytes, piece.end, chunkBytes)
  }
  return pieces
}

/** How far a message's files have got together, 0 to 1, by bytes rather than by files. */
export function overallProgress(items: readonly { sizeBytes: number; sentBytes: number }[]): number {
  const total = items.reduce((sum, item) => sum + Math.max(0, item.sizeBytes), 0)
  if (total <= 0) return 0
  const sent = items.reduce((sum, item) => sum + Math.min(Math.max(0, item.sentBytes), Math.max(0, item.sizeBytes)), 0)
  return Math.min(1, sent / total)
}

// ---------------------------------------------------------------- the calls

const encode = encodeURIComponent
const UPLOADS = '/v1/media/uploads'

export const startUpload = (body: StartMediaUploadRequest, signal?: AbortSignal) =>
  request<MediaUpload>(UPLOADS, { method: 'POST', body, signal })

/** Where an upload stands: what to send next after a failure. */
export const uploadStatus = (id: string, signal?: AbortSignal) => request<MediaUpload>(`${UPLOADS}/${encode(id)}`, { signal })

export const sendPiece = (id: string, offset: number, bytes: Blob, signal?: AbortSignal) =>
  request<MediaUpload>(`${UPLOADS}/${encode(id)}${query({ offset })}`, { method: 'PUT', bytes, signal })

export const completeUpload = (id: string, body: CompleteMediaUploadRequest, signal?: AbortSignal) =>
  request<MessageMedia>(`${UPLOADS}/${encode(id)}/complete`, { method: 'POST', body, signal })

/** Drops an upload no message carries; the server keeps nothing of it. */
export const cancelUpload = (id: string) => request<Ack>(`${UPLOADS}/${encode(id)}`, { method: 'DELETE' })

/** A signed address for the photo or the video, good for 15 minutes: asked afresh each time one is opened. */
export const mediaLink = (id: string, signal?: AbortSignal) => request<MediaLink>(`/v1/media/${encode(id)}/link`, { signal })

/** A thumbnail or a video's poster, read with her token like any other private picture. */
export const mediaThumbPath = (id: string) => `/v1/media/${encode(id)}/thumb`

// ---------------------------------------------------------------- one file

/** What goes up: the redrawn photo, or the video as it is with its poster. */
export interface UploadSource {
  kind: MediaKind
  mimeType: string
  blob: Blob
  posterBase64?: string | null
}

/** Told after every answer: the upload's id, and how much of the file the server holds. */
export type UploadProgress = (state: { uploadId: string; sentBytes: number }) => void

/** How many times in a row the server may refuse a piece as out of step before the upload gives up. */
const RESYNCS = 2

/**
 * Sends one file and finishes it. With `uploadId` from an earlier try it asks the server
 * how much it has and carries on from there; an upload the server no longer knows
 * (swept after a day) starts again. A piece refused as out of step — its answer was lost
 * and the server is ahead — sends the upload back to the server's own count.
 */
export async function uploadFile(
  source: UploadSource,
  uploadId: string | undefined,
  onProgress: UploadProgress,
  signal?: AbortSignal,
): Promise<MessageMedia> {
  let upload: MediaUpload | null = null
  if (uploadId) {
    try {
      upload = await uploadStatus(uploadId, signal)
    } catch (error) {
      if (!(error instanceof ApiFailure && error.status === 404)) throw error
    }
  }
  upload ??= await startUpload({ kind: source.kind, mimeType: source.mimeType, sizeBytes: source.blob.size }, signal)
  signal?.throwIfAborted()
  onProgress({ uploadId: upload.id, sentBytes: upload.receivedBytes })

  let refused = 0
  let piece = nextPiece(upload.sizeBytes, upload.receivedBytes, upload.chunkBytes)
  while (piece) {
    // Cancelled between two answers: no further piece goes.
    signal?.throwIfAborted()
    try {
      upload = await sendPiece(upload.id, piece.offset, source.blob.slice(piece.offset, piece.end), signal)
      refused = 0
    } catch (error) {
      if (!(error instanceof ApiFailure && error.status === 409) || refused >= RESYNCS) throw error
      refused += 1
      upload = await uploadStatus(upload.id, signal)
    }
    onProgress({ uploadId: upload.id, sentBytes: upload.receivedBytes })
    const next = nextPiece(upload.sizeBytes, upload.receivedBytes, upload.chunkBytes)
    // A piece accepted without the count moving would send it for ever.
    if (next && refused === 0 && next.offset === piece.offset && upload.receivedBytes < piece.end) {
      throw new ApiFailure('stalled', "Yuklash to'xtab qoldi — qayta urinib ko'ring")
    }
    piece = next
  }
  signal?.throwIfAborted()
  return completeUpload(upload.id, { posterBase64: source.posterBase64 ?? null }, signal)
}
