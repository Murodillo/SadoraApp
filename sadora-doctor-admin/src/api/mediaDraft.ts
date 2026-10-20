import { base64OfBlob, ImageProblem, prepareMessagePhoto } from './image'
import { limits } from './limits'
import { videoProblem } from './media'
import type { VideoMime } from './media'
import type { MediaKind } from './types'
import { probeVideo } from './video'

/*
 * A picked file made ready to send: a photo redrawn as a JPEG, a video measured and
 * given a poster. What she sends of these goes on to `mediaSend`.
 */

/** Why a picked file cannot go, worded for her. */
export class MediaProblem extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'MediaProblem'
  }
}

/** A picked file made ready, waiting in the send preview. */
export interface DraftMedia {
  key: string
  kind: MediaKind
  mimeType: string
  /** What goes up: the redrawn JPEG, or the video file itself. */
  blob: Blob
  width: number
  height: number
  durationMs: number | null
  /** An object URL for the preview: the photo, or the video's poster; null for a video with none. */
  previewUrl: string | null
  posterBase64: string | null
  name: string
}

let counter = 0
const nextKey = () => `draft-${Date.now().toString(36)}-${++counter}`

/**
 * A picked file as a draft. A photo is redrawn at 2048px as a JPEG; a video is checked
 * against 100 MB, measured against three minutes when the browser can read it, and given
 * a poster frame when the browser can draw one.
 */
export async function prepareDraft(file: File, choice: { kind: 'photo' } | { kind: 'video'; mimeType: VideoMime }): Promise<DraftMedia> {
  if (choice.kind === 'photo') {
    const photo = await prepareMessagePhoto(file)
    return {
      key: nextKey(),
      kind: 'photo',
      mimeType: 'image/jpeg',
      blob: photo.blob,
      width: photo.width,
      height: photo.height,
      durationMs: null,
      previewUrl: URL.createObjectURL(photo.blob),
      posterBase64: null,
      name: file.name,
    }
  }
  const tooBig = videoProblem(file.size, null)
  if (tooBig) throw new MediaProblem(tooBig)
  const probe = await probeVideo(file)
  const problem = videoProblem(file.size, probe.durationMs)
  if (problem) throw new MediaProblem(problem)
  // A poster over the server's megabyte would refuse the whole video; it goes without.
  const poster = probe.poster && probe.poster.size <= limits.mediaPosterMaxBytes ? probe.poster : null
  return {
    key: nextKey(),
    kind: 'video',
    mimeType: choice.mimeType,
    blob: file,
    width: probe.width,
    height: probe.height,
    durationMs: probe.durationMs,
    previewUrl: poster ? URL.createObjectURL(poster) : null,
    posterBase64: poster ? await base64OfBlob(poster) : null,
    name: file.name,
  }
}

/** The words for a file that could not be made ready. */
export function draftProblemText(error: unknown): string {
  if (error instanceof ImageProblem || error instanceof MediaProblem) return error.message
  return "Faylni o'qib bo'lmadi"
}

/** Lets go of a draft's preview: removed from the preview, or the preview closed. */
export function releaseDraft(draft: DraftMedia): void {
  if (draft.previewUrl) URL.revokeObjectURL(draft.previewUrl)
}
