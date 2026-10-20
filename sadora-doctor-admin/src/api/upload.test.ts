import { describe, expect, it, vi } from 'vitest'
import { apiError, mockApi } from '../test/http'
import { signIn } from '../test/render'
import type { Call } from '../test/http'
import { ApiFailure } from './client'
import type { MediaUpload, MessageMedia } from './types'
import { CHUNK_BYTES, nextPiece, overallProgress, remainingPieces, uploadFile } from './upload'

describe('nextPiece', () => {
  it('starts at 0 and cuts the file into whole pieces, the last one short', () => {
    expect(nextPiece(10, 0, 4)).toEqual({ offset: 0, end: 4 })
    expect(nextPiece(10, 8, 4)).toEqual({ offset: 8, end: 10 })
    expect(nextPiece(3 * CHUNK_BYTES + 5, 0, CHUNK_BYTES)).toEqual({ offset: 0, end: CHUNK_BYTES })
  })

  it('is done when the server holds the whole file', () => {
    expect(nextPiece(10, 10, 4)).toBeNull()
    expect(nextPiece(10, 12, 4)).toBeNull()
    expect(nextPiece(0, 0, 4)).toBeNull()
  })

  it('resumes on the piece boundary at or before what the server holds, never from 0', () => {
    expect(nextPiece(10, 4, 4)).toEqual({ offset: 4, end: 8 })
    // Half a piece arrived before the line dropped: that piece goes again, whole.
    expect(nextPiece(10, 6, 4)).toEqual({ offset: 4, end: 8 })
    expect(nextPiece(5 * CHUNK_BYTES, 2 * CHUNK_BYTES + 65_536, CHUNK_BYTES)).toEqual({
      offset: 2 * CHUNK_BYTES,
      end: 3 * CHUNK_BYTES,
    })
  })

  it('uses the standard piece when the server names none', () => {
    expect(nextPiece(CHUNK_BYTES + 1, 0, 0)).toEqual({ offset: 0, end: CHUNK_BYTES })
  })
})

describe('remainingPieces', () => {
  it('lists every piece still to send after what the server holds', () => {
    expect(remainingPieces(10, 0, 4)).toEqual([
      { offset: 0, end: 4 },
      { offset: 4, end: 8 },
      { offset: 8, end: 10 },
    ])
    expect(remainingPieces(10, 5, 4)).toEqual([
      { offset: 4, end: 8 },
      { offset: 8, end: 10 },
    ])
    expect(remainingPieces(10, 10, 4)).toEqual([])
  })

  it('cuts a 2.5 MiB video into three 1 MiB-aligned pieces', () => {
    const size = 2.5 * CHUNK_BYTES
    expect(remainingPieces(size, 0, CHUNK_BYTES).map((piece) => piece.offset)).toEqual([0, CHUNK_BYTES, 2 * CHUNK_BYTES])
  })
})

describe('overallProgress', () => {
  it('counts bytes, not files', () => {
    expect(overallProgress([{ sizeBytes: 100, sentBytes: 100 }, { sizeBytes: 900, sentBytes: 0 }])).toBe(0.1)
    expect(overallProgress([{ sizeBytes: 100, sentBytes: 50 }, { sizeBytes: 100, sentBytes: 100 }])).toBe(0.75)
  })

  it('stays between 0 and 1', () => {
    expect(overallProgress([])).toBe(0)
    expect(overallProgress([{ sizeBytes: 10, sentBytes: 40 }])).toBe(1)
  })
})

// ---------------------------------------------------------------- uploadFile against a fake server

const done: MessageMedia = { id: 'up-1', kind: 'video', width: 720, height: 1280, durationMs: 42_000, sizeBytes: 10, hasThumbnail: true }

/** An upload server that keeps what it is sent, in pieces of `chunk` bytes. */
function uploadServer(options: { chunk?: number; received?: number; failAt?: number; statusMissing?: boolean } = {}) {
  const chunk = options.chunk ?? 4
  let received = options.received ?? 0
  let failAt = options.failAt
  const state = (): MediaUpload => ({ id: 'up-1', kind: 'video', sizeBytes: 10, receivedBytes: received, chunkBytes: chunk })
  const api = mockApi({
    'POST /v1/media/uploads': () => {
      received = 0
      return state()
    },
    'GET /v1/media/uploads/up-1': () =>
      options.statusMissing ? apiError(404, 'not_found', 'Yuklash topilmadi — qaytadan boshlang') : state(),
    'PUT /v1/media/uploads/up-1': (call: Call) => {
      const offset = Number(call.search.get('offset'))
      if (offset === failAt) {
        failAt = undefined
        throw new TypeError('Failed to fetch')
      }
      if (offset > received) return apiError(409, 'conflict', 'Yuklash tartibi buzildi — qaytadan boshlang')
      received = offset + (call.raw as Blob).size
      return state()
    },
    'POST /v1/media/uploads/up-1/complete': done,
  })
  return { api, received: () => received }
}

