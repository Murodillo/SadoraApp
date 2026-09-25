import { cloneElement, isValidElement, useEffect, useId, useRef, useState } from 'react'
import type { ReactElement, ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useCountUp } from './motion'

export { useCountUp } from './motion'

export function Card({ title, children, action, className }: { title?: string; children: ReactNode; action?: ReactNode; className?: string }) {
  return (
    <section className={`card${className ? ` ${className}` : ''}`}>
      {(title || action) && (
        <div className="row card-head" style={{ justifyContent: 'space-between', marginBottom: 12 }}>
          {title && <h2 style={{ margin: 0 }}>{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

function CountedValue({ value }: { value: number }) {
  const shown = useCountUp(value)
  return <>{shown.toLocaleString('ru-RU')}</>
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: ReactNode }) {
  return (
    <div className="card stat">
      <div className="label">{label}</div>
      <div className={`value${typeof value === 'number' ? '' : ' text'}`}>
        {typeof value === 'number' ? <CountedValue value={value} /> : value}
      </div>
      {hint && <div className="hint">{hint}</div>}
    </div>
  )
}

/**
 * A labelled control. The label is tied to the control by id, so clicking it focuses the
 * field and a screen reader names the input.
 *
 * `error` is the server's word on this field (a validation `details` entry) or the
 * panel's own; it sits under the control and is announced with it. `hint` shares the
 * line on the right — a character counter, say. `prefix` is fixed text drawn inside the
 * box before the control, like the country code in front of a phone number.
 */
export function Field({
  label,
  children,
  error,
  hint,
  prefix,
}: {
  label: string
  children: ReactNode
  error?: string | null
  hint?: ReactNode
  prefix?: string
}) {
  const id = useId()
  const errorId = `${id}-error`
  const control =
    isValidElement(children) && !(children.props as { id?: string }).id
      ? cloneElement(children as ReactElement<Record<string, unknown>>, {
          id,
          'aria-invalid': error ? true : undefined,
          'aria-describedby': error ? errorId : undefined,
        })
      : children
  return (
    <div className={`field${error ? ' invalid' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {prefix ? (
        <div className="affix">
          <span className="affix-text" aria-hidden="true">
            {prefix}
          </span>
          {control}
        </div>
      ) : (
        control
      )}
      {(error || hint) && (
        <div className="field-foot">
          {error ? (
            <span className="field-error" id={errorId} role="alert">
              {error}
            </span>
          ) : (
            <span />
          )}
          {hint && <span className="field-hint">{hint}</span>}
        </div>
      )}
    </div>
  )
}

/** `123 / 1000`, turning red past the limit. */
export function Counter({ length, max }: { length: number; max: number }) {
  return (
    <span className={`counter${length > max ? ' over' : ''}`} aria-live="off">
      {length} / {max}
    </span>
  )
}

export function ErrorNotice({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : String(error)
  return (
    <div className="notice error row" style={{ justifyContent: 'space-between' }} role="alert">
      <span>{message}</span>
      {onRetry && (
        <button className="btn small" type="button" onClick={onRetry}>
          Qayta urinish
        </button>
      )}
    </div>
  )
}

export function Loading({ rows = 4, height = 32 }: { rows?: number; height?: number }) {
  return (
    <div className="grid" style={{ gap: 8 }} aria-busy="true" aria-label="Yuklanmoqda">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="skeleton" style={{ height, animationDelay: `${index * 80}ms` }} />
      ))}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}

/** A short spinner for a button that is waiting on the server. */
export function Spinner() {
  return <span className="spinner" aria-hidden="true" />
}

/** The staff panel's period picker, reused for the topic filter. */
export function Segmented<K extends string>({
  items,
  value,
  onChange,
  label,
}: {
  items: { key: K; label: ReactNode }[]
  value: K
  onChange: (key: K) => void
  label: string
}) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {items.map((item) => (
        <button
          key={item.key}
          type="button"
          role="radio"
          aria-checked={item.key === value}
          className={item.key === value ? 'active' : undefined}
          onClick={() => onChange(item.key)}
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}

/**
 * A body that crossfades when what it shows changes — the staff panel's tab switch,
 * used here for the open question. Re-keyed, so every switch plays it.
 */
export function SwapPanel({ id, children }: { id: string; children: ReactNode }) {
  return (
    <div className="swap-panel" key={id}>
      {children}
    </div>
  )
}

const MODAL_LEAVE_MS = 160

export function Modal({
  title,
  onClose,
  children,
  wide = false,
}: {
  title: string
  onClose: () => void
  children: ReactNode
  /** For forms that need a second column or a body textarea. */
  wide?: boolean
}) {
  const [leaving, setLeaving] = useState(false)
  const dialog = useRef<HTMLDivElement>(null)
  const closeRef = useRef(onClose)
  closeRef.current = onClose

  // The dialog plays its exit before the parent unmounts it, and closes on Escape from
  // wherever the focus happens to be — a form field, the backdrop, nowhere.
  const close = () => {
    if (leaving) return
    setLeaving(true)
    window.setTimeout(() => closeRef.current(), MODAL_LEAVE_MS)
  }

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        close()
      }
    }
    document.addEventListener('keydown', onKey)
    const previous = document.activeElement as HTMLElement | null
    const first = dialog.current?.querySelector<HTMLElement>('input, select, textarea, button')
    ;(first ?? dialog.current)?.focus()
    const { overflow } = document.body.style
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
      previous?.focus?.()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Rendered on <body>, not inside the page: every page keeps a `transform` from its
  // entrance animation, and a transformed ancestor becomes the containing block of a
  // fixed backdrop — the scrim would cover only the page box and the dialog could sit
  // off-screen on a long page.
  return createPortal(
    <div className={`backdrop${leaving ? ' leaving' : ''}`} onClick={close} role="presentation">
      <div
        ref={dialog}
        tabIndex={-1}
        className={`modal${wide ? ' wide' : ''}${leaving ? ' leaving' : ''}`}
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h3>{title}</h3>
          <button className="btn ghost small modal-close" onClick={close} aria-label="Yopish" type="button">
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}

/**
 * A check that is drawn rather than shown: the disc pops, then the tick is written along
 * its stroke — the logo's pen, reused for "sent".
 */
export function SuccessMark({ size = 36 }: { size?: number }) {
  return (
    <svg className="success-mark" width={size} height={size} viewBox="0 0 36 36" aria-hidden="true">
      <circle cx="18" cy="18" r="17" />
      <path d="M10.5 18.5l5 5 10-11" pathLength={1} />
    </svg>
  )
}

/** The check mark a verified doctor's name carries in the app. */
export function VerifiedMark({ title = 'Tasdiqlangan shifokor' }: { title?: string }) {
  return (
    <span className="verified" title={title} aria-label={title} role="img">
      ✓
    </span>
  )
}

/**
 * An alias's initial on one of the app's avatar tints. Aliases have no photo on purpose;
 * the tint index is all the server sends.
 */
export function Avatar({ name, tint, doctor = false }: { name: string; tint: number; doctor?: boolean }) {
  const initial = name.trim().charAt(0).toUpperCase() || '?'
  const index = ((Math.trunc(tint) % 6) + 6) % 6
  return (
    <span
      className={`avatar${doctor ? ' doctor' : ''}`}
      style={doctor ? undefined : { background: `var(--c${index + 1})` }}
      aria-hidden="true"
    >
      {initial}
    </span>
  )
}

/** `2026-08-27T09:15:00Z` -> `27.08.2026 14:15` in the doctor's own timezone. */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('uz-UZ', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDate(iso?: string | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('uz-UZ', { day: '2-digit', month: '2-digit', year: 'numeric' })
}

/**
 * `12 daqiqa oldin` for the last few days, the date after that. A question's age is what
 * a doctor sorts her attention by; an exact timestamp is in the tooltip.
 */
export function formatAgo(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '—'
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return '—'
  const minutes = Math.floor((now - then) / 60_000)
  if (minutes < 1) return 'hozirgina'
  if (minutes < 60) return `${minutes} daqiqa oldin`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} soat oldin`
  const days = Math.floor(hours / 24)
  if (days === 1) return 'kecha'
  if (days < 7) return `${days} kun oldin`
  return formatDate(iso)
}

/** `14:05` — for a "yangilangan" stamp next to live numbers. */
export function formatTime(value?: string | number | null): string {
  if (value === undefined || value === null || value === '' || value === 0) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString('uz-UZ', { hour: '2-digit', minute: '2-digit' })
}
