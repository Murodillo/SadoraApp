import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from '../App'
import { fitWithin } from '../api/image'
import type { Conversation, ConversationThread, DirectMessage, DoctorAccount, DoctorSummary } from '../api/types'
import { apiError, json, mockApi } from '../test/http'
import { doctorAccount, renderApp, signIn } from '../test/render'
import { consultationOpen, timeLeft } from '../api/consultation'

const HOUR = 3_600_000
const ago = (ms: number) => new Date(Date.now() - ms).toISOString()
const ahead = (ms: number) => new Date(Date.now() + ms).toISOString()

function conversation(id: string, name: string, overrides: Partial<Conversation> = {}): Conversation {
  return {
    id,
    alias: name,
    tint: 1,
    lastMessage: 'Salom, doktor',
    lastMessageAt: ago(10 * 60_000),
    unread: 0,
    blocked: false,
    patient: { name, age: 29, lifeStage: 'pregnancy' },
    consultation: { openedAt: ago(2 * HOUR), expiresAt: ahead(22 * HOUR), closedAt: null, open: true },
    lastMessageKind: 'text',
    lastMessageRead: false,
    ...overrides,
  }
}

function message(id: string, body: string, overrides: Partial<DirectMessage> = {}): DirectMessage {
  return { id, body, createdAt: ago(5 * 60_000), isMine: false, kind: 'text', read: false, ...overrides }
}

const closedConsultation = { openedAt: ago(30 * HOUR), expiresAt: ago(6 * HOUR), closedAt: ago(8 * HOUR), open: false }

function summary(): DoctorSummary {
  return {
    generatedAt: new Date().toISOString(),
    language: 'uz',
    person: { name: 'Malika Rahimova', age: 29, heightCm: 165, weightKg: 61, lifeStage: 'pregnancy', memberSince: '2026-03-01' },
    pregnancy: { week: 24, dueDate: '2027-01-10', lessMovementDays: ['2026-09-28'] },
    symptomCounts: [
      { key: 'nausea', label: "Ko'ngil aynishi", category: 'digestion', days: 12 },
      { key: 'back_pain', label: "Bel og'rig'i", category: 'pain', days: 7 },
    ],
    medications: [
      {
        name: 'Folat kislotasi',
        dosage: '400',
        unit: 'mkg',
        schedule: { kind: 'daily', times: ['09:00'] },
        foodRelation: 'with',
        startedOn: '2026-05-01',
        active: true,
        adherencePercent: 92,
      },
    ],
    appointments: [],
    mind: { windowDays: 90, daysLogged: 40, averageMood: 3.64, averageEnergy: 3.1, averageStress: 2.4 },
    nutrition: { windowDays: 90, daysLogged: 0, goals: {} },
    wearable: {
      providers: ['health_connect'],
      days: [],
      averages: [
        { metric: 'steps', value: 6421.4, unit: 'count', sampleCount: 30 },
        { metric: 'sleep_duration', value: 452, unit: 'min', sampleCount: 30 },
      ],
    },
  }
}

/**
 * A small messaging backend: two consultations, the first with a patient line and her
 * record attached. Sending appends to the thread; closing closes it.
 */
