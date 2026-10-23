import { useState } from 'react'
import { componentLabel, priorityLabels, priorityOrder, statusLabels, statusOrder, typeLabels, typeOrder } from './board'
import type { PmMeta, PmPriority, PmStatus, PmTaskType } from './types'

/* The pickers a task form and the task page share. Each is a plain control; Field labels it. */

export function StatusSelect({ value, onChange, id }: { value: PmStatus; onChange: (next: PmStatus) => void; id?: string }) {
  return (
    <select id={id} value={value} onChange={(event) => onChange(event.target.value as PmStatus)}>
      {statusOrder.map((status) => (
        <option key={status} value={status}>
          {statusLabels[status]}
        </option>
      ))}
    </select>
  )
}

export function TypeSelect({ value, onChange, id }: { value: PmTaskType; onChange: (next: PmTaskType) => void; id?: string }) {
  return (
    <select id={id} value={value} onChange={(event) => onChange(event.target.value as PmTaskType)}>
      {typeOrder.map((type) => (
        <option key={type} value={type}>
          {typeLabels[type].text}
        </option>
      ))}
    </select>
  )
}

export function PrioritySelect({ value, onChange, id }: { value: PmPriority; onChange: (next: PmPriority) => void; id?: string }) {
  return (
    <select id={id} value={value} onChange={(event) => onChange(event.target.value as PmPriority)}>
      {priorityOrder.map((priority) => (
        <option key={priority} value={priority}>
          {priorityLabels[priority].text}
        </option>
      ))}
    </select>
  )
}

/** An optional choice from a list; the empty option reads [none]. */
export function OptionalSelect({
  value,
  onChange,
  options,
  none,
  id,
}: {
  value?: string
  onChange: (next: string | undefined) => void
  options: { id: string; label: string }[]
  none: string
  id?: string
}) {
  return (
    <select id={id} value={value ?? ''} onChange={(event) => onChange(event.target.value || undefined)}>
      <option value="">{none}</option>
      {options.map((option) => (
        <option key={option.id} value={option.id}>
          {option.label}
        </option>
      ))}
    </select>
  )
}

export const peopleOptions = (meta?: PmMeta) => (meta?.people ?? []).map((person) => ({ id: person.id, label: person.name }))
export const epicOptions = (meta?: PmMeta) => (meta?.epics ?? []).map((epic) => ({ id: epic.id, label: epic.title }))
export const releaseOptions = (meta?: PmMeta) => (meta?.releases ?? []).map((release) => ({ id: release.id, label: release.name }))

export function personName(meta: PmMeta | undefined, id: string | undefined): string {
  if (!id) return '—'
  return meta?.people.find((person) => person.id === id)?.name ?? "Noma'lum"
}

/** The fixed component list as toggles. */
export function ComponentChips({ value, onChange, components }: { value: string[]; onChange: (next: string[]) => void; components: string[] }) {
  return (
    <div className="chip-row" role="group" aria-label="Komponentlar">
      {components.map((key) => {
        const on = value.includes(key)
        return (
          <button
            key={key}
            type="button"
            className={`chip-toggle${on ? ' on' : ''}`}
            aria-pressed={on}
            onClick={() => onChange(on ? value.filter((item) => item !== key) : components.filter((item) => item === key || value.includes(item)))}
          >
            {componentLabel(key)}
          </button>
        )
      })}
    </div>
  )
}

/** Labels as chips with a box to add one; Enter or a comma adds, × removes. */
export function LabelInput({ value, onChange, id }: { value: string[]; onChange: (next: string[]) => void; id?: string }) {
  const [draft, setDraft] = useState('')
  function add() {
    const label = draft.trim().toLowerCase().replace(/\s+/g, '-')
    if (label && !value.includes(label)) onChange([...value, label])
    setDraft('')
  }
  return (
    <div className="chip-row">
      {value.map((label) => (
        <span key={label} className="badge free pm-label">
          {label}
          <button type="button" className="link-btn" aria-label={`${label} labelini olib tashlash`} onClick={() => onChange(value.filter((item) => item !== label))}>
            ×
          </button>
        </span>
      ))}
      <input
        id={id}
        value={draft}
        placeholder="label qo'shish"
        style={{ width: 140 }}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ',') {
            event.preventDefault()
            add()
          }
        }}
        onBlur={add}
      />
    </div>
  )
}
