import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import { ImageProblem, preparePhoto } from '../api/image'
import type { Conversation, DoctorAccount } from '../api/types'
import { apiError, mockApi } from '../test/http'
import { doctorAccount, renderApp, signIn } from '../test/render'

// The canvas work needs a real browser; here the prepared photo is handed over as is.
vi.mock('../api/image', async (original) => ({
  ...(await original<typeof import('../api/image')>()),
  preparePhoto: vi.fn(),
}))

const NUDGE = "Rasmingizni qo'ying — bemorlar rasmli shifokorga ko'proq yozadi"

function jpeg(): Response {
  return new Response(new Uint8Array([0xff, 0xd8, 0xff]), { status: 200, headers: { 'Content-Type': 'image/jpeg' } })
}

let made = 0
beforeEach(() => {
  made = 0
  Object.assign(URL, {
    createObjectURL: vi.fn(() => `blob:photo-${++made}`),
    revokeObjectURL: vi.fn(),
  })
  vi.mocked(preparePhoto).mockReset()
})
afterEach(() => {
  delete (URL as Partial<typeof URL>).createObjectURL
  delete (URL as Partial<typeof URL>).revokeObjectURL
})

function workspace(account: DoctorAccount, extra: Parameters<typeof mockApi>[0] = {}) {
  return mockApi({
    'GET /v1/doctor/me': () => account,
    'GET /v1/doctor/questions': [],
    'GET /v1/community/conversations': [],
    'GET /v1/doctors/doc-1/photo': () => jpeg(),
    ...extra,
  })
}

describe('asking her for a photo', () => {
  it('shows the nudge across the workspace while she has none, and leads to her profile', async () => {
    signIn()
    workspace(doctorAccount({ photoUrl: null }))
    renderApp(<AppRoutes />, { route: '/stats' })

    const nudge = (await screen.findByText(NUDGE)).parentElement!
    await userEvent.click(within(nudge).getByRole('link', { name: "Rasm qo'yish" }))

    // On the profile page itself the card below asks; the banner steps aside.
    expect(await screen.findByRole('heading', { name: 'Rasmim' })).toBeInTheDocument()
    expect(screen.queryByText(NUDGE)).not.toBeInTheDocument()
  })

  it('stays closed for the rest of the session once she closes it', async () => {
    signIn()
    workspace(doctorAccount({ photoUrl: null }))
    const first = renderApp(<AppRoutes />, { route: '/stats' })
    await screen.findByText(NUDGE)
    await userEvent.click(screen.getByRole('button', { name: 'Eslatmani yopish' }))
    expect(screen.queryByText(NUDGE)).not.toBeInTheDocument()
    first.unmount()

    renderApp(<AppRoutes />, { route: '/stats' })
    await screen.findByRole('navigation', { name: "Bo'limlar" })
    expect(screen.queryByText(NUDGE)).not.toBeInTheDocument()
  })

  it('does not ask a doctor who has one, and shows it in the header and the rail', async () => {
    signIn()
    const api = workspace(doctorAccount({ photoUrl: '/v1/doctors/doc-1/photo?v=3' }))
    renderApp(<AppRoutes />, { route: '/stats' })

    const header = await screen.findByRole('banner')
    await waitFor(() => expect(header.querySelector('.avatar img')).toHaveAttribute('src', 'blob:photo-1'))
    const nav = screen.getByRole('navigation', { name: "Bo'limlar" })
    await waitFor(() => expect(within(nav).getByRole('link', { name: 'Profil' }).querySelector('img')).not.toBeNull())
    expect(screen.queryByText(NUDGE)).not.toBeInTheDocument()
    // Fetched once for both places.
    expect(api.callsTo('GET', '/v1/doctors/doc-1/photo')).toHaveLength(1)
  })
})

