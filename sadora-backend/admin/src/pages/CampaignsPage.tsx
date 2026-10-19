import { useEffect, useState } from 'react'
import { useCampaignAudience, useCampaigns, useCancelCampaign, useCreateCampaign, useTestCampaign } from '../api/hooks'
import type { Campaign, CampaignLanguage, CampaignSegment, CampaignText } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, ConfirmDialog, Empty, ErrorNotice, Field, formatDateTime, Loading, Modal, Spinner } from '../components/ui'
import {
  BODY_MAX,
  campaignLanguages,
  emptySegment,
  kindOptions,
  languageOptions,
  lifeStageOptions,
  localInputToIso,
  segmentSummary,
  skipLabels,
  statusLabels,
  sumValues,
  testFailures,
  textProblems,
  tierOptions,
  TITLE_MAX,
  toggle,
  writtenTexts,
} from './campaigns'

/**
 * Push campaigns: written once in up to three languages, sent to a segment.
 *
 * The server decides who actually gets one — her master switch, her "news" switch, quiet
 * hours, the frequency caps, and never a discreet account — and the page shows that split
 * before sending and after. Owner and Admin write; Analyst reads.
 */
export function CampaignsPage() {
  const campaigns = useCampaigns()
  const { can } = useAuth()
  const editable = can(['OWNER', 'ADMIN'])
  const [composing, setComposing] = useState(false)

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Card
        title="Kampaniyalar"
        action={
          editable && (
            <button className="btn small primary" onClick={() => setComposing(true)}>
              Yangi kampaniya
            </button>
          )
        }
      >
        {campaigns.isLoading ? (
          <Loading rows={4} />
        ) : campaigns.error ? (
          <ErrorNotice error={campaigns.error} />
        ) : !campaigns.data?.length ? (
          <Empty art="ic3d_bell">Hali kampaniya yo'q. Birinchisini yozing — u avval sizning telefoningizga sinov sifatida boradi.</Empty>
        ) : (
          <CampaignTable rows={campaigns.data} editable={editable} />
        )}
      </Card>
      <p className="faint" style={{ margin: 0 }}>
        Kampaniya oddiy eslatmalar yo'lidan boradi: sokin soatlarda kutadi, kunlik va haftalik chegaraga bo'ysunadi. «Yangiliklar»ni
        o'chirgan va yashirin rejimdagi foydalanuvchilarga umuman yuborilmaydi.
      </p>
      {composing && <Composer onClose={() => setComposing(false)} />}
    </div>
  )
}

function CampaignTable({ rows, editable }: { rows: Campaign[]; editable: boolean }) {
  const cancel = useCancelCampaign()
  const { notify } = useToast()
  const [cancelling, setCancelling] = useState<Campaign | null>(null)
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Kampaniya</th>
            <th>Holat</th>
            <th>Vaqt</th>
            <th style={{ textAlign: 'right' }}>Segmentda</th>
            <th style={{ textAlign: 'right' }}>Yetkazildi</th>
            <th style={{ textAlign: 'right' }}>Kutmoqda</th>
            <th style={{ textAlign: 'right' }}>O'tkazildi</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rows.map((campaign) => {
            const status = statusLabels[campaign.status]
            const skipped = sumValues(campaign.skipped)
            const failed = campaign.delivery.failed ?? 0
            return (
              <tr key={campaign.id}>
                <td>
                  <div>{campaign.name}</div>
                  <div className="faint">{campaign.texts.uz?.title}</div>
                  <div className="faint">{segmentSummary(campaign.segment)}</div>
                </td>
                <td>
                  <span className={`badge ${status.tone}`}>{status.text}</span>
                </td>
                <td className="faint" style={{ whiteSpace: 'nowrap' }}>{formatDateTime(campaign.finishedAt ?? campaign.scheduledFor)}</td>
                <td style={{ textAlign: 'right' }}>{campaign.audience ?? '—'}</td>
                <td style={{ textAlign: 'right' }}>
                  {campaign.delivery.sent ?? 0}
                  {failed > 0 && <div className="faint">{failed} yetmadi</div>}
                </td>
                <td style={{ textAlign: 'right' }}>{campaign.delivery.queued ?? 0}</td>
                <td
                  style={{ textAlign: 'right' }}
                  title={Object.entries(campaign.skipped).map(([reason, count]) => `${skipLabels[reason] ?? reason}: ${count}`).join('\n')}
                >
                  {skipped}
                </td>
                <td>
                  {editable && campaign.status === 'scheduled' && (
                    <button className="btn small ghost" onClick={() => setCancelling(campaign)}>
                      Bekor qilish
                    </button>
                  )}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      {cancelling && (
        <ConfirmDialog
          title={`«${cancelling.name}» bekor qilinsinmi?`}
          confirmLabel="Bekor qilish"
          cancelLabel="Qoldirish"
          danger
          pending={cancel.isPending}
          onClose={() => setCancelling(null)}
          onConfirm={() =>
            cancel.mutate(cancelling.id, {
              onSuccess: () => {
                notify('Kampaniya bekor qilindi', 'info')
                setCancelling(null)
              },
            })
          }
        >
          <ErrorNotice error={cancel.error} />
        </ConfirmDialog>
      )}
    </div>
  )
}

