import { describe, expect, it } from 'vitest'
import { emptyItem, itemFrom, presetTimes, problemsOf, summaryOf, toRequest } from './prescriptions'

describe('prescription drafts', () => {
  it('asks for the name, the dose, the food relation and a length before anything goes', () => {
    expect([...problemsOf(emptyItem())].sort()).toEqual(['days', 'dose', 'food', 'name'])
    const ready = { ...emptyItem(), name: 'Amoksitsillin', dose: '500', food: 'after' as const, days: '5' }
    expect(problemsOf(ready).size).toBe(0)
    expect(problemsOf({ ...ready, days: '', ongoing: true }).size).toBe(0)
    expect(problemsOf({ ...ready, times: ['09:00', '09:00'] }).has('times')).toBe(true)
  })

  it('sends the form unit when she typed none, sorted times, and no length for an ongoing course', () => {
    const item = { ...emptyItem(), name: ' Magniy ', dose: '1', food: 'with' as const, times: ['21:00', '09:00'], ongoing: true }
    const request = toRequest([item], '  ')
    expect(request.note).toBeNull()
    expect(request.items[0]).toMatchObject({
      name: 'Magniy',
      unit: 'tabletka',
      days: null,
      schedule: { kind: 'daily', times: ['09:00', '21:00'] },
    })
    expect(toRequest([{ ...item, everyDays: 3 }], '').items[0]!.schedule).toMatchObject({ kind: 'interval', intervalDays: 3 })
  })

  it('reads a sent item back into the form, and words it for the card', () => {
    const sent = toRequest([{ ...emptyItem(), name: 'D3', dose: '2', unit: 'tomchi', food: 'any' as const, startDay: 6, days: '10' }], '').items[0]!
    expect(itemFrom(sent)).toMatchObject({ name: 'D3', unit: 'tomchi', startDay: 6, days: '10', ongoing: false })
    expect(summaryOf(sent)).toBe("2 tomchi · kuniga 2 marta: 09:00, 21:00 · farqi yo'q · 6-kundan 10 kun")
    expect(presetTimes(3)).toEqual(['08:00', '14:00', '20:00'])
  })
})
