import { waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiError, json, mockApi } from '../test/http'
import type { Call } from '../test/http'
import { signIn } from '../test/render'
import type { DraftMedia } from './mediaDraft'
import { clearMediaSends, currentSends, discardMediaSend, queueMediaSend, retryMediaSend, sendsInFlight } from './mediaSend'
import { peekPhoto } from './photos'
import type { DirectMessage, MediaKind, MessageMedia } from './types'

let made = 0
const revoked: string[] = []
beforeEach(() => {
  made = 0
  revoked.length = 0
  Object.assign(URL, {
    createObjectURL: vi.fn(() => `blob:media-${++made}`),
    revokeObjectURL: vi.fn((url: string) => revoked.push(url)),
  })
})
afterEach(() => {
  clearMediaSends()
  delete (URL as Partial<typeof URL>).createObjectURL
  delete (URL as Partial<typeof URL>).revokeObjectURL
})

function draft(key: string, kind: MediaKind, bytes: string): DraftMedia {
  return {
    key,
    kind,
    mimeType: kind === 'photo' ? 'image/jpeg' : 'video/mp4',
    blob: new Blob([bytes]),
    width: 400,
    height: 300,
    durationMs: kind === 'video' ? 42_000 : null,
    previewUrl: `blob:preview-${key}`,
    posterBase64: kind === 'video' ? 'cG9zdGVy' : null,
    name: `${key}.bin`,
  }
}

/** Uploads numbered as they start; every piece is 4 bytes; finishing answers the item. */
function mediaServer(options: { failPieceOnce?: { upload: string; offset: number }; sendStatus?: () => Response | null } = {}) {
  let next = 0
  const uploads = new Map<string, { kind: MediaKind; size: number; received: number }>()
  let failure = options.failPieceOnce
  const mediaOf = (id: string): MessageMedia => {
    const upload = uploads.get(id)!
    return { id: `m-${id}`, kind: upload.kind, width: 400, height: 300, sizeBytes: upload.size, hasThumbnail: true }
  }
  const view = (id: string) => {
    const upload = uploads.get(id)!
    return { id, kind: upload.kind, sizeBytes: upload.size, receivedBytes: upload.received, chunkBytes: 4 }
  }
  const routes: Record<string, (call: Call) => unknown> = {
    'POST /v1/media/uploads': (call) => {
      const id = `u${++next}`
      const body = call.body as { kind: MediaKind; sizeBytes: number }
      uploads.set(id, { kind: body.kind, size: body.sizeBytes, received: 0 })
      return view(id)
    },
    'POST /v1/community/conversations/c1/messages': (call) => {
      const refused = options.sendStatus?.()
      if (refused) return refused
      const body = call.body as { body: string; mediaIds: string[] }
      const message: DirectMessage = {
        id: 'line-1',
        body: body.body,
        createdAt: new Date().toISOString(),
        isMine: true,
        kind: 'media',
        media: body.mediaIds.map((id) => ({ id, kind: 'photo', width: 1, height: 1, sizeBytes: 1 })),
      }
      return json(message, 201)
    },
  }
  for (const id of ['u1', 'u2', 'u3']) {
    routes[`GET /v1/media/uploads/${id}`] = () => view(id)
    routes[`PUT /v1/media/uploads/${id}`] = (call) => {
      const offset = Number(call.search.get('offset'))
      if (failure && failure.upload === id && failure.offset === offset) {
        failure = undefined
        throw new TypeError('Failed to fetch')
      }
      uploads.get(id)!.received = offset + (call.raw as Blob).size
      return view(id)
    }
    routes[`POST /v1/media/uploads/${id}/complete`] = () => mediaOf(id)
    routes[`DELETE /v1/media/uploads/${id}`] = () => ({ ok: true })
  }
  return mockApi(routes as Parameters<typeof mockApi>[0])
}

