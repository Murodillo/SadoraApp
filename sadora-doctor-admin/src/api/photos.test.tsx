import { render, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Avatar, initialOf } from '../components/ui'
import { apiError, mockApi } from '../test/http'
import { signIn } from '../test/render'
import { acquirePhoto, clearPhotoCache, IDLE_PHOTOS, peekPhoto, photoCacheSize, releasePhoto } from './photos'

/** A photo as the server answers one: JPEG bytes, not JSON. */
function jpeg(): Response {
  return new Response(new Uint8Array([0xff, 0xd8, 0xff]), {
    status: 200,
    headers: { 'Content-Type': 'image/jpeg' },
  })
}

// jsdom has no object URLs; these hand out numbered ones and remember what was revoked.
let made = 0
const revoked: string[] = []
beforeEach(() => {
  made = 0
  revoked.length = 0
  Object.assign(URL, {
    createObjectURL: vi.fn(() => `blob:photo-${++made}`),
    revokeObjectURL: vi.fn((url: string) => revoked.push(url)),
  })
})
afterEach(() => {
  clearPhotoCache()
  delete (URL as Partial<typeof URL>).createObjectURL
  delete (URL as Partial<typeof URL>).revokeObjectURL
})

describe('Avatar', () => {
  it('draws the initial on its tint when there is no photo, and asks the server nothing', () => {
    const api = mockApi({})
    const { container } = render(<Avatar name="madina" tint={2} url={null} />)
    const avatar = container.firstElementChild as HTMLElement
    expect(avatar).toHaveTextContent('M')
    expect(avatar.style.background).toBe('var(--c3)')
    expect(avatar.querySelector('img')).toBeNull()
    expect(api.calls).toHaveLength(0)
  })

  it('fetches the photo with her token and draws it in place of the initial', async () => {
    signIn()
    const api = mockApi({ 'GET /v1/community/conversations/c1/photo': jpeg() })
    const { container } = render(<Avatar name="Madina" tint={1} url="/v1/community/conversations/c1/photo?v=17" size={44} />)
    const avatar = container.firstElementChild as HTMLElement

    // The initial stands in while the photo is on its way.
    expect(avatar).toHaveTextContent('M')
    await waitFor(() => expect(avatar.querySelector('img')).toHaveAttribute('src', 'blob:photo-1'))
    expect(avatar).toHaveClass('photo')
    expect(avatar).not.toHaveTextContent('M')
    expect(avatar.style.width).toBe('44px')

    const [call] = api.callsTo('GET', '/v1/community/conversations/c1/photo')
    expect(call!.headers.Authorization).toBe('Bearer access-1')
    expect(call!.search.get('v')).toBe('17')
  })

  it('keeps the initial when the photo cannot be had', async () => {
    signIn()
    const api = mockApi({ 'GET /v1/doctors/d1/photo': apiError(404, 'not_found', 'Topilmadi') })
    const { container } = render(<Avatar name="Dilnoza" doctor url="/v1/doctors/d1/photo?v=1" />)

    await waitFor(() => expect(api.callsTo('GET', '/v1/doctors/d1/photo')).toHaveLength(1))
    await Promise.resolve()
    const avatar = container.firstElementChild as HTMLElement
    expect(avatar).toHaveTextContent('D')
    expect(avatar).toHaveClass('doctor')
    expect(avatar.querySelector('img')).toBeNull()
    // A failure is not cached: the next one to show it asks again.
    await waitFor(() => expect(photoCacheSize().held).toBe(0))
  })

  it('shows one photo in three places for one request, and a new version as a new photo', async () => {
    signIn()
    const api = mockApi({ 'GET /v1/community/conversations/c1/photo': () => jpeg() })
    const { rerender } = render(
      <>
        <Avatar name="A" url="/v1/community/conversations/c1/photo?v=1" />
        <Avatar name="A" url="/v1/community/conversations/c1/photo?v=1" />
        <Avatar name="A" url="/v1/community/conversations/c1/photo?v=1" />
      </>,
    )
    await waitFor(() => expect(document.querySelectorAll('img')).toHaveLength(3))
    expect(api.callsTo('GET', '/v1/community/conversations/c1/photo')).toHaveLength(1)

    // She changed her photo: the URL's version moved, so it is fetched afresh.
    rerender(<Avatar name="A" url="/v1/community/conversations/c1/photo?v=2" />)
    await waitFor(() => expect(document.querySelector('img')).toHaveAttribute('src', 'blob:photo-2'))
    expect(api.callsTo('GET', '/v1/community/conversations/c1/photo')).toHaveLength(2)
  })

  it('reuses a photo shown before without asking again', async () => {
    signIn()
    const api = mockApi({ 'GET /v1/doctors/d1/photo': () => jpeg() })
    const first = render(<Avatar name="D" doctor url="/v1/doctors/d1/photo?v=5" />)
    await waitFor(() => expect(first.container.querySelector('img')).not.toBeNull())
    first.unmount()

    // Back on the page: drawn at once from the cache, no flash of the initial.
    const again = render(<Avatar name="D" doctor url="/v1/doctors/d1/photo?v=5" />)
    expect(again.container.querySelector('img')).toHaveAttribute('src', 'blob:photo-1')
    expect(api.callsTo('GET', '/v1/doctors/d1/photo')).toHaveLength(1)
  })
})

