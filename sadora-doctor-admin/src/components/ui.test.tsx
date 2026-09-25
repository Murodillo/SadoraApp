import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Avatar, Counter, formatAgo, formatTime, Modal, Segmented, SwapPanel } from './ui'

// Ported from the staff panel: the dialog behaves the same in both.
describe('Modal', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('closes on Escape from anywhere, after its exit animation', () => {
    const onClose = vi.fn()
    render(
      <Modal title="Sinov" onClose={onClose}>
        <input aria-label="field" />
      </Modal>,
    )
    expect(screen.getByRole('dialog', { name: 'Sinov' })).toBeInTheDocument()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toHaveClass('leaving')
    act(() => {
      vi.advanceTimersByTime(200)
    })
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('closes from the corner button and the backdrop, but not from inside', () => {
    const onClose = vi.fn()
    render(
      <Modal title="Sinov" onClose={onClose}>
        <p>body</p>
      </Modal>,
    )
    fireEvent.click(screen.getByText('body'))
    act(() => {
      vi.advanceTimersByTime(200)
    })
    expect(onClose).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Yopish' }))
    act(() => {
      vi.advanceTimersByTime(200)
    })
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('locks the page scroll while open and gives it back', () => {
    const { unmount } = render(
      <Modal title="Sinov" onClose={() => undefined}>
        <p>body</p>
      </Modal>,
    )
    expect(document.body.style.overflow).toBe('hidden')
    unmount()
    expect(document.body.style.overflow).toBe('')
  })
})

describe('SwapPanel', () => {
  it('replays its entrance when what it shows changes, and only then', () => {
    const { container, rerender } = render(<SwapPanel id="q1">a</SwapPanel>)
    const first = container.firstElementChild
    rerender(<SwapPanel id="q1">a, updated</SwapPanel>)
    expect(container.firstElementChild).toBe(first)
    rerender(<SwapPanel id="q2">b</SwapPanel>)
    expect(container.firstElementChild).not.toBe(first)
    expect(container.firstElementChild).toHaveClass('swap-panel')
  })
})

describe('Segmented', () => {
  it('marks the choice and reports a change', () => {
    const onChange = vi.fn()
    render(
      <Segmented
        label="Bo'lim"
        value="a"
        onChange={onChange}
        items={[
          { key: 'a', label: 'Hammasi' },
          { key: 'b', label: 'Sikl' },
        ]}
      />,
    )
    expect(screen.getByRole('radio', { name: 'Hammasi' })).toHaveAttribute('aria-checked', 'true')
    fireEvent.click(screen.getByRole('radio', { name: 'Sikl' }))
    expect(onChange).toHaveBeenCalledWith('b')
  })
})

describe('Avatar', () => {
  it('draws the initial on one of six tints, whatever index the server sends', () => {
    const { container, rerender } = render(<Avatar name="lola" tint={7} />)
    expect(container.firstElementChild).toHaveTextContent('L')
    expect((container.firstElementChild as HTMLElement).style.background).toBe('var(--c2)')
    rerender(<Avatar name="" tint={-1} />)
    expect(container.firstElementChild).toHaveTextContent('?')
    expect((container.firstElementChild as HTMLElement).style.background).toBe('var(--c6)')
  })
})

describe('Counter', () => {
  it('turns red past the limit', () => {
    const { rerender } = render(<Counter length={10} max={10} />)
    expect(screen.getByText('10 / 10')).not.toHaveClass('over')
    rerender(<Counter length={11} max={10} />)
    expect(screen.getByText('11 / 10')).toHaveClass('over')
  })
})

describe('formatAgo', () => {
  const now = Date.parse('2026-09-26T12:00:00Z')
  const before = (minutes: number) => new Date(now - minutes * 60_000).toISOString()

  it('speaks in minutes, hours and days, then falls back to the date', () => {
    expect(formatAgo(before(0), now)).toBe('hozirgina')
    expect(formatAgo(before(12), now)).toBe('12 daqiqa oldin')
    expect(formatAgo(before(60 * 5), now)).toBe('5 soat oldin')
    expect(formatAgo(before(60 * 30), now)).toBe('kecha')
    expect(formatAgo(before(60 * 24 * 3), now)).toBe('3 kun oldin')
    expect(formatAgo(before(60 * 24 * 10), now)).toMatch(/16/)
  })

  it('shows a dash for nothing or nonsense', () => {
    expect(formatAgo(null, now)).toBe('—')
    expect(formatAgo('not a date', now)).toBe('—')
  })
})

describe('formatTime', () => {
  it('reads an ISO string or a timestamp, and has a dash for "never"', () => {
    expect(formatTime(Date.parse('2026-09-26T09:05:00'))).toMatch(/09.05/)
    expect(formatTime('2026-09-26T09:05:00')).toMatch(/09.05/)
    expect(formatTime(0)).toBe('—')
    expect(formatTime(null)).toBe('—')
    expect(formatTime('nonsense')).toBe('—')
  })
})
