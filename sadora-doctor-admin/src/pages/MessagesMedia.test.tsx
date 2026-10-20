import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import { prepareMessagePhoto } from '../api/image'
import { clearMediaSends } from '../api/mediaSend'
import type { Conversation, DirectMessage, MediaKind, MessageMedia } from '../api/types'
import { probeVideo } from '../api/video'
import { apiError, json, mockApi } from '../test/http'
import type { Call } from '../test/http'
import { doctorAccount, renderApp, signIn } from '../test/render'

vi.mock('../api/image', async (original) => ({
  ...(await original<typeof import('../api/image')>()),
  prepareMessagePhoto: vi.fn(),
}))
vi.mock('../api/video', () => ({ probeVideo: vi.fn() }))

const HOUR = 3_600_000
const MB = 1024 * 1024
const ago = (ms: number) => new Date(Date.now() - ms).toISOString()
const ahead = (ms: number) => new Date(Date.now() + ms).toISOString()

let made = 0
beforeEach(() => {
  made = 0
  Object.assign(URL, {
    createObjectURL: vi.fn(() => `blob:media-${++made}`),
    revokeObjectURL: vi.fn(),
  })
  vi.mocked(prepareMessagePhoto).mockResolvedValue({ blob: new Blob(['0123456789'], { type: 'image/jpeg' }), width: 2048, height: 1536 })
  vi.mocked(probeVideo).mockResolvedValue({
    durationMs: 42_000,
    width: 720,
    height: 1280,
    poster: new Blob(['poster'], { type: 'image/jpeg' }),
  })
})
afterEach(() => {
  clearMediaSends()
  vi.mocked(prepareMessagePhoto).mockReset()
  vi.mocked(probeVideo).mockReset()
  delete (URL as Partial<typeof URL>).createObjectURL
  delete (URL as Partial<typeof URL>).revokeObjectURL
})

function conversation(overrides: Partial<Conversation> = {}): Conversation {
  return {
    id: 'c1',
    alias: 'Malika Rahimova',
    tint: 1,
    lastMessage: 'Salom, doktor',
    lastMessageAt: ago(10 * 60_000),
    unread: 0,
    patient: { name: 'Malika Rahimova', age: 29, lifeStage: 'pregnancy' },
    consultation: { openedAt: ago(2 * HOUR), expiresAt: ahead(22 * HOUR), closedAt: null, open: true },
    lastMessageKind: 'text',
    lastMessageMediaCount: 0,
    ...overrides,
  }
}

function line(id: string, overrides: Partial<DirectMessage> = {}): DirectMessage {
  return { id, body: '', createdAt: ago(5 * 60_000), isMine: false, kind: 'text', ...overrides }
}

const photo = (id: string): MessageMedia => ({ id, kind: 'photo', width: 2048, height: 1536, sizeBytes: 900_000, hasThumbnail: true })
const clip: MessageMedia = { id: 'v1', kind: 'video', width: 720, height: 1280, durationMs: 84_000, sizeBytes: 18 * MB, hasThumbnail: false }

const jpeg = () => new Response(new Uint8Array([0xff, 0xd8, 0xff]), { status: 200, headers: { 'Content-Type': 'image/jpeg' } })

/**
 * One open consultation with Malika, a media server that hands out uploads u1, u2, … in
 * pieces of 4 bytes, and `hold` to keep a piece on the wire until the test lets it go.
 */
