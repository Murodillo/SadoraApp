import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import type { AccountStatus, SubscriptionTier } from '../api/types'

export function Card({ title, children, action }: { title?: string; children: ReactNode; action?: ReactNode }) {
  return (
    <section className="card">
      {(title || action) && (
        <div className="row" style={{ justifyContent: 'space-between', marginBottom: 12 }}>
          {title && <h2 style={{ margin: 0 }}>{title}</h2>}
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

/**
 * A number that counts up to its value the first time it is shown, and again whenever
 * it changes. A figure that simply appears reads as decoration; one that is counted
 * reads as a measurement, which is what a stat tile is for.
 */
export function useCountUp(target: number, durationMs = 720): number {
  const [value, setValue] = useState(0)
  const from = useRef(0)
  useEffect(() => {
    const start = performance.now()
    const begin = from.current
    let frame = 0
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs)
      // The app's Emphasized curve: quick to leave, slow to settle.
      const eased = 1 - Math.pow(1 - t, 3)
      const next = Math.round(begin + (target - begin) * eased)
      setValue(next)
      if (t < 1) frame = requestAnimationFrame(tick)
      else from.current = target
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, durationMs])
  return value
}

function CountedValue({ value }: { value: number }) {
  const shown = useCountUp(value)
  return <>{shown.toLocaleString('uz-UZ')}</>
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="card stat">
      <div className="label">{label}</div>
      <div className="value">{typeof value === 'number' ? <CountedValue value={value} /> : value}</div>
      {hint && <div className="hint">{hint}</div>}
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
        <div key={index} className="skeleton" style={{ height: 32 }} />
      ))}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}

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
  return (
    <div
      className="backdrop"
      onClick={onClose}
      onKeyDown={(event) => event.key === 'Escape' && onClose()}
      role="presentation"
    >
      <div
        className={`modal${wide ? ' wide' : ''}`}
        onClick={(event) => event.stopPropagation()}
        role="dialog"
        aria-label={title}
      >
        <h3>{title}</h3>
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