function messagingBackend(
  options: { first?: Partial<Conversation>; recordStatus?: number; account?: DoctorAccount } = {},
) {
  let malika = conversation('c1', 'Malika Rahimova', { unread: 2, ...options.first })
  const nodira = conversation('c2', 'Nodira Aliyeva', {
    lastMessage: 'Rahmat!',
    lastMessageAt: ago(26 * HOUR),
    lastMessageRead: true,
    consultation: closedConsultation,
    patient: { name: 'Nodira Aliyeva', age: 41, lifeStage: 'perimenopause' },
  })
  const messages: DirectMessage[] = [
    message('m1', "Salom, doktor. Homiladorlikning 24-haftasidaman, belim og'riyapti."),
    message('m2', '', { kind: 'record', createdAt: ago(4 * 60_000) }),
  ]
  let account = options.account ?? doctorAccount()
  const threadOf = (): ConversationThread => ({ conversation: { ...malika, unread: 0 }, messages, otherTyping: false })

  const api = mockApi({
    'GET /v1/doctor/me': () => account,
    'PUT /v1/doctor/me': (call) => {
      account = { ...account, ...(call.body as Partial<DoctorAccount>) }
      return account
    },
    'GET /v1/doctor/questions': [],
    'GET /v1/community/conversations': () => [malika, nodira],
    'GET /v1/community/conversations/c1': () => threadOf(),
    'GET /v1/community/conversations/c2': () => ({ conversation: nodira, messages: [message('n1', 'Rahmat!', { isMine: false })] }),
    'POST /v1/community/conversations/c1/messages': (call) => {
      const sent = message(`m${messages.length + 1}`, (call.body as { body: string }).body, {
        isMine: true,
        createdAt: new Date().toISOString(),
      })
      messages.push(sent)
      return json(sent, 201)
    },
    'POST /v1/community/conversations/c1/typing': { ok: true },
    'POST /v1/community/conversations/c1/close': () => {
      malika = { ...malika, consultation: { ...malika.consultation!, closedAt: new Date().toISOString(), open: false } }
      return threadOf()
    },
    'POST /v1/community/conversations/c1/report': { ok: true },
    'GET /v1/community/conversations/c1/messages/m2/record': () =>
      options.recordStatus
        ? apiError(options.recordStatus, 'forbidden', "Konsultatsiya yopilgan — karta endi ko'rinmaydi")
        : summary(),
  })
  return api
}

async function openMessages(route = '/messages') {
  signIn()
  renderApp(<AppRoutes />, { route })
  return screen.findByRole('region', { name: 'Suhbatlar' })
}

describe('Xabarlar — the conversation list', () => {
  it('lists patients by real name with age, life stage, last line, unread and state, and badges the rail', async () => {
    messagingBackend()
    const list = await openMessages()

    const malika = await within(list).findByRole('button', { name: /Malika Rahimova/ })
    expect(malika).toHaveTextContent('29 yosh · Homiladorlik')
    expect(malika).toHaveTextContent('Salom, doktor')
    expect(malika).toHaveTextContent('Ochiq')
    expect(within(malika).getByLabelText("2 ta o'qilmagan")).toHaveTextContent('2')

    const nodira = within(list).getByRole('button', { name: /Nodira Aliyeva/ })
    expect(nodira).toHaveTextContent('41 yosh · Perimenopauza')
    expect(nodira).toHaveTextContent('Yopilgan')
    expect(within(nodira).getByLabelText("O'qildi")).toHaveTextContent('✓✓')

    const nav = screen.getByRole('navigation')
    expect(within(nav).getByRole('link', { name: /Xabarlar/ })).toBeInTheDocument()
    expect(await within(nav).findByLabelText("2 ta o'qilmagan xabar")).toHaveTextContent('2')
    expect(screen.getByRole('heading', { name: /Xabarlar/ })).toBeInTheDocument()
  })

  it('asks for the patients scope', async () => {
    const api = messagingBackend()
    await openMessages()
    await waitFor(() => expect(api.callsTo('GET', '/v1/community/conversations').length).toBeGreaterThan(0))
    expect(api.callsTo('GET', '/v1/community/conversations').every((call) => call.search.get('scope') === 'patients')).toBe(true)
  })

  it('filters by name and by open or closed', async () => {
    messagingBackend()
    const list = await openMessages()
    await within(list).findByRole('button', { name: /Malika/ })

    await userEvent.type(within(list).getByLabelText("Bemor ismi bo'yicha qidirish"), 'nodi')
    expect(within(list).queryByRole('button', { name: /Malika/ })).not.toBeInTheDocument()
    expect(within(list).getByRole('button', { name: /Nodira/ })).toBeInTheDocument()

    await userEvent.clear(within(list).getByLabelText("Bemor ismi bo'yicha qidirish"))
    await userEvent.click(within(list).getByRole('radio', { name: 'Ochiq' }))
    expect(within(list).getByRole('button', { name: /Malika/ })).toBeInTheDocument()
    expect(within(list).queryByRole('button', { name: /Nodira/ })).not.toBeInTheDocument()

    await userEvent.click(within(list).getByRole('radio', { name: 'Yopilgan' }))
    expect(within(list).queryByRole('button', { name: /Malika/ })).not.toBeInTheDocument()
    expect(within(list).getByRole('button', { name: /Nodira/ })).toBeInTheDocument()
  })

  it('previews a photo and a record as words', async () => {
    mockApi({
      'GET /v1/doctor/me': doctorAccount(),
      'GET /v1/doctor/questions': [],
      'GET /v1/community/conversations': [
        conversation('c1', 'Malika', { lastMessageKind: 'image', lastMessage: '' }),
        conversation('c2', 'Nodira', { lastMessageKind: 'record', lastMessage: '' }),
      ],
    })
    const list = await openMessages()
    expect(await within(list).findByRole('button', { name: /Malika/ })).toHaveTextContent('📷 Rasm')
    expect(within(list).getByRole('button', { name: /Nodira/ })).toHaveTextContent('📋 Tibbiy karta')
  })
})

