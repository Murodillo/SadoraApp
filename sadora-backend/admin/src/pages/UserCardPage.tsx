import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  useAddNote,
  useDeleteNote,
  useFrameProducts,
  useGrantFrame,
  useGrantPremium,
  useSetBlocked,
  useUserCard,
  useUserFrames,
  useUserNotes,
  useUserRewards,
  useUserTimeline,
} from '../api/hooks'
import type { AdminConnections } from '../api/types'
import { actionLabel } from './auditLabels'
import {
  actorLabels,
  collapseTimeline,
  partnerStatusLabels,
  providerName,
  relationLabels,
  wearableStatusLabels,
} from './userCard'
import { frameNames } from './PaidProducts'
import { UserRewardsCard } from './RewardsPage'
import { limits } from '../api/limits'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import {
  Card,
  ConfirmDialog,
  ErrorNotice,
  Field,
  formatDateTime,
  formatExpiry,
  Loading,
  Modal,
  PrivacyNote,
  Spinner,
  StatusBadge,
  TabPanel,
  Tabs,
  TierBadge,
} from '../components/ui'
import { lifeStageLabels } from './DashboardPage'

type Tab = 'general' | 'history' | 'subscription' | 'rewards' | 'technical'

export function UserCardPage() {
  const { id } = useParams<{ id: string }>()
  const { can } = useAuth()
  const card = useUserCard(id)
  const [tab, setTab] = useState<Tab>('general')
  const [dialog, setDialog] = useState<'premium' | 'block' | null>(null)

  if (card.isLoading) return <Loading rows={6} />
  if (card.error) return <ErrorNotice error={card.error} />
  const data = card.data!
  const user = data.general

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <div>
          <Link to="/users" className="faint">
            ← Foydalanuvchilar
          </Link>
          <h2 style={{ margin: '6px 0 0', fontSize: 20 }}>{user.name || 'Ismsiz'}</h2>
          <div className="row" style={{ marginTop: 6 }}>
            <TierBadge tier={user.tier} />
            <StatusBadge status={user.status} />
            <span className="mono faint">{user.id}</span>
          </div>
        </div>
        {can(['OWNER', 'ADMIN', 'SUPPORT']) && (
          <div className="row">
            {can(['OWNER', 'ADMIN']) && (
              <button className="btn" onClick={() => setDialog('premium')}>
                Premium berish
              </button>
            )}
            <button
              className={`btn ${user.status === 'blocked' ? '' : 'danger'}`}
              onClick={() => setDialog('block')}
            >
              {user.status === 'blocked' ? 'Blokdan chiqarish' : 'Bloklash'}
            </button>
          </div>
        )}
      </div>

      <Tabs<Tab>
        value={tab}
        onChange={setTab}
        items={[
          { key: 'general', label: 'Umumiy' },
          { key: 'history', label: 'Tarix' },
          { key: 'subscription', label: 'Obuna' },
          { key: 'rewards', label: 'Gul' },
          { key: 'technical', label: 'Texnik' },
        ]}
      />

      <TabPanel id={tab}>
      {tab === 'general' && (
        <div className="grid" style={{ gap: 16 }}>
        <div className="two-col even">
        <Card>
          <table>
            <tbody>
              <Row label="Telefon" value={user.phone ?? '—'} mono />
              <Row label="Email" value={user.email ?? '—'} mono />
              <Row label="Til" value={user.language.toUpperCase()} />
              <Row label="Vaqt mintaqasi" value={data.technical.timezone} />
              <Row label="Hayot bosqichi" value={lifeStageLabels[user.lifeStage] ?? user.lifeStage} />
              <Row label="Ro'yxatdan o'tgan" value={formatDateTime(user.registeredAt)} />
              <Row label="Oxirgi faollik" value={formatDateTime(user.lastActiveAt)} />
            </tbody>
          </table>
          <div style={{ marginTop: 14 }}>
            <PrivacyNote detail="Sikl, simptomlar, kayfiyat, dorilar va AI yozishmalari bu yerda yo'q va bo'lmaydi: server ularni admin API'ga umuman uzatmaydi.">
              Sog'liq ma'lumotlari bu kartochkada yo'q va bo'lmaydi.
            </PrivacyNote>
          </div>
        </Card>
        <ConnectionsCard connections={data.connections} />
        </div>
        {id && <NotesCard userId={id} />}
        </div>
      )}

      {tab === 'history' && id && <TimelineTab userId={id} />}

      {tab === 'subscription' && (
        <Card>
          <table>
            <tbody>
              <Row label="Tarif" value={<TierBadge tier={data.subscription.tier} />} />
              <Row label="Manba" value={data.subscription.source ?? '—'} />
              <Row label="Tugash sanasi" value={formatExpiry(data.subscription.expiresAt)} />
              <Row label="Imtiyozli muddat" value={data.subscription.inGracePeriod ? 'Ha' : "Yo'q"} />
            </tbody>
          </table>

          <h2 style={{ marginTop: 20 }}>Tarix</h2>
          {data.subscription.history.length === 0 ? (
            <p className="faint">Obuna tarixi bo'sh.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Manba</th>
                  <th>Mahsulot</th>
                  <th>Boshlangan</th>
                  <th>Tugaydi</th>
                </tr>
              </thead>
              <tbody>
                {data.subscription.history.map((item, index) => (
                  <tr key={`${item.startedAt}-${index}`}>
                    <td>{item.source}</td>
                    <td className="mono">{item.productId ?? '—'}</td>
                    <td className="faint">{formatDateTime(item.startedAt)}</td>
                    <td className="faint">{formatExpiry(item.expiresAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Card>
      )}

      {tab === 'rewards' && id && <RewardsTab userId={id} />}

      {tab === 'technical' && (
        <div className="grid" style={{ gap: 16 }}>
          <Card title="Qurilmalar">
            {data.technical.devices.length === 0 ? (
              <p className="faint">Qurilma ro'yxatdan o'tmagan.</p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Qurilma ID</th>
                    <th>Platforma</th>
                    <th>Model</th>
                    <th>Ilova versiyasi</th>
                    <th>Oxirgi ko'rinish</th>
                  </tr>
                </thead>
                <tbody>
                  {data.technical.devices.map((device) => (
                    <tr key={device.deviceId}>
                      <td className="mono">{device.deviceId}</td>
                      <td>{device.platform}</td>
                      <td>{device.model ?? '—'}</td>
                      <td className="mono">{device.appVersion ?? '—'}</td>
                      <td className="faint">{formatDateTime(device.lastSeenAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>

          <Card title="Funksiya foydalanishi">
            {data.technical.featureUsage.length === 0 ? (
              <p className="faint">Hozircha limitli funksiyalardan foydalanilmagan.</p>
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Funksiya</th>
                    <th style={{ textAlign: 'right' }}>Bugun</th>
                    <th style={{ textAlign: 'right' }}>Shu oyda</th>
                  </tr>
                </thead>
                <tbody>
                  {data.technical.featureUsage.map((usage) => (
                    <tr key={usage.featureKey}>
                      <td className="mono">{usage.featureKey}</td>
                      <td style={{ textAlign: 'right' }}>{usage.usedToday}</td>
                      <td style={{ textAlign: 'right' }}>{usage.usedThisMonth}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Card>
        </div>
      )}

      </TabPanel>

      {dialog === 'premium' && id && <GrantPremiumDialog userId={id} onClose={() => setDialog(null)} />}
      {dialog === 'block' && id && (
        <BlockDialog userId={id} blocked={user.status === 'blocked'} onClose={() => setDialog(null)} />
      )}
    </div>
  )
}

function Row({ label, value, mono }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <tr>
      <td className="faint" style={{ width: 200 }}>
        {label}
      </td>
      <td className={mono ? 'mono' : undefined}>{value}</td>
    </tr>
  )
}

/**
 * The Gul half of a user's card.
 *
 * Loaded only when the tab is opened: most support conversations are not about coins,
 * and a card that fetched every domain up front would be four requests for one answer.
 */
function RewardsTab({ userId }: { userId: string }) {
  const rewards = useUserRewards(userId)
  if (rewards.isLoading) return <Loading rows={4} />
  if (rewards.error) return <ErrorNotice error={rewards.error} />
  return (
    <>
      <UserRewardsCard userId={userId} card={rewards.data} />
      <UserFramesCard userId={userId} />
    </>
  )
}

/** Her avatar frames, and a frame given by hand — a contest prize. A badge's frame only comes with the badge. */
function UserFramesCard({ userId }: { userId: string }) {
  const frames = useUserFrames(userId)
  const grant = useGrantFrame(userId)
  const toast = useToast()
  const [choice, setChoice] = useState('')
  const products = useFrameProducts()
  const owned = frames.data?.owned ?? []
  // The sold frames, as the server lists them: a badge's frame is never one of them.
  const giftable = (products.data ?? []).map((product) => product.key).filter((key) => !owned.includes(key))
  return (
    <Card title="Avatar ramkalari">
      <ErrorNotice error={frames.error ?? grant.error} />
      {frames.isLoading ? (
        <Loading rows={1} />
      ) : (
        <p>
          {owned.length === 0
            ? 'Ramkasi yo\'q.'
            : owned.map((key) => `${frameNames[key] ?? key}${frames.data?.worn === key ? ' (taqilgan)' : ''}`).join(', ')}
        </p>
      )}
      <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <select aria-label="Ramka" value={choice} onChange={(event) => setChoice(event.target.value)}>
          <option value="">Ramka tanlang</option>
          {giftable.map((key) => (
            <option key={key} value={key}>
              {frameNames[key] ?? key}
            </option>
          ))}
        </select>
        <button
          className="btn small"
          disabled={!choice || grant.isPending}
          onClick={() =>
            grant.mutate(choice, {
              onSuccess: () => {
                toast.notify('Ramka berildi')
                setChoice('')
              },
            })
          }
        >
          Sovg'a qilish
        </button>
      </div>
    </Card>
  )
}

function GrantPremiumDialog({ userId, onClose }: { userId: string; onClose: () => void }) {
  const grant = useGrantPremium(userId)
  const { notify } = useToast()
  const [reason, setReason] = useState('')
  const [expiresAt, setExpiresAt] = useState('')

  return (
    <Modal title="Qo'lda Premium berish" onClose={onClose}>
      <Field label="Sabab (majburiy)">
        <input
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={limits.reasonMax}
          autoFocus
        />
      </Field>
      <Field label="Tugash sanasi (bo'sh — muddatsiz)">
        <input type="date" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
      </Field>
      <p className="faint" style={{ margin: 0 }}>
        Sabab audit log'ga yoziladi va o'chirilmaydi — bu firibgarlikdan himoya.
      </p>
      {grant.error && <ErrorNotice error={grant.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button
          className="btn primary"
          disabled={!reason.trim() || grant.isPending}
          onClick={() =>
            grant.mutate(
              {
                reason: reason.trim(),
                expiresAt: expiresAt ? new Date(`${expiresAt}T00:00:00Z`).toISOString() : null,
              },
              {
                onSuccess: () => {
                  notify(expiresAt ? `Premium ${expiresAt} gacha berildi` : 'Muddatsiz Premium berildi')
                  onClose()
                },
              },
            )
          }
        >
          {grant.isPending && <Spinner />}
          {grant.isPending ? 'Berilmoqda…' : 'Berish'}
        </button>
      </div>
    </Modal>
  )
}

function BlockDialog({ userId, blocked, onClose }: { userId: string; blocked: boolean; onClose: () => void }) {
  const setBlocked = useSetBlocked(userId)
  const { notify } = useToast()
  const [reason, setReason] = useState('')

  return (
    <Modal title={blocked ? 'Blokdan chiqarish' : 'Hisobni bloklash'} onClose={onClose}>
      <Field label="Sabab (majburiy)">
        <input
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={limits.reasonMax}
          autoFocus
        />
      </Field>
      {!blocked && (
        <p className="faint" style={{ margin: 0 }}>
          Bloklash barcha qurilmalardagi seanslarni darhol bekor qiladi.
        </p>
      )}
      {setBlocked.error && <ErrorNotice error={setBlocked.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button
          className={`btn ${blocked ? 'primary' : 'danger'}`}
          disabled={!reason.trim() || setBlocked.isPending}
          onClick={() =>
            setBlocked.mutate(
              { blocked: !blocked, reason: reason.trim() },
              {
                onSuccess: () => {
                  notify(blocked ? 'Hisob blokdan chiqarildi' : 'Hisob bloklandi, seanslar bekor qilindi', blocked ? 'ok' : 'info')
                  onClose()
                },
              },
            )
          }
        >
          {setBlocked.isPending && <Spinner />}
          {setBlocked.isPending ? (blocked ? 'Blokdan chiqarilmoqda…' : 'Bloklanmoqda…') : blocked ? 'Blokdan chiqarish' : 'Bloklash'}
        </button>
      </div>
    </Modal>
  )
}

/**
 * Which links hang off the account, by their state only: support answers "my partner sees
 * nothing" or "my watch stopped" from this, and never learns who is on the other end.
 */
function ConnectionsCard({ connections }: { connections?: AdminConnections }) {
  const partner = connections?.partner
  const wearables = connections?.wearables ?? []
  return (
    <Card title="Ulanishlar">
      <table>
        <tbody>
          <Row
            label="Yaqinim"
            value={
              partner ? (
                <>
                  {partnerStatusLabels[partner.status] ?? partner.status} · {relationLabels[partner.relation] ?? partner.relation}
                  <span className="faint"> · {formatDateTime(partner.since)} dan</span>
                </>
              ) : (
                <span className="faint">ulanmagan</span>
              )
            }
          />
          <Row
            label="Yaqinim sifatida"
            value={connections?.partnerOf ? `${connections.partnerOf} ta hisobga ulangan` : <span className="faint">yo'q</span>}
          />
          <Row
            label="Telegram bot"
            value={
              connections?.telegramLinkedAt ? (
                <>ulangan<span className="faint"> · {formatDateTime(connections.telegramLinkedAt)}</span></>
              ) : (
                <span className="faint">ulanmagan</span>
              )
            }
          />
          <Row
            label="Soatlar"
            value={
              wearables.length ? (
                <div className="grid" style={{ gap: 4 }}>
                  {wearables.map((wearable) => {
                    const status = wearableStatusLabels[wearable.status] ?? { text: wearable.status, tone: 'free' }
                    return (
                      <div key={wearable.provider} title={wearable.lastError ?? undefined}>
                        {providerName(wearable.provider)} <span className={`badge ${status.tone}`}>{status.text}</span>
                        <span className="faint"> · sinxron {wearable.lastSyncAt ? formatDateTime(wearable.lastSyncAt) : "hali bo'lmagan"}</span>
                      </div>
                    )
                  })}
                </div>
              ) : (
                <span className="faint">ulanmagan</span>
              )
            }
          />
        </tbody>
      </table>
    </Card>
  )
}

/** The operators' notes to each other about this account; each may remove her own, the Owner any. */
function NotesCard({ userId }: { userId: string }) {
  const notes = useUserNotes(userId)
  const add = useAddNote(userId)
  const remove = useDeleteNote(userId)
  const { can } = useAuth()
  const { notify } = useToast()
  const [body, setBody] = useState('')
  const [removing, setRemoving] = useState<string | null>(null)
  const isOwner = can(['OWNER'])

  return (
    <Card title="Izohlar">
      <div className="grid" style={{ gap: 8 }}>
        <textarea
          aria-label="Yangi izoh"
          rows={2}
          maxLength={1000}
          value={body}
          placeholder="Masalan: pul qaytarishni so'radi, 3-kuni javob beramiz"
          onChange={(event) => setBody(event.target.value)}
        />
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <span className="faint">Faqat hisob ishlari — sog'liq haqida yozmang. Izohni boshqa operatorlar ko'radi.</span>
          <button
            className="btn small primary"
            disabled={!body.trim() || add.isPending}
            onClick={() =>
              add.mutate(body.trim(), {
                onSuccess: () => {
                  setBody('')
                  notify("Izoh qo'shildi")
                },
              })
            }
          >
            {add.isPending && <Spinner />}
            Qo'shish
          </button>
        </div>
        <ErrorNotice error={add.error} />

        {notes.isLoading ? (
          <Loading rows={2} />
        ) : notes.error ? (
          <ErrorNotice error={notes.error} />
        ) : !notes.data?.length ? (
          <p className="faint" style={{ margin: 0 }}>Hozircha izoh yo'q.</p>
        ) : (
          <div className="notes">
            {notes.data.map((note) => (
              <div key={note.id} className="note">
                <div className="note-body">{note.body}</div>
                <div className="row faint" style={{ justifyContent: 'space-between', gap: 8 }}>
                  <span>
                    {note.authorName ?? "O'chirilgan operator"} · {formatDateTime(note.createdAt)}
                  </span>
                  {(note.mine || isOwner) && (
                    <button className="link-btn" onClick={() => setRemoving(note.id)}>
                      O'chirish
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {removing && (
        <ConfirmDialog
          title="Izoh o'chirilsinmi?"
          confirmLabel="O'chirish"
          pendingLabel="O'chirilmoqda…"
          danger
          pending={remove.isPending}
          onClose={() => setRemoving(null)}
          onConfirm={() =>
            remove.mutate(removing, {
              onSuccess: () => {
                notify("Izoh o'chirildi", 'info')
                setRemoving(null)
              },
            })
          }
        >
          <ErrorNotice error={remove.error} />
        </ConfirmDialog>
      )}
    </Card>
  )
}

/**
 * What happened on the account, newest first, with a run of the same event folded into
 * one line. For anyone but the Owner the server leaves out who did it and from where.
 */
function TimelineTab({ userId }: { userId: string }) {
  const timeline = useUserTimeline(userId)
  const { can } = useAuth()
  if (timeline.isLoading) return <Loading rows={6} />
  if (timeline.error) return <ErrorNotice error={timeline.error} />
  const runs = collapseTimeline(timeline.data ?? [])

  return (
    <Card title="Hisob tarixi">
      {!runs.length ? (
        <p className="faint">Hodisa yo'q.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <tbody>
              {runs.map((run) => (
                <tr key={run.key}>
                  <td className="faint" style={{ whiteSpace: 'nowrap' }}>
                    {formatDateTime(run.latest)}
                    {run.count > 1 && <div>{formatDateTime(run.earliest)} dan</div>}
                  </td>
                  <td>
                    <span className={`badge ${run.actorType === 'admin' ? 'premium' : 'free'}`}>{actorLabels[run.actorType]}</span>
                  </td>
                  <td title={run.action}>
                    {actionLabel(run.action)}
                    {run.count > 1 && <span className="faint"> × {run.count}</span>}
                  </td>
                  <td className="muted">{run.count === 1 ? (run.entry.reason ?? '') : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {!can(['OWNER']) && (
        <p className="faint" style={{ marginBottom: 0 }}>
          Kim qilgani va qayerdan — faqat Owner'ga ko'rinadi.
        </p>
      )}
    </Card>
  )
}
