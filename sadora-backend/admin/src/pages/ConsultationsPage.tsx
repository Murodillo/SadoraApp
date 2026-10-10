import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAdminConsultations, useCommission, useMarkConsultationRefunded, useSetCommission } from '../api/hooks'
import type { AdminConsultationRow, ConsultationPayment } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import {
  Card,
  Empty,
  ErrorNotice,
  Field,
  formatDateTime,
  Loading,
  Modal,
  Spinner,
  Stat,
  TabPanel,
  Tabs,
} from '../components/ui'
import {
  canEditCommission,
  canMarkRefunded,
  closedReasonLabels,
  consultationTabs,
  firstReplyMinutes,
  formatMinutes,
  formatSom,
  parsePercent,
  paymentLabels,
  providerLabels,
} from './consultations'

const PAGE_SIZE = 25

/**
 * Paid consultations: the money a patient paid a doctor through Sadora.
 *
 * A consultation that closed without a word from the doctor is owed back. The server
 * cannot return money itself — Payme and Click refunds are made in their own cabinets —
 * so the operator returns it there first and then marks it here, which is all this page
 * changes. Support reads the list; Owner and Admin mark refunds; only the Owner sets
 * Sadora's share.
 */
export function ConsultationsPage() {
  const [payment, setPayment] = useState<ConsultationPayment>('refund_due')
  const [offset, setOffset] = useState(0)
  const consultations = useAdminConsultations(payment, PAGE_SIZE, offset)
  const data = consultations.data

  const counts: Partial<Record<ConsultationPayment, number>> = data
    ? { paid: data.paid, refund_due: data.refundDue, refunded: data.refunded }
    : {}

  function changePayment(next: ConsultationPayment) {
    setPayment(next)
    setOffset(0)
  }

  return (
    <div className="grid" style={{ gap: 16 }}>
      {data && (
        <div className="grid stat-row">
          <Stat
            label="Qaytarilishi kerak"
            value={data.refundDue}
            hint={data.refundDue > 0 ? 'javobsiz qolgan — pulni qaytaring' : "navbat bo'sh"}
          />
          <Stat label="To'langan" value={data.paid} />
          <Stat label="Qaytarilgan" value={data.refunded} />
        </div>
      )}

      <CommissionCard />

      <Tabs<ConsultationPayment>
        value={payment}
        onChange={changePayment}
        items={consultationTabs.map((key) => {
          const count = counts[key]
          return {
            key,
            label: (
              <>
                {paymentLabels[key].text}
                {count !== undefined && (
                  <span className={`badge ${key === 'refund_due' && count > 0 ? 'warn' : 'free'}`} style={{ marginLeft: 6 }}>
                    {count}
                  </span>
                )}
              </>
            ),
          }
        })}
      />

      <TabPanel id={payment}>
        <Card>
          {consultations.error && <ErrorNotice error={consultations.error} />}
          {consultations.isLoading && !data ? (
            <Loading rows={6} />
          ) : !data?.page.items.length ? (
            <Empty>
              {payment === 'refund_due'
                ? "Qaytarilishi kerak bo'lgan to'lov yo'q."
                : payment === 'paid'
                  ? "To'langan konsultatsiya hali yo'q."
                  : "Qaytarilgan to'lov yo'q."}
            </Empty>
          ) : (
            <ConsultationTable rows={data.page.items} total={data.page.total} offset={offset} onOffset={setOffset} />
          )}
        </Card>
      </TabPanel>
    </div>
  )
}

// ---------------------------------------------------------------- commission

function CommissionCard() {
  const commission = useCommission()
  const { session } = useAuth()
  const [editing, setEditing] = useState(false)
  const editable = canEditCommission(session?.role)

  return (
    <Card
      title="Sadora ulushi"
      action={
        editable ? (
          <button className="btn small" onClick={() => setEditing(true)} disabled={!commission.data}>
            O'zgartirish
          </button>
        ) : (
          <span className="faint">Faqat Owner o'zgartiradi</span>
        )
      }
    >
      {commission.error && <ErrorNotice error={commission.error} />}
      {commission.isLoading ? (
        <Loading rows={1} />
      ) : commission.data ? (
        <>
          <div style={{ fontSize: 22, fontWeight: 700 }}>Sadora ulushi: {commission.data.percent}%</div>
          <p className="faint" style={{ margin: '6px 0 0' }}>
            Har bir pullik konsultatsiyadan Sadora oladigan ulush; qolgani shifokorniki. O'zgarish faqat
            yangi konsultatsiyalarga tegadi — ochilgan va yopilganlari eski foizda qoladi.
          </p>
        </>
      ) : null}
      {editing && commission.data && (
        <CommissionDialog current={commission.data.percent} onClose={() => setEditing(false)} />
      )}
    </Card>
  )
}

