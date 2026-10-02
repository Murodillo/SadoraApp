import { useMemo, useState } from 'react'
import { useAddDoctorPayout, useDoctorEarnings, useDoctorQuality, useMoreDoctorEarnings } from '../api/hooks'
import type { AdminDoctorQuality, DoctorPayoutView, EarningLine } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from '../components/Avatar'
import { useToast } from '../components/toast'
import { Card, Empty, ErrorNotice, Field, formatDate, formatDateTime, Loading, Spinner } from '../components/ui'
import {
  canManageMoney,
  formatMinutes,
  formatSom,
  nextSort,
  parseSomToMinor,
  paymentLabels,
  sortQuality,
} from './consultations'
import type { QualitySort, QualitySortKey } from './consultations'
import { doctorStatusLabels, specialtyLabels } from './doctorReview'

const NOTE_MAX = 500
const QUALITY_PAGE = 50

// ---------------------------------------------------------------- quality table

/**
 * The approved and suspended doctors side by side, a page at a time: how fast she
 * answers, how many she leaves unanswered, how she is rated, and where her money stands.
 * The server pages the busiest this month first; the column headers sort the page on
 * screen. A click opens her card on the Doctors page, where payouts are recorded.
 */
export function DoctorQualityTable({ onOpen }: { onOpen: (doctor: AdminDoctorQuality) => void }) {
  const [offset, setOffset] = useState(0)
  const quality = useDoctorQuality(QUALITY_PAGE, offset)
  const [sort, setSort] = useState<QualitySort>({ key: 'consultationsMonth', descending: true })
  const page = quality.data
  const rows = useMemo(() => (page ? sortQuality(page.items, sort) : []), [page, sort])

  const head = (key: QualitySortKey, label: string, right = false) => {
    const active = sort.key === key
    return (
      <th
        style={right ? { textAlign: 'right' } : undefined}
        aria-sort={active ? (sort.descending ? 'descending' : 'ascending') : 'none'}
      >
        <button type="button" className={`th-sort${active ? ' active' : ''}`} onClick={() => setSort(nextSort(sort, key))}>
          {label}
          <span className="arrow" aria-hidden="true">
            {active ? (sort.descending ? '↓' : '↑') : ''}
          </span>
        </button>
      </th>
    )
  }

  return (
    <Card>
      {quality.error && <ErrorNotice error={quality.error} />}
      {quality.isLoading ? (
        <Loading rows={6} />
      ) : !rows.length ? (
        <Empty>Tasdiqlangan shifokor hali yo'q.</Empty>
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  {head('fullName', 'Ism')}
                  <th>Holat</th>
                  {head('priceMinor', 'Narx', true)}
                  {head('consultationsMonth', 'Oy', true)}
                  {head('consultationsTotal', 'Jami', true)}
                  {head('openNow', 'Ochiq', true)}
                  {head('avgFirstReplyMinutes', 'Birinchi javob', true)}
                  {head('unansweredTotal', 'Javobsiz', true)}
                  {head('rating', 'Reyting', true)}
                  {head('grossMinor', 'Tushum', true)}
                  {head('netMinor', 'Sof', true)}
                  {head('paidOutMinor', "To'langan", true)}
                  {head('balanceMinor', 'Qoldiq', true)}
                  {head('refundDueMinor', 'Qaytarish', true)}
                </tr>
              </thead>
              <tbody>
                {rows.map((doctor) => {
                  const status =
                    doctor.status === 'none' ? null : doctorStatusLabels[doctor.status] ?? { text: doctor.status, tone: 'free' }
                  return (
                    <tr key={doctor.doctorId} className="clickable" onClick={() => onOpen(doctor)}>
                      <td>
                        <div className="person">
                          <Avatar name={doctor.fullName} photoUrl={doctor.photoUrl} size={32} />
                          <div style={{ minWidth: 0 }}>
                            <div style={{ fontWeight: 600 }}>{doctor.fullName}</div>
                            <div className="faint">{specialtyLabels[doctor.specialty] ?? doctor.specialty}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div className="row" style={{ gap: 4, flexWrap: 'nowrap' }}>
                          {status && <span className={`badge ${status.tone}`}>{status.text}</span>}
                          {doctor.busy ? (
                            <span className="badge warn">Band</span>
                          ) : doctor.onlineNow ? (
                            <span className="badge ok">Onlayn</span>
                          ) : (
                            <span className="badge free">Oflayn</span>
                          )}
                        </div>
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        {doctor.priceMinor > 0 ? formatSom(doctor.priceMinor) : <span className="faint">Bepul</span>}
                      </td>
                      <td style={{ textAlign: 'right' }}>{doctor.consultationsMonth}</td>
                      <td style={{ textAlign: 'right' }} className="muted">
                        {doctor.consultationsTotal}
                      </td>
                      <td style={{ textAlign: 'right' }}>{doctor.openNow}</td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>{formatMinutes(doctor.avgFirstReplyMinutes)}</td>
                      <td style={{ textAlign: 'right' }} className={doctor.unansweredTotal > 0 ? 'num-alert' : undefined}>
                        {doctor.unansweredTotal}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        {doctor.rating !== null && doctor.rating !== undefined ? (
                          <>
                            ★ {doctor.rating.toFixed(1)} <span className="faint">({doctor.ratingCount})</span>
                          </>
                        ) : (
                          <span className="faint">—</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>{formatSom(doctor.grossMinor)}</td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>{formatSom(doctor.netMinor)}</td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }} className="muted">
                        {formatSom(doctor.paidOutMinor)}
                      </td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 600 }}>{formatSom(doctor.balanceMinor)}</td>
                      <td
                        style={{ textAlign: 'right', whiteSpace: 'nowrap' }}
                        className={doctor.refundDueMinor > 0 ? 'num-alert' : 'faint'}
                      >
                        {formatSom(doctor.refundDueMinor)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          {page && page.total > page.items.length && (
            <div className="row" style={{ justifyContent: 'space-between', marginTop: 12 }}>
              <span className="faint">
                {offset + 1}–{offset + page.items.length} / {page.total}
              </span>
              <div className="row">
                <button
                  className="btn small"
                  disabled={offset === 0}
                  onClick={() => setOffset(Math.max(0, offset - QUALITY_PAGE))}
                >
                  Oldingi
                </button>
                <button
                  className="btn small"
                  disabled={offset + page.items.length >= page.total}
                  onClick={() => setOffset(offset + QUALITY_PAGE)}
                >
                  Keyingi
                </button>
              </div>
            </div>
          )}
          <p className="faint" style={{ marginBottom: 0 }}>
            Oy — shu oydagi konsultatsiyalar. Sof — Sadora ulushidan keyin shifokorga tegishli summa; qoldiq —
            undan hali to'lanmagani. Sahifalar shu oyda eng ko'p konsultatsiya qilganlardan boshlanadi; ustun
            sarlavhasi shu sahifani saralaydi. Shifokor kartasini ochish uchun qatorni bosing.
          </p>
        </>
      )}
    </Card>
  )
}

// ---------------------------------------------------------------- earnings on the doctor card

/**
 * Her money: what paid consultations earned her after Sadora's share, what has been paid
 * out, and what is still hers. Owner and Admin only — the server refuses Support, so the
 * card does not ask on her behalf.
 */
export function EarningsSection({ doctorId }: { doctorId: string }) {
  const { session } = useAuth()
  const allowed = canManageMoney(session?.role)
  const earnings = useDoctorEarnings(doctorId, allowed)
  const data = earnings.data
  const lines = useEarningsList<EarningLine>(doctorId, 'lines', data?.lines, data?.linesTotal, (line) => line.sessionId)
  const payouts = useEarningsList<DoctorPayoutView>(doctorId, 'payouts', data?.payouts, data?.payoutsTotal, (payout) => payout.id)

  if (!allowed) return null

  return (
    <>
      <h2 style={{ marginTop: 16 }}>Daromad va to'lovlar</h2>
      {earnings.error && <ErrorNotice error={earnings.error} />}
      {earnings.isLoading ? (
        <Loading rows={3} />
      ) : data ? (
        <>
          <table>
            <tbody>
              <MoneyRow label="Tushum" value={formatSom(data.grossMinor)} />
              <MoneyRow label="Sadora ulushi" value={formatSom(data.commissionMinor)} />
              <MoneyRow label="Sof daromad" value={formatSom(data.netMinor)} />
              <MoneyRow label="To'langan" value={formatSom(data.paidOutMinor)} />
              <MoneyRow label="Qoldiq" value={<strong>{formatSom(data.balanceMinor)}</strong>} />
              {data.refundDueMinor > 0 && (
                <MoneyRow label="Qaytarilishi kerak" value={<span className="num-alert">{formatSom(data.refundDueMinor)}</span>} />
              )}
            </tbody>
          </table>

          <PayoutForm doctorId={doctorId} balanceMinor={data.balanceMinor} />

          <h3 style={{ marginTop: 16, marginBottom: 6 }}>To'lovlar tarixi</h3>
          {payouts.shown.length === 0 ? (
            <p className="faint" style={{ margin: 0 }}>
              Hali to'lov qilinmagan.
            </p>
          ) : (
            <>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Sana</th>
                      <th style={{ textAlign: 'right' }}>Summa</th>
                      <th>Izoh</th>
                    </tr>
                  </thead>
                  <tbody>
                    {payouts.shown.map((payout) => (
                      <tr key={payout.id}>
                        <td className="faint" style={{ whiteSpace: 'nowrap' }}>
                          {formatDateTime(payout.paidAt)}
                        </td>
                        <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>{formatSom(payout.amountMinor)}</td>
                        <td className="muted">{payout.note || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <MoreRows list={payouts} />
            </>
          )}

          <h3 style={{ marginTop: 16, marginBottom: 6 }}>Pullik konsultatsiyalar</h3>
          {lines.shown.length === 0 ? (
            <p className="faint" style={{ margin: 0 }}>
              Pullik konsultatsiya bo'lmagan.
            </p>
          ) : (
            <>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Sana</th>
                      <th>Bemor</th>
                      <th style={{ textAlign: 'right' }}>Narx</th>
                      <th style={{ textAlign: 'right' }}>Sof</th>
                      <th>Holat</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lines.shown.map((line) => {
                      const payment = paymentLabels[line.payment] ?? { text: line.payment, tone: 'free' }
                      return (
                        <tr key={line.sessionId}>
                          <td className="faint" style={{ whiteSpace: 'nowrap' }}>
                            {formatDate(line.openedAt)}
                          </td>
                          <td>{line.patientName}</td>
                          <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>{formatSom(line.priceMinor)}</td>
                          <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>{formatSom(line.netMinor)}</td>
                          <td>
                            <span className={`badge ${payment.tone}`}>{payment.text}</span>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
              <MoreRows list={lines} />
            </>
          )}
        </>
      ) : null}
    </>
  )
}

/** The card shows this many consultations until "more" is asked for. */
const LINES_PREVIEW = 8

/**
 * One list of the card — consultations or payouts: the first page the totals came with,
 * then the pages asked for since, each row once (a payout recorded in between shifts the
 * offsets). [preview] keeps the card short until the first "more".
 */
function useEarningsList<T extends EarningLine | DoctorPayoutView>(
  doctorId: string,
  list: 'lines' | 'payouts',
  first: T[] | undefined,
  total: number | undefined,
  keyOf: (item: T) => string,
  preview = list === 'lines' ? LINES_PREVIEW : Infinity,
) {
  const [asked, setAsked] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const firstPage = first ?? []
  const more = useMoreDoctorEarnings<T>(doctorId, list, firstPage.length, asked)
  const rows: T[] = []
  const seen = new Set<string>()
  for (const item of [firstPage, ...(more.data?.pages.map((page) => page.items) ?? [])].flat()) {
    if (!seen.has(keyOf(item))) {
      seen.add(keyOf(item))
      rows.push(item)
    }
  }
  // Before the first fetch the totals' count says whether the server has more; after, the last page does.
  const serverHasMore = asked ? Boolean(more.hasNextPage) || more.isPending : firstPage.length < (total ?? 0)
  const shown = expanded ? rows : rows.slice(0, preview)
  return {
    shown,
    total: Math.max(total ?? 0, rows.length),
    hasMore: shown.length < rows.length || serverHasMore,
    loading: asked && (more.isPending || more.isFetchingNextPage),
    error: more.error,
    loadMore: () => {
      setExpanded(true)
      // What is already here is shown first; the server is asked only past it.
      if (shown.length < rows.length) return
      if (!asked) setAsked(true)
      else if (more.data) void more.fetchNextPage()
      else void more.refetch()
    },
  }
}

function MoreRows({ list }: { list: ReturnType<typeof useEarningsList> }) {
  return (
    <>
      {list.error && <ErrorNotice error={list.error} />}
      {list.hasMore && (
        <button className="btn ghost small" style={{ marginTop: 8 }} disabled={list.loading} onClick={list.loadMore}>
          {list.loading && <Spinner />}
          {list.loading ? 'Yuklanmoqda…' : `Ko'proq (${list.shown.length} / ${list.total})`}
        </button>
      )}
    </>
  )
}

function MoneyRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <tr>
      <td className="faint" style={{ width: 160 }}>
        {label}
      </td>
      <td>{value}</td>
    </tr>
  )
}

/**
 * Records money Sadora has already sent her — by bank or card, outside the panel. The
 * form writes the record only; the amount is typed in so'm and sent in tiyin.
 */
function PayoutForm({ doctorId, balanceMinor }: { doctorId: string; balanceMinor: number }) {
  const add = useAddDoctorPayout()
  const { notify } = useToast()
  const [open, setOpen] = useState(false)
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const amountMinor = parseSomToMinor(amount)
  const overBalance = amountMinor !== null && amountMinor > balanceMinor

  function reset() {
    setOpen(false)
    setAmount('')
    setNote('')
    add.reset()
  }

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (amountMinor === null) return
    add.mutate(
      { id: doctorId, amountMinor, note: note.trim() || undefined },
      {
        onSuccess: () => {
          notify(`To'lov qo'shildi: ${formatSom(amountMinor)}`, 'ok')
          reset()
        },
        onError: (error) => notify(error instanceof Error ? error.message : String(error), 'error'),
      },
    )
  }

  if (!open) {
    return (
      <div className="row" style={{ justifyContent: 'flex-end', marginTop: 8 }}>
        <button className="btn small primary" onClick={() => setOpen(true)}>
          To'lov qo'shish
        </button>
      </div>
    )
  }

  return (
    <form className="grid" style={{ gap: 10, marginTop: 12 }} onSubmit={submit}>
      <p className="faint" style={{ margin: 0 }}>
        Shifokorga pul bank yoki karta orqali alohida o'tkaziladi; bu yerda faqat to'lov yozib qo'yiladi va
        qoldiqdan ayriladi.
      </p>
      <Field label="Summa (so'm)">
        <input
          inputMode="decimal"
          placeholder={balanceMinor > 0 ? formatSom(balanceMinor).replace(" so'm", '') : '0'}
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
          autoFocus
        />
      </Field>
      {amount.trim() !== '' && amountMinor === null && (
        <div className="notice error">Musbat summa kiriting, masalan 150 000.</div>
      )}
      {overBalance && (
        <div className="notice">Summa qoldiqdan ({formatSom(balanceMinor)}) katta — shunday bo'lishi kerakmi?</div>
      )}
      <Field label={`Izoh (ixtiyoriy, ${note.trim().length}/${NOTE_MAX})`}>
        <input value={note} maxLength={NOTE_MAX} onChange={(event) => setNote(event.target.value)} placeholder="Masalan: sentyabr, karta orqali" />
      </Field>
      {add.error && <ErrorNotice error={add.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn ghost" onClick={reset}>
          Bekor qilish
        </button>
        <button type="submit" className="btn primary" disabled={amountMinor === null || add.isPending}>
          {add.isPending && <Spinner />}
          {add.isPending ? 'Saqlanmoqda…' : amountMinor !== null ? `${formatSom(amountMinor)} qo'shish` : "To'lov qo'shish"}
        </button>
      </div>
    </form>
  )
}
