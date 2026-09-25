import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider, useToast } from './toast'

function Trigger() {
  const { notify } = useToast()
  return (
    <>
      <button onClick={() => notify('Saqlandi')}>ok</button>
      <button onClick={() => notify('Xato', 'error')}>err</button>
    </>
  )
}

describe('toasts', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('shows a message and lets it go by itself', () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    )
    fireEvent.click(screen.getByText('ok'))
    expect(screen.getByRole('status')).toHaveTextContent('Saqlandi')
    expect(screen.getByRole('status')).toHaveClass('ok')

    act(() => {
      vi.advanceTimersByTime(3600)
    })
    expect(screen.getByRole('status')).toHaveClass('leaving')
    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('carries the tone and dismisses on click', () => {
    render(
      <ToastProvider>
        <Trigger />
      </ToastProvider>,
    )
    fireEvent.click(screen.getByText('err'))
    const toast = screen.getByRole('status')
    expect(toast).toHaveClass('error')
    fireEvent.click(toast)
    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('is a no-op outside the provider', () => {
    render(<Trigger />)
    expect(() => fireEvent.click(screen.getByText('ok'))).not.toThrow()
  })
})
