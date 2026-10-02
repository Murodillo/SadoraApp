import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from '../App'
import type { DoctorEarnings, DoctorSettings, DoctorStats, QuickReply, UpdateDoctorSettingsRequest } from '../api/types'
import { apiError, json, mockApi } from '../test/http'
import { doctorAccount, renderApp, signIn } from '../test/render'

const base = {
  'GET /v1/doctor/me': doctorAccount(),
  'GET /v1/doctor/questions': [],
  'GET /v1/community/conversations': [],
}

function open(route: string) {
  signIn()
  renderApp(<AppRoutes />, { route })
}

describe('Ish vaqti va narx', () => {
  function settingsBackend(initial: Partial<DoctorSettings> = {}) {
    let settings: DoctorSettings = {
      priceMinor: 0,
      busy: false,
      hours: [{ weekday: 2, startMinute: 600, endMinute: 1440 }],
      timezone: 'Asia/Tashkent',
      acceptsConsultations: true,
      commissionPercent: 15,
      ...initial,
    }
    return mockApi({
      ...base,
      'GET /v1/doctor/settings': () => settings,
      'PUT /v1/doctor/settings': (call) => {
        const body = call.body as UpdateDoctorSettingsRequest
        settings = {
          ...settings,
          ...(body.priceMinor !== undefined ? { priceMinor: body.priceMinor } : {}),
          ...(body.busy !== undefined ? { busy: body.busy } : {}),
          ...(body.hours !== undefined ? { hours: body.hours } : {}),
        }
        return settings
      },
    })
  }

  it('is in the rail and shows her week, the commission and the midnight end', async () => {
    settingsBackend()
    open('/settings')
    const tuesday = await screen.findByRole('checkbox', { name: 'Seshanba' })
    const nav = screen.getByRole('navigation')
    expect(within(nav).getByRole('link', { name: 'Ish vaqti va narx' })).toHaveAttribute('aria-current', 'page')
    expect(tuesday).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Dushanba' })).not.toBeChecked()
    expect(screen.getByLabelText('Seshanba: boshlanishi')).toHaveValue('10:00')
    expect(screen.getByLabelText('Seshanba: tugashi')).toHaveValue('00:00')
    expect(screen.getByText('15%')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Saqlash' })).toBeDisabled()
  })

  it("saves the price in tiyin and the whole week, a new day starting 09:00–18:00", async () => {
    const api = settingsBackend()
    open('/settings')

    const price = await screen.findByLabelText("Narx, so'm")
    await userEvent.clear(price)
    await userEvent.type(price, '50000')
    expect(screen.getByText(/42 500 so'm har bir konsultatsiyadan/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Dushanba' }))
    await userEvent.click(screen.getByRole('button', { name: 'Saqlash' }))

    await waitFor(() => expect(api.callsTo('PUT', '/v1/doctor/settings')).toHaveLength(1))
    expect(api.callsTo('PUT', '/v1/doctor/settings')[0]!.body).toEqual({
      priceMinor: 5_000_000,
      hours: [
        { weekday: 1, startMinute: 540, endMinute: 1080 },
        { weekday: 2, startMinute: 600, endMinute: 1440 },
      ],
    })
    expect(await screen.findByText('Saqlandi')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText("Narx, so'm")).toHaveValue('50 000'))
  })

  it('refuses a price outside 0 or 1 000–2 000 000 and a day that ends before it starts', async () => {
    const api = settingsBackend()
    open('/settings')

    const price = await screen.findByLabelText("Narx, so'm")
    await userEvent.clear(price)
    await userEvent.type(price, '500')
    expect(screen.getByText(/1 000–2 000 000 so'm/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Saqlash' })).toBeDisabled()

    await userEvent.clear(price)
    await userEvent.type(price, '0')
    const start = screen.getByLabelText('Seshanba: boshlanishi')
    await userEvent.clear(start)
    await userEvent.type(start, '23:00')
    const end = screen.getByLabelText('Seshanba: tugashi')
    await userEvent.clear(end)
    await userEvent.type(end, '08:00')
    expect(screen.getByText("Boshlanish tugashdan oldin bo'lsin")).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Saqlash' })).toBeDisabled()
    expect(api.callsTo('PUT', '/v1/doctor/settings')).toHaveLength(0)
  })

  it('shows the server word on the price field', async () => {
    mockApi({
      ...base,
      'GET /v1/doctor/settings': { priceMinor: 0, busy: false, hours: [], timezone: 'Asia/Tashkent', acceptsConsultations: true, commissionPercent: 0 },
      'PUT /v1/doctor/settings': apiError(400, 'validation_failed', "Ma'lumot noto'g'ri", { priceMinor: 'Narx juda katta' }),
    })
    open('/settings')
    const price = await screen.findByLabelText("Narx, so'm")
    await userEvent.clear(price)
    await userEvent.type(price, '3000')
    await userEvent.click(screen.getByRole('button', { name: 'Saqlash' }))
    expect(await screen.findByText('Narx juda katta')).toBeInTheDocument()
  })

  it('switches "Bandman" at once with just that field', async () => {
    const api = settingsBackend()
    open('/settings')
    const busy = await screen.findByRole('switch', { name: 'Bandman' })
    expect(busy).not.toBeChecked()
    await userEvent.click(busy)
    await waitFor(() => expect(api.callsTo('PUT', '/v1/doctor/settings')).toHaveLength(1))
    expect(api.callsTo('PUT', '/v1/doctor/settings')[0]!.body).toEqual({ busy: true })
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Bandman' })).toBeChecked())
  })
})