describe('queueMediaSend', () => {
  it('shows the send at once, uploads every file in order, then sends one message with their ids', async () => {
    signIn()
    const api = mediaServer()
    const onSent = vi.fn()

    const key = queueMediaSend({
      conversationId: 'c1',
      caption: 'Natijalar',
      items: [draft('a', 'photo', '0123456789'), draft('b', 'video', 'abcdef')],
      onSent,
    })
    expect(currentSends().map((send) => [send.key, send.status])).toEqual([[key, 'uploading']])
    expect(sendsInFlight()).toBe(true)

    await waitFor(() => expect(onSent).toHaveBeenCalledTimes(1))
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')[0]!.body).toEqual({
      body: 'Natijalar',
      mediaIds: ['m-u1', 'm-u2'],
    })
    expect(api.callsTo('PUT', '/v1/media/uploads/u1').map((call) => call.search.get('offset'))).toEqual(['0', '4', '8'])
    expect(api.callsTo('POST', '/v1/media/uploads/u2/complete')[0]!.body).toEqual({ posterBase64: 'cG9zdGVy' })
    expect(onSent.mock.calls[0]![0]).toMatchObject({ id: 'line-1', kind: 'media' })
    expect(currentSends()).toEqual([])
    expect(sendsInFlight()).toBe(false)
    // The pictures she already had become the new line's thumbnails, not fetched back.
    expect(peekPhoto('/v1/media/m-u1/thumb')).toBe('blob:preview-a')
    expect(revoked).not.toContain('blob:preview-a')
  })

  it('fails with the reason, and the retry resumes the cut upload from the server count', async () => {
    signIn()
    const api = mediaServer({ failPieceOnce: { upload: 'u2', offset: 4 } })
    const onSent = vi.fn()

    const key = queueMediaSend({
      conversationId: 'c1',
      caption: '',
      items: [draft('a', 'photo', '0123'), draft('b', 'photo', '0123456789')],
      onSent,
    })
    await waitFor(() => expect(currentSends()[0]?.status).toBe('failed'))
    expect(currentSends()[0]!.error).toBe("Serverga ulanib bo'lmadi. Internet aloqasini tekshiring.")
    expect(currentSends()[0]!.items.map((item) => [item.uploadId, item.sentBytes, Boolean(item.media)])).toEqual([
      ['u1', 4, true],
      ['u2', 4, false],
    ])
    expect(sendsInFlight()).toBe(false)

    retryMediaSend(key)
    await waitFor(() => expect(onSent).toHaveBeenCalledTimes(1))
    // The finished photo is not sent again; the cut one asks where it stands and goes on from 4.
    expect(api.callsTo('POST', '/v1/media/uploads')).toHaveLength(2)
    expect(api.callsTo('PUT', '/v1/media/uploads/u1')).toHaveLength(1)
    expect(api.callsTo('GET', '/v1/media/uploads/u2')).toHaveLength(1)
    expect(api.callsTo('PUT', '/v1/media/uploads/u2').map((call) => call.search.get('offset'))).toEqual(['0', '4', '4', '8'])
  })

  it("words a refused send in the panel's terms and sends it again without uploading again", async () => {
    signIn()
    let refuse = true
    const api = mediaServer({
      sendStatus: () => (refuse ? apiError(403, 'feature_disabled', "Bu bo'lim hozircha yopiq", { flag: 'chat_media' }) : null),
    })
    const onSent = vi.fn()
    const key = queueMediaSend({ conversationId: 'c1', caption: '', items: [draft('a', 'video', '01')], onSent })

    await waitFor(() => expect(currentSends()[0]?.status).toBe('failed'))
    expect(currentSends()[0]!.error).toBe('Video va albom hozircha yoqilmagan')

    refuse = false
    retryMediaSend(key)
    await waitFor(() => expect(onSent).toHaveBeenCalled())
    expect(api.callsTo('POST', '/v1/media/uploads')).toHaveLength(1)
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(2)
  })

  it("says the day's allowance is spent when an upload cannot start", async () => {
    signIn()
    mockApi({ 'POST /v1/media/uploads': apiError(429, 'limit_reached', 'Limit tugadi', { feature: 'chat_media' }) })
    queueMediaSend({ conversationId: 'c1', caption: '', items: [draft('a', 'photo', '01')], onSent: vi.fn() })
    await waitFor(() => expect(currentSends()[0]?.error).toBe('Bugungi limit tugadi'))
  })

  it('drops a send on discard and deletes every upload it started on the server', async () => {
    signIn()
    const api = mediaServer({ failPieceOnce: { upload: 'u2', offset: 0 } })
    const key = queueMediaSend({
      conversationId: 'c1',
      caption: '',
      items: [draft('a', 'photo', '0123'), draft('b', 'photo', '0123')],
      onSent: vi.fn(),
    })
    await waitFor(() => expect(currentSends()[0]?.status).toBe('failed'))

    discardMediaSend(key)
    expect(currentSends()).toEqual([])
    expect(revoked).toEqual(expect.arrayContaining(['blob:preview-a', 'blob:preview-b']))
    await waitFor(() => expect(api.callsTo('DELETE', '/v1/media/uploads/u2')).toHaveLength(1))
    expect(api.callsTo('DELETE', '/v1/media/uploads/u1')).toHaveLength(1)
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(0)
  })

  it('stops what is on the wire when the session ends', async () => {
    signIn()
    let release: () => void = () => undefined
    const api = mockApi({
      'POST /v1/media/uploads': () =>
        new Promise((resolve) => {
          release = () => resolve({ id: 'u1', kind: 'photo', sizeBytes: 2, receivedBytes: 0, chunkBytes: 4 })
        }),
    })
    queueMediaSend({ conversationId: 'c1', caption: '', items: [draft('a', 'photo', '01')], onSent: vi.fn() })
    await waitFor(() => expect(api.calls).toHaveLength(1))

    clearMediaSends()
    release()
    expect(currentSends()).toEqual([])
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(api.callsTo('PUT', '/v1/media/uploads/u1')).toHaveLength(0)
  })
})
