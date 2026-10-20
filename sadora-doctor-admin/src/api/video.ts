import { drawJpeg } from './image'

/*
 * What the browser can tell about a video before it goes: its length, its shape, and a
 * frame for the poster. A browser cannot transcode, so the file itself goes up as it is;
 * the server reads its boxes again and refuses one over three minutes either way.
 */

/** The poster's longest edge and quality: a tile in the thread, never a full picture. */
export const POSTER_EDGE = 640
export const POSTER_QUALITY = 0.8
/** Where the poster is taken: past a fade in from black, and still inside a very short clip. */
export const POSTER_AT_SECONDS = 0.5

const METADATA_TIMEOUT_MS = 10_000
const FRAME_TIMEOUT_MS = 4_000

export interface ProbedVideo {
  /** Null when the browser could not read the length; the server measures it anyway. */
  durationMs: number | null
  /** As it plays: a video recorded upright is taller than wide. 0 when unknown. */
  width: number
  height: number
  /** A frame as a JPEG, or null when the browser could not draw one (a codec it cannot play). */
  poster: Blob | null
}

/**
 * Opens the file in a muted, detached `<video>`, reads its length on `loadedmetadata`,
 * seeks to half a second in and draws that frame. Every step has a time limit: a file
 * the browser cannot decode answers what was learned so far rather than hanging the
 * composer.
 */
export async function probeVideo(file: Blob): Promise<ProbedVideo> {
  const url = URL.createObjectURL(file)
  const video = document.createElement('video')
  video.muted = true
  video.playsInline = true
  video.preload = 'auto'
  try {
    const loaded = await waitFor(video, 'loadedmetadata', METADATA_TIMEOUT_MS, () => {
      video.src = url
    })
    if (!loaded) return { durationMs: null, width: 0, height: 0, poster: null }
    const seconds = Number.isFinite(video.duration) && video.duration > 0 ? video.duration : null
    const width = video.videoWidth
    const height = video.videoHeight
    let poster: Blob | null = null
    if (width > 0 && height > 0) {
      const at = seconds ? Math.min(POSTER_AT_SECONDS, seconds / 2) : 0.1
      const seeked = await waitFor(video, 'seeked', FRAME_TIMEOUT_MS, () => {
        video.currentTime = at
      })
      if (seeked) {
        try {
          poster = (await drawJpeg(video, width, height, POSTER_EDGE, POSTER_QUALITY)).blob
        } catch {
          poster = null
        }
      }
    }
    return { durationMs: seconds ? Math.round(seconds * 1000) : null, width, height, poster }
  } finally {
    video.removeAttribute('src')
    video.load()
    URL.revokeObjectURL(url)
  }
}

/** Resolves true on `event`, false on the element's error or after `timeoutMs`. `start` runs once listening. */
function waitFor(target: HTMLMediaElement, event: string, timeoutMs: number, start: () => void): Promise<boolean> {
  return new Promise((resolve) => {
    const finish = (ok: boolean) => {
      window.clearTimeout(timer)
      target.removeEventListener(event, onEvent)
      target.removeEventListener('error', onError)
      resolve(ok)
    }
    const onEvent = () => finish(true)
    const onError = () => finish(false)
    const timer = window.setTimeout(() => finish(false), timeoutMs)
    target.addEventListener(event, onEvent)
    target.addEventListener('error', onError)
    start()
  })
}
