import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'

/*
 * Toasts: a save that succeeded says so, briefly, in the corner.
 *
 * Every editable table on the panel disables its "Saqlash" button once the row is
 * clean again, and that was the only confirmation an operator got. A button going grey
 * is easy to miss and impossible to tell from "still saving"; a line that slides in and
 * out is not.
 */

export type ToastTone = 'ok' | 'error' | 'info'

export interface Toast {
  id: number
  message: string
  tone: ToastTone
  leaving?: boolean
}

interface ToastValue {
  notify: (message: string, tone?: ToastTone) => void
}

const ToastContext = createContext<ToastValue | null>(null)

const LIFETIME_MS = 3600
const LEAVE_MS = 220

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.map((toast) => (toast.id === id ? { ...toast, leaving: true } : toast)))
    window.setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), LEAVE_MS)
  }, [])

  const notify = useCallback(
    (message: string, tone: ToastTone = 'ok') => {
      const id = nextId.current++
      setToasts((current) => [...current.slice(-3), { id, message, tone }])
      window.setTimeout(() => dismiss(id), LIFETIME_MS)
    },
    [dismiss],
  )

  const value = useMemo(() => ({ notify }), [notify])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`toast ${toast.tone}${toast.leaving ? ' leaving' : ''}`}
            role="status"
            onClick={() => dismiss(toast.id)}
          >
            <span className="toast-glyph" aria-hidden="true">
              {toast.tone === 'ok' ? '✓' : toast.tone === 'error' ? '!' : 'i'}
            </span>
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

/** Outside a provider (tests, storybook-ish renders) the calls are simply dropped. */
export function useToast(): ToastValue {
  const value = useContext(ToastContext)
  return value ?? { notify: () => undefined }
}
