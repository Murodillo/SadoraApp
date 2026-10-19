import { useEffect, useState } from 'react'
import { useAppRelease, useSaveAppRelease } from '../api/hooks'
import type { AppReleasePolicy, CampaignLanguage, PlatformRelease } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, ErrorNotice, Field, formatDateTime, Loading, Spinner, Switch } from '../components/ui'
import { campaignLanguages } from './campaigns'
import { releaseWarnings } from './appRelease'

/**
 * Which builds may run, which are told a newer one is out, and the maintenance screen.
 *
 * The phone asks on every start and every return, before sign-in, so a change here reaches
 * an open app within seconds. A build that predates the question keeps working whatever is
 * set — the gate exists only from the build that learned to ask.
 */
export function AppReleasePage() {
  const policy = useAppRelease()
  const save = useSaveAppRelease()
  const { can } = useAuth()
  const { notify } = useToast()
  const editable = can(['OWNER', 'ADMIN'])
  const [draft, setDraft] = useState<AppReleasePolicy | null>(null)
  const [confirming, setConfirming] = useState(false)

  useEffect(() => {
    if (policy.data) setDraft(policy.data)
  }, [policy.data])

  if (policy.isLoading || !draft) return <Loading rows={6} />
  if (policy.error) return <ErrorNotice error={policy.error} />
  const saved = policy.data!
  const dirty = JSON.stringify(draft) !== JSON.stringify(saved)
  const warnings = releaseWarnings(saved, draft)

  function platform(key: 'android' | 'ios', patch: Partial<PlatformRelease>) {
    setDraft((current) => current && { ...current, [key]: { ...current[key], ...patch } })
  }

  function store() {
    save.mutate(draft!, {
      onSuccess: () => {
        notify('Saqlandi — ilovalar bir necha soniyada biladi')
        setConfirming(false)
      },
    })
  }

  return (
    <div className="grid" style={{ gap: 16 }}>
      <div className="two-col even">
        <PlatformCard
          title="Android"
          hint="Build raqami — versionCode (Play Console'dagi «Version code»)."
          release={draft.android}
          editable={editable}
          onChange={(patch) => platform('android', patch)}
        />
        <PlatformCard
          title="iOS"
          hint="Build raqami — CFBundleVersion (TestFlight'dagi build). Versiya 1.0 bo'lib qoladi."
          release={draft.ios}
          editable={editable}
          onChange={(patch) => platform('ios', patch)}
        />
      </div>

      <Card title="Texnik ishlar">
        <div className="grid" style={{ gap: 12 }}>
          <Switch
            label={draft.maintenance.active ? "Yoqilgan — ilova faqat shu xabarni ko'rsatadi" : "O'chiq"}
            checked={draft.maintenance.active}
            disabled={!editable}
            onChange={(active) => setDraft({ ...draft, maintenance: { ...draft.maintenance, active } })}
          />
          <div className="faint">
            Server ishlashda davom etadi — to'lov callback'lari va panel to'xtamaydi. Faqat ilova o'zini yopib, xabarni va
            «Qayta urinish» tugmasini ko'rsatadi.
          </div>
          {campaignLanguages.map(({ key, label }) => (
            <Field key={key} label={`${label}${key === 'uz' ? ' (majburiy)' : ''}`}>
              <textarea
                rows={2}
                maxLength={300}
                disabled={!editable}
                value={draft.maintenance.message[key as CampaignLanguage] ?? ''}
                placeholder={key === 'uz' ? "Masalan: Ilova yangilanmoqda, 30 daqiqadan so'ng qayta urinib ko'ring" : "Bo'sh bo'lsa, o'zbekcha matn ko'rsatiladi"}
                onChange={(event) =>
                  setDraft({
                    ...draft,
                    maintenance: { ...draft.maintenance, message: { ...draft.maintenance.message, [key]: event.target.value } },
                  })
                }
              />
            </Field>
          ))}
        </div>
      </Card>

      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="faint">{saved.updatedAt ? `Oxirgi o'zgarish: ${formatDateTime(saved.updatedAt)}` : "Hali o'zgartirilmagan"}</span>
        {editable && (
          <div className="row">
            <button className="btn ghost" disabled={!dirty || save.isPending} onClick={() => setDraft(saved)}>
              Bekor qilish
            </button>
            <button className="btn primary" disabled={!dirty || save.isPending} onClick={() => (warnings.length ? setConfirming(true) : store())}>
              {save.isPending && <Spinner />}
              Saqlash
            </button>
          </div>
        )}
      </div>
      <ErrorNotice error={save.error} />

      {confirming && (
        <ConfirmDialog
          title="Bu o'zgarish foydalanuvchilarni to'xtatadi"
          confirmLabel="Baribir saqlash"
          danger
          pending={save.isPending}
          onClose={() => setConfirming(false)}
          onConfirm={store}
        >
          <ul className="muted" style={{ margin: 0, paddingLeft: 18 }}>
            {warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
          <ErrorNotice error={save.error} />
        </ConfirmDialog>
      )}
    </div>
  )
}

function PlatformCard({
  title,
  hint,
  release,
  editable,
  onChange,
}: {
  title: string
  hint: string
  release: PlatformRelease
  editable: boolean
  onChange: (patch: Partial<PlatformRelease>) => void
}) {
  const number = (value: string) => (value.trim() === '' ? null : Math.max(1, Math.round(Number(value))))
  return (
    <Card title={title}>
      <div className="grid" style={{ gap: 12 }}>
        <div className="filters" style={{ gridTemplateColumns: '1fr 1fr' }}>
          <Field label="Majburiy: kamida shu build">
            <input type="number" min={1} disabled={!editable} value={release.minBuild ?? ''} placeholder="yo'q" onChange={(event) => onChange({ minBuild: number(event.target.value) })} />
          </Field>
          <Field label="Taklif: eng yangi build">
            <input type="number" min={1} disabled={!editable} value={release.latestBuild ?? ''} placeholder="yo'q" onChange={(event) => onChange({ latestBuild: number(event.target.value) })} />
          </Field>
        </div>
        <Field label="Do'kon havolasi">
          <input disabled={!editable} value={release.storeUrl ?? ''} placeholder="https://" onChange={(event) => onChange({ storeUrl: event.target.value || null })} />
        </Field>
        <div className="faint">{hint} Minimaldan past build faqat «Yangilang» ekranini ko'radi; eng yangidan pasti bir marta taklif oladi.</div>
      </div>
    </Card>
  )
}
