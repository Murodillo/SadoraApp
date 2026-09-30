import { limits } from './limits'
import type { MessageImageUpload } from './types'

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
  const source = await decode(file)
  const size = fitWithin(source.width, source.height)
  const canvas = document.createElement('canvas')
  canvas.width = size.width
  canvas.height = size.height
  const context = canvas.getContext('2d')
  if (!context) throw new ImageProblem("Rasmni o'qib bo'lmadi")
  // A transparent PNG would turn black as a JPEG; white is what a photo of paper expects.
  context.fillStyle = '#fff'
  context.fillRect(0, 0, size.width, size.height)
  context.drawImage(source.image, 0, 0, size.width, size.height)
  source.release()

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', IMAGE_QUALITY))
  if (!blob) throw new ImageProblem("Rasmni o'qib bo'lmadi")
  if (blob.size > limits.messageImageMaxBytes) throw new ImageProblem('Rasm juda katta — 3 MB dan oshmasin')

  const dataUrl = await readAsDataUrl(blob)
  return {
    upload: { imageBase64: dataUrl.slice(dataUrl.indexOf(',') + 1), mimeType: 'image/jpeg' },
    width: size.width,
    height: size.height,
    bytes: blob.size,
    previewUrl: URL.createObjectURL(blob),
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
