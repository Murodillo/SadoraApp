import { describe, expect, it } from 'vitest'
import type { AdminRole } from '../api/types'
import { groups } from '../layout/nav'
import { matchPages, moveIndex, normalise, resultItems } from './palette'

const as = (role: AdminRole) => (roles: AdminRole[]) => roles.includes(role)

describe('matchPages', () => {
  it('finds a page by its name, its group or a keyword', () => {
    expect(matchPages(groups, 'funksiya', as('OWNER')).map((item) => item.to)).toEqual(['/features'])
    expect(matchPages(groups, 'bayroq', as('OWNER')).map((item) => item.to)).toEqual(['/features'])
    expect(matchPages(groups, 'payme', as('OWNER')).map((item) => item.to)).toContain('/billing')
  })

  it("ignores case and the apostrophe in Do'kon", () => {
    expect(matchPages(groups, 'DOKON', as('OWNER')).map((item) => item.to)).toEqual(['/shop'])
  })

  it('offers only the pages the role can open', () => {
    expect(matchPages(groups, 'operator', as('OWNER')).map((item) => item.to)).toContain('/operators')
    expect(matchPages(groups, 'operator', as('ADMIN')).map((item) => item.to)).not.toContain('/operators')
    expect(matchPages(groups, 'shifokor', as('ANALYST'))).toEqual([])
  })

  it('lists every page the role can open for an empty box', () => {
    const all = groups.flatMap((group) => group.entries).length
    expect(matchPages(groups, '', as('OWNER'))).toHaveLength(all)
  })
})

describe('resultItems', () => {
  it('turns finds into lines that open the right page', () => {
    const items = resultItems({
      users: [
        {
          id: 'u1',
          name: '',
          phone: '+998901234567',
          language: 'uz',
          lifeStage: 'cycle',
          tier: 'free',
          status: 'active',
          registeredAt: '2026-10-01T00:00:00Z',
        },
      ],
      doctors: [{ id: 'd1', fullName: 'Dr Aziza', specialty: 'gynecologist', status: 'approved' }],
      payments: [
        { id: 'p1', userId: 'u1', provider: 'payme', amountMinor: 4_990_000, state: 'paid', createdAt: '2026-10-01T00:00:00Z' },
      ],
    })
    expect(items.map((item) => [item.section, item.title, item.to])).toEqual([
      ['Foydalanuvchilar', 'Ismsiz', '/users/u1'],
      ['Shifokorlar', 'Dr Aziza', '/doctors?status=approved&id=d1'],
      ["To'lovlar", "49 900 so'm · payme", '/users/u1'],
    ])
    expect(items[1]!.subtitle).toBe('Ginekolog · Tasdiqlangan')
  })

  it('is empty before the server answers', () => {
    expect(resultItems(undefined)).toEqual([])
  })
})

describe('moveIndex and normalise', () => {
  it('wraps around the list both ways', () => {
    expect(moveIndex(0, -1, 3)).toBe(2)
    expect(moveIndex(2, 1, 3)).toBe(0)
    expect(moveIndex(1, 1, 3)).toBe(2)
    expect(moveIndex(0, 1, 0)).toBe(0)
  })

  it('drops case and every kind of apostrophe', () => {
    expect(normalise(" O‘zbek O'zbek Oʻzbek ")).toBe('ozbek ozbek ozbek')
  })
})