describe('Statistika', () => {
  it('shows her numbers and the topics as bars', async () => {
    const stats: DoctorStats = {
      consultationsWeek: 3,
      consultationsMonth: 11,
      consultationsTotal: 42,
      openNow: 2,
      avgFirstReplyMinutes: 75,
      unansweredTotal: 1,
      rating: 4.75,
      ratingCount: 8,
      topTopics: [
        { topic: 'pregnancy', count: 20 },
        { topic: 'cycle', count: 5 },
      ],
      answersTotal: 25,
    }
    mockApi({ ...base, 'GET /v1/doctor/stats': stats })
    open('/stats')

    expect(await screen.findByText('1 soat 15 daqiqa')).toBeInTheDocument()
    expect(screen.getByText('★ 4.8')).toBeInTheDocument()
    expect(screen.getByText('8 ta baho')).toBeInTheDocument()
    const bars = screen.getByRole('list', { name: 'Mavzular' })
    const rows = within(bars).getAllByRole('listitem')
    expect(rows[0]).toHaveTextContent('Homiladorlik20')
    expect(rows[1]).toHaveTextContent('Sikl5')
    expect(rows[1]!.querySelector<HTMLElement>('.bar-fill')!.style.width).toBe('25%')
    expect(screen.getByRole('heading', { name: 'Statistika' })).toBeInTheDocument()
  })

  it('says so when she has not answered in the room yet', async () => {
    mockApi({
      ...base,
      'GET /v1/doctor/stats': {
        consultationsWeek: 0,
        consultationsMonth: 0,
        consultationsTotal: 0,
        openNow: 0,
        unansweredTotal: 0,
        ratingCount: 0,
        topTopics: [],
        answersTotal: 0,
      },
    })
    open('/stats')
    expect(await screen.findByText(/Hali chatdagi savollarga javob bermagansiz/)).toBeInTheDocument()
    expect(screen.getByText("hali baho yo'q")).toBeInTheDocument()
  })
})