function mediaBackend(
  options: {
    chatMedia?: boolean
    messages?: DirectMessage[]
    hold?: { upload: string; offset: number }
    dropOnce?: { upload: string; offset: number }
    startRefusal?: Response
  } = {},
) {
  const messages = [...(options.messages ?? [line('m1', { body: 'Salom, doktor' })])]
  const uploads = new Map<string, { kind: MediaKind; size: number; received: number; media?: MessageMedia }>()
  let next = 0
  let release: () => void = () => undefined
  let drop = options.dropOnce
  const view = (id: string) => {
    const upload = uploads.get(id)!
    return { id, kind: upload.kind, sizeBytes: upload.size, receivedBytes: upload.received, chunkBytes: 4 }
  }
  const routes: Record<string, unknown> = {
    'GET /v1/doctor/me': doctorAccount(),
    'GET /v1/doctor/questions': [],
    'GET /v1/feature-flags': { flags: { chat_media: options.chatMedia ?? true }, evaluatedAt: new Date().toISOString(), ttlSeconds: 300 },
    'GET /v1/community/conversations': () => [conversation()],
    'GET /v1/community/conversations/c1': () => ({ conversation: conversation(), messages: [...messages] }),
    'POST /v1/community/conversations/c1/typing': { ok: true },
    'GET /v1/community/conversations/c1/prescriptions': [],
    'GET /v1/doctor/quick-replies': [],
    'GET /v1/doctor/patients/c1/note': { body: '' },
    'GET /v1/doctor/patients/c1/history': { sessions: [] },
    'POST /v1/media/uploads': (call: Call) => {
      if (options.startRefusal) return options.startRefusal
      const id = `u${++next}`
      const body = call.body as { kind: MediaKind; sizeBytes: number }
      uploads.set(id, { kind: body.kind, size: body.sizeBytes, received: 0 })
      return view(id)
    },
    'POST /v1/community/conversations/c1/messages': (call: Call) => {
      const body = call.body as { body: string; mediaIds: string[] }
      const media = body.mediaIds.map((id) => [...uploads.values()].find((upload) => upload.media?.id === id)!.media!)
      const sent = line(`m${messages.length + 1}`, { body: body.body, isMine: true, kind: 'media', media, createdAt: new Date().toISOString() })
      messages.push(sent)
      return json(sent, 201)
    },
    'GET /v1/media/p1/thumb': () => jpeg(),
    'GET /v1/media/p2/thumb': () => jpeg(),
    'GET /v1/media/p1/link': () => ({ url: `/v1/media/p1/file?v=full&exp=9&sig=s${Math.random()}`, expiresAt: ahead(900_000) }),
    'GET /v1/media/v1/link': { url: '/v1/media/v1/file?v=full&exp=9&sig=v', expiresAt: ahead(900_000) },
    'GET /v1/media/p2/link': { url: '/v1/media/p2/file?v=full&exp=9&sig=p', expiresAt: ahead(900_000) },
    'GET /v1/media/p1/file': () => jpeg(),
  }
  for (const id of ['u1', 'u2', 'u3']) {
    routes[`GET /v1/media/uploads/${id}`] = () => view(id)
    routes[`PUT /v1/media/uploads/${id}`] = async (call: Call) => {
      const offset = Number(call.search.get('offset'))
      if (drop && drop.upload === id && drop.offset === offset) {
        drop = undefined
        throw new TypeError('Failed to fetch')
      }
      if (options.hold && options.hold.upload === id && options.hold.offset === offset) {
        await new Promise<void>((resolve) => (release = resolve))
      }
      uploads.get(id)!.received = offset + (call.raw as Blob).size
      return view(id)
    }
    routes[`POST /v1/media/uploads/${id}/complete`] = () => {
      const upload = uploads.get(id)!
      upload.media = { id: `m-${id}`, kind: upload.kind, width: 400, height: 300, sizeBytes: upload.size, hasThumbnail: true }
      if (upload.kind === 'video') upload.media = { ...upload.media, durationMs: 42_000, width: 720, height: 1280 }
      return upload.media
    }
    routes[`DELETE /v1/media/uploads/${id}`] = { ok: true }
  }
  const api = mockApi(routes as Parameters<typeof mockApi>[0])
  return { ...api, release: () => release() }
}

async function openThread() {
  signIn()
  renderApp(<AppRoutes />, { route: '/messages?c=c1' })
  return screen.findByRole('region', { name: 'Yozishma' })
}

function fileInput(): HTMLInputElement {
  return document.querySelector<HTMLInputElement>('.chat-composer input[type="file"]')!
}

