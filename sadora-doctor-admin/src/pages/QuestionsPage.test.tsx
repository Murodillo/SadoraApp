import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from '../App'
import type { CommunityComment, CommunityPost } from '../api/types'
import { apiError, json, mockApi } from '../test/http'
import { comment, doctorAccount, question, renderApp, signIn } from '../test/render'

const DISCLAIMER = "Chatdagi javobingiz umumiy maslahat sifatida ko'rinadi, tashxis emas."

/**
 * A small backend for the answer flow: two unanswered questions, and a thread that grows
 * when the doctor answers. Answering takes the question off the unanswered list, as the
 * server does.
 */
function questionsBackend() {
  let questions: CommunityPost[] = [
    question('q1', 'Hayz 10 kun kechikdi, test manfiy. Nima qilay?', { commentCount: 1 }),
    question('q2', "Homiladorlikda qahva ichsa bo'ladimi?", { topic: 'pregnancy' }),
  ]
  let answers = 36
  const threads: Record<string, CommunityComment[]> = {
    q1: [comment('c1', 'q1', "Menda ham shunday bo'lgan")],
    q2: [],
  }
  const api = mockApi({
    'GET /v1/doctor/me': doctorAccount(),
    'GET /v1/doctor/questions': (call) => {
      const topic = call.search.get('topic')
      return topic ? questions.filter((item) => item.topic === topic) : questions
    },
    'GET /v1/doctors/doc-1': () => ({
      id: 'doc-1',
      fullName: 'Dilnoza Karimova',
      specialty: 'gynecologist',
      workplace: 'Toshkent',
      experienceYears: 12,
      verifiedSince: '2026-09-02T09:00:00Z',
      postCount: 3,
      answerCount: answers,
      posts: [],
    }),
    'GET /v1/community/posts/q1/comments': () => threads.q1,
    'GET /v1/community/posts/q2/comments': () => threads.q2,
    'POST /v1/community/posts/q1/comments': (call) => {
      const answer = comment('c2', 'q1', (call.body as { body: string }).body, {
        alias: 'Dilnoza Karimova',
        isMine: true,
        doctor: { id: 'doc-1', fullName: 'Dilnoza Karimova', specialty: 'gynecologist' },
      })
      threads.q1 = [answer, ...threads.q1!]
      answers += 1
      questions = questions.filter((item) => item.id !== 'q1')
      return json(answer, 201)
    },
  })
  return api
}

async function openWorkspace() {
  signIn()
  const api = questionsBackend()
  renderApp(<AppRoutes />)
  await screen.findByText('Hayz 10 kun kechikdi, test manfiy. Nima qilay?', { selector: '.q-body' })
  return api
}

