import { useState } from 'react'
import { useAddFlagRule, useFlags, useRemoveFlagRule, useUpdateFlag } from '../api/hooks'
import type { AdminFlag } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, ErrorNotice, Field, Loading, Modal, Switch } from '../components/ui'

/**
 * Priority orders the rules, and the server refuses anything outside this. Clamping
 * here rather than letting the field hold -3 keeps the refusal from arriving after the
 * dialog has closed over the number that caused it.
 */
function clampPriority(raw: string): number {
  const value = Number(raw)
  if (!Number.isFinite(value)) return 0
  return Math.min(1000, Math.max(0, Math.round(value)))
}

export function FlagsPage() {
  const flags = useFlags()
  const update = useUpdateFlag()
  const { can } = useAuth()
  const { notify } = useToast()
  const editable = can(['OWNER', 'ADMIN'])
  const [ruleFor, setRuleFor] = useState<AdminFlag | null>(null)
  const [change, setChange] = useState<FlagChange | null>(null)

  if (flags.isLoading) return <Loading rows={8} />
  if (flags.error) return <ErrorNotice error={flags.error} />

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="notice">
        <strong>Yoqilgan</strong> — umumiy o'chirgich: o'chirilsa barcha qoidalar chetlab o'tiladi va
        bayroq hamma uchun yopiladi. <strong>Standart</strong> — hech bir qoida mos kelmaganda
        qaytariladigan qiymat. Foizli yoyish foydalanuvchi ID va bayroq kalitidan olingan barqaror
        hash bo'yicha bo'linadi, shuning uchun 5% dan 20% ga kengaytirish hech kimni chiqarib
        yubormaydi.
      </div>

      {update.error && <ErrorNotice error={update.error} />}

      {(flags.data ?? []).map((flag) => (
        <Card key={flag.key}>
          <div className="row" style={{ justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <div className="mono" style={{ fontWeight: 600 }}>
                {flag.key}
              </div>
              <div className="faint">{flag.description}</div>
            </div>
            <div className="row">
              <Switch
                label="Yoqilgan"
                disabled={!editable || update.isPending}
                checked={flag.enabled}
                onChange={(enabled) => setChange({ flag, field: 'enabled', value: enabled })}
              />
              <Switch
                label="Standart"
                disabled={!editable || update.isPending}
                checked={flag.defaultValue}
                onChange={(defaultValue) => setChange({ flag, field: 'defaultValue', value: defaultValue })}
              />
              {editable && (
                <button className="btn small" onClick={() => setRuleFor(flag)}>
                  Qoida qo'shish
                </button>
              )}
            </div>
          </div>

          {flag.rules.length > 0 && (
            <div className="table-wrap" style={{ marginTop: 12 }}>
              <table>
                <thead>
                  <tr>
                    <th>Muhit</th>
                    <th>Til</th>
                    <th>Platforma</th>
                    <th>Yoyish</th>
                    <th>Qiymat</th>
                    <th>Prioritet</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {flag.rules.map((rule) => (
                    <RuleRow key={rule.id} flagKey={flag.key} rule={rule} editable={editable} />
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      ))}

      {ruleFor && <AddRuleDialog flag={ruleFor} onClose={() => setRuleFor(null)} />}
      {change && (
        <ConfirmDialog
          title={flagChangeCopy(change).title}
          confirmLabel={flagChangeCopy(change).confirmLabel}
          pendingLabel={flagChangeCopy(change).pendingLabel}
          danger={change.field === 'enabled' && !change.value}
          pending={update.isPending}
          onClose={() => setChange(null)}
          onConfirm={() => {
            const { flag, field, value } = change
            const enabled = field === 'enabled' ? value : flag.enabled
            const defaultValue = field === 'defaultValue' ? value : flag.defaultValue
            update.mutate(
              { key: flag.key, enabled, defaultValue },
              {
                onSuccess: () => {
                  notify(
                    field === 'enabled'
                      ? `${flag.key}: ${value ? 'yoqildi' : "o'chirildi"}`
                      : `${flag.key}: standart qiymat ${value ? 'ha' : "yo'q"}`,
                    field === 'enabled' && !value ? 'info' : 'ok',
                  )
                  setChange(null)
                },
              },
            )
          }}
        >
          <p className="muted" style={{ margin: 0 }}>
            {flagChangeCopy(change).body}
          </p>
          {update.error && <ErrorNotice error={update.error} />}
        </ConfirmDialog>
      )}
    </div>
  )
}

/** A switch flipped on a flag, held until the operator confirms it. */
interface FlagChange {
  flag: AdminFlag
  field: 'enabled' | 'defaultValue'
  value: boolean
}

/** What the confirmation says: every flag change reaches every user at once. */
function flagChangeCopy({ flag, field, value }: FlagChange): {
  title: string
  body: string
  confirmLabel: string
  pendingLabel: string
} {
  if (field === 'enabled' && !value) {
    return {
      title: `«${flag.key}» hamma uchun o'chirilsinmi?`,
      body: "Barcha qoidalar chetlab o'tiladi va bu funksiya hamma foydalanuvchi uchun darhol yopiladi.",
      confirmLabel: "Hamma uchun o'chirish",
      pendingLabel: "O'chirilmoqda…",
    }
  }
  if (field === 'enabled') {
    return {
      title: `«${flag.key}» yoqilsinmi?`,
      body: 'Qoidalar va standart qiymat darhol yana ishlay boshlaydi.',
      confirmLabel: 'Yoqish',
      pendingLabel: 'Yoqilmoqda…',
    }
  }
  return {
    title: `«${flag.key}» standart qiymati «${value ? 'ha' : "yo'q"}» bo'lsinmi?`,
    body: "Hech bir qoida mos kelmagan barcha foydalanuvchilar uchun darhol o'zgaradi.",
    confirmLabel: "Standartni o'zgartirish",
    pendingLabel: 'Saqlanmoqda…',
  }
}

function RuleRow({
  flagKey,
  rule,
  editable,
}: {
  flagKey: string
  rule: AdminFlag['rules'][number]
  editable: boolean
}) {
  const remove = useRemoveFlagRule()
  const { notify } = useToast()
  const [confirming, setConfirming] = useState(false)
  return (
    <tr>
      <td>{rule.environment ?? 'har qanday'}</td>
      <td>{rule.language ?? 'har qanday'}</td>
      <td>{rule.platform ?? 'har qanday'}</td>
      <td>{rule.rolloutPercentage}%</td>
      <td>
        <span className={`badge ${rule.value ? 'ok' : 'free'}`}>{rule.value ? 'yoqadi' : "o'chiradi"}</span>
      </td>
      <td>{rule.priority}</td>
      <td>
        {editable && (
          <button className="btn small danger" onClick={() => setConfirming(true)}>
            O'chirish
          </button>
        )}
        {confirming && (
          <ConfirmDialog
            title="Qoida o'chirilsinmi?"
            confirmLabel="Qoidani o'chirish"
            pendingLabel="O'chirilmoqda…"
            danger
            pending={remove.isPending}
            onClose={() => setConfirming(false)}
            onConfirm={() =>
              remove.mutate(
                { key: flagKey, ruleId: rule.id },
                {
                  onSuccess: () => {
                    notify("Qoida o'chirildi", 'info')
                    setConfirming(false)
                  },
                },
              )
            }
          >
            <p className="muted" style={{ margin: 0 }}>
              Bu qoidaga tushgan foydalanuvchilar darhol keyingi qoida yoki standart qiymatga o'tadi.
            </p>
            {remove.error && <ErrorNotice error={remove.error} />}
          </ConfirmDialog>
        )}
      </td>
    </tr>
  )
}

function AddRuleDialog({ flag, onClose }: { flag: AdminFlag; onClose: () => void }) {
  const add = useAddFlagRule()
  const { notify } = useToast()
  const [environment, setEnvironment] = useState('')
  const [rollout, setRollout] = useState(100)
  const [value, setValue] = useState(true)
  const [priority, setPriority] = useState(100)

  return (
    <Modal title={`Qoida — ${flag.key}`} onClose={onClose}>
      <Field label="Muhit (bo'sh — har qanday)">
        <select value={environment} onChange={(event) => setEnvironment(event.target.value)}>
          <option value="">Har qanday</option>
          <option value="DEV">DEV</option>
          <option value="STAGE">STAGE</option>
          <option value="PROD">PROD</option>
        </select>
      </Field>
      <Field label={`Yoyish: ${rollout}%`}>
        <input
          type="range"
          min={0}
          max={100}
          value={rollout}
          onChange={(event) => setRollout(Number(event.target.value))}
        />
      </Field>
      <Field label="Qiymat">
        <select value={String(value)} onChange={(event) => setValue(event.target.value === 'true')}>
          <option value="true">Yoqadi</option>
          <option value="false">O'chiradi</option>
        </select>
      </Field>
      <Field label="Prioritet (kichik raqam avval tekshiriladi)">
        <input
          type="number"
          min={0}
          max={1000}
          value={priority}
          onChange={(event) => setPriority(clampPriority(event.target.value))}
        />
      </Field>
      {add.error && <ErrorNotice error={add.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button
          className="btn primary"
          disabled={add.isPending}
          onClick={() =>
            add.mutate(
              {
                key: flag.key,
                environment: environment || null,
                rolloutPercentage: rollout,
                value,
                priority,
              },
              {
                onSuccess: () => {
                  notify(`${flag.key}: qoida qo'shildi (${rollout}%)`)
                  onClose()
                },
              },
            )
          }
        >
          Qo'shish
        </button>
      </div>
    </Modal>
  )
}
