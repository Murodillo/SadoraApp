import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useBillingPlans, useBillingSummary, usePayments } from '../api/hooks'
import type { PaymentProvider, PaymentState } from '../api/types'
import { Card, Empty, ErrorNotice, formatDateTime, Loading, Stat } from '../components/ui'

const providerLabels: Record<PaymentProvider, string> = {
  payme: 'Payme',
  click: 'Click',
  app_store: 'App Store',
  google_play: 'Google Play',
}

const stateLabels: Record<PaymentState, string> = {
  pending: 'Kutilmoqda',
  paid: "To'langan",
  cancelled: 'Bekor qilingan',
  failed: 'Muvaffaqiyatsiz',
}

const stateClass: Record<PaymentState, string> = {
  pending: 'badge warn',
  paid: 'badge ok',
  cancelled: 'badge',
  failed: 'badge danger',
}

/** Tiyin to "299 000 so'm" — the panel never shows a minor unit to a person. */
function sum(minor: number): string {
  const whole = Math.round(minor / 100)
  return `${whole.toLocaleString('ru-RU').replace(/ /g, ' ')} so'm`
}

/**
 * Money in.
 *
 * Revenue is counted from paid transactions, never from active subscriptions: a comped
 * subscription is support, not income, and a page that mixes them is how a team convinces
 * itself it is selling more than it is.
 */
export function BillingPage() {
  const [days, setDays] = useState(30)
  const [state, setState] = useState<PaymentState | undefined>(undefined)
  const summary = useBillingSummary(days)
  const plans = useBillingPlans()
  const payments = usePayments(state)

  return (
    <div className="grid" style={{ gap: 16 }}>
      {summary.isLoading && <Loading />}
      <ErrorNotice error={summary.error} />

      {summary.data && (
        <div className="grid stat-row">
          <Stat label="Tushum" value={sum(summary.data.revenueMinor)} hint={`${days} kun`} />
          <Stat label="To'langan" value={summary.data.paidCount} />
          <Stat label="Kutilmoqda" value={summary.data.pendingCount} hint="hali tasdiqlanmagan" />
          <Stat label="Muvaffaqiyatsiz" value={summary.data.failedCount} />
          <Stat label="Aktiv obuna" value={summary.data.activeSubscriptions} hint="qo'lda berilganlar ham" />
        </div>
      )}

      <Card
        title="Provayderlar bo'yicha"
        action={
          <div className="segmented" role="group" aria-label="Davr">
            {[7, 30, 90].map((option) => (
              <button key={option} className={option === days ? 'active' : undefined} onClick={() => setDays(option)}>
                {option} kun
              </button>
            ))}
          </div>
        }
      >
        {summary.data && summary.data.byProvider.length === 0 ? (
          <Empty>Bu oraliqda to'lov yo'q.</Empty>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Provayder</th>
                <th>To'lovlar</th>
                <th>Tushum</th>
              </tr>
            </thead>
            <tbody>
              {summary.data?.byProvider.map((row) => (
                <tr key={row.provider}>
                  <td>{providerLabels[row.provider]}</td>
                  <td>{row.paidCount}</td>
                  <td>{sum(row.revenueMinor)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card title="Tariflar">
        <ErrorNotice error={plans.error} />
        {plans.data && (
          <table>
            <thead>
              <tr>
                <th>Tarif</th>
                <th>Davri</th>
                <th>Narxi</th>
                <th>Sinov</th>
                <th>Store mahsulotlari</th>
              </tr>
            </thead>
            <tbody>
              {plans.data.map((plan) => (
                <tr key={plan.id}>
                  <td>
                    <div>{plan.title}</div>
                    <div className="faint">{plan.id}</div>
                  </td>
                  <td>{plan.period === 'year' ? 'Yillik' : 'Oylik'}</td>
                  <td>
                    {sum(plan.priceMinor)}
                    {plan.monthlyEquivalentMinor ? (
                      <div className="faint">{sum(plan.monthlyEquivalentMinor)}/oy</div>
                    ) : null}
                  </td>
                  <td>{plan.trialDays > 0 ? `${plan.trialDays} kun` : '—'}</td>
                  <td className="faint">
                    {plan.appStoreProductId ?? '—'} · {plan.googlePlayProductId ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <p className="faint">
          Narx bazada saqlanadi va ilova uni serverdan o'qiydi — narxni o'zgartirish uchun
          yangi versiya chiqarish shart emas.
        </p>
      </Card>

      <Card
        title="To'lovlar"
        action={
          <div className="segmented" role="group" aria-label="Holat">
            {([undefined, 'paid', 'pending', 'failed'] as const).map((option) => (
              <button
                key={option ?? 'all'}
                className={option === state ? 'active' : undefined}
                onClick={() => setState(option as PaymentState | undefined)}
              >
                {option ? stateLabels[option] : 'Barchasi'}
              </button>
            ))}
          </div>
        }
      >
        <ErrorNotice error={payments.error} />
        {payments.data && payments.data.length === 0 && <Empty>To'lov topilmadi.</Empty>}
        {payments.data && payments.data.length > 0 && (
          <table>
            <thead>
              <tr>
                <th>Sana</th>
                <th>Provayder</th>
                <th>Tarif</th>
                <th>Summa</th>
                <th>Holat</th>
                <th>Provayder ID</th>
                <th>Foydalanuvchi</th>
              </tr>
            </thead>
            <tbody>
              {payments.data.map((payment) => (
                <tr key={payment.id}>
                  <td className="faint">{formatDateTime(payment.paidAt ?? payment.createdAt)}</td>
                  <td>{providerLabels[payment.provider]}</td>
                  <td>{payment.planId}</td>
                  <td>{sum(payment.amountMinor)}</td>
                  <td>
                    <span className={stateClass[payment.state]}>{stateLabels[payment.state]}</span>
                  </td>
                  <td className="mono faint">{payment.externalId ?? '—'}</td>
                  <td>
                    <Link to={`/users/${payment.userId}`}>Karta</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