function CommissionDialog({ current, onClose }: { current: number; onClose: () => void }) {
  const save = useSetCommission()
  const { notify } = useToast()
  const [text, setText] = useState(String(current))
  const percent = parsePercent(text)

  function submit() {
    if (percent === null) return
    save.mutate(percent, {
      onSuccess: (view) => {
        notify(`Sadora ulushi ${view.percent}% qilindi`, 'ok')
        onClose()
      },
      onError: (error) => notify(error instanceof Error ? error.message : String(error), 'error'),
    })
  }

  return (
    <Modal title="Sadora ulushini o'zgartirish" onClose={onClose}>
      <p className="muted" style={{ margin: 0 }}>
        Yangi foiz faqat bundan keyin ochiladigan konsultatsiyalarga qo'llanadi. Oldingilarning
        ulushi va shifokorlarning hisoblangan daromadi o'zgarmaydi.
      </p>
      <Field label="Ulush, % (0–100)">
        <input
          className="narrow"
          inputMode="numeric"
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && submit()}
          autoFocus
        />
      </Field>
      {text.trim() !== '' && percent === null && <div className="notice error">0 dan 100 gacha butun son kiriting.</div>}
      {save.error && <ErrorNotice error={save.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button className="btn primary" disabled={percent === null || percent === current || save.isPending} onClick={submit}>
          {save.isPending && <Spinner />}
          {save.isPending ? 'Saqlanmoqda…' : 'Saqlash'}
        </button>
      </div>
    </Modal>
  )
}

// ---------------------------------------------------------------- list

function ConsultationTable({
  rows,
  total,
  offset,
  onOffset,
}: {
  rows: AdminConsultationRow[]
  total: number
  offset: number
  onOffset: (offset: number) => void
}) {
  const { session } = useAuth()
  const [refunding, setRefunding] = useState<AdminConsultationRow | null>(null)
  const anyAction = rows.some((row) => canMarkRefunded(row.payment, session?.role))

  return (
    <>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Sana</th>
              <th>Shifokor</th>
              <th>Bemor</th>
              <th style={{ textAlign: 'right' }}>Narx</th>
              <th style={{ textAlign: 'right' }}>Ulush</th>
              <th>Provayder · tranzaksiya</th>
              <th>Yopilish</th>
              <th>Birinchi javob</th>
              <th>Baho</th>
              <th>Qaytarilgan</th>
              {anyAction && <th />}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td className="faint" style={{ whiteSpace: 'nowrap' }}>
                  {formatDateTime(row.openedAt ?? row.createdAt)}
                </td>
                <td style={{ fontWeight: 600 }}>{row.doctorName || '—'}</td>
                <td className="mono" style={{ whiteSpace: 'nowrap' }}>
                  <Link to={`/users/${row.patientId}`}>{row.patientPhone || 'Karta'}</Link>
                </td>
                <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>{formatSom(row.priceMinor)}</td>
                <td className="muted" style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                  {formatSom(row.commissionMinor)}
                </td>
                <td>
                  <div>{row.provider ? providerLabels[row.provider] ?? row.provider : '—'}</div>
                  {row.providerTransactionId && <CopyId value={row.providerTransactionId} />}
                </td>
                <td className="muted">
                  {row.closedReason ? closedReasonLabels[row.closedReason] ?? row.closedReason : row.closedAt ? '—' : 'Ochiq'}
                </td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  {row.firstReplyAt ? (
                    formatMinutes(firstReplyMinutes(row.openedAt, row.firstReplyAt))
                  ) : (
                    <span className="faint">javob yo'q</span>
                  )}
                </td>
                <td style={{ whiteSpace: 'nowrap' }}>{row.rating ? `★ ${row.rating}` : '—'}</td>
                <td className="faint" style={{ whiteSpace: 'nowrap' }}>
                  {formatDateTime(row.refundedAt)}
                </td>
                {anyAction && (
                  <td style={{ textAlign: 'right' }}>
                    {canMarkRefunded(row.payment, session?.role) && (
                      <button
                        className={`btn small${row.payment === 'refund_due' ? ' primary' : ''}`}
                        style={{ whiteSpace: 'nowrap' }}
                        onClick={() => setRefunding(row)}
                      >
                        Qaytarildi deb belgilash
                      </button>
                    )}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="row" style={{ justifyContent: 'space-between', marginTop: 12 }}>
        <span className="faint">
          {offset + 1}–{offset + rows.length} / {total}
        </span>
        <div className="row">
          <button className="btn small" disabled={offset === 0} onClick={() => onOffset(Math.max(0, offset - PAGE_SIZE))}>
            Oldingi
          </button>
          <button className="btn small" disabled={offset + rows.length >= total} onClick={() => onOffset(offset + PAGE_SIZE)}>
            Keyingi
          </button>
        </div>
      </div>
      {refunding && <RefundDialog row={refunding} onClose={() => setRefunding(null)} />}
    </>
  )
}

/** The provider's transaction id, which is what its cabinet searches by, one click to copy. */
function CopyId({ value }: { value: string }) {
  const { notify } = useToast()

  async function copy() {
    try {
      await navigator.clipboard.writeText(value)
      notify('Tranzaksiya ID nusxalandi', 'info')
    } catch {
      notify("Nusxalab bo'lmadi — ID ni qo'lda belgilang", 'error')
    }
  }

  return (
    <button
      type="button"
      className="btn ghost small mono"
      style={{ padding: '2px 6px', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}
      onClick={copy}
      title={`${value} — nusxalash`}
      aria-label={`Tranzaksiya ID ${value} — nusxalash`}
    >
      {value} ⧉
    </button>
  )
}

function RefundDialog({ row, onClose }: { row: AdminConsultationRow; onClose: () => void }) {
  const mark = useMarkConsultationRefunded()
  const { notify } = useToast()
  const [confirmed, setConfirmed] = useState(false)
  const provider = row.provider ? providerLabels[row.provider] ?? row.provider : 'provayder'

  function submit() {
    mark.mutate(row.id, {
      onSuccess: () => {
        notify("Konsultatsiya qaytarilgan deb belgilandi", 'ok')
        onClose()
      },
      onError: (error) => notify(error instanceof Error ? error.message : String(error), 'error'),
    })
  }

  return (
    <Modal title="Qaytarildi deb belgilash" onClose={onClose}>
      <div className="notice">
        Sadora pulni o'zi qaytarmaydi. Avval <strong>{provider}</strong> kabinetida shu tranzaksiya bo'yicha{' '}
        <strong>{formatSom(row.priceMinor)}</strong> ni bemorga qaytaring, keyin shu yerda belgilang — bu faqat
        Sadora'dagi yozuvni o'zgartiradi.
      </div>
      <table>
        <tbody>
          <tr>
            <td className="faint" style={{ width: 160 }}>
              Shifokor
            </td>
            <td>{row.doctorName || '—'}</td>
          </tr>
          <tr>
            <td className="faint">Bemor</td>
            <td className="mono">{row.patientPhone || '—'}</td>
          </tr>
          <tr>
            <td className="faint">Summa</td>
            <td>{formatSom(row.priceMinor)}</td>
          </tr>
          <tr>
            <td className="faint">{provider} ID</td>
            <td className="mono">{row.providerTransactionId || '—'}</td>
          </tr>
          <tr>
            <td className="faint">Sadora ID</td>
            <td className="mono">{row.transactionId || '—'}</td>
          </tr>
        </tbody>
      </table>
      {row.payment === 'paid' && (
        <p className="muted" style={{ margin: 0 }}>
          Bu konsultatsiya javobsiz qolmagan — shifokor javob bergan yoki hali ochiq. Belgilansa, bu summa
          shifokor daromadidan chiqariladi.
        </p>
      )}
      <label className="row" style={{ gap: 8 }}>
        <input type="checkbox" style={{ width: 'auto' }} checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
        <span>Pul {provider} kabinetida bemorga qaytarildi</span>
      </label>
      {mark.error && <ErrorNotice error={mark.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button className="btn primary" disabled={!confirmed || mark.isPending} onClick={submit}>
          {mark.isPending && <Spinner />}
          {mark.isPending ? 'Belgilanmoqda…' : 'Qaytarildi deb belgilash'}
        </button>
      </div>
    </Modal>
  )
}