describe('initialOf', () => {
  it('is the first letter, capitalised, or a question mark', () => {
    expect(initialOf('  nodira')).toBe('N')
    expect(initialOf('')).toBe('?')
    expect(initialOf('   ')).toBe('?')
  })
})

describe('the photo cache', () => {
  it('revokes the oldest unshown photo once the idle line is full', async () => {
    signIn()
    mockApi({ 'GET /v1/doctors/d1/photo': () => jpeg() })
    const urls = Array.from({ length: IDLE_PHOTOS + 2 }, (_, index) => `/v1/doctors/d1/photo?v=${index}`)
    for (const url of urls) await acquirePhoto(url)
    expect(photoCacheSize()).toEqual({ held: urls.length, idle: 0 })

    // Still shown: nothing is revoked, however many there are.
    expect(revoked).toHaveLength(0)

    for (const url of urls) releasePhoto(url)
    expect(photoCacheSize()).toEqual({ held: IDLE_PHOTOS, idle: IDLE_PHOTOS })
    expect(revoked).toEqual(['blob:photo-1', 'blob:photo-2'])
    expect(peekPhoto(urls[0]!)).toBeNull()
    expect(peekPhoto(urls.at(-1)!)).toBe(`blob:photo-${urls.length}`)
  })

  it('takes a photo shown again out of the idle line', async () => {
    signIn()
    mockApi({ 'GET /v1/doctors/d1/photo': () => jpeg() })
    await acquirePhoto('/v1/doctors/d1/photo?v=1')
    releasePhoto('/v1/doctors/d1/photo?v=1')
    expect(photoCacheSize().idle).toBe(1)
    await acquirePhoto('/v1/doctors/d1/photo?v=1')
    expect(photoCacheSize()).toEqual({ held: 1, idle: 0 })
  })

  it('revokes everything when the session ends, and drops an answer that arrives after', async () => {
    signIn()
    let answer: (response: Response) => void = () => {}
    mockApi({
      'GET /v1/doctors/d1/photo': () => jpeg(),
      'GET /v1/doctors/d2/photo': () => new Promise<Response>((resolve) => (answer = resolve)),
    })
    await acquirePhoto('/v1/doctors/d1/photo?v=1')
    const late = acquirePhoto('/v1/doctors/d2/photo?v=1')

    clearPhotoCache()
    expect(revoked).toEqual(['blob:photo-1'])
    expect(photoCacheSize().held).toBe(0)

    answer(jpeg())
    expect(await late).toBeNull()
    expect(photoCacheSize().held).toBe(0)
    expect(made).toBe(1)
  })
})
