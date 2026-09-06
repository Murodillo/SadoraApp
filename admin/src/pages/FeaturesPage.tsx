import { useEffect, useState } from 'react'
import { useFeatures, useUpdateFeature } from '../api/hooks'
import type { FeatureDefinition } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, ErrorNotice, Loading } from '../components/ui'

/**
 * The entitlement table, editable in place.
 *
 * A change here reaches every phone on the next entitlement read — no app release. That
 * is the point of the page, and also why it is limited to Owner and Admin.
 */
export function FeaturesPage() {
  const features = useFeatures()
  const update = useUpdateFeature()
  const { can } = useAuth()
  const editable = can(['OWNER', 'ADMIN'])

  const [draft, setDraft] = useState<Record<string, FeatureDefinition>>({})

  useEffect(() => {
    if (features.data) {
      setDraft(Object.fromEntries(features.data.map((feature) => [feature.key, feature])))
    }
  }, [features.data])

  if (features.isLoading) return <Loading rows={8} />
  if (features.error) return <ErrorNotice error={features.error} />

  const rows = features.data ?? []

  function edit(key: string, patch: Partial<FeatureDefinition>) {
    setDraft((current) => ({ ...current, [key]: { ...current[key]!, ...patch } }))
  }

  function isDirty(feature: FeatureDefinition): boolean {
    const current = draft[feature.key]
    return Boolean(current) && JSON.stringify(current) !== JSON.stringify(feature)
  }

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="notice">
        Bu yerdagi o'zgarish ilovani yangilamasdan kuchga kiradi. Bo'sh limit — cheksiz degani.
        AI xarajati oshib ketsa, chat limitini shu yerdan tushirasiz.
      </div>

      {update.error && <ErrorNotice error={update.error} />}

      <Card>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Funksiya</th>
                <th>Free</th>
                <th>Free kunlik</th>
                <th>Free oylik</th>
                <th>Premium</th>
                <th>Premium kunlik</th>
                <th>Premium oylik</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((feature) => {
                const current = draft[feature.key] ?? feature
                return (
                  <tr key={feature.key}>
                    <td>
                      <div className="mono">{feature.key}</div>
                      <div className="faint">{feature.description}</div>
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        style={{ width: 'auto' }}
                        disabled={!editable}
                        checked={current.freeEnabled}
                        onChange={(event) => edit(feature.key, { freeEnabled: event.target.checked })}
                      />
                    </td>
                    <td>
                      <LimitInput
                        disabled={!editable}
                        value={current.freeDailyLimit}
                        onChange={(value) => edit(feature.key, { freeDailyLimit: value })}
                      />
                    </td>
                    <td>
                      <LimitInput
                        disabled={!editable}
                        value={current.freeMonthlyLimit}
                        onChange={(value) => edit(feature.key, { freeMonthlyLimit: value })}
                      />
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        style={{ width: 'auto' }}
                        disabled={!editable}
                        checked={current.premiumEnabled}
                        onChange={(event) => edit(feature.key, { premiumEnabled: event.target.checked })}
                      />
                    </td>
                    <td>
                      <LimitInput
                        disabled={!editable}
                        value={current.premiumDailyLimit}
                        onChange={(value) => edit(feature.key, { premiumDailyLimit: value })}
                      />
                    </td>
                    <td>
                      <LimitInput
                        disabled={!editable}
                        value={current.premiumMonthlyLimit}
                        onChange={(value) => edit(feature.key, { premiumMonthlyLimit: value })}
                      />
                    </td>
                    <td>
                      <button
                        className="btn small primary"
                        disabled={!editable || !isDirty(feature) || update.isPending}
                        onClick={() => update.mutate(current)}
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
    </div>
  )
}

function LimitInput({
  value,
  onChange,
  disabled,
}: {
  value?: number | null
  onChange: (value: number | null) => void
  disabled: boolean
}) {
  return (
    <input
      className="narrow"
      type="number"
      min={0}
      placeholder="∞"
      disabled={disabled}
      value={value ?? ''}
      onChange={(event) => onChange(event.target.value === '' ? null : Number(event.target.value))}
    />
  )
}
