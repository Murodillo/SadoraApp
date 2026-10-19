import { useState } from 'react'
import {
  useAdminMe,
  useChangeOperatorRole,
  useCreateOperator,
  useOperators,
  useResetOperatorPassword,
  useResetOperatorTotp,
  useSetOperatorActive,
} from '../api/hooks'
import type { AdminOperator, AdminRole, OperatorCredentials } from '../api/types'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, ErrorNotice, Field, formatDateTime, Loading, Modal, Spinner } from '../components/ui'
import { looksLikeEmail, operatorStatus, roleHints, roleLabels, roleOrder } from './operators'

/** A change waiting for the Owner's yes: every one of them lands on the operator's next request. */
type Pending =
  | { kind: 'role'; operator: AdminOperator; role: AdminRole }
  | { kind: 'active'; operator: AdminOperator; active: boolean }
  | { kind: 'password'; operator: AdminOperator }
  | { kind: 'totp'; operator: AdminOperator }

/**
 * The Owner's staff list: who can sign into the panel, as what, and whether they still can.
 *
 * A new operator and a reset both produce a temporary password shown here once; the Owner
 * passes it on herself, and the operator must replace it before anything else opens. Her
 * own row has no buttons — the server refuses them, and her password and 2FA have their own
 * page that asks for proof.
 */