type Texts = Partial<Record<CampaignLanguage, CampaignText>>

/** The segment the audience count asks about, settled a moment after the last click. */
function useSettled<T>(value: T, ms = 400): T {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setSettled(value), ms)
    return () => window.clearTimeout(timer)
  }, [value, ms])
  return settled
}

function Composer({ onClose }: { onClose: () => void }) {
  const create = useCreateCampaign()
  const test = useTestCampaign()
  const { notify } = useToast()
  const [name, setName] = useState('')
  const [texts, setTexts] = useState<Texts>({ uz: { title: '', body: '' } })
  const [language, setLanguage] = useState<CampaignLanguage>('uz')
  const [segment, setSegment] = useState<CampaignSegment>(emptySegment)
  const [later, setLater] = useState(false)
  const [when, setWhen] = useState('')
  const [phone, setPhone] = useState('')
  const [confirming, setConfirming] = useState(false)
  const settled = useSettled(segment)
  const audience = useCampaignAudience(settled)

  const current = texts[language] ?? { title: '', body: '' }
  const problems = [...textProblems(texts), ...(name.trim() ? [] : ['Kampaniya nomini yozing'])]
  const scheduledFor = later ? localInputToIso(when) : null
  if (later && !scheduledFor) problems.push('Yuborish vaqtini tanlang')
  const preview = texts[language]?.title || texts[language]?.body ? texts[language]! : texts.uz

  function edit(patch: Partial<CampaignText>) {
    setTexts((all) => ({ ...all, [language]: { ...(all[language] ?? { title: '', body: '' }), ...patch } }))
  }

  function sendTest() {
    test.mutate(
      { texts: writtenTexts(texts), phone },
      {
        onSuccess: (result) =>
          result.sent
            ? notify(`Sinov yuborildi (${result.language?.toUpperCase() ?? 'UZ'} matni)`)
            : notify(testFailures[result.reason ?? ''] ?? "Yuborib bo'lmadi", 'info'),
      },
    )
  }

  return (
    <Modal title="Yangi kampaniya" onClose={onClose} wide>
      <div className="campaign-composer">
        <div className="grid" style={{ gap: 12 }}>
          <Field label="Nomi (faqat panelda ko'rinadi)">
            <input value={name} maxLength={120} onChange={(event) => setName(event.target.value)} placeholder="Masalan: Kegel — yangi darajalar" />
          </Field>

          <div className="segmented" role="group" aria-label="Matn tili">
            {campaignLanguages.map((option) => (
              <button
                key={option.key}
                className={language === option.key ? 'active' : undefined}
                aria-pressed={language === option.key}
                onClick={() => setLanguage(option.key)}
              >
                {option.label}
                {option.key === 'uz' ? ' *' : texts[option.key]?.title ? ' ✓' : ''}
              </button>
            ))}
          </div>
          <Field label={`Sarlavha · ${current.title.length}/${TITLE_MAX}`}>
            <input value={current.title} maxLength={TITLE_MAX} onChange={(event) => edit({ title: event.target.value })} />
          </Field>
          <Field label={`Matn · ${current.body.length}/${BODY_MAX}`}>
            <textarea rows={3} value={current.body} maxLength={BODY_MAX} onChange={(event) => edit({ body: event.target.value })} />
          </Field>
          {language !== 'uz' && <div className="faint">Bo'sh qoldirilsa, bu tildagi foydalanuvchilar o'zbekcha matnni oladi.</div>}

          <Field label="Kimga">
            <div className="grid" style={{ gap: 8 }}>
              <ChipRow
                options={kindOptions}
                selected={segment.accountKinds}
                onToggle={(key) => {
                  const next = toggle(segment.accountKinds, key, kindOptions.map((option) => option.key))
                  if (next.length) setSegment({ ...segment, accountKinds: next })
                }}
              />
              <ChipRow
                options={lifeStageOptions}
                selected={segment.lifeStages}
                anyLabel="Barcha bosqichlar"
                onToggle={(key) => setSegment({ ...segment, lifeStages: toggle(segment.lifeStages, key, lifeStageOptions.map((option) => option.key)) })}
              />
              <ChipRow
                options={languageOptions}
                selected={segment.languages}
                anyLabel="Barcha tillar"
                onToggle={(key) => setSegment({ ...segment, languages: toggle(segment.languages, key, languageOptions.map((option) => option.key)) })}
              />
              <div className="segmented" role="group" aria-label="Tarif">
                {tierOptions.map((option) => (
                  <button
                    key={option.key}
                    className={segment.tier === option.key ? 'active' : undefined}
                    aria-pressed={segment.tier === option.key}
                    onClick={() => setSegment({ ...segment, tier: option.key })}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          </Field>

          <Field label="Qachon">
            <div className="row">
              <div className="segmented" role="group" aria-label="Qachon">
                <button className={!later ? 'active' : undefined} aria-pressed={!later} onClick={() => setLater(false)}>
                  Hozir
                </button>
                <button className={later ? 'active' : undefined} aria-pressed={later} onClick={() => setLater(true)}>
                  Vaqtni tanlash
                </button>
              </div>
              {later && <input type="datetime-local" aria-label="Yuborish vaqti" value={when} onChange={(event) => setWhen(event.target.value)} style={{ maxWidth: 220 }} />}
            </div>
          </Field>
        </div>

        <aside className="grid campaign-side" style={{ gap: 12 }}>
          <div className="lock-preview" aria-label="Telefondagi ko'rinishi">
            <div className="lock-app">
              <span className="lock-icon" aria-hidden="true">S</span> SADORA · hozir
            </div>
            <div className="lock-title">{preview?.title || 'Sarlavha'}</div>
            <div className="lock-body">{preview?.body || "Matn shu yerda ko'rinadi"}</div>
          </div>

          <div className="card campaign-audience">
            {audience.isLoading ? (
              <Loading rows={2} />
            ) : audience.error ? (
              <ErrorNotice error={audience.error} />
            ) : audience.data ? (
              <>
                <div>
                  <span className="system-number">{audience.data.reachable}</span> kishiga yetib boradi
                  <span className="faint"> · segmentda {audience.data.matched}</span>
                </div>
                {Object.entries(audience.data.skipped).map(([reason, count]) => (
                  <div key={reason} className="faint">
                    {skipLabels[reason] ?? reason}: {count}
                  </div>
                ))}
              </>
            ) : null}
          </div>

          <Field label="Sinov: telefon raqami">
            <div className="row" style={{ flexWrap: 'nowrap' }}>
              <input value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="90 123 45 67" inputMode="tel" />
              <button className="btn small" disabled={!phone.trim() || textProblems(texts).length > 0 || test.isPending} onClick={sendTest}>
                {test.isPending && <Spinner />}
                Sinov
              </button>
            </div>
          </Field>
          <ErrorNotice error={test.error} />
        </aside>
      </div>

      {problems.length > 0 && <div className="faint">{problems[0]}</div>}
      <ErrorNotice error={create.error} />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button className="btn primary" disabled={problems.length > 0} onClick={() => setConfirming(true)}>
          {later ? 'Rejalashtirish' : 'Yuborish'}
        </button>
      </div>

      {confirming && (
        <ConfirmDialog
          title={later ? 'Kampaniya rejalashtirilsinmi?' : 'Kampaniya hozir yuborilsinmi?'}
          confirmLabel={later ? 'Rejalashtirish' : 'Yuborish'}
          pending={create.isPending}
          onClose={() => setConfirming(false)}
          onConfirm={() =>
            create.mutate(
              { name: name.trim(), texts: writtenTexts(texts), segment, scheduledFor },
              {
                onSuccess: () => {
                  notify(later ? 'Kampaniya rejalashtirildi' : 'Kampaniya bir daqiqa ichida yuboriladi')
                  setConfirming(false)
                  onClose()
                },
              },
            )
          }
        >
          <p className="muted" style={{ margin: 0 }}>
            Hozirgi hisob-kitobda {audience.data?.reachable ?? '—'} kishiga yetib boradi ({segmentSummary(segment)}).
            {later && scheduledFor ? ` Yuborish vaqti: ${formatDateTime(scheduledFor)}.` : ''} Yuborilgan kampaniyani qaytarib bo'lmaydi.
          </p>
          <ErrorNotice error={create.error} />
        </ConfirmDialog>
      )}
    </Modal>
  )
}

/** A row of on/off chips for a multi-select; with nothing picked the row means "any". */
function ChipRow<K extends string>({
  options,
  selected,
  onToggle,
  anyLabel,
}: {
  options: { key: K; label: string }[]
  selected: K[]
  onToggle: (key: K) => void
  anyLabel?: string
}) {
  return (
    <div className="chip-row">
      {anyLabel && <span className={`chip-any${selected.length ? '' : ' on'}`}>{anyLabel}</span>}
      {options.map((option) => (
        <button
          key={option.key}
          type="button"
          className={`chip-toggle${selected.includes(option.key) ? ' on' : ''}`}
          aria-pressed={selected.includes(option.key)}
          onClick={() => onToggle(option.key)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