const photoFile = (name = 'tahlil.jpg') => new File(['raw-photo'], name, { type: 'image/jpeg' })
const videoFile = (name = 'klip.mp4') => new File(['vvvvvv'], name, { type: 'video/mp4' })

describe('Xabarlar — photos and videos in the list', () => {
  it('words a photo, a video and an album, with the caption after it', async () => {
    mockApi({
      'GET /v1/doctor/me': doctorAccount(),
      'GET /v1/doctor/questions': [],
      'GET /v1/community/conversations': [
        conversation({ id: 'c1', alias: 'Malika', patient: null, lastMessageKind: 'media', lastMessage: 'Mana', lastMessageMedia: photo('p1'), lastMessageMediaCount: 1 }),
        conversation({ id: 'c2', alias: 'Nodira', patient: null, lastMessageKind: 'media', lastMessage: '', lastMessageMedia: { ...clip, durationMs: 42_000 }, lastMessageMediaCount: 1 }),
        conversation({ id: 'c3', alias: 'Zarina', patient: null, lastMessageKind: 'media', lastMessage: '', lastMessageMedia: photo('p2'), lastMessageMediaCount: 3, lastMessageVideoCount: 1 }),
      ],
    })
    signIn()
    renderApp(<AppRoutes />, { route: '/messages' })
    const list = await screen.findByRole('region', { name: 'Suhbatlar' })
    expect(await within(list).findByRole('button', { name: /Malika/ })).toHaveTextContent('📷 Rasm · Mana')
    expect(within(list).getByRole('button', { name: /Nodira/ })).toHaveTextContent('🎥 Video · 0:42')
    expect(within(list).getByRole('button', { name: /Zarina/ })).toHaveTextContent('📷 2 ta rasm, 1 ta video')
  })
})