export function OperatorsPage() {
  const operators = useOperators()
  const me = useAdminMe()
  const [creating, setCreating] = useState(false)
  const [pending, setPending] = useState<Pending | null>(null)
  const [credentials, setCredentials] = useState<{ title: string; value: OperatorCredentials } | null>(null)

  if (operators.isLoading) return <Loading rows={5} />
  if (operators.error) return <ErrorNotice error={operators.error} />
  const rows = operators.data ?? []

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Card
        title={`Operatorlar · ${rows.filter((row) => row.active).length} faol`}
        action={
          <button className="btn small primary" onClick={() => setCreating(true)}>
            Yangi operator
          </button>
        }
      >
        <div className="table-wrap operators-table">
          <table>
            <thead>
              <tr>
                <th>Operator</th>
                <th>Rol</th>
                <th>2FA</th>
                <th>Oxirgi kirish</th>
                <th>Holat</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((operator) => {
                const self = operator.id === me.data?.id
                const status = operatorStatus(operator)
                return (
                  <tr key={operator.id} style={{ opacity: operator.active ? 1 : 0.6 }}>
                    <td>
                      <div>
                        {operator.name}
                        {self && <span className="faint"> (siz)</span>}
                      </div>
                      <div className="mono faint">{operator.email}</div>
                    </td>
                    <td>
                      {self ? (
                        roleLabels[operator.role]
                      ) : (
                        <select
                          aria-label={`${operator.name}: rol`}
                          value={operator.role}
                          onChange={(event) =>
                            setPending({ kind: 'role', operator, role: event.target.value as AdminRole })
                          }
                        >
                          {roleOrder.map((role) => (
                            <option key={role} value={role}>
                              {roleLabels[role]}
                            </option>
                          ))}
                        </select>
                      )}
                    </td>
                    <td>
                      <span className={`badge ${operator.totpEnabled ? 'ok' : 'free'}`}>
                        {operator.totpEnabled ? 'Yoqilgan' : "Yo'q"}
                      </span>
                    </td>
                    <td className="faint">{operator.lastLoginAt ? formatDateTime(operator.lastLoginAt) : 'hali kirmagan'}</td>
                    <td>
                      <span className={`badge ${status.tone}`}>{status.text}</span>
                    </td>
                    <td style={{ whiteSpace: 'nowrap' }}>
                      {!self && (
                        <div className="row" style={{ gap: 6, justifyContent: 'flex-end' }}>
                          <button className="btn small ghost" onClick={() => setPending({ kind: 'password', operator })}>
                            Parolni tiklash
                          </button>
                          {operator.totpEnabled && (
                            <button className="btn small ghost" onClick={() => setPending({ kind: 'totp', operator })}>
                              2FA'ni tiklash
                            </button>
                          )}
                          <button
                            className={`btn small ${operator.active ? 'danger' : ''}`}
                            onClick={() => setPending({ kind: 'active', operator, active: !operator.active })}
                          >
                            {operator.active ? "O'chirish" : 'Yoqish'}
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </Card>

      <Card title="Rollar">
        <div className="grid" style={{ gap: 6 }}>
          {roleOrder.map((role) => (
            <div key={role} className="row" style={{ gap: 8 }}>
              <span className="badge free" style={{ minWidth: 72, textAlign: 'center' }}>
                {roleLabels[role]}
              </span>
              <span className="muted">{roleHints[role]}</span>
            </div>
          ))}
        </div>
      </Card>

      {creating && (
        <CreateDialog
          onClose={() => setCreating(false)}
          onCreated={(value) => {
            setCreating(false)
            setCredentials({ title: `${value.operator.name} qo'shildi`, value })
          }}
        />
      )}
      {pending && (
        <PendingDialog
          pending={pending}
          onClose={() => setPending(null)}
          onCredentials={(value) => setCredentials({ title: `${value.operator.name}: yangi parol`, value })}
        />
      )}
      {credentials && <CredentialsDialog {...credentials} onClose={() => setCredentials(null)} />}
    </div>
  )
}

function CreateDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (value: OperatorCredentials) => void }) {
  const create = useCreateOperator()
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [role, setRole] = useState<AdminRole>('SUPPORT')
  const ready = looksLikeEmail(email) && name.trim().length > 0

  return (
    <Modal title="Yangi operator" onClose={onClose}>
      <Field label="Email">
        <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="ism@sadora.uz" />
      </Field>
      <Field label="Ism">
        <input value={name} maxLength={80} onChange={(event) => setName(event.target.value)} />
      </Field>
      <Field label="Rol">
        <select value={role} onChange={(event) => setRole(event.target.value as AdminRole)}>
          {roleOrder.map((option) => (
            <option key={option} value={option}>
              {roleLabels[option]}
            </option>
          ))}
        </select>
      </Field>
      <div className="faint">{roleHints[role]}</div>
      <ErrorNotice error={create.error} />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button
          className="btn primary"
          disabled={!ready || create.isPending}
          onClick={() => create.mutate({ email: email.trim(), name: name.trim(), role }, { onSuccess: onCreated })}
        >
          {create.isPending && <Spinner />}
          Qo'shish
        </button>
      </div>
    </Modal>
  )
}

function PendingDialog({
  pending,
  onClose,
  onCredentials,
}: {
  pending: Pending
  onClose: () => void
  onCredentials: (value: OperatorCredentials) => void
}) {
  const changeRole = useChangeOperatorRole()
  const setActive = useSetOperatorActive()
  const resetPassword = useResetOperatorPassword()
  const resetTotp = useResetOperatorTotp()
  const { notify } = useToast()
  const name = pending.operator.name
  const busy = changeRole.isPending || setActive.isPending || resetPassword.isPending || resetTotp.isPending
  const error = changeRole.error ?? setActive.error ?? resetPassword.error ?? resetTotp.error

  const copy =
    pending.kind === 'role'
      ? {
          title: `${name} — ${roleLabels[pending.role]} bo'lsinmi?`,
          body: `${roleHints[pending.role]}. Keyingi so'rovidan kuchga kiradi.`,
          confirm: "Rolni o'zgartirish",
          danger: false,
        }
      : pending.kind === 'active'
        ? pending.active
          ? { title: `${name} yoqilsinmi?`, body: 'U yana panelga kira oladi.', confirm: 'Yoqish', danger: false }
          : {
              title: `${name} o'chirilsinmi?`,
              body: "Ochiq seansi darhol yopiladi va u kira olmaydi. Hisob va uning audit yozuvlari saqlanadi.",
              confirm: "O'chirish",
              danger: true,
            }
        : pending.kind === 'password'
          ? {
              title: `${name} uchun yangi parol berilsinmi?`,
              body: "Eski paroli ishlamay qoladi, bloklangan bo'lsa ochiladi. Yangi vaqtinchalik parolni u birinchi kirishda almashtiradi.",
              confirm: 'Yangi parol berish',
              danger: false,
            }
          : {
              title: `${name} uchun 2FA tiklansinmi?`,
              body: "Telefoni yo'qolgan bo'lsa shu. 2FA o'chadi va u «Hisobim» sahifasida qaytadan sozlaydi.",
              confirm: "2FA'ni tiklash",
              danger: true,
            }

  function confirm() {
    const done = (message: string) => {
      notify(message)
      onClose()
    }
    const id = pending.operator.id
    if (pending.kind === 'role') {
      changeRole.mutate({ id, role: pending.role }, { onSuccess: () => done(`${name}: ${roleLabels[pending.role]}`) })
    } else if (pending.kind === 'active') {
      setActive.mutate({ id, active: pending.active }, { onSuccess: () => done(`${name} ${pending.active ? 'yoqildi' : "o'chirildi"}`) })
    } else if (pending.kind === 'password') {
      resetPassword.mutate(id, {
        onSuccess: (value) => {
          onClose()
          onCredentials(value)
        },
      })
    } else {
      resetTotp.mutate(id, { onSuccess: () => done(`${name}: 2FA tiklandi`) })
    }
  }

  return (
    <ConfirmDialog
      title={copy.title}
      confirmLabel={copy.confirm}
      danger={copy.danger}
      pending={busy}
      onClose={onClose}
      onConfirm={confirm}
    >
      <p className="muted" style={{ margin: 0 }}>
        {copy.body}
      </p>
      <ErrorNotice error={error} />
    </ConfirmDialog>
  )
}

/** The temporary password, once. Copying it is one click; nothing keeps it after the dialog closes. */
function CredentialsDialog({ title, value, onClose }: { title: string; value: OperatorCredentials; onClose: () => void }) {
  const { notify } = useToast()
  return (
    <Modal title={title} onClose={onClose}>
      <div className="notice">
        Bu parol faqat hozir ko'rsatiladi. Uni {value.operator.name}ga o'zingiz, xavfsiz yo'l bilan bering —
        birinchi kirishda u o'z parolini qo'yadi.
      </div>
      <Field label={`Vaqtinchalik parol · ${value.operator.email}`}>
        <input className="mono" value={value.temporaryPassword} readOnly onFocus={(event) => event.target.select()} />
      </Field>
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button
          className="btn"
          onClick={() =>
            void navigator.clipboard
              ?.writeText(value.temporaryPassword)
              .then(() => notify('Parol nusxalandi'))
              .catch(() => notify("Nusxalab bo'lmadi — parolni belgilab oling", 'info'))
          }
        >
          Nusxa olish
        </button>
        <button className="btn primary" onClick={onClose}>
          Tayyor
        </button>
      </div>
    </Modal>
  )
}
