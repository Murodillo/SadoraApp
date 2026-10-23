import { useState } from 'react'
import { API_BASE } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, Empty, ErrorNotice, Field, formatDateTime, Loading, Modal } from '../components/ui'
import { downloadPmBackup, useCreatePmToken, usePmMeta, usePmTokens, useRevokePmToken } from './api'
import { personName } from './fields'
import type { PmToken } from './types'

/**
 * Tokens for tools/pm, the command line Claude sessions use to read and write tasks and
 * docs. A token is shown once and only its hash is kept; it opens this section and
 * nothing else, and it can take a task to review but never to done.
 */
export function TokensPage() {
  const tokens = usePmTokens()
  const meta = usePmMeta()
  const { can } = useAuth()
  const [creating, setCreating] = useState(false)
  const [revoking, setRevoking] = useState<PmToken | null>(null)
  const revoke = useRevokePmToken()
  const { notify } = useToast()
  const lead = can(['OWNER', 'ADMIN'])

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Card
        title="API tokenlar"
        action={
          <button className="btn primary small" onClick={() => setCreating(true)}>
            Yangi token
          </button>
        }
      >
        {tokens.isLoading ? (
          <Loading rows={3} />
        ) : tokens.error ? (
          <ErrorNotice error={tokens.error} />
        ) : !tokens.data?.length ? (
          <Empty art="ic3d_lock">Hali token yo'q. tools/pm va Claude sessiyalari uchun bittasini yarating.</Empty>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Nomi</th>
                  {lead && <th>Egasi</th>}
                  <th>Yaratilgan</th>
                  <th>Oxirgi ishlatilgan</th>
                  <th>Holat</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {tokens.data.map((token) => (
                  <tr key={token.id}>
                    <td>{token.name}</td>
                    {lead && <td>{personName(meta.data, token.adminId)}</td>}
                    <td className="faint">{formatDateTime(token.createdAt)}</td>
                    <td className="faint">{token.lastUsedAt ? formatDateTime(token.lastUsedAt) : 'hech qachon'}</td>
                    <td>{token.revoked ? <span className="badge free">Bekor qilingan</span> : <span className="badge ok">Faol</span>}</td>
                    <td>
                      {!token.revoked && (
                        <button className="btn ghost small" onClick={() => setRevoking(token)}>
                          Bekor qilish
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="tools/pm sozlash">
        <ol className="pm-steps">
          <li>Yuqorida token yarating va nusxalang.</li>
          <li>
            Mac'da saqlang: <code>tools/pm login</code> — manzil sifatida <code>{API_BASE || window.location.origin}</code>, keyin tokenni kiriting.
            U <code>~/.config/sadora/pm_token</code> fayliga yoziladi.
          </li>
          <li>
            Tekshiring: <code>tools/pm task list --mine</code>. Claude sessiyalari ham shu buyruqdan foydalanadi (qoidalar repo'dagi{' '}
            <code>CLAUDE.md</code>'da).
          </li>
        </ol>
      </Card>

      {can(['OWNER']) && <BackupCard />}

      {creating && <NewToken onClose={() => setCreating(false)} />}
      {revoking && (
        <ConfirmDialog
          title={`«${revoking.name}» bekor qilinsinmi?`}
          confirmLabel="Bekor qilish"
          cancelLabel="Qoldirish"
          danger
          pending={revoke.isPending}
          onClose={() => setRevoking(null)}
          onConfirm={() =>
            revoke.mutate(revoking.id, {
              onSuccess: () => {
                notify('Token bekor qilindi', 'info')
                setRevoking(null)
              },
            })
          }
        >
          <p>Bu tokenni ishlatayotgan har qanday skript darhol to'xtaydi.</p>
          <ErrorNotice error={revoke.error} />
        </ConfirmDialog>
      )}
    </div>
  )
}

function NewToken({ onClose }: { onClose: () => void }) {
  const create = useCreatePmToken()
  const { notify } = useToast()
  const [name, setName] = useState('')
  const token = create.data?.token
  return (
    <Modal title={token ? 'Token tayyor' : 'Yangi token'} onClose={onClose}>
      {token ? (
        <div className="grid" style={{ gap: 12 }}>
          <div className="notice">Token faqat hozir ko'rinadi. Nusxalab, xavfsiz joyga saqlang.</div>
          <code className="pm-token">{token}</code>
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <button
              className="btn primary"
              onClick={() => {
                void navigator.clipboard?.writeText(token).then(() => notify('Nusxalandi'))
              }}
            >
              Nusxalash
            </button>
            <button className="btn ghost" onClick={onClose}>
              Yopish
            </button>
          </div>
        </div>
      ) : (
        <form
          className="grid"
          style={{ gap: 12 }}
          onSubmit={(event) => {
            event.preventDefault()
            create.mutate(name.trim())
          }}
        >
          <Field label="Nomi (qayerda ishlatiladi)">
            <input value={name} maxLength={80} placeholder="MacBook — Claude" onChange={(event) => setName(event.target.value)} autoFocus required />
          </Field>
          <ErrorNotice error={create.error} />
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <button type="button" className="btn ghost" onClick={onClose}>
              Bekor qilish
            </button>
            <button type="submit" className="btn primary" disabled={!name.trim() || create.isPending}>
              Yaratish
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}

function BackupCard() {
  const { notify } = useToast()
  const [busy, setBusy] = useState(false)
  async function download() {
    setBusy(true)
    try {
      const blob = await downloadPmBackup()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `sadora-loyiha-${new Date().toISOString().slice(0, 10)}.json`
      link.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      notify(error instanceof Error ? error.message : 'Yuklab bo‘lmadi', 'error')
    } finally {
      setBusy(false)
    }
  }
  return (
    <Card title="Zaxira">
      <p className="faint" style={{ marginTop: 0 }}>
        Server har kuni Loyiha bo'limining to'liq nusxasini (tasklar, hujjatlar, versiyalar, rasmlar) media papkasiga yozadi va oxirgi 14
        tasini saqlaydi. Bu yerdan hozirgi holatni kompyuteringizga yuklab olishingiz mumkin; <code>tools/pm backup</code> ham shuni qiladi.
      </p>
      <button className="btn small" onClick={() => void download()} disabled={busy}>
        {busy ? 'Tayyorlanmoqda…' : 'Zaxirani yuklab olish'}
      </button>
    </Card>
  )
}
