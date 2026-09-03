import { useState } from 'react'
import { useAudit } from '../api/hooks'
import { Card, Empty, ErrorNotice, formatDateTime, Loading } from '../components/ui'

const PAGE_SIZE = 40

export function AuditPage() {
  const [action, setAction] = useState('')
  const [offset, setOffset] = useState(0)
  const audit = useAudit({ action: action || undefined, limit: PAGE_SIZE, offset })

  const page = audit.data

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="notice">
        Bu sahifa faqat Owner rolida ko'rinadi. Yozuvlar o'chirilmaydi va tahrirlanmaydi — kim,
        qachon, nima qildi va qaysi IP'dan.
      </div>

      <Card>
        <div className="row">
          <input
            placeholder="Amal bo'yicha filtr, masalan subscription.granted"
            value={action}
            onChange={(event) => {
              setAction(event.target.value)
              setOffset(0)
            }}
            style={{ maxWidth: 380 }}
          />
        </div>
      </Card>

      <Card>
        {audit.error && <ErrorNotice error={audit.error} />}
        {audit.isLoading && !page ? (
          <Loading rows={8} />
        ) : !page?.items.length ? (
          <Empty>Bu filtrga mos yozuv yo'q.</Empty>
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Vaqt</th>
                    <th>Kim</th>
                    <th>Amal</th>
                    <th>Obyekt</th>
                    <th>Sabab</th>
                    <th>IP</th>
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((entry) => (
                    <tr key={entry.id}>
                      <td className="faint" style={{ whiteSpace: 'nowrap' }}>
                        {formatDateTime(entry.createdAt)}
                      </td>
                      <td>
                        <span className="badge free">{entry.actorType}</span>{' '}
                        <span className="faint">{entry.actorLabel ?? ''}</span>
                      </td>
                      <td className="mono">{entry.action}</td>
                      <td className="mono faint">
                        {entry.entityType ? `${entry.entityType}:${entry.entityId?.slice(0, 8) ?? ''}` : '—'}
                      </td>
                      <td className="muted">{entry.reason ?? '—'}</td>
                      <td className="mono faint">{entry.ip ?? '—'}</td>
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
                <button className="btn small" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}>
                  Oldingi
                </button>
                <button
                  className="btn small"
                  disabled={offset + page.items.length >= page.total}
                  onClick={() => setOffset(offset + PAGE_SIZE)}
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
