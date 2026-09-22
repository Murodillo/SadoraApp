import { useEffect, useState } from 'react'
import {
  useAdjustCoins,
  useCoinRules,
  useRedemptions,
  useRewardsOverview,
  useSaveCoinRule,
  useUpdateRedemption,
} from '../api/hooks'
import type { CoinRule, RedemptionStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ErrorNotice, Loading, Stat, Switch, formatDateTime } from '../components/ui'

/**
 * The Gul economy, in one page.
 *
 * The header is the number that matters before any rate is touched: coins outstanding
 * is what the app currently owes. Raising a rate raises that liability, and an operator
 * should see it while making the change rather than a week later in the shop.
 *
 * Rates take effect on the next award — no app release — which is the point of them
 * living in a table. Existing ledger rows are never recalculated: what was paid was
 * paid, and rewriting history would make the wallet's own explanation wrong.
 */
export function RewardsPage() {
  const overview = useRewardsOverview()
  const rules = useCoinRules()
  const save = useSaveCoinRule()
  const redemptions = useRedemptions(50)
  const updateRedemption = useUpdateRedemption()
  const { can } = useAuth()
  const { notify } = useToast()
  const editable = can(['OWNER', 'ADMIN'])

  const [draft, setDraft] = useState<Record<string, CoinRule>>({})

  useEffect(() => {
    if (rules.data) setDraft(Object.fromEntries(rules.data.map((rule) => [rule.reason, rule])))
  }, [rules.data])

  if (rules.isLoading) return <Loading rows={8} />
  if (rules.error) return <ErrorNotice error={rules.error} />

  function edit(reason: string, patch: Partial<CoinRule>) {
    setDraft((current) => ({ ...current, [reason]: { ...current[reason]!, ...patch } }))
  }

  function isDirty(rule: CoinRule): boolean {
    const current = draft[rule.reason]
    return Boolean(current) && JSON.stringify(current) !== JSON.stringify(rule)
  }

  const totals = overview.data

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="notice">
        Bu yerdagi o‘zgarish ilovani yangilamasdan kuchga kiradi va faqat keyingi
        mukofotlarga ta’sir qiladi — allaqachon berilgan gul qayta hisoblanmaydi. Gul
        faqat <b>harakat</b> uchun beriladi: ilovani ochish, belgilash, o‘qish. Uyqu yoki
        boshqa salomatlik ko‘rsatkichi uchun gul berilmaydi.
      </div>

      {save.error && <ErrorNotice error={save.error} />}

      <div className="grid stat-row">
        <Stat
          label="Muomaladagi gul"
          value={(totals?.coinsOutstanding ?? 0).toLocaleString('ru-RU')}
          hint="Berilgan, lekin hali sarflanmagan"
        />
        <Stat label="Jami berilgan" value={(totals?.coinsEarnedTotal ?? 0).toLocaleString('ru-RU')} />
        <Stat label="Jami sarflangan" value={(totals?.coinsSpentTotal ?? 0).toLocaleString('ru-RU')} />
        <Stat label="Faol streaklar" value={totals?.activeStreaks ?? 0} hint={`Eng uzun — ${totals?.longestStreak ?? 0} kun`} />
        <Stat label="Taklif bo‘yicha kelganlar" value={totals?.referralsAccepted ?? 0} />
        <Stat label="Berilgan kodlar" value={totals?.redemptionsIssued ?? 0} />
      </div>

      <Card title="Mukofot qoidalari">
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Harakat</th>
                <th>Gul</th>
                <th>Kunlik limit</th>
                <th>Yoqilgan</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(rules.data ?? []).map((rule) => {
                const current = draft[rule.reason] ?? rule
                return (
                  <tr key={rule.reason}>
                    <td>
                      <div className="mono">{rule.reason}</div>
                      <div className="faint">{rule.description}</div>
                    </td>
                    <td>
                      <input
                        type="number"
                        min={0}
                        disabled={!editable}
                        value={current.amount}
                        onChange={(event) => edit(rule.reason, { amount: Number(event.target.value) })}
                      />
                    </td>
                    <td>
                      {/* Empty means once a day, which is what the ledger's unique index
                          enforces — so the placeholder says so rather than "unlimited". */}
                      <input
                        type="number"
                        min={1}
                        placeholder="kuniga 1 marta"
                        disabled={!editable}
                        value={current.dailyCap ?? ''}
                        onChange={(event) =>
                          edit(rule.reason, {
                            dailyCap: event.target.value === '' ? null : Number(event.target.value),
                          })
                        }
                      />
                    </td>
                    <td>
                      <Switch disabled={!editable} checked={current.enabled} onChange={(value) => edit(rule.reason, { enabled: value })} />
                    </td>
                    <td>
                      <button
                        className="btn small"
                        disabled={!editable || !isDirty(rule) || save.isPending}
                        onClick={() => save.mutate(current, { onSuccess: () => notify(`${rule.reason}: endi ${current.amount} gul`) })}
                      >
                        Saqlash
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Berilgan kodlar">
        {updateRedemption.error && <ErrorNotice error={updateRedemption.error} />}
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Sana</th>
                <th>Foydalanuvchi</th>
                <th>Mahsulot</th>
                <th>Kod</th>
                <th>Gul</th>
                <th>Holat</th>
              </tr>
            </thead>
            <tbody>
              {(redemptions.data ?? []).map((redemption) => (
                <tr key={redemption.id}>
                  <td className="faint">{formatDateTime(redemption.createdAt)}</td>
                  <td>{redemption.userName}</td>
                  <td>
                    {redemption.productTitle}
                    {redemption.discountPercent > 0 && (
                      <span className="faint"> · −{redemption.discountPercent}%</span>
                    )}
                  </td>
                  <td className="mono">{redemption.code}</td>
                  <td>{redemption.coinCost}</td>
                  <td>
                    {/* Support can mark a code used when a partner phones in — that is
                        the whole reason the status is editable from here. */}
                    <select
                      value={redemption.status}
                      disabled={!can(['OWNER', 'ADMIN', 'SUPPORT'])}
                      onChange={(event) =>
                        updateRedemption.mutate(
                          { id: redemption.id, status: event.target.value as RedemptionStatus },
                          { onSuccess: () => notify(`${redemption.code}: holat yangilandi`) },
                        )
                      }
                    >
                      <option value="issued">Faol</option>
                      <option value="used">Ishlatilgan</option>
                      <option value="expired">Muddati tugagan</option>
                      <option value="cancelled">Bekor qilingan</option>
                    </select>
                  </td>
                </tr>
              ))}
              {!redemptions.data?.length && (
                <tr>
                  <td colSpan={6} className="faint">
                    Hozircha kod berilmagan
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  )
}

/**
 * The coin controls on a user card — the balance, the streak, and a manual correction.
 *
 * The note is required by the server and lands on the audit row, because "who gave this
 * account 5000 gul and why" is exactly the question a scheme like this eventually
 * raises.
 */
export function UserRewardsCard({ userId, card }: { userId: string; card: AdminRewardsCardProps }) {
  const adjust = useAdjustCoins(userId)
  const { can } = useAuth()
  const { notify } = useToast()
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')

  if (!card) return null

  return (
    <Card title="Gul va streak">
      <div className="grid stat-row">
        <Stat label="Balans" value={card.coins.balance.toLocaleString('ru-RU')} />
        <Stat label="Streak" value={`${card.streak.current} kun`} hint={`Eng uzun — ${card.streak.longest}`} />
        {card.referral && <Stat label="Taklif qilganlari" value={card.referral.invited} />}
      </div>

      {can(['OWNER', 'ADMIN']) && (
        <div className="row" style={{ gap: 8, alignItems: 'flex-end' }}>
          <label style={{ flex: '0 0 120px' }}>
            <span className="faint">Miqdor (± gul)</span>
            <input
              type="number"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="+100"
            />
          </label>
          <label style={{ flex: 1 }}>
            <span className="faint">Sabab (audit logga tushadi)</span>
            <input value={note} onChange={(event) => setNote(event.target.value)} placeholder="Qo‘llab-quvvatlash so‘rovi" />
          </label>
          <button
            className="btn small"
            disabled={!amount || !note.trim() || adjust.isPending}
            onClick={() =>
              adjust.mutate(
                { amount: Number(amount), note: note.trim() },
                {
                  onSuccess: () => {
                    notify(`${Number(amount) >= 0 ? '+' : ''}${Number(amount)} gul qo‘llandi`)
                    setAmount('')
                    setNote('')
                  },
                },
              )
            }
          >
            Qo‘llash
          </button>
        </div>
      )}

      {adjust.error && <ErrorNotice error={adjust.error} />}

      <div className="table-wrap">
        <table>
          <tbody>
            {card.history.map((entry) => (
              <tr key={entry.id}>
                <td className="faint">{formatDateTime(entry.createdAt)}</td>
                <td>{entry.title}</td>
                <td style={{ textAlign: 'right' }}>
                  {entry.amount >= 0 ? `+${entry.amount}` : entry.amount}
                </td>
              </tr>
            ))}
            {!card.history.length && (
              <tr>
                <td className="faint">Harakat yo‘q</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Card>
  )
}

type AdminRewardsCardProps = import('../api/types').AdminRewardsCard | undefined
