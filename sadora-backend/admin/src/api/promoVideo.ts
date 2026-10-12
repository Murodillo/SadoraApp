import { ApiFailure, request } from './client'
import type { PromoUploadProgress, PromoVideoView } from './types'

const UPLOADS = '/v1/admin/promo-video/uploads'
const ATTEMPTS = 3

/** Why a chosen file cannot be the promo video, or null when it can. */
export function promoVideoProblem(file: { name: string; size: number }, maxBytes: number): string | null {
  if (!/\.(mp4|webm)$/i.test(file.name)) return 'Faqat MP4 yoki WebM video yuklanadi.'
  if (file.size === 0) return "Fayl bo'sh."
  if (file.size > maxBytes) return `Video juda katta — eng ko'pi ${Math.floor(maxBytes / (1024 * 1024))} MB.`
  return null
}

/**
 * Sends the file a piece at a time and makes it the video once the last piece is in.
 * A piece that failed on the way — the connection, a 5xx from a proxy — is sent again
 * before the whole upload is given up; the server puts a repeated piece where it went
 * the first time.
 */
export async function uploadPromoVideo(
  file: File,
  chunkBytes: number,
  onProgress: (sentBytes: number) => void,
  signal?: AbortSignal,
  pause: (attempt: number) => Promise<void> = (attempt) => new Promise((done) => setTimeout(done, attempt * 800)),
): Promise<PromoVideoView> {
  const { uploadId } = await request<PromoUploadProgress>(UPLOADS, {
    method: 'POST',
    body: { fileName: file.name, sizeBytes: file.size },
    signal,
  })
  for (let offset = 0; offset < file.size; offset += chunkBytes) {
    const piece = file.slice(offset, offset + chunkBytes)
    for (let attempt = 1; ; attempt++) {
      try {
        await request<PromoUploadProgress>(`${UPLOADS}/${uploadId}?offset=${offset}`, { method: 'PUT', bytes: piece, signal })
        break
      } catch (cause) {
        const again = cause instanceof ApiFailure && (cause.code === 'network' || (cause.status ?? 0) >= 500)
        if (!again || attempt >= ATTEMPTS) throw cause
        await pause(attempt)
      }
    }
    onProgress(Math.min(offset + chunkBytes, file.size))
  }
  return request<PromoVideoView>(`${UPLOADS}/${uploadId}/complete`, { method: 'POST', signal })
}
