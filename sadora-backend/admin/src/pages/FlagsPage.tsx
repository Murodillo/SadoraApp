import { useState } from 'react'
import { useAddFlagRule, useFlags, useRemoveFlagRule, useUpdateFlag } from '../api/hooks'
import type { AdminFlag } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ErrorNotice, Field, Loading, Modal, Switch } from '../components/ui'

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

  if (flags.isLoading) return <Loading rows={8} />
  if (flags.error) return <ErrorNotice error={flags.error} />

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="notice">
        <strong>Yoqilgan</strong> — kill switch: o'chirilsa barcha qoidalar chetlab o'tiladi va
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
                onChange={(enabled) =>
                  update.mutate(
                    { key: flag.key, enabled, defaultValue: flag.defaultValue },
                    { onSuccess: () => notify(`${flag.key}: ${enabled ? 'yoqildi' : 'o‘chirildi'}`, enabled ? 'ok' : 'info') },
                  )
                }
              />
              <Switch
                label="Standart"
                disabled={!editable || update.isPending}
                checked={flag.defaultValue}
                onChange={(defaultValue) =>
                  update.mutate(
                    { key: flag.key, enabled: flag.enabled, defaultValue },
                    { onSuccess: () => notify(`${flag.key}: standart qiymat ${defaultValue ? 'ha' : 'yo‘q'}`) },
                  )
                }
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
    </div>
  )
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
          <button
            className="btn small danger"
            disabled={remove.isPending}
            onClick={() => remove.mutate({ key: flagKey, ruleId: rule.id }, { onSuccess: () => notify('Qoida o‘chirildi', 'info') })}
          >
            O'chirish
          </button>
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
                  notify(`${flag.key}: qoida qo‘shildi (${rollout}%)`)
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
