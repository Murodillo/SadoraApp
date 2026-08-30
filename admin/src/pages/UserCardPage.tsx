import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useGrantPremium, useSetBlocked, useUserCard } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import {
  Card,
  ErrorNotice,
  Field,
  formatDateTime,
  formatExpiry,
  Loading,
  Modal,
  StatusBadge,
  TierBadge,
} from '../components/ui'

type Tab = 'general' | 'subscription' | 'technical'

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

      <div className="tabs">
        {(
          [
            ['general', 'Umumiy'],
            ['subscription', 'Obuna'],
            ['technical', 'Texnik'],
          ] as [Tab, string][]
        ).map(([key, label]) => (
          <button key={key} className={`tab${tab === key ? ' active' : ''}`} onClick={() => setTab(key)}>
            {label}
          </button>
        ))}
      </div>

      {tab === 'general' && (
        <Card>
          <table>
            <tbody>
              <Row label="Telefon" value={user.phone ?? '—'} mono />
              <Row label="Email" value={user.email ?? '—'} mono />
              <Row label="Til" value={user.language.toUpperCase()} />
              <Row label="Vaqt mintaqasi" value={data.technical.timezone} />
              <Row label="Hayot bosqichi" value={user.lifeStage} />
              <Row label="Ro'yxatdan o'tgan" value={formatDateTime(user.registeredAt)} />
              <Row label="Oxirgi faollik" value={formatDateTime(user.lastActiveAt)} />
            </tbody>
          </table>
          <div className="notice privacy" style={{ marginTop: 14 }}>
            Sikl, simptomlar, kayfiyat, dorilar va AI yozishmalari bu yerda yo'q va bo'lmaydi.
            Backend ularni admin API'ga umuman uzatmaydi.
          </div>
        </Card>
      )}

      {tab === 'subscription' && (
        <Card>
          <table>
            <tbody>
              <Row label="Tarif" value={<TierBadge tier={data.subscription.tier} />} />
              <Row label="Manba" value={data.subscription.source ?? '—'} />
              <Row label="Tugash sanasi" value={formatExpiry(data.subscription.expiresAt)} />
              <Row label="Grace period" value={data.subscription.inGracePeriod ? 'Ha' : "Yo'q"} />
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

function GrantPremiumDialog({ userId, onClose }: { userId: string; onClose: () => void }) {
  const grant = useGrantPremium(userId)
  const [reason, setReason] = useState('')
  const [expiresAt, setExpiresAt] = useState('')

  return (
    <Modal title="Qo'lda Premium berish" onClose={onClose}>
      <Field label="Sabab (majburiy)">
        <input value={reason} onChange={(event) => setReason(event.target.value)} autoFocus />
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
              { onSuccess: onClose },
            )
          }
        >
          {grant.isPending ? 'Yuborilmoqda…' : 'Berish'}
        </button>
      </div>
    </Modal>
  )
}

function BlockDialog({ userId, blocked, onClose }: { userId: string; blocked: boolean; onClose: () => void }) {
  const setBlocked = useSetBlocked(userId)
  const [reason, setReason] = useState('')

  return (
    <Modal title={blocked ? 'Blokdan chiqarish' : 'Hisobni bloklash'} onClose={onClose}>
      <Field label="Sabab (majburiy)">
        <input value={reason} onChange={(event) => setReason(event.target.value)} autoFocus />
      </Field>
      {!blocked && (
        <p className="faint" style={{ margin: 0 }}>
          Bloklash barcha qurilmalardagi sessiyalarni darhol bekor qiladi.
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
            setBlocked.mutate({ blocked: !blocked, reason: reason.trim() }, { onSuccess: onClose })
          }
        >
          {setBlocked.isPending ? 'Yuborilmoqda…' : blocked ? 'Blokdan chiqarish' : 'Bloklash'}
        </button>
      </div>
    </Modal>
  )
}
