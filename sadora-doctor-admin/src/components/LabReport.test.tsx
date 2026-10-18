import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import type { LabReport } from '../api/types'
import { formatLabDate, LabCard, labCounts, labRange, LabReportDialog, RecordLabs } from './LabReport'

function sheet(overrides: Partial<LabReport> = {}): LabReport {
  return {
    id: 'r1',
    takenOn: '2026-10-01',
    place: 'Invitro',
    scanned: true,
    values: [
      {
        id: 'v1',
        markerCode: 'hgb',
        name: 'Gemoglobin',
        value: 104,
        unit: 'g/L',
        refLow: 120,
        refHigh: 150,
        flag: 'low',
        about: "Qondagi kislorod tashuvchi oqsil.",
      },
      { id: 'v2', name: 'TTG', value: 2.1, unit: 'mIU/L', refHigh: 4, flag: 'normal' },
      { id: 'v3', name: 'Ferritin', value: 40, unit: 'ng/mL', refLow: 30, flag: 'normal' },
      { id: 'v4', name: 'Gepatit B', valueText: 'manfiy', refText: 'manfiy' },
      { id: 'v5', name: 'Glyukoza', value: 7.2, unit: 'mmol/L', refLow: 3.3, refHigh: 5.5, flag: 'high' },
    ],
    photos: [],
    createdAt: '2026-10-02T08:00:00Z',
    updatedAt: '2026-10-02T08:00:00Z',
    ...overrides,
  }
}

function CardThatOpens({ report }: { report: LabReport }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <LabCard report={report} onOpen={() => setOpen(true)} />
      {open && <LabReportDialog report={report} onClose={() => setOpen(false)} />}
    </>
  )
}

describe('lab results', () => {
  it('words a date, a range and the counts the way the app does', () => {
    expect(formatLabDate('2026-10-01')).toBe('01.10.2026')
    const [hgb, ttg, ferritin, hepatitis] = sheet().values!
    expect(labRange(hgb!)).toBe('120–150')
    expect(labRange(ttg!)).toBe('< 4')
    expect(labRange(ferritin!)).toBe('> 30')
    expect(labRange(hepatitis!)).toBe('manfiy')
    expect(labCounts(sheet())).toBe("5 ta ko'rsatkich · 2 tasi normadan tashqarida")
    expect(labCounts(sheet({ values: [] }))).toBe("0 ta ko'rsatkich")
  })

  it('opens the card to every row, flagging only what the laboratory range puts outside', async () => {
    render(<CardThatOpens report={sheet()} />)
    expect(screen.getByText('Tahlil natijalari')).toBeInTheDocument()
    expect(screen.getByText('01.10.2026 · Invitro')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Ochish' }))
    const dialog = await screen.findByRole('dialog', { name: 'Tahlil natijalari' })
    const rows = within(dialog).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(5)
    expect(rows[0]).toHaveTextContent('Gemoglobin')
    expect(rows[0]).toHaveTextContent('104 g/L')
    expect(rows[0]).toHaveTextContent('Past')
    expect(rows[0]).toHaveClass('lab-out')
    expect(within(dialog).getByText("Qondagi kislorod tashuvchi oqsil.")).toBeInTheDocument()
    expect(rows[1]).toHaveTextContent('Normada')
    // No printed range: no flag at all, not "normal".
    expect(rows[3]).toHaveTextContent('manfiy')
    expect(rows[3]).not.toHaveTextContent('Normada')
    expect(rows[4]).toHaveTextContent('Yuqori')
  })

  it('lists a record’s sheets with the newest open', () => {
    const { container } = render(<RecordLabs labs={[sheet(), sheet({ id: 'r0', takenOn: '2026-06-12', place: null, values: [] })]} />)
    const sheets = container.querySelectorAll('details')
    expect(sheets).toHaveLength(2)
    expect(sheets[0]).toHaveAttribute('open')
    expect(sheets[1]).not.toHaveAttribute('open')
    expect(sheets[1]).toHaveTextContent('12.06.2026')
  })
})