const file = () => ({ kind: 'video' as const, mimeType: 'video/mp4', blob: new Blob(['0123456789']), posterBase64: 'cG9zdGVy' })

describe('uploadFile', () => {
  it('starts, sends every piece as raw bytes at its offset, and completes with the poster', async () => {
    signIn()
    const { api } = uploadServer()
    const progress = vi.fn()

    await expect(uploadFile(file(), undefined, progress)).resolves.toEqual(done)

    expect(api.callsTo('POST', '/v1/media/uploads')[0]!.body).toEqual({ kind: 'video', mimeType: 'video/mp4', sizeBytes: 10 })
    const pieces = api.callsTo('PUT', '/v1/media/uploads/up-1')
    expect(pieces.map((call) => call.search.get('offset'))).toEqual(['0', '4', '8'])
    expect(pieces.map((call) => (call.raw as Blob).size)).toEqual([4, 4, 2])
    expect(pieces[0]!.headers['Content-Type']).toBe('application/octet-stream')
    expect(pieces[0]!.headers.Authorization).toBe('Bearer access-1')
    expect(await (pieces[1]!.raw as Blob).text()).toBe('4567')
    expect(api.callsTo('POST', '/v1/media/uploads/up-1/complete')[0]!.body).toEqual({ posterBase64: 'cG9zdGVy' })
    expect(progress.mock.calls.map(([state]) => state.sentBytes)).toEqual([0, 4, 8, 10])
    expect(progress).toHaveBeenLastCalledWith({ uploadId: 'up-1', sentBytes: 10 })
  })

  it('resumes an upload from what the server holds instead of starting again', async () => {
    signIn()
    const { api } = uploadServer({ received: 6 })

    await uploadFile(file(), 'up-1', () => undefined)

    expect(api.callsTo('POST', '/v1/media/uploads')).toHaveLength(0)
    expect(api.callsTo('GET', '/v1/media/uploads/up-1')).toHaveLength(1)
    // 6 bytes held: the piece at 4 goes again whole, then the last.
    expect(api.callsTo('PUT', '/v1/media/uploads/up-1').map((call) => call.search.get('offset'))).toEqual(['4', '8'])
  })

  it('starts afresh when the server no longer knows the upload', async () => {
    signIn()
    const { api } = uploadServer({ statusMissing: true })

    await uploadFile(file(), 'up-1', () => undefined)

    expect(api.callsTo('POST', '/v1/media/uploads')).toHaveLength(1)
    expect(api.callsTo('PUT', '/v1/media/uploads/up-1').map((call) => call.search.get('offset'))).toEqual(['0', '4', '8'])
  })

  it('stops on a dropped connection, and the next try carries on from the server count', async () => {
    signIn()
    const { api } = uploadServer({ failAt: 8 })
    let uploadId: string | undefined

    const failure = await uploadFile(file(), undefined, (state) => {
      uploadId = state.uploadId
    }).catch((error: unknown) => error)
    expect(failure).toBeInstanceOf(ApiFailure)
    expect((failure as ApiFailure).code).toBe('network')
    expect(uploadId).toBe('up-1')

    await uploadFile(file(), uploadId, () => undefined)
    expect(api.callsTo('PUT', '/v1/media/uploads/up-1').map((call) => call.search.get('offset'))).toEqual(['0', '4', '8', '8'])
    expect(api.callsTo('POST', '/v1/media/uploads')).toHaveLength(1)
  })

  it('goes back to the server count when a piece is refused as out of step', async () => {
    signIn()
    let received = 0
    let lost = true
    const api = mockApi({
      'POST /v1/media/uploads': { id: 'up-1', kind: 'photo', sizeBytes: 10, receivedBytes: 0, chunkBytes: 4 },
      'GET /v1/media/uploads/up-1': () => ({ id: 'up-1', kind: 'photo', sizeBytes: 10, receivedBytes: received, chunkBytes: 4 }),
      'PUT /v1/media/uploads/up-1': (call: Call) => {
        const offset = Number(call.search.get('offset'))
        received = offset + (call.raw as Blob).size
        // The first piece lands but its answer is lost on the way back.
        if (lost) {
          lost = false
          return apiError(409, 'conflict', 'Yuklash tartibi buzildi — qaytadan boshlang')
        }
        return { id: 'up-1', kind: 'photo', sizeBytes: 10, receivedBytes: received, chunkBytes: 4 }
      },
      'POST /v1/media/uploads/up-1/complete': { ...done, kind: 'photo' },
    })

    await uploadFile({ kind: 'photo', mimeType: 'image/jpeg', blob: new Blob(['0123456789']) }, undefined, () => undefined)

    expect(api.callsTo('PUT', '/v1/media/uploads/up-1').map((call) => call.search.get('offset'))).toEqual(['0', '4', '8'])
    expect(api.callsTo('GET', '/v1/media/uploads/up-1')).toHaveLength(1)
    expect(api.callsTo('POST', '/v1/media/uploads/up-1/complete')[0]!.body).toEqual({ posterBase64: null })
  })
})