describe('Savollar — the answer flow', () => {
  it('lists the unanswered questions and opens the first with its thread', async () => {
    const api = await openWorkspace()

    const list = screen.getByRole('region', { name: "Savollar ro'yxati" })
    expect(within(list).getAllByRole('button')).toHaveLength(2)
    expect(within(list).getByRole('button', { current: true })).toHaveTextContent('Hayz 10 kun kechikdi')
    expect(await screen.findByText("Menda ham shunday bo'lgan")).toBeInTheDocument()
    expect(screen.getByText(/2 ta javobsiz savol/)).toBeInTheDocument()
    expect(screen.getByText('Javob kutmoqda')).toBeInTheDocument()
    expect(screen.getByText(DISCLAIMER)).toBeInTheDocument()
    expect(api.callsTo('GET', '/v1/doctor/questions')[0]!.search.get('limit')).toBe('100')
  })

  it('sends an answer, says so, drops the question from the list and shows the answer in the thread', async () => {
    const api = await openWorkspace()
    await screen.findByText("Menda ham shunday bo'lgan")

    const box = screen.getByLabelText('Javobingiz')
    const send = screen.getByRole('button', { name: 'Javob berish' })
    expect(send).toBeDisabled()

    await userEvent.type(box, '  Iltimos, ginekologga uchrashing.  ')
    expect(send).toBeEnabled()
    await userEvent.click(send)

    // The server got the trimmed body, on the open question.
    await waitFor(() => expect(api.callsTo('POST', '/v1/community/posts/q1/comments')).toHaveLength(1))
    expect(api.callsTo('POST', '/v1/community/posts/q1/comments')[0]!.body).toEqual({ body: 'Iltimos, ginekologga uchrashing.' })

    expect(await screen.findByRole('status')).toHaveTextContent('Javobingiz yuborildi')

    // Refetched: q1 plays its exit on the list, marked answered, and then is gone.
    const list = screen.getByRole('region', { name: "Savollar ro'yxati" })
    await waitFor(() => expect(within(list).getAllByRole('button')).toHaveLength(1))
    expect(await within(list).findByText('✓ Javob berildi')).toBeInTheDocument()
    await waitFor(() => expect(within(list).queryByText(/Hayz 10 kun kechikdi/)).not.toBeInTheDocument())

    // The thread leads with her answer, marked as a doctor's and as just arrived.
    const thread = await screen.findByRole('list', { name: 'Izohlar' })
    await waitFor(() => expect(within(thread).getAllByRole('listitem')).toHaveLength(2))
    const [first] = within(thread).getAllByRole('listitem')
    expect(first).toHaveClass('doctor')
    expect(first).toHaveTextContent('Iltimos, ginekologga uchrashing.')
    expect(first).toHaveTextContent('Shifokor javobi')
    expect(first).toHaveClass('arrived')
    expect(screen.getByText('shu safar +1')).toBeInTheDocument()

    expect(api.callsTo('GET', '/v1/doctor/questions').length).toBeGreaterThanOrEqual(2)
    expect(api.callsTo('GET', '/v1/community/posts/q1/comments').length).toBeGreaterThanOrEqual(2)
    expect(screen.getByLabelText('Javobingiz')).toHaveValue('')

    // And the next question is one click away.
    await userEvent.click(screen.getByRole('button', { name: 'Keyingi savol →' }))
    expect(within(list).getByRole('button', { current: true })).toHaveTextContent('qahva')
  })

  it('sends with Ctrl+Enter and Cmd+Enter', async () => {
    const api = await openWorkspace()
    const box = screen.getByLabelText('Javobingiz')

    await userEvent.type(box, "Suv ko'proq iching.")
    fireEvent.keyDown(box, { key: 'Enter', ctrlKey: true })

    await waitFor(() => expect(api.callsTo('POST', '/v1/community/posts/q1/comments')).toHaveLength(1))
  })

  it("keeps each question's draft when she switches between them", async () => {
    await openWorkspace()
    const list = screen.getByRole('region', { name: "Savollar ro'yxati" })

    await userEvent.type(screen.getByLabelText('Javobingiz'), 'Yarim javob')
    await userEvent.click(within(list).getByRole('button', { name: /qahva/ }))
    expect(screen.getByLabelText('Javobingiz')).toHaveValue('')
    await userEvent.click(within(list).getByRole('button', { name: /Hayz 10 kun/ }))
    expect(screen.getByLabelText('Javobingiz')).toHaveValue('Yarim javob')
    expect(within(list).getByText('Qoralama')).toBeInTheDocument()
  })

  it('filters by topic', async () => {
    const api = await openWorkspace()

    await userEvent.click(screen.getByRole('radio', { name: 'Homiladorlik' }))

    const list = screen.getByRole('region', { name: "Savollar ro'yxati" })
    await waitFor(() => expect(within(list).getAllByRole('button')).toHaveLength(1))
    expect(within(list).getByRole('button', { current: true })).toHaveTextContent('qahva')
    expect(api.calls.some((call) => call.path === '/v1/doctor/questions' && call.search.get('topic') === 'pregnancy')).toBe(true)
  })

  it('shows a refused answer in a toast and the field detail under the box, and keeps the draft', async () => {
    signIn()
    mockApi({
      'GET /v1/doctor/me': doctorAccount(),
      'GET /v1/doctor/questions': [question('q1', 'Savol')],
      'GET /v1/community/posts/q1/comments': [],
      'POST /v1/community/posts/q1/comments': apiError(422, 'validation_failed', "Ma'lumotlar noto'g'ri", {
        body: "Eng ko'pi 1000 belgi",
      }),
    })
    renderApp(<AppRoutes />)
    const box = await screen.findByLabelText('Javobingiz')

    await userEvent.type(box, 'Javob matni')
    await userEvent.click(screen.getByRole('button', { name: 'Javob berish' }))

    expect(await screen.findByRole('status')).toHaveTextContent("Ma'lumotlar noto'g'ri")
    expect(screen.getByRole('alert')).toHaveTextContent("Eng ko'pi 1000 belgi")
    expect(box).toHaveValue('Javob matni')
  })

  it('says so when there is nothing to answer', async () => {
    signIn()
    mockApi({ 'GET /v1/doctor/me': doctorAccount(), 'GET /v1/doctor/questions': [] })
    renderApp(<AppRoutes />)

    expect(await screen.findByText("Hozircha javob kutayotgan savol yo'q.")).toBeInTheDocument()
    expect(screen.queryByLabelText('Javobingiz')).not.toBeInTheDocument()
  })
})