describe('Daromad', () => {
  it("shows every sum in so'm, the consultations and the payouts", async () => {
    const earnings: DoctorEarnings = {
      currency: 'UZS',
      grossMinor: 10_000_000,
      commissionMinor: 1_500_000,
      netMinor: 8_500_000,
      paidOutMinor: 4_000_000,
      balanceMinor: 4_500_000,
      refundDueMinor: 5_000_000,
      lines: [
        {
          sessionId: 's1',
          patientName: 'Malika Rahimova',
          openedAt: '2026-09-20T09:00:00Z',
          priceMinor: 5_000_000,
          commissionMinor: 750_000,
          netMinor: 4_250_000,
          payment: 'paid',
        },
        {
          sessionId: 's2',
          patientName: 'Nodira Aliyeva',
          openedAt: '2026-09-22T09:00:00Z',
          priceMinor: 5_000_000,
          commissionMinor: 0,
          netMinor: 0,
          payment: 'refund_due',
        },
      ],
      payouts: [{ id: 'p1', amountMinor: 4_000_000, note: 'Sentyabr, 1-qism', paidAt: '2026-09-25T12:00:00Z' }],
    }
    mockApi({ ...base, 'GET /v1/doctor/earnings': earnings })
    open('/earnings')

    expect(await screen.findByText("100 000 so'm")).toBeInTheDocument()
    expect(screen.getByText("85 000 so'm")).toBeInTheDocument()
    expect(screen.getByText("45 000 so'm")).toBeInTheDocument()

    const [lines, payouts] = screen.getAllByRole('table')
    const malika = within(lines!).getByText('Malika Rahimova').closest('tr')!
    expect(malika).toHaveTextContent("50 000 so'm")
    expect(malika).toHaveTextContent("7 500 so'm")
    expect(malika).toHaveTextContent("42 500 so'm")
    expect(malika).toHaveTextContent("To'langan")
    expect(within(lines!).getByText('Nodira Aliyeva').closest('tr')).toHaveTextContent('Qaytarilishi kerak')
    expect(within(payouts!).getByText('Sentyabr, 1-qism').closest('tr')).toHaveTextContent("40 000 so'm")
  })

  it('brings the consultations past the first page when she asks, each once', async () => {
    const line = (id: string, name: string) => ({
      sessionId: id,
      patientName: name,
      openedAt: '2026-09-20T09:00:00Z',
      priceMinor: 5_000_000,
      commissionMinor: 750_000,
      netMinor: 4_250_000,
      payment: 'paid' as const,
    })
    const api = mockApi({
      ...base,
      'GET /v1/doctor/earnings': {
        currency: 'UZS',
        grossMinor: 15_000_000,
        commissionMinor: 2_250_000,
        netMinor: 12_750_000,
        paidOutMinor: 0,
        balanceMinor: 12_750_000,
        refundDueMinor: 0,
        lines: [line('s1', 'Malika Rahimova'), line('s2', 'Nodira Aliyeva')],
        payouts: [],
        linesTotal: 3,
        payoutsTotal: 0,
      } satisfies DoctorEarnings,
      // A line paid in between shifts the offsets: s2 comes again and is shown once.
      'GET /v1/doctor/earnings/lines': { items: [line('s2', 'Nodira Aliyeva'), line('s3', 'Zarina Tosheva')], total: 4, limit: 50, offset: 2 },
    })
    open('/earnings')

    expect(await screen.findByText('Malika Rahimova')).toBeInTheDocument()
    expect(screen.queryByText('Zarina Tosheva')).not.toBeInTheDocument()
    expect(api.callsTo('GET', '/v1/doctor/earnings/lines')).toHaveLength(0)

    await userEvent.click(screen.getByRole('button', { name: "Ko'proq ko'rsatish" }))

    expect(await screen.findByText('Zarina Tosheva')).toBeInTheDocument()
    expect(screen.getAllByText('Nodira Aliyeva')).toHaveLength(1)
    expect(api.callsTo('GET', '/v1/doctor/earnings/lines')[0]!.search.get('offset')).toBe('2')
    await waitFor(() => expect(screen.queryByRole('button', { name: "Ko'proq ko'rsatish" })).not.toBeInTheDocument())
  })

  it('has words for no paid consultations yet', async () => {
    mockApi({
      ...base,
      'GET /v1/doctor/earnings': {
        currency: 'UZS',
        grossMinor: 0,
        commissionMinor: 0,
        netMinor: 0,
        paidOutMinor: 0,
        balanceMinor: 0,
        refundDueMinor: 0,
        lines: [],
        payouts: [],
      },
    })
    open('/earnings')
    expect(await screen.findByText(/Hali pullik konsultatsiya bo'lmagan/)).toBeInTheDocument()
    expect(screen.getByText(/Sadora hali sizga to'lov o'tkazmagan/)).toBeInTheDocument()
  })
})

describe('Tayyor javoblar', () => {
  function repliesBackend(initial: QuickReply[]) {
    let list = [...initial]
    return mockApi({
      ...base,
      'GET /v1/doctor/quick-replies': () => list,
      'POST /v1/doctor/quick-replies': (call) => {
        const body = call.body as Omit<QuickReply, 'id'>
        const saved = { id: `q${list.length + 1}`, ...body }
        list = [...list, saved]
        return json(saved, 201)
      },
      'PUT /v1/doctor/quick-replies/q1': (call) => {
        const saved = { id: 'q1', ...(call.body as Omit<QuickReply, 'id'>) }
        list = list.map((item) => (item.id === 'q1' ? saved : item))
        return saved
      },
      'DELETE /v1/doctor/quick-replies/q1': () => {
        list = list.filter((item) => item.id !== 'q1')
        return { ok: true }
      },
    })
  }

  it('adds one with the next position', async () => {
    const api = repliesBackend([{ id: 'q1', title: 'Salom', body: 'Assalomu alaykum!', position: 0 }])
    open('/quick-replies')

    expect(await screen.findByText('Assalomu alaykum!')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: "+ Qo'shish" }))
    const dialog = screen.getByRole('dialog', { name: 'Yangi tayyor javob' })
    await userEvent.type(within(dialog).getByLabelText('Sarlavha'), 'Tahlil')
    await userEvent.type(within(dialog).getByLabelText('Matn'), 'Qon tahlilini topshiring.')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Saqlash' }))

    await waitFor(() => expect(api.callsTo('POST', '/v1/doctor/quick-replies')).toHaveLength(1))
    expect(api.callsTo('POST', '/v1/doctor/quick-replies')[0]!.body).toEqual({
      title: 'Tahlil',
      body: 'Qon tahlilini topshiring.',
      position: 1,
    })
    expect(await screen.findByText('Qon tahlilini topshiring.')).toBeInTheDocument()
  })

  it('edits and deletes one', async () => {
    const api = repliesBackend([{ id: 'q1', title: 'Salom', body: 'Assalomu alaykum!', position: 3 }])
    open('/quick-replies')

    await userEvent.click(await screen.findByRole('button', { name: 'Salom: tahrirlash' }))
    const dialog = screen.getByRole('dialog', { name: 'Tayyor javobni tahrirlash' })
    const body = within(dialog).getByLabelText('Matn')
    await userEvent.clear(body)
    await userEvent.type(body, 'Salom! Qanday yordam bera olaman?')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Saqlash' }))
    await waitFor(() => expect(api.callsTo('PUT', '/v1/doctor/quick-replies/q1')).toHaveLength(1))
    expect(api.callsTo('PUT', '/v1/doctor/quick-replies/q1')[0]!.body).toEqual({
      title: 'Salom',
      body: 'Salom! Qanday yordam bera olaman?',
      position: 3,
    })
    expect(await screen.findByText('Salom! Qanday yordam bera olaman?')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: "Salom: o'chirish" }))
    const confirm = screen.getByRole('dialog', { name: "Tayyor javobni o'chirish" })
    await userEvent.click(within(confirm).getByRole('button', { name: "O'chirish" }))
    await waitFor(() => expect(api.callsTo('DELETE', '/v1/doctor/quick-replies/q1')).toHaveLength(1))
    expect(await screen.findByText(/Hali tayyor javob yo'q/)).toBeInTheDocument()
  })

  it('stops at thirty', async () => {
    repliesBackend(Array.from({ length: 30 }, (_, index) => ({ id: `r${index}`, title: `T${index}`, body: `B${index}`, position: index })))
    open('/quick-replies')
    expect(await screen.findByText('B29')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: "+ Qo'shish" })).toBeDisabled()
  })
})
