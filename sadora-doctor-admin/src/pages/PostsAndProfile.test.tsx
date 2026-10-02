import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from '../App'
import type { CommunityPost, DoctorAccount } from '../api/types'
import { apiError, json, mockApi } from '../test/http'
import { doctorAccount, question, renderApp, signIn } from '../test/render'

describe('Postlarim', () => {
  it('shows her public page and publishes a new post under a chosen topic', async () => {
    signIn()
    let posts: CommunityPost[] = [question('p1', 'Hayz og‘rig‘i haqida', { doctor: { id: 'doc-1', fullName: 'Dilnoza Karimova', specialty: 'gynecologist' } })]
    const api = mockApi({
      'GET /v1/doctor/me': doctorAccount(),
      'GET /v1/doctor/questions': [],
      'GET /v1/doctors/doc-1': () => ({
        id: 'doc-1',
        fullName: 'Dilnoza Karimova',
        specialty: 'gynecologist',
        workplace: 'Toshkent',
        experienceYears: 12,
        verifiedSince: '2026-09-02T09:00:00Z',
        postCount: posts.length,
        answerCount: 7,
        posts,
      }),
      'POST /v1/community/posts': (call) => {
        const body = call.body as { topic: 'body'; body: string }
        const created = question('p2', body.body, { topic: body.topic })
        posts = [created, ...posts]
        return json(created, 201)
      },
    })
    renderApp(<AppRoutes />, { route: '/posts' })

    expect(await screen.findByText('Hayz og‘rig‘i haqida')).toBeInTheDocument()
    expect(screen.getByText('12 yil')).toBeInTheDocument()

    // Nothing goes further without a topic.
    await userEvent.type(screen.getByLabelText('Matn'), "Ko'krakni oyiga bir marta tekshiring.")
    await userEvent.click(screen.getByRole('button', { name: "Ko'rib chiqish" }))
    expect(screen.getByRole('alert')).toHaveTextContent("Bo'limni tanlang")
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    // The preview shows the post as the chat will: her name, the check mark, the room.
    await userEvent.selectOptions(screen.getByLabelText("Bo'lim"), 'Tana')
    await userEvent.click(screen.getByRole('button', { name: "Ko'rib chiqish" }))
    const dialog = screen.getByRole('dialog', { name: "Post chatda shunday ko'rinadi" })
    expect(dialog).toHaveTextContent('Dilnoza Karimova')
    expect(dialog).toHaveTextContent('Ginekolog · Tana')
    expect(api.callsTo('POST', '/v1/community/posts')).toHaveLength(0)

    await userEvent.click(within(dialog).getByRole('button', { name: 'Chop etish' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Post chop etildi')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.callsTo('POST', '/v1/community/posts')[0]!.body).toEqual({
      topic: 'body',
      body: "Ko'krakni oyiga bir marta tekshiring.",
    })
    await waitFor(() => expect(screen.getAllByRole('article')).toHaveLength(2))
    expect(screen.getByLabelText('Matn')).toHaveValue('')
  })

  it('reads her older posts past the twenty her page carries, a page at a time', async () => {
    signIn()
    const all = Array.from({ length: 23 }, (_, n) => question(`p${n}`, `Post raqami ${n}`))
    const api = mockApi({
      'GET /v1/doctor/me': doctorAccount(),
      'GET /v1/doctor/questions': [],
      'GET /v1/doctors/doc-1': {
        id: 'doc-1',
        fullName: 'Dilnoza Karimova',
        specialty: 'gynecologist',
        workplace: 'Toshkent',
        experienceYears: 12,
        verifiedSince: '2026-09-02T09:00:00Z',
        postCount: all.length,
        answerCount: 0,
        posts: all.slice(0, 20),
      },
      'GET /v1/doctors/doc-1/posts': (call) => {
        const offset = Number(call.search.get('offset'))
        const limit = Number(call.search.get('limit'))
        // One post came in between: the page starts one earlier, and the repeat is shown once.
        const from = offset - 1
        return { items: all.slice(from, from + limit), total: all.length, limit, offset: from }
      },
    })
    renderApp(<AppRoutes />, { route: '/posts' })

    expect(await screen.findByText('Post raqami 19')).toBeInTheDocument()
    expect(screen.queryByText('Post raqami 20')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Yana postlar' }))

    expect(await screen.findByText('Post raqami 22')).toBeInTheDocument()
    expect(screen.getAllByText('Post raqami 19')).toHaveLength(1)
    expect(screen.getAllByRole('article')).toHaveLength(23)
    expect(api.callsTo('GET', '/v1/doctors/doc-1/posts')[0]!.search.get('offset')).toBe('20')
    expect(screen.queryByRole('button', { name: 'Yana postlar' })).not.toBeInTheDocument()
  })
})

describe('Profil', () => {
  it("saves workplace and bio, and shows the server's field detail when refused", async () => {
    signIn()
    let account: DoctorAccount = doctorAccount({ workplace: 'Toshkent', bio: null })
    let refuse = true
    const api = mockApi({
      'GET /v1/doctor/me': () => account,
      'GET /v1/doctor/questions': [],
      'PUT /v1/doctor/me': (call) => {
        if (refuse) return apiError(422, 'validation_failed', "Ma'lumotlar noto'g'ri", { workplace: 'Ish joyi juda qisqa' })
        const body = call.body as { workplace: string; bio: string }
        account = { ...account, workplace: body.workplace, bio: body.bio || null }
        return account
      },
    })
    renderApp(<AppRoutes />, { route: '/profile' })

    const workplace = await screen.findByLabelText('Ish joyi')
    const save = screen.getByRole('button', { name: 'Saqlash' })
    expect(save).toBeDisabled()

    await userEvent.clear(workplace)
    await userEvent.type(workplace, '  Samarqand, 2-son tug‘ruqxona  ')
    await userEvent.type(screen.getByLabelText("O'zim haqimda"), 'Homiladorlik va tug‘ruq')
    await userEvent.click(save)

    expect(await screen.findByRole('status')).toHaveTextContent("Ma'lumotlar noto'g'ri")
    expect(screen.getByRole('alert')).toHaveTextContent('Ish joyi juda qisqa')

    refuse = false
    await userEvent.type(workplace, ' ')
    await userEvent.click(screen.getByRole('button', { name: 'Saqlash' }))

    await waitFor(() => expect(screen.getAllByRole('status').at(-1)).toHaveTextContent('Profil saqlandi'))
    expect(api.callsTo('PUT', '/v1/doctor/me').at(-1)!.body).toEqual({
      workplace: 'Samarqand, 2-son tug‘ruqxona',
      bio: 'Homiladorlik va tug‘ruq',
    })
    expect(workplace).toHaveValue('Samarqand, 2-son tug‘ruqxona')
    expect(screen.getByRole('button', { name: 'Saqlash' })).toBeDisabled()
  })

  it('will not send an empty workplace', async () => {
    signIn()
    const api = mockApi({ 'GET /v1/doctor/me': doctorAccount(), 'GET /v1/doctor/questions': [] })
    renderApp(<AppRoutes />, { route: '/profile' })

    await userEvent.clear(await screen.findByLabelText('Ish joyi'))

    expect(screen.getByRole('alert')).toHaveTextContent("To'ldirilishi shart")
    expect(screen.getByRole('button', { name: 'Saqlash' })).toBeDisabled()
    expect(api.callsTo('PUT', '/v1/doctor/me')).toHaveLength(0)
  })
})