describe('Profil — her photo', () => {
  const prepared = {
    upload: { imageBase64: 'AAAA', mimeType: 'image/jpeg' as const },
    width: 1600,
    height: 1200,
    bytes: 240_000,
    previewUrl: 'blob:preview',
  }

  it("previews, uploads with the server's refusal shown, then replaces and removes", async () => {
    signIn()
    let account = doctorAccount({ photoUrl: null })
    let refuse = true
    vi.mocked(preparePhoto).mockResolvedValue(prepared)
    const api = workspace(account, {
      'GET /v1/doctor/me': () => account,
      'PUT /v1/doctor/photo': () => {
        if (refuse) return apiError(400, 'validation_failed', "So'rov ma'lumotlari noto'g'ri", { image: 'Rasm juda kichik' })
        account = { ...account, photoUrl: '/v1/doctors/doc-1/photo?v=9' }
        return { photoUrl: account.photoUrl }
      },
      'DELETE /v1/doctor/photo': () => {
        account = { ...account, photoUrl: null }
        return { ok: true }
      },
    })
    renderApp(<AppRoutes />, { route: '/profile' })

    const card = (await screen.findByRole('heading', { name: 'Rasmim' })).closest('section')!
    expect(card).toHaveTextContent(
      "Yuzingiz aniq ko'rinadigan, professional rasm. Sadora xodimlari mos bo'lmagan rasmni olib tashlashi mumkin.",
    )
    expect(within(card).queryByRole('button', { name: 'Olib tashlash' })).not.toBeInTheDocument()

    // Chosen: shown as the circle patients will see, not sent yet.
    const file = new File(['x'], 'me.jpg', { type: 'image/jpeg' })
    await userEvent.upload(screen.getByLabelText('Rasm fayli'), file)
    expect(preparePhoto).toHaveBeenCalledWith(file)
    expect(within(card).getByRole('img', { name: 'Yangi rasm' }).querySelector('img')).toHaveAttribute('src', 'blob:preview')
    expect(api.callsTo('PUT', '/v1/doctor/photo')).toHaveLength(0)

    // Refused: the server's own words, under the photo.
    await userEvent.click(within(card).getByRole('button', { name: 'Rasmni saqlash' }))
    expect(await within(card).findByRole('alert')).toHaveTextContent('Rasm juda kichik')
    expect(api.callsTo('PUT', '/v1/doctor/photo')[0]!.body).toEqual({ imageBase64: 'AAAA', mimeType: 'image/jpeg' })

    refuse = false
    await userEvent.click(within(card).getByRole('button', { name: 'Rasmni saqlash' }))
    await waitFor(() => expect(screen.getAllByRole('status').at(-1)).toHaveTextContent('Rasm saqlandi'))
    expect(within(card).queryByRole('alert')).not.toBeInTheDocument()
    await waitFor(() => expect(card.querySelector('.avatar img')).toHaveAttribute('src', 'blob:photo-1'))
    expect(within(card).getByRole('button', { name: 'Rasmni almashtirish' })).toBeInTheDocument()

    // Removed, after she confirms.
    await userEvent.click(within(card).getByRole('button', { name: 'Olib tashlash' }))
    const dialog = screen.getByRole('dialog', { name: 'Rasmni olib tashlash' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Olib tashlash' }))
    await waitFor(() => expect(screen.getAllByRole('status').at(-1)).toHaveTextContent('Rasm olib tashlandi'))
    expect(api.callsTo('DELETE', '/v1/doctor/photo')).toHaveLength(1)
    expect(within(card).getByRole('button', { name: 'Rasm yuklash' })).toBeInTheDocument()
    expect(card.querySelector('.avatar img')).toBeNull()
    expect(card.querySelector('.avatar')).toHaveTextContent('D')
  })

  it('says why a chosen file cannot be used, and sends nothing', async () => {
    signIn()
    vi.mocked(preparePhoto).mockRejectedValue(new ImageProblem('Faqat JPEG yoki PNG rasm'))
    const api = workspace(doctorAccount({ photoUrl: null }))
    renderApp(<AppRoutes />, { route: '/profile' })

    const card = (await screen.findByRole('heading', { name: 'Rasmim' })).closest('section')!
    await userEvent.upload(screen.getByLabelText('Rasm fayli'), new File(['x'], 'me.png', { type: 'image/png' }))
    expect(await within(card).findByRole('alert')).toHaveTextContent('Faqat JPEG yoki PNG rasm')
    expect(api.callsTo('PUT', '/v1/doctor/photo')).toHaveLength(0)
  })
})

describe("Xabarlar — patients' photos", () => {
  const conversation = (id: string, name: string, photoUrl: string | null): Conversation => ({
    id,
    alias: name,
    tint: 1,
    lastMessage: 'Salom',
    lastMessageAt: new Date(Date.now() - 60_000).toISOString(),
    unread: 0,
    patient: { name, age: 30, lifeStage: 'cycle', photoUrl },
    consultation: {
      openedAt: new Date(Date.now() - 3_600_000).toISOString(),
      expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
      open: true,
    },
  })

  it('draws a consultation patient’s photo in the list, the chat header and the side panel, fetched once', async () => {
    signIn()
    const malika = conversation('c1', 'Malika Rahimova', '/v1/community/conversations/c1/photo?v=4')
    const nodira = conversation('c2', 'Nodira Aliyeva', null)
    const api = workspace(doctorAccount({ photoUrl: '/v1/doctors/doc-1/photo?v=1' }), {
      'GET /v1/community/conversations': () => [malika, nodira],
      'GET /v1/community/conversations/c1': () => ({ conversation: malika, messages: [] }),
      'GET /v1/community/conversations/c1/photo': () => jpeg(),
      'GET /v1/doctor/patients/c1/note': { body: '' },
      'GET /v1/doctor/patients/c1/history': { patient: malika.patient, sessions: [] },
      'GET /v1/doctor/quick-replies': [],
    })
    renderApp(<AppRoutes />, { route: '/messages' })

    const list = await screen.findByRole('region', { name: 'Suhbatlar' })
    const row = await within(list).findByRole('button', { name: /Malika Rahimova/ })
    await waitFor(() => expect(row.querySelector('.avatar img')).not.toBeNull())
    // No photo: her initial, on her tint.
    const other = within(list).getByRole('button', { name: /Nodira Aliyeva/ })
    expect(other.querySelector('.avatar img')).toBeNull()
    expect(other.querySelector('.avatar')).toHaveTextContent('N')

    await userEvent.click(row)
    const thread = await screen.findByRole('region', { name: 'Yozishma' })
    await waitFor(() => expect(thread.querySelector('.chat-head .avatar img')).not.toBeNull())
    const side = screen.getByRole('complementary', { name: 'Bemor' })
    await waitFor(() => expect(side.querySelector('.side-person .avatar img')).not.toBeNull())

    expect(api.callsTo('GET', '/v1/community/conversations/c1/photo')).toHaveLength(1)
    expect(api.callsTo('GET', '/v1/community/conversations/c2/photo')).toHaveLength(0)
  })
})
