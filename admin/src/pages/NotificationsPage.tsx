import { useEffect, useState } from 'react'
import { useCaps, useSaveTemplate, useTemplates, useUpdateCaps } from '../api/hooks'
import type { NotificationTemplate } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Card, ErrorNotice, Field, Loading } from '../components/ui'

const categoryLabels: Record<string, string> = {
  med_reminder: 'Dori eslatmasi',
  cycle: 'Sikl',
  daily_check_in: 'Kunlik check-in',
  water: 'Suv',
  insight: 'Insight',
  system: 'Tizim',
}

/**
 * Page 10: the frequency caps and the templates.
 *
 * Text lives here rather than in the app so a wording can be fixed, or a translation
 * added, without a release. Medication reminders and system messages ignore the caps by
 * design — that is stated next to the caps so nobody tightens them expecting otherwise.
 */
export function NotificationsPage() {
  const { can } = useAuth()
  const editable = can(['OWNER', 'ADMIN'])

  return (
    <div className="grid" style={{ gap: 16 }}>
      <CapsCard editable={editable} />
      <TemplatesCard editable={editable} />
    </div>
  )
}

function CapsCard({ editable }: { editable: boolean }) {
  const caps = useCaps()
  const update = useUpdateCaps()
  const [perDay, setPerDay] = useState(6)
  const [perWeek, setPerWeek] = useState(25)

  useEffect(() => {
    if (caps.data) {
      setPerDay(caps.data.maxPerDay)
      setPerWeek(caps.data.maxPerWeek)
    }
  }, [caps.data])

  if (caps.isLoading) return <Loading rows={2} />
  if (caps.error) return <ErrorNotice error={caps.error} />

  const dirty = caps.data && (caps.data.maxPerDay !== perDay || caps.data.maxPerWeek !== perWeek)

  return (
    <Card title="Chastota chegaralari">
      <div className="notice" style={{ marginBottom: 12 }}>
        Chegaralar reklama xarakteridagi bildirishnomalarga tegishli. Dori eslatmasi va tizim
        xabarlari ularni chetlab o'tadi — foydalanuvchi o'zi qo'ygan vaqt byudjet emas.
      </div>
      <div className="filters">
        <Field label="Kuniga eng ko'pi">
          <input
            className="narrow"
            type="number"
            min={0}
            max={50}
            disabled={!editable}
            value={perDay}
            onChange={(event) => setPerDay(Number(event.target.value))}
          />
        </Field>
        <Field label="Haftasiga eng ko'pi">
          <input
            className="narrow"
            type="number"
            min={0}
            max={300}
            disabled={!editable}
            value={perWeek}
            onChange={(event) => setPerWeek(Number(event.target.value))}
          />
        </Field>
        <div className="row" style={{ alignSelf: 'end' }}>
          <button
            className="btn primary"
            disabled={!editable || !dirty || update.isPending}
            onClick={() => update.mutate({ maxPerDay: perDay, maxPerWeek: perWeek })}
          >
            Saqlash
          </button>
        </div>
      </div>
      {update.error && <ErrorNotice error={update.error} />}
    </Card>
  )
}

function TemplatesCard({ editable }: { editable: boolean }) {
  const templates = useTemplates()
  const save = useSaveTemplate()
  const [draft, setDraft] = useState<Record<string, NotificationTemplate>>({})

  useEffect(() => {
    if (templates.data) {
      setDraft(Object.fromEntries(templates.data.map((template) => [id(template), template])))
    }
  }, [templates.data])

  if (templates.isLoading) return <Loading rows={6} />
  if (templates.error) return <ErrorNotice error={templates.error} />

  const rows = templates.data ?? []

  function edit(key: string, patch: Partial<NotificationTemplate>) {
    setDraft((current) => ({ ...current, [key]: { ...current[key]!, ...patch } }))
  }

  function isDirty(template: NotificationTemplate): boolean {
    const current = draft[id(template)]
    return Boolean(current) && JSON.stringify(current) !== JSON.stringify(template)
  }

  return (
    <Card title="Shablonlar">
      {save.error && <ErrorNotice error={save.error} />}
      {!rows.length ? (
        <p className="faint" style={{ margin: 0 }}>
          Shablonlar hali yo'q — server ularni migratsiya bilan yaratadi.
        </p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Kalit</th>
                <th>Til</th>
                <th>Turkum</th>
                <th>Sarlavha</th>
                <th style={{ width: '35%' }}>Matn</th>
                <th>Faol</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((template) => {
                const key = id(template)
                const current = draft[key] ?? template
                return (
                  <tr key={key}>
                    <td className="mono">{template.key}</td>
                    <td>{template.language.toUpperCase()}</td>
                    <td className="muted">{categoryLabels[template.category] ?? template.category}</td>
                    <td>
                      <input
                        disabled={!editable}
                        value={current.title}
                        onChange={(event) => edit(key, { title: event.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        disabled={!editable}
                        value={current.body}
                        onChange={(event) => edit(key, { body: event.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        type="checkbox"
                        style={{ width: 'auto' }}
                        disabled={!editable}
                        checked={current.active}
                        onChange={(event) => edit(key, { active: event.target.checked })}
                      />
                    </td>
                    <td>
                      <button
                        className="btn small primary"
                        disabled={!editable || !isDirty(template) || save.isPending}
                        onClick={() => save.mutate(current)}
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
      )}
    </Card>
  )
}

const id = (template: NotificationTemplate) => `${template.key}:${template.language}`
