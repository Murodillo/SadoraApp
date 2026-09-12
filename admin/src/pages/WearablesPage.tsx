import { useState } from 'react'
import { useMappings, useProviders, useSaveMapping } from '../api/hooks'
import type { HealthMetric, HealthProvider, MetricMapping } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, Empty, ErrorNotice, Field, formatDateTime, Loading, Modal } from '../components/ui'

const providers: HealthProvider[] = [
  'apple_health',
  'health_connect',
  'oura',
  'garmin',
  'whoop',
  'fitbit',
  'samsung_health',
  'manual',
]

const metrics: HealthMetric[] = [
  'steps',
  'active_energy',
  'distance',
  'heart_rate',
  'resting_heart_rate',
  'hrv',
  'respiratory_rate',
  'body_temperature',
  'sleep_duration',
  'sleep_deep',
  'sleep_rem',
  'sleep_light',
  'sleep_awake',
  'sleep_performance',
  'sleep_efficiency',
  'weight',
  'recovery',
  'strain',
  'spo2',
  'skin_temperature',
]

/**
 * Pages 11 and 12: which providers are sending, and how their fields map onto SADORA's
 * metrics. Adding a provider, or following one that renamed a field, is a row here.
 */
export function WearablesPage() {
  const { can } = useAuth()
  const editable = can(['OWNER', 'ADMIN'])
  const health = useProviders()
  const mappings = useMappings()
  const save = useSaveMapping()
  const [editing, setEditing] = useState<MetricMapping | 'new' | null>(null)

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Card title="Provayderlar">
        {health.isLoading ? (
          <Loading rows={3} />
        ) : health.error ? (
          <ErrorNotice error={health.error} />
        ) : !health.data?.length ? (
          <Empty>Hali hech bir provayderdan namuna kelmagan.</Empty>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Provayder</th>
                <th style={{ textAlign: 'right' }}>Namunalar</th>
                <th>Oxirgi namuna</th>
              </tr>
            </thead>
            <tbody>
              {health.data.map((row) => (
                <tr key={row.provider}>
                  <td className="mono">{row.provider}</td>
                  <td style={{ textAlign: 'right', fontWeight: 600 }}>{row.sampleCount}</td>
                  <td className="faint">{formatDateTime(row.lastSampleAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card
        title="Ma'lumot moslashtirish"
        action={
          editable && (
            <button className="btn small primary" onClick={() => setEditing('new')}>
              Qator qo'shish
            </button>
          )
        }
      >
        <div className="notice" style={{ marginBottom: 12 }}>
          Provayder metrikasi → SADORA metrikasi. <span className="mono">scale</span> — kanonik birlikka
          ko'paytiruvchi (kJ → kkal uchun 0.239). Nofaol qator kelgan namunani rad etadi.
        </div>
        {save.error && <ErrorNotice error={save.error} />}
        {mappings.isLoading ? (
          <Loading rows={6} />
        ) : mappings.error ? (
          <ErrorNotice error={mappings.error} />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Provayder</th>
                  <th>Provayder metrikasi</th>
                  <th>SADORA metrikasi</th>
                  <th>Birlik</th>
                  <th style={{ textAlign: 'right' }}>Scale</th>
                  <th>Faol</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {(mappings.data ?? []).map((mapping) => (
                  <tr key={`${mapping.provider}:${mapping.providerMetric}`}>
                    <td className="mono">{mapping.provider}</td>
                    <td className="mono">{mapping.providerMetric}</td>
                    <td>{mapping.metric}</td>
                    <td className="muted">{mapping.providerUnit ?? '—'}</td>
                    <td style={{ textAlign: 'right' }}>{mapping.scale}</td>
                    <td>{mapping.active ? <span className="badge ok">ha</span> : <span className="badge free">yo'q</span>}</td>
                    <td>
                      {editable && (
                        <button className="btn small" onClick={() => setEditing(mapping)}>
                          Tahrirlash
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

      {editing && (
        <MappingDialog
          initial={editing === 'new' ? null : editing}
          onClose={() => setEditing(null)}
          onSave={(mapping) => save.mutate(mapping, { onSuccess: () => setEditing(null) })}
          pending={save.isPending}
        />
      )}
    </div>
  )
}

function MappingDialog({
  initial,
  onClose,
  onSave,
  pending,
}: {
  initial: MetricMapping | null
  onClose: () => void
  onSave: (mapping: MetricMapping) => void
  pending: boolean
}) {
  const [provider, setProvider] = useState<HealthProvider>(initial?.provider ?? 'health_connect')
  const [providerMetric, setProviderMetric] = useState(initial?.providerMetric ?? '')
  const [metric, setMetric] = useState<HealthMetric>(initial?.metric ?? 'steps')
  const [unit, setUnit] = useState(initial?.providerUnit ?? '')
  const [scale, setScale] = useState(String(initial?.scale ?? 1))
  const [active, setActive] = useState(initial?.active ?? true)

  return (
    <Modal title={initial ? 'Moslashtirishni tahrirlash' : 'Yangi moslashtirish'} onClose={onClose}>
      <Field label="Provayder">
        <select value={provider} disabled={Boolean(initial)} onChange={(event) => setProvider(event.target.value as HealthProvider)}>
          {providers.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Provayder metrikasi (ularning nomi)">
        <input
          value={providerMetric}
          disabled={Boolean(initial)}
          placeholder="masalan StepCount"
          onChange={(event) => setProviderMetric(event.target.value)}
          autoFocus={!initial}
        />
      </Field>
      <Field label="SADORA metrikasi">
        <select value={metric} onChange={(event) => setMetric(event.target.value as HealthMetric)}>
          {metrics.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </Field>
      <Field label="Provayder birligi (ixtiyoriy)">
        <input value={unit} onChange={(event) => setUnit(event.target.value)} />
      </Field>
      <Field label="Scale">
        <input type="number" step="any" value={scale} onChange={(event) => setScale(event.target.value)} />
      </Field>
      <label className="row" style={{ gap: 6 }}>
        <input type="checkbox" style={{ width: 'auto' }} checked={active} onChange={(event) => setActive(event.target.checked)} />
        <span className="faint">Faol</span>
      </label>
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button
          className="btn primary"
          disabled={!providerMetric.trim() || Number.isNaN(Number(scale)) || pending}
          onClick={() =>
            onSave({
              provider,
              providerMetric: providerMetric.trim(),
              metric,
              providerUnit: unit.trim() || null,
              scale: Number(scale),
              active,
            })
          }
        >
          {pending ? 'Saqlanmoqda…' : 'Saqlash'}
        </button>
      </div>
    </Modal>
  )
}
