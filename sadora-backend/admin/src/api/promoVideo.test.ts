import { describe, expect, it, vi } from 'vitest'
import { ApiFailure } from './client'
import { promoVideoProblem, uploadPromoVideo } from './promoVideo'

function reply(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), { status })
}

const view = { pageUrl: 'https://sadora.app/video/', video: null, maxBytes: 100, chunkBytes: 4 }

describe('promoVideoProblem', () => {
  it('lets an MP4 or WebM of a fitting size through', () => {
    expect(promoVideoProblem({ name: 'Reklama.MP4', size: 10 }, 100)).toBeNull()
    expect(promoVideoProblem({ name: 'a.webm', size: 100 }, 100)).toBeNull()
  })

  it('names what is wrong with the rest', () => {
    expect(promoVideoProblem({ name: 'a.mov', size: 10 }, 100)).toContain('MP4')
    expect(promoVideoProblem({ name: 'a.mp4', size: 0 }, 100)).toContain("bo'sh")
    expect(promoVideoProblem({ name: 'a.mp4', size: 5 * 1024 * 1024 }, 3 * 1024 * 1024)).toContain('3 MB')
  })
})

describe('uploadPromoVideo', () => {
  const file = new File([new Uint8Array(10)], 'reklama.mp4', { type: 'video/mp4' })

  it('sends the file in pieces, each at its offset, and completes', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(reply(200, { uploadId: 'u1', receivedBytes: 0 }))
      .mockResolvedValueOnce(reply(200, { uploadId: 'u1', receivedBytes: 4 }))
      .mockResolvedValueOnce(reply(200, { uploadId: 'u1', receivedBytes: 8 }))
      .mockResolvedValueOnce(reply(200, { uploadId: 'u1', receivedBytes: 10 }))
      .mockResolvedValueOnce(reply(200, view))
    vi.stubGlobal('fetch', fetchMock)
    const progress: number[] = []

    const result = await uploadPromoVideo(file, 4, (sent) => progress.push(sent))

    expect(result).toEqual(view)
    expect(progress).toEqual([4, 8, 10])
    const calls = fetchMock.mock.calls as unknown as Array<[string, RequestInit]>
    expect(calls.map(([path, init]) => `${init.method} ${path}`)).toEqual([
      'POST /v1/admin/promo-video/uploads',
      'PUT /v1/admin/promo-video/uploads/u1?offset=0',
      'PUT /v1/admin/promo-video/uploads/u1?offset=4',
      'PUT /v1/admin/promo-video/uploads/u1?offset=8',
      'POST /v1/admin/promo-video/uploads/u1/complete',
    ])
    expect(calls[0]![1].body).toBe('{"fileName":"reklama.mp4","sizeBytes":10}')
    expect((calls[3]![1].body as Blob).size).toBe(2)
    expect(calls[1]![1].headers).toMatchObject({ 'Content-Type': 'application/octet-stream' })
  })

  it('sends a piece again when the connection drops, and gives up on a refusal', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(reply(200, { uploadId: 'u2', receivedBytes: 0 }))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(reply(502))
      .mockResolvedValueOnce(reply(200, { uploadId: 'u2', receivedBytes: 10 }))
      .mockResolvedValueOnce(reply(200, view))
    vi.stubGlobal('fetch', fetchMock)
    const pause = vi.fn(async () => {})

    await expect(uploadPromoVideo(file, 10, () => {}, undefined, pause)).resolves.toEqual(view)
    expect(pause).toHaveBeenCalledTimes(2)

    const refused = vi
      .fn()
      .mockResolvedValueOnce(reply(200, { uploadId: 'u3', receivedBytes: 0 }))
      .mockResolvedValueOnce(reply(409, { error: { code: 'conflict', message: 'Yuklash tartibi buzildi — qaytadan boshlang' } }))
    vi.stubGlobal('fetch', refused)
    const failure = await uploadPromoVideo(file, 10, () => {}, undefined, pause).catch((cause: unknown) => cause)
    expect(failure).toBeInstanceOf(ApiFailure)
    expect((failure as ApiFailure).code).toBe('conflict')
    expect(refused).toHaveBeenCalledTimes(2)
  })
})
