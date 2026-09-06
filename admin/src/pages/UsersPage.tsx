import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useUsers } from '../api/hooks'
import type { UserFilters } from '../api/hooks'
import type { AdminUserSummary } from '../api/types'
import { Card, Empty, ErrorNotice, formatDateTime, Loading, StatusBadge, TierBadge } from '../components/ui'

const PAGE_SIZE = 25

export function UsersPage() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState<UserFilters>({ limit: PAGE_SIZE, offset: 0 })
  const [search, setSearch] = useState('')

  const users = useUsers(filters)

  function update(patch: Partial<UserFilters>) {
    setFilters((current) => ({ ...current, ...patch, offset: 0 }))
  }

  const page = users.data
  const offset = filters.offset ?? 0

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="notice privacy">
        Bu yerda sog'liq ma'lumotlari ko'rinmaydi — sikl, simptomlar, kayfiyat, dorilar va AI
        yozishmalari operatorga umuman uzatilmaydi. Faqat hisob va texnik metadata (TZ 17-bo'lim).
      </div>

      <Card>
        <form
          className="filters"
          onSubmit={(event) => {
            event.preventDefault()
            update({ q: search })
          }}
        >
          <div className="field">
            <label>Qidiruv</label>
            <input
              placeholder="email, telefon yoki ism"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
          <div className="field">
            <label>Holat</label>
            <select value={filters.status ?? ''} onChange={(event) => update({ status: event.target.value })}>
              <option value="">Barchasi</option>
              <option value="active">Faol</option>
              <option value="blocked">Bloklangan</option>
              <option value="deletion_pending">O'chirilmoqda</option>
            </select>
          </div>
          <div className="field">
            <label>Til</label>
            <select value={filters.language ?? ''} onChange={(event) => update({ language: event.target.value })}>
              <option value="">Barchasi</option>
              <option value="uz">O'zbekcha</option>
              <option value="ru">Ruscha</option>
              <option value="en">Inglizcha</option>
            </select>
          </div>
          <div className="field">
            <label>Hayot bosqichi</label>
            <select value={filters.lifeStage ?? ''} onChange={(event) => update({ lifeStage: event.target.value })}>
              <option value="">Barchasi</option>
              <option value="cycle">Sikl</option>
              <option value="trying_to_conceive">Rejalashtirish</option>
              <option value="pregnancy">Homiladorlik</option>
              <option value="postpartum">Tug'ruqdan keyin</option>
              <option value="perimenopause">Perimenopauza</option>
              <option value="menopause">Menopauza</option>
            </select>
          </div>
          <div className="row">
            <button className="btn primary" type="submit">
              Qidirish
            </button>
            <button
              className="btn"
              type="button"
              disabled={!page?.items.length}
              onClick={() => exportCsv(page?.items ?? [])}
            >
              CSV
            </button>
          </div>
        </form>
      </Card>

      <Card>
        {users.error && <ErrorNotice error={users.error} />}
        {users.isLoading && !page ? (
          <Loading rows={6} />
        ) : !page?.items.length ? (
          <Empty>Bu filtrlarga mos foydalanuvchi topilmadi.</Empty>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Ism</th>
                    <th>Telefon / email</th>
                    <th>Til</th>
                    <th>Bosqich</th>
                    <th>Tarif</th>
                    <th>Holat</th>
                    <th>Ro'yxatdan o'tgan</th>
                    <th>Oxirgi faollik</th>
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((user) => (
                    <tr key={user.id} className="clickable" onClick={() => navigate(`/users/${user.id}`)}>
                      <td style={{ fontWeight: 600 }}>{user.name || '—'}</td>
                      <td className="mono">{user.phone ?? user.email ?? '—'}</td>
                      <td>{user.language.toUpperCase()}</td>
                      <td className="muted">{user.lifeStage}</td>
                      <td>
                        <TierBadge tier={user.tier} />
                      </td>
                      <td>
                        <StatusBadge status={user.status} />
                      </td>
                      <td className="faint">{formatDateTime(user.registeredAt)}</td>
                      <td className="faint">{formatDateTime(user.lastActiveAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="row" style={{ justifyContent: 'space-between', marginTop: 12 }}>
              <span className="faint">
                {offset + 1}–{offset + page.items.length} / {page.total}
              </span>
              <div className="row">
                <button
                  className="btn small"
                  disabled={offset === 0}
                  onClick={() => setFilters((c) => ({ ...c, offset: Math.max(0, offset - PAGE_SIZE) }))}
                >
                  Oldingi
                </button>
                <button
                  className="btn small"
                  disabled={offset + page.items.length >= page.total}
                  onClick={() => setFilters((c) => ({ ...c, offset: offset + PAGE_SIZE }))}
                >
                  Keyingi
                </button>
              </div>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}

/**
 * Exports the rows currently on screen.
 *
 * Deliberately the visible page rather than the whole filtered set: a full export is a
 * bulk extraction of account data and belongs behind its own audited endpoint, not
 * behind a button that looks like a convenience.
 */
function exportCsv(rows: AdminUserSummary[]) {
  const header = ['id', 'name', 'phone', 'email', 'language', 'lifeStage', 'tier', 'status', 'registeredAt']
  const body = rows.map((row) =>
    [row.id, row.name, row.phone ?? '', row.email ?? '', row.language, row.lifeStage, row.tier, row.status, row.registeredAt]
      .map((cell) => `"${String(cell).replaceAll('"', '""')}"`)
      .join(','),
  )
  const blob = new Blob([[header.join(','), ...body].join('\n')], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `sadora-users-${new Date().toISOString().slice(0, 10)}.csv`
  link.click()
  URL.revokeObjectURL(url)
}
