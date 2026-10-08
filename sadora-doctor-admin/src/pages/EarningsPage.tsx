import { useState } from 'react'
import { uniqueRows, useDoctorEarnings, useMoreEarnings } from '../api/hooks'
import type { DoctorPayoutView, EarningLine } from '../api/types'
import { formatSom } from '../api/work'
import { paymentLabel } from '../components/labels'
import { Card, Empty, ErrorNotice, formatDate, formatDateTime, Loading, Spinner, Stat } from '../components/ui'

/**
 * One list of the page — consultations or payouts — as the first page the totals came
 * with, plus whatever pages she has asked for since, each row once.
 */
function useEarningsList<T extends EarningLine | DoctorPayoutView>(
  list: 'lines' | 'payouts',
  first: T[],
  total: number | undefined,
  keyOf: (item: T) => string,
) {
  const [asked, setAsked] = useState(false)
  const more = useMoreEarnings<T>(list, first.length, asked)
  const rows = uniqueRows([first, ...(more.data?.pages.map((page) => page.items) ?? [])], keyOf)
  // Before she asks, the totals' count says whether there is more; after, the last page does.
  const hasMore = asked ? Boolean(more.hasNextPage) || more.isPending : first.length < (total ?? 0)
  return {
    rows,
    hasMore,
    loading: asked && (more.isPending || more.isFetchingNextPage),
    error: more.error,
    loadMore: () => {
      if (!asked) setAsked(true)
      else if (more.data) void more.fetchNextPage()
      else void more.refetch()
    },
  }
}

function MoreButton({ loading, onClick }: { loading: boolean; onClick: () => void }) {
  return (
    <div className="row" style={{ justifyContent: 'center', marginTop: 8 }}>
      <button type="button" className="btn ghost small" disabled={loading} onClick={onClick}>
        {loading && <Spinner />}
        {loading ? 'Yuklanmoqda…' : "Ko'proq ko'rsatish"}
      </button>
    </div>
  )
}

/**
 * "Daromad": what her paid consultations brought, Sadora's share, what has been paid out
 * to her and what is still hers. Every sum arrives in tiyin and is shown in so'm. The
 * totals cover everything; the two lists come a page at a time, the latest first.
 */
export function EarningsPage() {
  const earnings = useDoctorEarnings()
  const lines = useEarningsList<EarningLine>('lines', earnings.data?.lines ?? [], earnings.data?.linesTotal, (line) => line.sessionId)
  const payouts = useEarningsList<DoctorPayoutView>(
    'payouts',
    earnings.data?.payouts ?? [],
    earnings.data?.payoutsTotal,
    (payout) => payout.id,
  )

  if (earnings.isPending) {
    return (
      <div className="grid" style={{ gap: 16 }}>
        <div className="grid stat-row">
          {Array.from({ length: 6 }, (_, index) => (
            <div key={index} className="card stat">
              <Loading rows={2} height={20} />
            </div>
          ))}
        </div>
        <Card>
          <Loading rows={5} height={28} />
        </Card>
      </div>
    )
  }
  if (!earnings.data) return <ErrorNotice error={earnings.error} onRetry={() => void earnings.refetch()} />

  const data = earnings.data
  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="grid stat-row">
        <Stat art="ic3d_gem" label="Jami tushum" value={formatSom(data.grossMinor)} hint="to'langan konsultatsiyalar" />
        <Stat art="ic3d_heart" label="Sadora ulushi" value={formatSom(data.commissionMinor)} hint="komissiya" />
        <Stat art="ic3d_trophy" label="Sizning daromadingiz" value={formatSom(data.netMinor)} hint="komissiyadan keyin" />
        <Stat
          label="To'lab berilgan"
          art="ic3d_calendar"
          value={formatSom(data.paidOutMinor)}
          hint={`${data.payoutsTotal ?? data.payouts.length} ta to'lov`}
        />
        <Stat art="ic3d_gem" label="Qoldiq" value={formatSom(data.balanceMinor)} hint="sizga to'lanadi" />
        <Stat art="ic3d_clock" label="Qaytariladi" value={formatSom(data.refundDueMinor)} hint="javobsiz qolganlar" />
      </div>

      <Card title="Konsultatsiyalar">
        {lines.rows.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Sana</th>
                  <th>Bemor</th>
                  <th className="num">Narx</th>
                  <th className="num">Sadora ulushi</th>
                  <th className="num">Sizga</th>
                  <th>Holat</th>
                </tr>
              </thead>
              <tbody>
                {lines.rows.map((line) => {
                  const payment = paymentLabel(line.payment)
                  return (
                    <tr key={line.sessionId}>
                      <td title={formatDateTime(line.openedAt)}>{formatDate(line.openedAt)}</td>
                      <td>{line.patientName}</td>
                      <td className="num">{formatSom(line.priceMinor)}</td>
                      <td className="num faint">{formatSom(line.commissionMinor)}</td>
                      <td className="num">
                        <b>{formatSom(line.netMinor)}</b>
                      </td>
                      <td>
                        <span className={`badge ${payment.tone}`}>{payment.text}</span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty art="ic3d_gem">
            Hali pullik konsultatsiya bo'lmagan.
            <div className="faint">Narxni "Ish vaqti va narx" sahifasida belgilaysiz.</div>
          </Empty>
        )}
        {lines.error && <ErrorNotice error={lines.error} onRetry={lines.loadMore} />}
        {lines.hasMore && <MoreButton loading={lines.loading} onClick={lines.loadMore} />}
      </Card>

      <Card title="To'lovlar">
        {payouts.rows.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Sana</th>
                  <th className="num">Summa</th>
                  <th>Izoh</th>
                </tr>
              </thead>
              <tbody>
                {payouts.rows.map((payout) => (
                  <tr key={payout.id}>
                    <td title={formatDateTime(payout.paidAt)}>{formatDate(payout.paidAt)}</td>
                    <td className="num">
                      <b>{formatSom(payout.amountMinor)}</b>
                    </td>
                    <td className="muted">{payout.note || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty art="ic3d_gem">Sadora hali sizga to'lov o'tkazmagan.</Empty>
        )}
        {payouts.error && <ErrorNotice error={payouts.error} onRetry={payouts.loadMore} />}
        {payouts.hasMore && <MoreButton loading={payouts.loading} onClick={payouts.loadMore} />}
        <p className="faint" style={{ marginBottom: 0 }}>
          Qoldiq Sadora tomonidan sizga o'tkaziladi. Savollar bo'lsa, qo'llab-quvvatlash xizmatiga yozing.
        </p>
      </Card>
    </div>
  )
}
