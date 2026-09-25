import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from '../App'
import { tokenStore } from '../api/client'
import { apiError, mockApi } from '../test/http'
import { doctorAccount, renderApp } from '../test/render'

const challenge = (devCode?: string) => ({
  challengeId: 'ch-1',
  expiresAt: '2026-09-26T12:05:00Z',
  resendAfterSeconds: 60,
  attemptsLeft: 5,
  ...(devCode ? { devCode } : {}),
})

const session = {
  tokens: { accessToken: 'access-1', refreshToken: 'refresh-1', accessExpiresAt: 'x', refreshExpiresAt: 'y' },
  user: { id: 'user-1', name: 'Dilnoza', phone: '+998901234567' },
  entitlements: {},
  isNewUser: false,
}

describe('LoginPage', () => {
  it('holds the button until the number is a real Uzbek mobile, and says why', async () => {
    mockApi({})
    renderApp(<AppRoutes />)
    const phone = screen.getByLabelText('Telefon raqami')
    const send = screen.getByRole('button', { name: 'Kod olish' })

    await userEvent.type(phone, '70123')
    expect(send).toBeDisabled()
    // Half-typed is not wrong yet.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    await userEvent.type(phone, '4567')
    expect(phone).toHaveValue('70 123 45 67')
    expect(screen.getByRole('alert')).toHaveTextContent("Bunday operator kodi yo'q")

    // A number pasted from a contact card, country code and all.
    await userEvent.clear(phone)
    await userEvent.paste('+998 90 123 45 67')
    expect(phone).toHaveValue('90 123 45 67')
    expect(send).toBeEnabled()
  })

  it('sends the code, fills in a dev code, and signs in to the gate', async () => {
    const api = mockApi({
      'POST /v1/auth/otp/request': challenge('123456'),
      'POST /v1/auth/otp/verify': session,
      'GET /v1/doctor/me': doctorAccount({ status: 'none' }),
    })
    renderApp(<AppRoutes />)

    await userEvent.type(screen.getByLabelText('Telefon raqami'), '901234567')
    await userEvent.click(screen.getByRole('button', { name: 'Kod olish' }))

    expect(await screen.findByText('+998 90 123 45 67')).toBeInTheDocument()
    expect(screen.getByLabelText('SMS kod')).toHaveValue('123456')
    expect(api.callsTo('POST', '/v1/auth/otp/request')[0]!.body).toEqual({ phone: '+998901234567', language: 'uz' })

    await userEvent.click(screen.getByRole('button', { name: 'Kirish' }))

    expect(await screen.findByText('Ariza Sadora Doctor ilovasida topshiriladi')).toBeInTheDocument()
    expect(tokenStore.readAccess()).toBe('access-1')
  })

  it('slides the code step in, and back out to the number', async () => {
    mockApi({ 'POST /v1/auth/otp/request': challenge() })
    renderApp(<AppRoutes />)
    // First paint: the card pops in on its own; the step does not slide.
    expect(screen.getByLabelText('Telefon raqami').closest('form')).toHaveClass('step-none')

    await userEvent.type(screen.getByLabelText('Telefon raqami'), '901234567')
    await userEvent.click(screen.getByRole('button', { name: 'Kod olish' }))
    expect((await screen.findByLabelText('SMS kod')).closest('form')).toHaveClass('step-forward')

    await userEvent.click(screen.getByRole('button', { name: "← Raqamni o'zgartirish" }))
    const phone = screen.getByLabelText('Telefon raqami')
    expect(phone.closest('form')).toHaveClass('step-back')
    // The number she typed is still there.
    expect(phone).toHaveValue('90 123 45 67')
  })

  it('puts a wrong code under the code field and lets her try again', async () => {
    mockApi({
      'POST /v1/auth/otp/request': challenge(),
      'POST /v1/auth/otp/verify': apiError(400, 'otp_invalid', "Kod noto'g'ri. 4 ta urinish qoldi"),
    })
    renderApp(<AppRoutes />)

    await userEvent.type(screen.getByLabelText('Telefon raqami'), '901234567')
    await userEvent.click(screen.getByRole('button', { name: 'Kod olish' }))
    await userEvent.type(await screen.findByLabelText('SMS kod'), '000000')
    await userEvent.click(screen.getByRole('button', { name: 'Kirish' }))

    expect(await screen.findByRole('alert')).toHaveTextContent("Kod noto'g'ri. 4 ta urinish qoldi")
    expect(screen.getByLabelText('SMS kod')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('button', { name: /Qayta yuborish/ })).toBeDisabled()
    expect(tokenStore.readAccess()).toBeNull()
  })

  it('shows a refusal of the SMS itself as a toast', async () => {
    mockApi({
      'POST /v1/auth/otp/request': apiError(429, 'rate_limited', "Juda ko'p urinish. Birozdan keyin qayta urining"),
    })
    renderApp(<AppRoutes />)

    await userEvent.type(screen.getByLabelText('Telefon raqami'), '901234567')
    await userEvent.click(screen.getByRole('button', { name: 'Kod olish' }))

    expect(await screen.findByRole('status')).toHaveTextContent("Juda ko'p urinish")
    expect(screen.getByLabelText('Telefon raqami')).toBeInTheDocument()
  })
})
