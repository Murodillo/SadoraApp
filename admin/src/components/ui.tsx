import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import type { AccountStatus, SubscriptionTier } from '../api/types'
import { percentChange } from './analytics'
import { Sparkline } from './charts'
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

/**
 * Change against the previous period. Up is good for most tiles, but not for "failed
 * payments" or "blocked" — `invert` flips the colour, never the arrow.
 */
export function Delta({ current, previous, invert = false, suffix = '' }: { current: number; previous: number; invert?: boolean; suffix?: string }) {
  const change = percentChange(current, previous)
  if (change === null) {
    return current > 0 && previous === 0 ? <span className="delta new">yangi</span> : null
  }
  const percent = Math.round(change * 100)
  if (percent === 0) return <span className="delta flat">0%{suffix}</span>
  const up = percent > 0
  const good = invert ? !up : up
  return (
    <span className={`delta ${good ? 'good' : 'bad'}`} title="Oldingi davrga nisbatan">
      {up ? '▲' : '▼'} {Math.abs(percent)}%{suffix}
    </span>
  )
}

export function Stat({
  label,
  value,
  hint,
  delta,
  spark,
  sparkColor,
}: {
  label: string
  value: ReactNode
  hint?: ReactNode
  /** Previous-period value for the change badge; only meaningful with a numeric `value`. */
  delta?: { previous: number; invert?: boolean }
  /** A small trend behind the number. */
  spark?: number[]
  sparkColor?: string
}) {
  return (
    <div className="card stat">
      <div className="label">{label}</div>
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-end', gap: 6 }}>
        <div className={`value${typeof value === 'number' ? '' : ' text'}`}>{typeof value === 'number' ? <CountedValue value={value} /> : value}</div>
        {spark && spark.length > 1 && <Sparkline values={spark} color={sparkColor} />}
      </div>
      {(hint || (delta && typeof value === 'number')) && (
        <div className="hint row" style={{ gap: 6 }}>
          {delta && typeof value === 'number' && <Delta current={value} previous={delta.previous} invert={delta.invert} />}
          {hint && <span>{hint}</span>}
        </div>
      )}
    </div>
  )
}

export function TierBadge({ tier }: { tier: SubscriptionTier }) {
  return <span className={`badge ${tier}`}>{tier === 'premium' ? 'Premium' : 'Free'}</span>
}

const statusLabels: Record<AccountStatus, { text: string; tone: string }> = {
  active: { text: 'Faol', tone: 'ok' },
  blocked: { text: 'Bloklangan', tone: 'danger' },
  deletion_pending: { text: "O'chirilmoqda", tone: 'warn' },
}

export function StatusBadge({ status }: { status: AccountStatus }) {
  const { text, tone } = statusLabels[status]
  return <span className={`badge ${tone}`}>{text}</span>
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
    </div>
  )
}

export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof Error ? error.message : String(error)
  return <div className="notice error">{message}</div>
}

export function Loading({ rows = 4 }: { rows?: number }) {
  return (
    <div className="grid" style={{ gap: 8 }}>
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className="skeleton" style={{ height: 32, animationDelay: `${index * 80}ms` }} />
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

/**
 * The app's toggle, for a boolean that takes effect on save. A checkbox in a table cell
 * is eleven pixels of state; a switch can be read across the room.
 */
export function Switch({
  checked,
  onChange,
  disabled = false,
  label,
}: {
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
  label?: string
}) {
  return (
    <label className={`switch${disabled ? ' disabled' : ''}`}>
      <input
        type="checkbox"
        role="switch"
        aria-checked={checked}
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="switch-track">
        <span className="switch-knob" />
      </span>
      {label && <span className="switch-label">{label}</span>}
    </label>
  )
}

/**
 * Tabs whose underline slides to the selected one rather than jumping. The indicator is
 * measured from the buttons, so a label of any length gets the right width.
 */
export function Tabs<K extends string>({
  items,
  value,
  onChange,
}: {
  items: { key: K; label: ReactNode }[]
  value: K
  onChange: (key: K) => void
}) {
  const bar = useRef<HTMLDivElement>(null)
  const [indicator, setIndicator] = useState<{ left: number; width: number } | null>(null)

  useLayoutEffect(() => {
    const node = bar.current?.querySelector<HTMLButtonElement>(`[data-tab="${value}"]`)
    if (node) setIndicator({ left: node.offsetLeft, width: node.offsetWidth })
  }, [value, items])

  return (
    <div className="tabs" ref={bar} role="tablist">
      {items.map((item) => (
        <button
          key={item.key}
          data-tab={item.key}
          role="tab"
          aria-selected={item.key === value}
          className={`tab${item.key === value ? ' active' : ''}`}
          onClick={() => onChange(item.key)}
        >
          {item.label}
        </button>
      ))}
      {indicator && <span className="tab-indicator" style={{ left: indicator.left, width: indicator.width }} />}
    </div>
  )
}

/** The tab's body, re-keyed so a switch plays a short crossfade. */
export function TabPanel({ id, children }: { id: string; children: ReactNode }) {
  return (
    <div className="tab-panel" key={id} role="tabpanel">
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

  return (
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
    </div>
  )
}

/** `2026-08-27T09:15:00Z` -> `27.08.2026 14:15` in the operator's own timezone. */
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

/** "hech qachon" reads better than an empty cell for a subscription with no end. */
export function formatExpiry(iso?: string | null): string {
  return iso ? formatDate(iso) : 'muddatsiz'
}

/** `14:05` — for a "yangilangan" stamp next to live numbers. */
export function formatTime(iso?: string | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString('uz-UZ', { hour: '2-digit', minute: '2-digit' })
}