describe('Xabarlar — the thread', () => {
  it('opens a thread, sends a text with Enter and posts exactly the body', async () => {
    const api = messagingBackend()
    const list = await openMessages()
    await userEvent.click(await within(list).findByRole('button', { name: /Malika/ }))

    const thread = screen.getByRole('region', { name: 'Yozishma' })
    expect(await within(thread).findByText(/belim og'riyapti/)).toBeInTheDocument()
    expect(within(thread).getByText(/Konsultatsiya ochiq/)).toBeInTheDocument()
    expect(within(thread).getByText(/qoldi/)).toBeInTheDocument()

    const box = within(thread).getByLabelText('Xabar')
    const send = within(thread).getByRole('button', { name: 'Yuborish' })
    expect(send).toBeDisabled()

    await userEvent.type(box, '  Bel uchun yotib dam oling.  ')
    fireEvent.keyDown(box, { key: 'Enter' })

    await waitFor(() => expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(1))
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')[0]!.body).toEqual({ body: 'Bel uchun yotib dam oling.' })

    const sent = await within(thread).findByText('Bel uchun yotib dam oling.')
    expect(sent.closest('.msg')).toHaveClass('mine')
    expect(within(sent.closest('.msg') as HTMLElement).getByLabelText('Yuborildi')).toHaveTextContent('✓')
    expect(box).toHaveValue('')

    // Shift+Enter is a new line, not a send; the typing ping went out once, not per key.
    await userEvent.type(box, 'Birinchi')
    fireEvent.keyDown(box, { key: 'Enter', shiftKey: true })
    expect(api.callsTo('POST', '/v1/community/conversations/c1/messages')).toHaveLength(1)
    expect(api.callsTo('POST', '/v1/community/conversations/c1/typing').length).toBeLessThanOrEqual(2)
    expect(api.callsTo('POST', '/v1/community/conversations/c1/typing').length).toBeGreaterThanOrEqual(1)
  })

  it('shows "yozmoqda…" and read ticks from the thread', async () => {
    mockApi({
      'GET /v1/doctor/me': doctorAccount(),
      'GET /v1/doctor/questions': [],
      'GET /v1/community/conversations': [conversation('c1', 'Malika')],
      'GET /v1/community/conversations/c1': {
        conversation: conversation('c1', 'Malika'),
        messages: [message('m1', 'Savolim bor'), message('m2', 'Eshitaman', { isMine: true, read: true })],
        otherTyping: true,
      },
    })
    await openMessages('/messages?c=c1')

    const thread = screen.getByRole('region', { name: 'Yozishma' })
    expect(await within(thread).findByText('Malika yozmoqda…')).toBeInTheDocument()
    const mine = within(thread).getByText('Eshitaman').closest('.msg') as HTMLElement
    expect(within(mine).getByLabelText("O'qildi")).toHaveTextContent('✓✓')
    expect(within(thread).getByText('Bugun')).toBeInTheDocument()
  })

  it('locks the composer, with the reason, on a closed consultation', async () => {
    const api = messagingBackend()
    await openMessages('/messages?c=c2')

    const thread = screen.getByRole('region', { name: 'Yozishma' })
    expect(await within(thread).findByText('Rahmat!', { selector: '.msg-text' })).toBeInTheDocument()
    expect(within(thread).getByLabelText('Xabar')).toBeDisabled()
    expect(within(thread).getByRole('button', { name: 'Yuborish' })).toBeDisabled()
    expect(within(thread).getByRole('button', { name: 'Rasm biriktirish' })).toBeDisabled()
    expect(within(thread).getByText(/Konsultatsiya yopilgan — bemor yangisini ochsa/)).toBeInTheDocument()
    expect(within(thread).queryByRole('button', { name: 'Konsultatsiyani yakunlash' })).not.toBeInTheDocument()
    expect(api.callsTo('POST', '/v1/community/conversations/c2/messages')).toHaveLength(0)
  })

  it('closes a consultation after she confirms', async () => {
    const api = messagingBackend()
    await openMessages('/messages?c=c1')
    const thread = screen.getByRole('region', { name: 'Yozishma' })

    await userEvent.click(await within(thread).findByRole('button', { name: 'Konsultatsiyani yakunlash' }))
    const dialog = screen.getByRole('dialog', { name: 'Konsultatsiyani yakunlash' })
    expect(api.callsTo('POST', '/v1/community/conversations/c1/close')).toHaveLength(0)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Yakunlash' }))

    await waitFor(() => expect(api.callsTo('POST', '/v1/community/conversations/c1/close')).toHaveLength(1))
    expect(await screen.findByText('Konsultatsiya yakunlandi')).toBeInTheDocument()
    await waitFor(() => expect(within(thread).getByLabelText('Xabar')).toBeDisabled())
    expect(within(thread).getByText('Yopilgan')).toBeInTheDocument()
  })

  it('reports the conversation with a chosen reason', async () => {
    const api = messagingBackend()
    await openMessages('/messages?c=c1')
    const thread = screen.getByRole('region', { name: 'Yozishma' })
    await within(thread).findByText(/belim og'riyapti/)

    await userEvent.click(within(thread).getByRole('button', { name: 'Shikoyat' }))
    const dialog = screen.getByRole('dialog', { name: 'Shikoyat' })
    await userEvent.selectOptions(within(dialog).getByLabelText('Sabab'), 'Spam yoki reklama')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Shikoyat yuborish' }))

    await waitFor(() => expect(api.callsTo('POST', '/v1/community/conversations/c1/report')).toHaveLength(1))
    expect(api.callsTo('POST', '/v1/community/conversations/c1/report')[0]!.body).toEqual({ reason: 'spam' })
    expect(await screen.findByText('Shikoyat yuborildi')).toBeInTheDocument()
  })
})

describe('Xabarlar — the patient panel', () => {
  it('renders the attached record, leaving empty sections out', async () => {
    const api = messagingBackend()
    await openMessages('/messages?c=c1')

    const side = screen.getByRole('complementary', { name: 'Bemor' })
    expect(await within(side).findByText('Hafta')).toBeInTheDocument()
    expect(within(side).getByText('24')).toBeInTheDocument()
    expect(within(side).getByText('10-yanvar 2027')).toBeInTheDocument()
    expect(within(side).getByText('Bola harakati kam sezilgan kunlar: 1')).toBeInTheDocument()
    expect(within(side).getByText("Ko'ngil aynishi")).toBeInTheDocument()
    expect(within(side).getByText('12 kun')).toBeInTheDocument()
    expect(within(side).getByText('Folat kislotasi · 400 mkg')).toBeInTheDocument()
    expect(within(side).getByText(/Qabul: 92%/)).toBeInTheDocument()
    expect(within(side).getByText('3.6 / 5')).toBeInTheDocument()
    expect(within(side).getByText('6421 qadam')).toBeInTheDocument()
    expect(within(side).getByText('7 soat 32 daq')).toBeInTheDocument()
    expect(within(side).getByText("Bu bemor o'zi kiritgan ma'lumotlar, tashxis emas")).toBeInTheDocument()
    // Nothing logged, nothing booked: those sections are not drawn at all.
    expect(within(side).queryByRole('region', { name: 'Ovqatlanish' })).not.toBeInTheDocument()
    expect(within(side).queryByRole('region', { name: "Ko'riklar va tekshiruvlar" })).not.toBeInTheDocument()
    expect(within(side).queryByRole('region', { name: 'Hayz sikli' })).not.toBeInTheDocument()

    const recordCall = api.callsTo('GET', '/v1/community/conversations/c1/messages/m2/record')[0]!
    expect(recordCall.search.get('lang')).toBe('uz')

    // The record line in the thread is a card that opens it.
    const thread = screen.getByRole('region', { name: 'Yozishma' })
    expect(within(thread).getByText('Tibbiy karta')).toBeInTheDocument()
    await userEvent.click(within(thread).getByRole('button', { name: 'Ochish' }))
    expect(await within(side).findByText('Hafta')).toBeInTheDocument()
  })

  it('says the record is gone once the consultation is closed', async () => {
    messagingBackend({ recordStatus: 403 })
    await openMessages('/messages?c=c1')

    const side = screen.getByRole('complementary', { name: 'Bemor' })
    expect(await within(side).findByText("Konsultatsiya yopilgan — karta endi ko'rinmaydi")).toBeInTheDocument()
    expect(within(side).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows the patient and the consultation window', async () => {
    messagingBackend()
    await openMessages('/messages?c=c1')

    const side = screen.getByRole('complementary', { name: 'Bemor' })
    expect(await within(side).findByText('Ochilgan')).toBeInTheDocument()
    expect(within(side).getByText('Tugaydi')).toBeInTheDocument()
    expect(within(side).getByText('Homiladorlik', { selector: 'dd' })).toBeInTheDocument()
  })
})

describe('Profil — consultations switch', () => {
  it('turns consultations off with a PUT of just that field', async () => {
    const api = messagingBackend({ account: doctorAccount({ acceptsConsultations: true }) })
    signIn()
    renderApp(<AppRoutes />, { route: '/profile' })

    const toggle = await screen.findByRole('switch', { name: 'Konsultatsiya qabul qilaman' })
    expect(toggle).toBeChecked()
    await userEvent.click(toggle)

    await waitFor(() => expect(api.callsTo('PUT', '/v1/doctor/me')).toHaveLength(1))
    expect(api.callsTo('PUT', '/v1/doctor/me')[0]!.body).toEqual({ acceptsConsultations: false })
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Konsultatsiya qabul qilaman' })).not.toBeChecked())
    expect(await screen.findByText("Yangi konsultatsiyalar to'xtatildi")).toBeInTheDocument()
  })
})

describe('helpers', () => {
  it('fits a photo within 1280px on its longest edge without enlarging it', () => {
    expect(fitWithin(4000, 3000)).toEqual({ width: 1280, height: 960 })
    expect(fitWithin(1080, 1920)).toEqual({ width: 720, height: 1280 })
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 })
  })

  it('reads a consultation as open only while the window runs', () => {
    const open = conversation('c1', 'Malika')
    expect(consultationOpen(open)).toBe(true)
    expect(consultationOpen({ ...open, consultation: { ...open.consultation!, expiresAt: ago(1000) } })).toBe(false)
    expect(consultationOpen({ ...open, consultation: closedConsultation })).toBe(false)
  })

  it('words the time left', () => {
    const now = Date.parse('2026-09-30T10:00:00Z')
    expect(timeLeft('2026-09-30T15:12:00Z', now)).toBe('5 soat 12 daqiqa')
    expect(timeLeft('2026-09-30T10:40:00Z', now)).toBe('40 daqiqa')
    expect(timeLeft('2026-09-30T09:00:00Z', now)).toBe('0 daqiqa')
  })
})