describe('Xabarlar — an album in the thread', () => {
  const album = line('m2', { kind: 'media', body: 'Natijalarim', media: [photo('p1'), clip, photo('p2')] })

  it('draws labelled tiles, fetches thumbnails with her token, and a quiet tile for a video without a poster', async () => {
    const api = mediaBackend({ messages: [album] })
    const thread = await openThread()

    const first = await within(thread).findByRole('button', { name: 'Rasm 1 / 3' })
    const video = within(thread).getByRole('button', { name: 'Video 2 / 3, 1 daqiqa 24 soniya' })
    expect(within(thread).getByRole('button', { name: 'Rasm 3 / 3' })).toBeInTheDocument()
    expect(within(thread).getByText('Natijalarim')).toBeInTheDocument()
    expect(video).toHaveTextContent('1:24 · 18 MB')
    expect(video.querySelector('.album-blank')).not.toBeNull()
    expect(first.closest('.album')).toHaveClass('three')

    await waitFor(() => expect(first.querySelector('img')).toHaveAttribute('src', expect.stringMatching(/^blob:media-/)))
    expect(api.callsTo('GET', '/v1/media/p1/thumb')[0]!.headers.Authorization).toBe('Bearer access-1')
    expect(api.callsTo('GET', '/v1/media/v1/thumb')).toHaveLength(0)
  })

  it('shows four tiles of a longer album and counts the rest on the last', async () => {
    mediaBackend({ messages: [line('m2', { kind: 'media', media: ['p1', 'p2', 'p3', 'p4', 'p5', 'p6'].map(photo) })] })
    const thread = await openThread()

    const last = await within(thread).findByRole('button', { name: 'Rasm 4 / 6, yana 2 ta' })
    expect(last).toHaveTextContent('+2')
    expect(last.closest('.album')).toHaveClass('grid')
    expect(within(thread).getAllByRole('button', { name: /^Rasm \d \/ 6/ })).toHaveLength(4)
  })

  it('opens the viewer on a fresh link, walks the album with the arrows and the keys, and returns the focus', async () => {
    const api = mediaBackend({ messages: [album] })
    const thread = await openThread()
    const first = await within(thread).findByRole('button', { name: 'Rasm 1 / 3' })
    await userEvent.click(first)

    const viewer = await screen.findByRole('dialog', { name: 'Rasm 1 / 3' })
    await waitFor(() =>
      expect(within(viewer).getByRole('img', { name: 'Rasm 1 / 3: Natijalarim' })).toHaveAttribute(
        'src',
        expect.stringMatching(/^\/v1\/media\/p1\/file\?v=full/),
      ),
    )
    expect(within(viewer).getByText('1 / 3')).toBeInTheDocument()
    expect(within(viewer).getByText('Natijalarim')).toBeInTheDocument()
    expect(within(viewer).getByRole('button', { name: 'Oldingi' })).toBeDisabled()

    await userEvent.click(within(viewer).getByRole('button', { name: 'Keyingi' }))
    expect(viewer).toHaveAccessibleName('Video 2 / 3, 1 daqiqa 24 soniya')
    await waitFor(() => expect(viewer.querySelector('video')).toHaveAttribute('src', '/v1/media/v1/file?v=full&exp=9&sig=v'))
    const player = viewer.querySelector('video')!
    expect(player).toHaveAttribute('controls')
    expect(player.autoplay).toBe(true)

    fireEvent.keyDown(viewer, { key: 'ArrowRight' })
    expect(viewer).toHaveAccessibleName('Rasm 3 / 3')
    expect(within(viewer).getByRole('button', { name: 'Keyingi' })).toBeDisabled()
    fireEvent.keyDown(viewer, { key: 'ArrowRight' })
    expect(viewer).toHaveAccessibleName('Rasm 3 / 3')

    fireEvent.keyDown(viewer, { key: 'ArrowLeft' })
    fireEvent.keyDown(viewer, { key: 'ArrowLeft' })
    expect(viewer).toHaveAccessibleName('Rasm 1 / 3')
    // Every look asks for its own address: the first one may have run out.
    await waitFor(() => expect(api.callsTo('GET', '/v1/media/p1/link')).toHaveLength(2))

    // Tab stays inside the viewer.
    const close = within(viewer).getByRole('button', { name: 'Yopish' })
    const buttons = within(viewer).getAllByRole('button').filter((button) => !(button as HTMLButtonElement).disabled)
    buttons.at(-1)!.focus()
    fireEvent.keyDown(buttons.at(-1)!, { key: 'Tab' })
    expect(document.activeElement).toBe(buttons[0])
    expect(close).toBeInTheDocument()

    fireEvent.keyDown(viewer, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(document.activeElement).toBe(first)
  })

  it('downloads the file shown, from a signed address and without her token', async () => {
    const api = mediaBackend({ messages: [album] })
    const saved: string[] = []
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      saved.push(this.download)
    })
    const thread = await openThread()
    await userEvent.click(await within(thread).findByRole('button', { name: 'Rasm 1 / 3' }))
    const viewer = await screen.findByRole('dialog', { name: 'Rasm 1 / 3' })

    await userEvent.click(within(viewer).getByRole('button', { name: 'Yuklab olish' }))

    await waitFor(() => expect(saved).toEqual(['sadora-rasm-p1.jpg']))
    const [file] = api.callsTo('GET', '/v1/media/p1/file')
    expect(file!.headers.Authorization).toBeUndefined()
  })
})

