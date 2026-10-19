import { describe, expect, it } from 'vitest'
import { todayItems } from './today'

const at = '2026-10-19T12:00:00Z'

describe('todayItems', () => {
  it('lists the waiting queues, money first', () => {
    const items = todayItems({ doctorsPending: 2, openReports: 7, refundsDue: 1, shopSoldOut: 1, generatedAt: at })
    expect(items.map((item) => [item.key, item.count])).toEqual([
      ['refundsDue', 1],
      ['doctorsPending', 2],
      ['openReports', 7],
      ['shopSoldOut', 1],
    ])
  })

  it('drops an empty queue and one the role cannot open', () => {
    const items = todayItems({ doctorsPending: null, openReports: 3, refundsDue: 0, shopSoldOut: null, generatedAt: at })
    expect(items.map((item) => item.key)).toEqual(['openReports'])
  })

  it('is empty when nothing waits, and before the answer arrives', () => {
    expect(todayItems({ openReports: 0, generatedAt: at })).toEqual([])
    expect(todayItems(undefined)).toEqual([])
  })

  it('links the reports straight to their tab', () => {
    const [reports] = todayItems({ openReports: 1, generatedAt: at })
    expect(reports!.to).toBe('/community?tab=reports')
  })
})
