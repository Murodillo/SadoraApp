import { limits } from './limits'
import type { MessageImageUpload, PhotoUpload } from './types'

/** The longest edge a photo is sent at: plenty for a rash or a test strip, and small on the wire. */
export const IMAGE_MAX_EDGE = 1280
export const IMAGE_QUALITY = 0.82

/** A photo ready to send, with a preview she sees before it goes. */
export interface PreparedImage {
  upload: MessageImageUpload
  width: number
  height: number
  bytes: number
  previewUrl: string
}

export class ImageProblem extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ImageProblem'
  }
}

/** The size a picture is drawn at so its longest edge is at most `max`; never enlarged. */
export function fitWithin(width: number, height: number, max: number = IMAGE_MAX_EDGE): { width: number; height: number } {
  const longest = Math.max(width, height)
  if (longest <= max || longest <= 0) return { width: Math.max(1, Math.round(width)), height: Math.max(1, Math.round(height)) }
  const scale = max / longest
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) }
}

/**
 * A chosen file, redrawn in a canvas at no more than 1280px on its longest edge and
 * saved as a JPEG — whatever it came in as, so the server's JPEG/PNG check always
 * passes and a phone's 12-megapixel photo leaves as a few hundred kilobytes. A result
 * still over the server's 3 MB is refused here rather than there.
 */
export async function prepareImage(file: File): Promise<PreparedImage> {
  if (!file.type.startsWith('image/')) throw new ImageProblem('Faqat rasm yuborish mumkin')
  const encoded = await encodeJpeg(file, IMAGE_MAX_EDGE, IMAGE_QUALITY)
  if (encoded.blob.size > limits.messageImageMaxBytes) throw new ImageProblem('Rasm juda katta — 3 MB dan oshmasin')
  return {
    upload: { imageBase64: base64Of(await readAsDataUrl(encoded.blob)), mimeType: 'image/jpeg' },
    width: encoded.width,
    height: encoded.height,
    bytes: encoded.blob.size,
    previewUrl: URL.createObjectURL(encoded.blob),
  }
}

// ---------------------------------------------------------------- her profile photo

/** Her photo leaves at no more than this on its longest edge; the server keeps a 512px square of it. */
export const PHOTO_MAX_EDGE = 1600
export const PHOTO_QUALITY = 0.88
/** The server refuses a photo with either side under this. */
export const PHOTO_MIN_EDGE = 64
/** The server's ceiling on the decoded upload. */
export const PHOTO_MAX_BYTES = 8_000_000
/** What the picker offers and the server accepts. */
export const PHOTO_ACCEPT = 'image/jpeg,image/png'

/** A photo ready to upload, with the preview she sees before it goes. */
export interface PreparedPhoto {
  upload: PhotoUpload
  width: number
  height: number
  bytes: number
  previewUrl: string
}

/** Only JPEG and PNG, which is all the server reads. */
export function photoTypeProblem(type: string): string | null {
  return PHOTO_ACCEPT.split(',').includes(type.toLowerCase()) ? null : 'Faqat JPEG yoki PNG rasm'
}

/** A picture too small to be a face at 512px is refused before it is sent. */
export function photoSizeProblem(width: number, height: number): string | null {
  return width < PHOTO_MIN_EDGE || height < PHOTO_MIN_EDGE
    ? `Rasm juda kichik — kamida ${PHOTO_MIN_EDGE}×${PHOTO_MIN_EDGE} piksel bo'lsin`
    : null
}

/**
 * Her chosen photo, checked, redrawn upright at no more than 1600px and saved as a JPEG.
 * A phone's 12-megapixel picture leaves as a few hundred kilobytes; the server crops and
 * shrinks it again, so nothing sharper than this would ever be seen.
 */
export async function preparePhoto(file: File): Promise<PreparedPhoto> {
  const typeProblem = photoTypeProblem(file.type)
  if (typeProblem) throw new ImageProblem(typeProblem)
  const encoded = await encodeJpeg(file, PHOTO_MAX_EDGE, PHOTO_QUALITY, photoSizeProblem)
  if (encoded.blob.size > PHOTO_MAX_BYTES) throw new ImageProblem('Rasm juda katta — kichikroq rasm tanlang')
  return {
    upload: { imageBase64: base64Of(await readAsDataUrl(encoded.blob)), mimeType: 'image/jpeg' },
    width: encoded.width,
    height: encoded.height,
    bytes: encoded.blob.size,
    previewUrl: URL.createObjectURL(encoded.blob),
  }
}

/** The payload of a `data:` URL — what the server's `imageBase64` expects, without the prefix. */
export function base64Of(dataUrl: string): string {
  return dataUrl.slice(dataUrl.indexOf(',') + 1)
}

/**
 * Decodes, redraws at no more than `maxEdge` and encodes as a JPEG. The browser applies
 * the photo's EXIF orientation when it decodes an `<img>` (`image-orientation:
 * from-image` is the default), so the canvas receives the picture upright and the JPEG
 * leaves upright — the orientation tag itself is not carried over.
 */
async function encodeJpeg(
  file: File,
  maxEdge: number,
  quality: number,
  check?: (width: number, height: number) => string | null,
): Promise<{ blob: Blob; width: number; height: number }> {
  const source = await decode(file)
  try {
    const problem = check?.(source.width, source.height)
    if (problem) throw new ImageProblem(problem)
    const size = fitWithin(source.width, source.height, maxEdge)
    const canvas = document.createElement('canvas')
    canvas.width = size.width
    canvas.height = size.height
    const context = canvas.getContext('2d')
    if (!context) throw new ImageProblem("Rasmni o'qib bo'lmadi")
    // A transparent PNG would turn black as a JPEG; white is what a photo of paper expects.
    context.fillStyle = '#fff'
    context.fillRect(0, 0, size.width, size.height)
    context.drawImage(source.image, 0, 0, size.width, size.height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality))
    if (!blob) throw new ImageProblem("Rasmni o'qib bo'lmadi")
    return { blob, ...size }
  } finally {
    source.release()
  }
}

async function decode(file: File): Promise<{ image: CanvasImageSource; width: number; height: number; release: () => void }> {
  const url = URL.createObjectURL(file)
  try {
    const image = new Image()
    image.decoding = 'async'
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve()
      image.onerror = () => reject(new ImageProblem("Rasmni o'qib bo'lmadi"))
      image.src = url
    })
    return { image, width: image.naturalWidth, height: image.naturalHeight, release: () => URL.revokeObjectURL(url) }
  } catch (error) {
    URL.revokeObjectURL(url)
    throw error
  }
}

function readAsDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new ImageProblem("Rasmni o'qib bo'lmadi"))
    reader.readAsDataURL(blob)
  })
}

/** `1.4 MB`, `320 KB` — for the chip under the composer. */
export function formatBytes(bytes: number): string {
  if (bytes >= 1_000_000) return `${(bytes / 1_000_000).toFixed(1)} MB`
  return `${Math.max(1, Math.round(bytes / 1000))} KB`
}
