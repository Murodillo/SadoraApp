import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from '../App'
import type { DoctorStatus } from '../api/types'
import { screenFor } from '../auth/doctor'
import { apiError, mockApi } from '../test/http'
import { doctorAccount, question, renderApp, signIn } from '../test/render'

describe('screenFor', () => {
  it.each<[DoctorStatus | string, string]>([
    ['approved', 'workspace'],
    ['none', 'status'],
    ['pending', 'status'],
    ['rejected', 'status'],
    ['suspended', 'status'],
    ['something_new', 'status'],
  ])('%s -> %s', (status, expected) => {
    expect(screenFor(status)).toBe(expected)
  })
})

describe('the gate after sign-in', () => {
  it('shows the sign-in page to nobody signed in, without asking the server anything', () => {
    const api = mockApi({})
    renderApp(<AppRoutes />)
    expect(screen.getByLabelText('Telefon raqami')).toBeInTheDocument()
    expect(api.calls).toHaveLength(0)
  })

  it('sends an account that never applied to the Sadora Doctor app, naming her number', async () => {
    signIn()
    mockApi({ 'GET /v1/doctor/me': doctorAccount({ status: 'none', profileId: null, fullName: null }) })
    renderApp(<AppRoutes />)

    expect(await screen.findByRole('heading', { name: 'Ariza Sadora Doctor ilovasida topshiriladi' })).toBeInTheDocument()
    expect(screen.getByText('+998 90 123 45 67')).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('tells a pending doctor her application is being reviewed', async () => {
    signIn()
    mockApi({ 'GET /v1/doctor/me': doctorAccount({ status: 'pending', profileId: null }) })
    renderApp(<AppRoutes />)

    expect(await screen.findByRole('heading', { name: "Arizangiz ko'rib chiqilmoqda" })).toBeInTheDocument()
    expect(screen.getByText('Dilnoza Karimova')).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it.each<[DoctorStatus, string]>([
    ['rejected', 'Ariza rad etildi'],
    ['suspended', "Shifokor hisobingiz to'xtatilgan"],
  ])("shows a %s doctor the reviewer's note", async (status, title) => {
    signIn()
    mockApi({ 'GET /v1/doctor/me': doctorAccount({ status, reviewNote: "Litsenziya rasmi o'qib bo'lmaydi" }) })
    renderApp(<AppRoutes />)

    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument()
    expect(screen.getByText("Litsenziya rasmi o'qib bo'lmaydi")).toBeInTheDocument()
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
  })

  it('opens the workspace for an approved doctor, with her name and specialty in the header', async () => {
    signIn()
    mockApi({
      'GET /v1/doctor/me': doctorAccount(),
      'GET /v1/doctor/questions': [question('q1', 'Hayz kechikdi, nima qilay?')],
      'GET /v1/community/posts/q1/comments': [],
    })
    renderApp(<AppRoutes />)

    const nav = await screen.findByRole('navigation', { name: "Bo'limlar" })
    expect(within(nav).getByRole('link', { name: /Savollar/ })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Postlarim' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Profil' })).toBeInTheDocument()
    const header = screen.getByRole('banner')
    expect(within(header).getByText('Dilnoza Karimova')).toBeInTheDocument()
    expect(within(header).getByRole('img', { name: 'Tasdiqlangan shifokor' })).toBeInTheDocument()
    expect(within(header).getByText('Ginekolog')).toBeInTheDocument()
  })

  it('lands an approved doctor on the page she asked for', async () => {
    signIn()
    mockApi({
      'GET /v1/doctor/me': doctorAccount({ workplace: 'Toshkent, 1-son klinika' }),
      'GET /v1/doctor/questions': [],
    })
    renderApp(<AppRoutes />, { route: '/profile' })

    expect(await screen.findByLabelText('Ish joyi')).toHaveValue('Toshkent, 1-son klinika')
  })

  it('moves a doctor who was suspended mid-session to the status page on the next refusal', async () => {
    signIn()
    let status: DoctorStatus = 'approved'
    mockApi({
      'GET /v1/doctor/me': () => doctorAccount({ status, reviewNote: status === 'suspended' ? "Shikoyatlar ko'p" : null }),
      'GET /v1/doctor/questions': () => {
        status = 'suspended'
        return apiError(403, 'forbidden', 'Faqat tasdiqlangan shifokorlar uchun')
      },
    })
    renderApp(<AppRoutes />)

    expect(await screen.findByRole('heading', { name: "Shifokor hisobingiz to'xtatilgan" })).toBeInTheDocument()
    expect(screen.getByText("Shikoyatlar ko'p")).toBeInTheDocument()
  })

  it('offers a retry and a way out when the account cannot be read', async () => {
    signIn()
    let fail = true
    mockApi({
      'GET /v1/doctor/me': () => (fail ? apiError(500, 'internal_error', 'Ichki xatolik') : doctorAccount({ status: 'pending' })),
    })
    renderApp(<AppRoutes />)

    expect(await screen.findByText('Ichki xatolik')).toBeInTheDocument()
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Qayta urinish' }))
    expect(await screen.findByRole('heading', { name: "Arizangiz ko'rib chiqilmoqda" })).toBeInTheDocument()
  })
})
