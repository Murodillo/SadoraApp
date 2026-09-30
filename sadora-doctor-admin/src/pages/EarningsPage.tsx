import { useDoctorEarnings } from '../api/hooks'
import { formatSom } from '../api/work'
import { paymentLabel } from '../components/labels'
import { Card, Empty, ErrorNotice, formatDate, formatDateTime, Loading, Stat } from '../components/ui'

/**
 * "Daromad": what her paid consultations brought, Sadora's share, what has been paid out
 * to her and what is still hers. Every sum arrives in tiyin and is shown in so'm.
 */
export function EarningsPage() {
  const earnings = useDoctorEarnings()

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
        <Stat label="Jami tushum" value={formatSom(data.grossMinor)} hint="to'langan konsultatsiyalar" />
        <Stat label="Sadora ulushi" value={formatSom(data.commissionMinor)} hint="komissiya" />
        <Stat label="Sizning daromadingiz" value={formatSom(data.netMinor)} hint="komissiyadan keyin" />
        <Stat label="To'lab berilgan" value={formatSom(data.paidOutMinor)} hint={`${data.payouts.length} ta to'lov`} />
        <Stat label="Qoldiq" value={formatSom(data.balanceMinor)} hint="sizga to'lanadi" />
        <Stat label="Qaytariladi" value={formatSom(data.refundDueMinor)} hint="javobsiz qolganlar" />
      </div>

      <Card title="Konsultatsiyalar">
        {data.lines.length ? (
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
                {data.lines.map((line) => {
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
          <Empty>
            Hali pullik konsultatsiya bo'lmagan.
            <div className="faint">Narxni "Ish vaqti va narx" sahifasida belgilaysiz.</div>
          </Empty>
        )}
      </Card>

      <Card title="To'lovlar">
        {data.payouts.length ? (
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
                {data.payouts.map((payout) => (
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
          <Empty>Sadora hali sizga to'lov o'tkazmagan.</Empty>
        )}
        <p className="faint" style={{ marginBottom: 0 }}>
          Qoldiq Sadora tomonidan sizga o'tkaziladi. Savollar bo'lsa, qo'llab-quvvatlash xizmatiga yozing.
        </p>
      </Card>
    </div>
  )
}
