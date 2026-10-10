import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useBillingPlans,
  useBillingSummary,
  useFrameProducts,
  usePayments,
  usePetProducts,
  useRefundGift,
  useUpdateFrameProduct,
  useUpdatePetProduct,
} from '../api/hooks'
import type { AdminFrameProduct, AdminPayment, AdminPetProduct, PaymentProvider, PaymentState } from '../api/types'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, Empty, ErrorNotice, formatDateTime, Loading, Stat } from '../components/ui'

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

const PAGE_SIZE = 50

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
  const [offset, setOffset] = useState(0)
  const summary = useBillingSummary(days)
  const plans = useBillingPlans()
  const payments = usePayments(state, PAGE_SIZE, offset)
  const [refunding, setRefunding] = useState<AdminPayment | null>(null)
  const page = payments.data

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

      <PetProductsCard />

      <FrameProductsCard />

      <Card
        title="To'lovlar"
        action={
          <div className="segmented" role="group" aria-label="Holat">
            {([undefined, 'paid', 'pending', 'failed'] as const).map((option) => (
              <button
                key={option ?? 'all'}
                className={option === state ? 'active' : undefined}
                onClick={() => {
                  // A new filter is a new list; page 3 of "all" means nothing for "failed".
                  setState(option as PaymentState | undefined)
                  setOffset(0)
                }}
              >
                {option ? stateLabels[option] : 'Barchasi'}
              </button>
            ))}
          </div>
        }
      >
        <ErrorNotice error={payments.error} />
        {page && page.items.length === 0 && <Empty>To'lov topilmadi.</Empty>}
        {page && page.items.length > 0 && (
          <>
          <table>
            <thead>
              <tr>
                <th>Sana</th>
                <th>Provayder</th>
                <th>Tarif</th>
                <th>Summa</th>
                <th>Holat</th>
                <th>Provayder ID</th>
                <th>Kim → kimga</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {page.items.map((payment) => (
                <tr key={payment.id}>
                  <td className="faint">{formatDateTime(payment.paidAt ?? payment.createdAt)}</td>
                  <td>{providerLabels[payment.provider]}</td>
                  <td>
                    {payment.planId ??
                      (payment.pet ? `Hamroh · ${payment.pet}` : payment.frame ? `Ramka · ${frameNames[payment.frame] ?? payment.frame}` : 'Konsultatsiya')}
                    {payment.gift && <span className="badge" style={{ marginLeft: 6 }}>Sovg'a</span>}
                  </td>
                  <td>{sum(payment.amountMinor)}</td>
                  <td>
                    <span className={stateClass[payment.state]}>{stateLabels[payment.state]}</span>
                    {payment.refundedAt && <div className="faint">Qaytarilgan {formatDateTime(payment.refundedAt)}</div>}
                  </td>
                  <td className="mono faint">{payment.externalId ?? '—'}</td>
                  <td>
                    {payment.gift ? (
                      <>
                        {payment.payerId ? <Link to={`/users/${payment.payerId}`}>To'lovchi</Link> : <span className="faint">Brauzer</span>}
                        {' → '}
                        <Link to={`/users/${payment.userId}`}>Oluvchi</Link>
                      </>
                    ) : (
                      <Link to={`/users/${payment.userId}`}>Karta</Link>
                    )}
                  </td>
                  <td>
                    {payment.refundable && (
                      <button className="btn small" onClick={() => setRefunding(payment)}>
                        Qaytarildi deb belgilash
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

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
      {refunding && <RefundGiftDialog payment={refunding} onClose={() => setRefunding(null)} />}
    </div>
  )
}

/**
 * Marks a payment refunded after the money went back in the provider's cabinet. The panel
 * moves no money itself: it only takes back what the payment bought and records the refund.
 */
function RefundGiftDialog({ payment, onClose }: { payment: AdminPayment; onClose: () => void }) {
  const refund = useRefundGift()
  const { notify } = useToast()
  const [confirmed, setConfirmed] = useState(false)
  const provider = providerLabels[payment.provider] ?? payment.provider
  const what = payment.pet ? 'Hamroh' : payment.frame ? 'Ramka' : "Sovg'a qilingan Premium kunlari"

  return (
    <ConfirmDialog
      title="Qaytarildi deb belgilash"
      confirmLabel="Qaytarildi deb belgilash"
      pendingLabel="Belgilanmoqda…"
      cancelLabel="Bekor qilish"
      pending={refund.isPending}
      disabled={!confirmed}
      onClose={onClose}
      onConfirm={() =>
        refund.mutate(payment.id, {
          onSuccess: () => {
            notify("To'lov qaytarilgan deb belgilandi", 'ok')
            onClose()
          },
        })
      }
    >
      <div className="notice">
        Sadora pulni o'zi qaytarmaydi. Avval <strong>{provider}</strong> kabinetida{' '}
        <strong>{sum(payment.amountMinor)}</strong> ni qaytaring, keyin shu yerda belgilang. {what} foydalanuvchidan
        olib tashlanadi.
      </div>
      <label className="row" style={{ gap: 8 }}>
        <input type="checkbox" style={{ width: 'auto' }} checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
        <span>Pul {provider} kabinetida qaytarildi</span>
      </label>
      {refund.error && <ErrorNotice error={refund.error} />}
    </ConfirmDialog>
  )
}

/**
 * The legendary pet's price. One price everywhere: the app shows this one and Payme and
 * Click charge it; the stores charge what is set in their consoles, so a change here has
 * to be made there too. The sale itself is the `pet_humo_sale` flag.
 */
function PetProductsCard() {
  const products = usePetProducts()
  return (
    <Card title="Legendar hamroh">
      <ErrorNotice error={products.error} />
      {products.data?.map((product) => <PetProductRow key={product.pet} product={product} />)}
      <p className="faint">
        Sotuv <code>pet_humo_sale</code> flagi bilan yoqiladi. Play va App Store narxi ularning konsolida
        alohida o'zgartiriladi.
      </p>
    </Card>
  )
}

function PetProductRow({ product }: { product: AdminPetProduct }) {
  const update = useUpdatePetProduct()
  const [price, setPrice] = useState(String(Math.round(product.priceMinor / 100)))
  const whole = Number(price.replace(/\s/g, ''))
  const valid = Number.isFinite(whole) && whole >= 1000
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
      <b style={{ textTransform: 'capitalize' }}>{product.pet}</b>
      <span className="faint">{product.appStoreProductId ?? '—'} · {product.googlePlayProductId ?? '—'}</span>
      <input
        aria-label="Narx, so'm"
        inputMode="numeric"
        value={price}
        onChange={(event) => setPrice(event.target.value)}
        style={{ width: 140 }}
      />
      <span>so'm</span>
      <button
        className="btn small"
        disabled={!valid || update.isPending || whole * 100 === product.priceMinor}
        onClick={() => update.mutate({ pet: product.pet, priceMinor: whole * 100, active: product.active })}
      >
        Saqlash
      </button>
      <ErrorNotice error={update.error} />
    </div>
  )
}

/** What each frame is called in the panel; the keys are the contract's AvatarFrames. */
export const frameNames: Record<string, string> = {
  tulip: 'Lola',
  lavender: 'Lavanda',
  sakura: 'Sakura',
  moon: 'Oy va yulduzlar',
  rose: 'Atirgul toji',
  gold_flame: 'Oltin olov',
  gold_laurel: 'Oltin dafna',
  rainbow: 'Kamalak',
  humo_wing: 'Humo qanoti',
}

/**
 * Avatar frames sold for Gul or money. A badge's frame is not here: only the badge gives
 * it. Switching a frame off hides it from anyone who does not own it yet. The paid frames'
 * sale is the `frame_sale` flag.
 */
function FrameProductsCard() {
  const products = useFrameProducts()
  return (
    <Card title="Avatar ramkalari">
      <ErrorNotice error={products.error} />
      {products.data?.map((product) => <FrameProductRow key={product.key} product={product} />)}
      <p className="faint">
        Pullik ramkalar <code>frame_sale</code> flagi bilan sotuvga chiqadi. Play va App Store narxi ularning
        konsolida alohida o'zgartiriladi.
      </p>
    </Card>
  )
}

function FrameProductRow({ product }: { product: AdminFrameProduct }) {
  const update = useUpdateFrameProduct()
  const coins = product.unlock === 'coins'
  const current = coins ? (product.coinCost ?? 0) : Math.round((product.priceMinor ?? 0) / 100)
  const [price, setPrice] = useState(String(current))
  const whole = Number(price.replace(/\s/g, ''))
  const valid = Number.isFinite(whole) && (coins ? whole >= 1 : whole >= 1000)
  const save = (active: boolean) =>
    update.mutate(
      coins
        ? { key: product.key, coinCost: whole, active }
        : { key: product.key, priceMinor: whole * 100, active },
    )
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
      <b style={{ minWidth: 140 }}>{frameNames[product.key] ?? product.key}</b>
      {!coins && (
        <span className="faint">
          {product.appStoreProductId ?? '—'} · {product.googlePlayProductId ?? '—'}
        </span>
      )}
      <input
        aria-label={coins ? 'Narx, Gul' : "Narx, so'm"}
        inputMode="numeric"
        value={price}
        onChange={(event) => setPrice(event.target.value)}
        style={{ width: 120 }}
      />
      <span>{coins ? 'Gul' : "so'm"}</span>
      <button
        className="btn small"
        disabled={!valid || update.isPending || whole === current}
        onClick={() => save(product.active)}
      >
        Saqlash
      </button>
      <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <input
          type="checkbox"
          checked={product.active}
          disabled={update.isPending || !valid}
          onChange={(event) => save(event.target.checked)}
        />
        Sotuvda
      </label>
      <ErrorNotice error={update.error} />
    </div>
  )
}