describe('Xabarlar — sending photos and videos', () => {
  it('previews the picked files, sends them with a caption, and shows the album while it uploads', async () => {
    const api = mediaBackend({ hold: { upload: 'u2', offset: 4 } })
    const thread = await openThread()
    await within(thread).findByText('Salom, doktor')

    await userEvent.click(within(thread).getByRole('button', { name: 'Biriktirish' }))
    expect(await within(thread).findByRole('menuitem', { name: '🖼 Rasm yoki video' })).toBeInTheDocument()
    expect(fileInput().multiple).toBe(true)
    expect(fileInput().accept).toContain('video/mp4')

    fireEvent.change(fileInput(), { target: { files: [photoFile(), photoFile('ikkinchi.jpg'), videoFile()] } })
    const preview = await screen.findByRole('dialog', { name: 'Yuborish: 2 ta rasm, 1 ta video' })
    expect(within(preview).getByRole('button', { name: "Yana qo'shish" })).toBeInTheDocument()

    await userEvent.click(within(preview).getByRole('button', { name: 'Olib tashlash: Rasm 2 / 3' }))
    expect(preview).toHaveAccessibleName('Yuborish: 1 ta rasm, 1 ta video')
    await userEvent.type(within(preview).getByLabelText('Izoh (ixtiyoriy)'), 'Natijalar')
    await userEvent.click(within(preview).getByRole('button', { name: 'Yuborish' }))

    // In the thread at once, from what the tab already has.
    const pending = await waitFor(() => {
      const bubble = thread.querySelector('.msg.pending')
      expect(bubble).not.toBeNull()
      return bubble as HTMLElement
    })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(within(pending).getByRole('img', { name: 'Rasm 1 / 2' })).toBeInTheDocument()
    expect(within(pending).getByRole('img', { name: 'Video 2 / 2, 42 soniya' })).toBeInTheDocument()
    expect(within(pending).getByText('Natijalar')).toBeInTheDocument()
    expect(within(pending).getByRole('progressbar', { name: 'Yuklanmoqda' })).toBeInTheDocument()
    expect(within(pending).getByRole('button', { name: 'Yuborishni bekor qilish' })).toBeInTheDocument()
    await waitFor(() => expect(api.callsTo('PUT', '/v1/media/uploads/u2')).toHaveLength(2))
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(0)

    api.release()
    await waitFor(() => expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(1))
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')[0]!.body).toEqual({
      body: 'Natijalar',
      mediaIds: ['m-u1', 'm-u2'],
    })
    expect(api.callsTo('POST', '/v1/media/uploads').map((call) => call.body)).toEqual([
      { kind: 'photo', mimeType: 'image/jpeg', sizeBytes: 10 },
      { kind: 'video', mimeType: 'video/mp4', sizeBytes: 6 },
    ])
    expect(api.callsTo('POST', '/v1/media/uploads/u2/complete')[0]!.body).toEqual({ posterBase64: btoa('poster') })

    await waitFor(() => expect(thread.querySelector('.msg.pending')).toBeNull())
    expect(await within(thread).findByRole('button', { name: 'Rasm 1 / 2' })).toBeInTheDocument()
    expect(within(thread).getByRole('button', { name: 'Video 2 / 2, 42 soniya' })).toBeInTheDocument()
    // Her own pictures are shown from the tab, not fetched back.
    expect(api.callsTo('GET', '/v1/media/m-u1/thumb')).toHaveLength(0)
  })

  it('takes the typed text as the caption and empties the box after sending', async () => {
    const api = mediaBackend()
    const thread = await openThread()
    await within(thread).findByText('Salom, doktor')
    await userEvent.type(within(thread).getByLabelText('Xabar'), 'Shu rasmga qarang')

    fireEvent.change(fileInput(), { target: { files: [photoFile()] } })
    const preview = await screen.findByRole('dialog', { name: 'Yuborish: Rasm' })
    expect(within(preview).getByLabelText('Izoh (ixtiyoriy)')).toHaveValue('Shu rasmga qarang')
    fireEvent.keyDown(within(preview).getByLabelText('Izoh (ixtiyoriy)'), { key: 'Enter' })

    await waitFor(() => expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(1))
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')[0]!.body).toEqual({
      body: 'Shu rasmga qarang',
      mediaIds: ['m-u1'],
    })
    expect(within(thread).getByLabelText('Xabar')).toHaveValue('')
  })

  it('with chat_media off offers one photo and leaves videos out', async () => {
    const api = mediaBackend({ chatMedia: false })
    const thread = await openThread()
    await within(thread).findByText('Salom, doktor')
    await waitFor(() => expect(api.callsTo('GET', '/v1/feature-flags')).toHaveLength(1))

    await userEvent.click(within(thread).getByRole('button', { name: 'Biriktirish' }))
    expect(within(thread).getByRole('menuitem', { name: '📷 Rasm' })).toBeInTheDocument()
    expect(fileInput().multiple).toBe(false)
    expect(fileInput().accept).toBe('image/*')

    fireEvent.change(fileInput(), { target: { files: [videoFile(), photoFile(), photoFile('b.jpg')] } })
    const preview = await screen.findByRole('dialog', { name: 'Yuborish: Rasm' })
    expect(await screen.findByText('Video va albom hozircha yoqilmagan: bitta rasm yuborish mumkin')).toBeInTheDocument()
    expect(within(preview).getAllByRole('button', { name: /^Olib tashlash/ })).toHaveLength(1)
    expect(within(preview).queryByRole('button', { name: "Yana qo'shish" })).not.toBeInTheDocument()
    expect(probeVideo).not.toHaveBeenCalled()
  })

  it('refuses a video over three minutes before it goes', async () => {
    vi.mocked(probeVideo).mockResolvedValue({ durationMs: 200_000, width: 720, height: 1280, poster: null })
    const api = mediaBackend()
    const thread = await openThread()
    await within(thread).findByText('Salom, doktor')

    fireEvent.change(fileInput(), { target: { files: [videoFile('uzun.mp4')] } })

    expect(await screen.findByText("uzun.mp4: Video 3 daqiqadan uzun bo'lmasin")).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.callsTo('POST', '/v1/media/uploads')).toHaveLength(0)
  })

  it('says a send failed, and "Qayta yuborish" carries the upload on from where the server stopped', async () => {
    const api = mediaBackend({ dropOnce: { upload: 'u1', offset: 4 } })
    const thread = await openThread()
    await within(thread).findByText('Salom, doktor')

    fireEvent.change(fileInput(), { target: { files: [photoFile()] } })
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yuborish' }))

    const alert = await within(thread).findByRole('alert')
    expect(alert).toHaveTextContent("Yuborilmadi · Serverga ulanib bo'lmadi")
    await userEvent.click(within(alert).getByRole('button', { name: 'Qayta yuborish' }))

    await waitFor(() => expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(1))
    expect(api.callsTo('POST', '/v1/media/uploads')).toHaveLength(1)
    expect(api.callsTo('GET', '/v1/media/uploads/u1')).toHaveLength(1)
    expect(api.callsTo('PUT', '/v1/media/uploads/u1').map((call) => call.search.get('offset'))).toEqual(['0', '4', '4', '8'])
    await waitFor(() => expect(within(thread).queryByRole('alert')).not.toBeInTheDocument())
  })

  it("says the day's allowance is spent, and a discarded send leaves nothing behind", async () => {
    const api = mediaBackend({ startRefusal: apiError(429, 'limit_reached', 'Limit tugadi', { feature: 'chat_media' }) })
    const thread = await openThread()
    await within(thread).findByText('Salom, doktor')

    fireEvent.change(fileInput(), { target: { files: [photoFile()] } })
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yuborish' }))

    const alert = await within(thread).findByRole('alert')
    expect(alert).toHaveTextContent('Yuborilmadi · Bugungi limit tugadi')
    await userEvent.click(within(alert).getByRole('button', { name: 'Bekor qilish' }))
    expect(thread.querySelector('.msg.pending')).toBeNull()
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(0)
  })

  it('cancels an upload on the wire and deletes it on the server', async () => {
    const api = mediaBackend({ hold: { upload: 'u1', offset: 4 } })
    const thread = await openThread()
    await within(thread).findByText('Salom, doktor')

    fireEvent.change(fileInput(), { target: { files: [photoFile()] } })
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yuborish' }))
    await waitFor(() => expect(api.callsTo('PUT', '/v1/media/uploads/u1')).toHaveLength(2))

    await userEvent.click(within(thread).getByRole('button', { name: 'Yuborishni bekor qilish' }))
    expect(thread.querySelector('.msg.pending')).toBeNull()
    await waitFor(() => expect(api.callsTo('DELETE', '/v1/media/uploads/u1')).toHaveLength(1))
    api.release()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(api.callsTo('PUT', '/v1/media/uploads/u1')).toHaveLength(2)
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(0)
  })
})
