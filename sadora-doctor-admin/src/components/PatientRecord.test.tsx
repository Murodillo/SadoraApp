import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { DoctorSummary } from '../api/types'
import { PatientRecord, rankedCounts } from './PatientRecord'

function record(overrides: Partial<DoctorSummary> = {}): DoctorSummary {
  return {
    generatedAt: '2026-10-18T09:00:00Z',
    language: 'uz',
    person: { name: 'Dilnoza', age: 27, lifeStage: 'cycle', memberSince: '2026-02-01' },
    ...overrides,
  }
}

describe('PatientRecord — conditions and the pain diary', () => {
  it('shows each condition, a diagnosis with its date and a suspicion in the warning tone', () => {
    render(
      <PatientRecord
        record={record({
          conditions: {
            conditions: [
              { condition: 'pcos', status: 'diagnosed', diagnosedOn: '2025-03-12', updatedAt: '2026-10-01T00:00:00Z' },
              { condition: 'endometriosis', status: 'suspected', updatedAt: '2026-10-01T00:00:00Z' },
            ],
            pain: {
              days: 90,
              daysLogged: 40,
              painDays: 14,
              averageLevel: 5.26,
              maxLevel: 8,
              painkillerDays: 9,
              missedDays: 3,
              byKind: { period: 11, intercourse: 4, bowel: 6, constant: 0 },
              byArea: { lower_back: 5, lower_abdomen: 12 },
            },
          },
        })}
      />,
    )
    const section = screen.getByRole('region', { name: 'Holatlar' })
    expect(within(section).getByText('Polikistoz tuxumdon sindromi (PCOS)')).toBeInTheDocument()
    expect(within(section).getByText('Shifokor tashxisi · 12-mart 2025')).toBeInTheDocument()
    expect(within(section).getByText('Endometrioz')).toBeInTheDocument()
    expect(within(section).getByText("Gumon (tashxis yo'q)")).toHaveClass('record-suspected')

    expect(within(section).getByText("Og'riq kundaligi · so'nggi 90 kun")).toBeInTheDocument()
    expect(within(section).getByText('14 / 40')).toBeInTheDocument()
    expect(within(section).getByText('5.3 / 10')).toBeInTheDocument()
    expect(within(section).getByText('8 / 10')).toBeInTheDocument()
    expect(within(section).getByText('9')).toBeInTheDocument()
    expect(within(section).getByText('3')).toBeInTheDocument()

    // Most first; a zero count is not listed.
    const labels = Array.from(section.querySelectorAll('dt')).map((dt) => dt.textContent)
    expect(labels.slice(-5)).toEqual(['Hayzda', "Ichak bo'shatishda", 'Jinsiy aloqada', 'Qorin pasti', 'Bel'])
    expect(within(section).queryByText('Doimiy')).not.toBeInTheDocument()
  })

  it('leaves the section out when she marked no condition', () => {
    render(<PatientRecord record={record({ conditions: null })} />)
    expect(screen.queryByRole('region', { name: 'Holatlar' })).not.toBeInTheDocument()
  })

  it('shows the next period as a range for an irregular cycle', () => {
    const history = { cycles: [], averageCycleLength: 34 }
    render(
      <PatientRecord
        record={record({
          cycle: {
            today: '2026-10-18',
            history: {
              ...history,
              prediction: {
                confidence: 'low',
                reason: 'irregular',
                nextPeriodStart: '2026-11-02',
                nextPeriodEarliest: '2026-10-29',
                nextPeriodLatest: '2026-11-08',
              },
            },
          },
        })}
      />,
    )
    expect(screen.getByText('29-oktabr 2026 – 8-noyabr 2026 (taxminiy)')).toBeInTheDocument()
  })

  it('ranks counts most first and keeps the given order on a tie', () => {
    expect(rankedCounts({ b: 2, a: 2, c: 5, d: 0 }, ['a', 'b', 'c', 'd'] as const)).toEqual([
      ['c', 5],
      ['a', 2],
      ['b', 2],
    ])
    expect(rankedCounts(undefined, ['a'])).toEqual([])
  })
})
