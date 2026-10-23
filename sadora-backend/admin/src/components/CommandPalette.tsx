import { useEffect, useId, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAdminSearch } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import { groups, PM_ROLES } from '../layout/nav'
import { usePmSearch } from '../pm/api'
import { Art } from './art'
import { matchPages, moveIndex, pmResultItems, resultItems } from './palette'
import type { PaletteItem } from './palette'
import { ErrorNotice, Modal, Spinner } from './ui'

const DEBOUNCE_MS = 200

/**
 * Ctrl+K: one box for a page, an account, a doctor or a payment. Pages match as she types;
 * accounts, doctors and payments come from the server a moment after she stops, for the
 * roles that can open them. Arrow keys move, Enter opens, Escape closes.
 */
export function CommandPalette({ onClose }: { onClose: () => void }) {
  const { can } = useAuth()
  const navigate = useNavigate()
  const listId = useId()
  const input = useRef<HTMLInputElement>(null)
  const [text, setText] = useState('')
  const [settled, setSettled] = useState('')
  const [index, setIndex] = useState(0)
  // Analyst opens no card, so the server's finds would all be dead ends for her.
  const searchable = can(['OWNER', 'ADMIN', 'SUPPORT'])

  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(text), DEBOUNCE_MS)
    return () => window.clearTimeout(timer)
  }, [text])
  useEffect(() => setIndex(0), [text])
  // The dialog focuses its first control once it opens; the box takes it back a frame later.
  useEffect(() => {
    const frame = window.requestAnimationFrame(() => input.current?.focus())
    return () => window.cancelAnimationFrame(frame)
  }, [])

  // The team's tasks and docs, for the roles that reach the Loyiha section.
  const searchesPm = can(PM_ROLES)
  const search = useAdminSearch(settled, searchable)
  const pmSearch = usePmSearch(settled, searchesPm)
  const long = settled.trim().length >= 2
  const asked = searchable && long
  // Still typing, or the server has not answered: "nothing found" would be a guess.
  const waiting =
    (searchable || searchesPm) && text.trim().length >= 2 && (text !== settled || search.isFetching || pmSearch.isFetching)
  const items: PaletteItem[] = [
    ...matchPages(groups, text, can).slice(0, text.trim() ? 6 : 8),
    ...(searchesPm && long ? pmResultItems(pmSearch.data) : []),
    ...(asked ? resultItems(search.data) : []),
  ]
  const active = items[Math.min(index, Math.max(items.length - 1, 0))]

  function open(item: PaletteItem | undefined) {
    if (!item) return
    navigate(item.to)
    onClose()
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      setIndex((current) => moveIndex(current, event.key === 'ArrowDown' ? 1 : -1, items.length))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      open(active)
    }
  }

  return (
    <Modal title="Qidirish" onClose={onClose} wide>
      <div className="palette">
        <div className="palette-input">
          <input
            ref={input}
            type="search"
            value={text}
            onChange={(event) => setText(event.target.value)}
            onKeyDown={onKeyDown}
            placeholder={searchable ? "Sahifa, telefon, ism, shifokor, to'lov ID yoki SAD-12" : searchesPm ? 'Sahifa, task (SAD-12) yoki hujjat' : 'Sahifa nomi'}
            aria-label="Qidirish"
            role="combobox"
            aria-expanded={items.length > 0}
            aria-controls={listId}
            aria-activedescendant={active ? `${listId}-${active.key}` : undefined}
            autoComplete="off"
          />
          {waiting && <Spinner />}
        </div>

        <ErrorNotice error={asked ? search.error : searchesPm && long ? pmSearch.error : null} />

        <div className="palette-list" id={listId} role="listbox" aria-label="Natijalar">
          {items.map((item, position) => {
            const heading = position === 0 || items[position - 1]!.section !== item.section
            return (
              <div key={item.key}>
                {heading && <div className="palette-section">{item.section}</div>}
                <button
                  id={`${listId}-${item.key}`}
                  type="button"
                  role="option"
                  aria-selected={item === active}
                  className={`palette-item${item === active ? ' active' : ''}`}
                  onMouseEnter={() => setIndex(position)}
                  onClick={() => open(item)}
                >
                  {item.art && <Art name={item.art} size={22} />}
                  <span className="palette-text">
                    <span>{item.title}</span>
                    {item.subtitle && <span className="faint">{item.subtitle}</span>}
                  </span>
                </button>
              </div>
            )
          })}
          {!items.length && (
            <div className="faint palette-empty">
              {waiting ? 'Qidirilmoqda…' : 'Hech narsa topilmadi'}
            </div>
          )}
        </div>

        <div className="faint palette-hint">↑ ↓ tanlash · Enter ochish · Esc yopish</div>
      </div>
    </Modal>
  )
}
