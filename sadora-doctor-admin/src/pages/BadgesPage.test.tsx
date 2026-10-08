import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AppRoutes } from '../App'
import type { BadgeBoard } from '../api/types'
import { mockApi } from '../test/http'
import { doctorAccount, renderApp, signIn } from '../test/render'

const base = {
  'GET /v1/doctor/me': doctorAccount(),
  'GET /v1/doctor/questions': [],
  'GET /v1/community/conversations': [],
}

function board(unseen: BadgeBoard['unseen'] = []): BadgeBoard {
  return {
    canWear: false,
    unseen,
    badges: [
      { key: 'verified', tier: 1, thresholds: [1], progress: 1, earnedAt: '2026-10-01T09:00:00Z' },
      { key: 'consults', tier: 1, thresholds: [1, 10, 50], progress: 4, earnedAt: '2026-10-02T09:00:00Z' },
      { key: 'answers', tier: 0, thresholds: [5, 25, 100], progress: 2 },
      // A key a newer server knows and this panel does not: left out, never a raw name.
      { key: 'brand_new', tier: 1, thresholds: [1], progress: 1 },
    ],
  }
}

describe('Nishonlar', () => {
  it('shows what she earned, what is ahead, and a badge’s ladder', async () => {
    mockApi({ ...base, 'GET /v1/doctor/badges': board() })
    signIn()
    renderApp(<AppRoutes />, { route: '/badges' })

    expect(await screen.findByRole('button', { name: 'Tasdiqlangan shifokor, Maxsus' })).toBeInTheDocument()
    const nav = screen.getByRole('navigation')
    expect(within(nav).getByRole('link', { name: 'Nishonlar' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Konsultant, Bronza' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Savollarga javob, hali ochilmagan' })).toBeInTheDocument()
    expect(screen.queryByText('brand_new')).not.toBeInTheDocument()
    // Tiers of the known badges: 1 + 1 reached of 1 + 3 + 3.
    expect(screen.getByRole('progressbar', { name: 'Olingan bosqichlar' })).toHaveAttribute('aria-valuemax', '7')

    await userEvent.click(screen.getByRole('button', { name: 'Konsultant, Bronza' }))
    const dialog = await screen.findByRole('dialog', { name: 'Konsultant' })
    expect(within(dialog).getByText("10 ta konsultatsiya o'tkazing")).toBeInTheDocument()
    expect(within(dialog).getByText('Keyingisi: Kumush — 4 / 10')).toBeInTheDocument()
  })

  it('plays a new tier anywhere in the panel and tells the server once it is seen', async () => {
    let unseen: BadgeBoard['unseen'] = [
      { key: 'consults', tier: 1, maxTier: 3, coins: 0, earnedAt: '2026-10-02T09:00:00Z' },
      { key: 'verified', tier: 1, maxTier: 1, coins: 0, earnedAt: '2026-10-01T09:00:00Z' },
    ]
    const api = mockApi({
      ...base,
      'GET /v1/doctor/badges': () => board(unseen),
      'POST /v1/doctor/badges/seen': (call) => {
        const keys = (call.body as { keys: string[] }).keys
        unseen = keys.length ? unseen.filter((unlock) => !keys.includes(unlock.key)) : []
        return { ok: true }
      },
    })
    signIn()
    renderApp(<AppRoutes />, { route: '/' })

    const overlay = await screen.findByRole('dialog', { name: 'Yangi nishon: Konsultant' })
    expect(within(overlay).getByText('Yana 1 ta nishon')).toBeInTheDocument()
    expect(within(overlay).getByText("1 ta konsultatsiya o'tkazing")).toBeInTheDocument()
    await userEvent.click(within(overlay).getByRole('button', { name: 'Ajoyib!' }))
    await waitFor(() => expect(api.callsTo('POST', '/v1/doctor/badges/seen')[0]?.body).toEqual({ keys: ['consults'] }))

    const next = await screen.findByRole('dialog', { name: 'Yangi nishon: Tasdiqlangan shifokor' })
    await userEvent.click(within(next).getByRole('button', { name: 'Ajoyib!' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: /Yangi nishon/ })).not.toBeInTheDocument())
  })
})
