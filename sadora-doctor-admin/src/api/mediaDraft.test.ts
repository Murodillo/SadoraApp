import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { prepareMessagePhoto } from './image'
import { MediaProblem, prepareDraft } from './mediaDraft'
import { probeVideo } from './video'

vi.mock('./image', async (original) => ({
  ...(await original<typeof import('./image')>()),
  prepareMessagePhoto: vi.fn(),
}))
vi.mock('./video', () => ({ probeVideo: vi.fn() }))

let made = 0
beforeEach(() => {
  made = 0
  Object.assign(URL, {
    createObjectURL: vi.fn(() => `blob:media-${++made}`),
    revokeObjectURL: vi.fn(),
  })
})
afterEach(() => {
  vi.mocked(prepareMessagePhoto).mockReset()
  vi.mocked(probeVideo).mockReset()
  delete (URL as Partial<typeof URL>).createObjectURL
  delete (URL as Partial<typeof URL>).revokeObjectURL
})

describe('prepareDraft', () => {
  const file = (name: string, type: string, size: number) => {
    const chosen = new File(['x'], name, { type })
    Object.defineProperty(chosen, 'size', { value: size })
    return chosen
  }

  it('redraws a photo as a JPEG with a preview', async () => {
    const jpeg = new Blob(['jpeg'], { type: 'image/jpeg' })
    vi.mocked(prepareMessagePhoto).mockResolvedValue({ blob: jpeg, width: 2048, height: 1536 })
    const prepared = await prepareDraft(file('a.png', 'image/png', 5_000_000), { kind: 'photo' })
    expect(prepared).toMatchObject({ kind: 'photo', mimeType: 'image/jpeg', blob: jpeg, width: 2048, height: 1536, previewUrl: 'blob:media-1' })
  })

  it('sends a video as it is, with its length and a poster', async () => {
    vi.mocked(probeVideo).mockResolvedValue({
      durationMs: 42_000,
      width: 720,
      height: 1280,
      poster: new Blob(['poster'], { type: 'image/jpeg' }),
    })
    const source = file('IMG_1.MOV', 'video/quicktime', 20_000_000)
    const prepared = await prepareDraft(source, { kind: 'video', mimeType: 'video/quicktime' })
    expect(prepared).toMatchObject({ kind: 'video', mimeType: 'video/quicktime', blob: source, durationMs: 42_000, width: 720, height: 1280 })
    expect(prepared.posterBase64).toBe(btoa('poster'))
    expect(prepared.previewUrl).toBe('blob:media-1')
  })

  it('refuses a video over 100 MB before reading it', async () => {
    const source = file('big.mp4', 'video/mp4', 100 * 1024 * 1024 + 1)
    await expect(prepareDraft(source, { kind: 'video', mimeType: 'video/mp4' })).rejects.toThrow(MediaProblem)
    expect(probeVideo).not.toHaveBeenCalled()
  })

  it('refuses a video over three minutes', async () => {
    vi.mocked(probeVideo).mockResolvedValue({ durationMs: 200_000, width: 1, height: 1, poster: null })
    await expect(prepareDraft(file('long.mp4', 'video/mp4', 1000), { kind: 'video', mimeType: 'video/mp4' })).rejects.toThrow(
      "Video 3 daqiqadan uzun bo'lmasin",
    )
  })

  it('sends a video the browser cannot draw without a poster, and leaves its length to the server', async () => {
    vi.mocked(probeVideo).mockResolvedValue({ durationMs: null, width: 0, height: 0, poster: null })
    const prepared = await prepareDraft(file('hevc.mov', 'video/quicktime', 1000), { kind: 'video', mimeType: 'video/quicktime' })
    expect(prepared).toMatchObject({ durationMs: null, previewUrl: null, posterBase64: null })
  })

  it('leaves out a poster over the server limit rather than lose the video', async () => {
    const poster = new Blob(['p'])
    Object.defineProperty(poster, 'size', { value: 1_000_001 })
    vi.mocked(probeVideo).mockResolvedValue({ durationMs: 1000, width: 10, height: 10, poster })
    const prepared = await prepareDraft(file('a.mp4', 'video/mp4', 1000), { kind: 'video', mimeType: 'video/mp4' })
    expect(prepared.posterBase64).toBeNull()
  })
})
