import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Delta, Modal, Switch, Tabs } from './ui'

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
    // The dialog plays its exit first; the parent is told once it is over.
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

describe('Switch', () => {
  it('reports the new state and honours disabled', () => {
    const onChange = vi.fn()
    const { rerender } = render(<Switch checked={false} onChange={onChange} label="Faol" />)
    fireEvent.click(screen.getByRole('switch', { name: 'Faol' }))
    expect(onChange).toHaveBeenCalledWith(true)

    // jsdom dispatches a click on a disabled control where a browser would not, so the
    // guarantee checked here is the attribute the browser honours.
    rerender(<Switch checked disabled onChange={onChange} label="Faol" />)
    expect(screen.getByRole('switch', { name: 'Faol' })).toBeDisabled()
    expect(screen.getByRole('switch')).toBeChecked()
  })
})

describe('Tabs', () => {
  it('marks the selected tab and reports a change', () => {
    const onChange = vi.fn()
    render(
      <Tabs
        value="a"
        onChange={onChange}
        items={[
          { key: 'a', label: 'Birinchi' },
          { key: 'b', label: 'Ikkinchi' },
        ]}
      />,
    )
    expect(screen.getByRole('tab', { name: 'Birinchi' })).toHaveAttribute('aria-selected', 'true')
    fireEvent.click(screen.getByRole('tab', { name: 'Ikkinchi' }))
    expect(onChange).toHaveBeenCalledWith('b')
  })
})

describe('Delta', () => {
  it('shows a rise as good and a fall as bad, unless inverted', () => {
    const { rerender } = render(<Delta current={120} previous={100} />)
    expect(screen.getByText('▲ 20%')).toHaveClass('good')

    rerender(<Delta current={80} previous={100} />)
    expect(screen.getByText('▼ 20%')).toHaveClass('bad')

    rerender(<Delta current={80} previous={100} invert />)
    expect(screen.getByText('▼ 20%')).toHaveClass('good')
  })

  it('calls a first occurrence new and says nothing for nothing', () => {
    const { rerender, container } = render(<Delta current={3} previous={0} />)
    expect(screen.getByText('yangi')).toBeInTheDocument()
    rerender(<Delta current={0} previous={0} />)
    expect(container).toBeEmptyDOMElement()
  })
})
