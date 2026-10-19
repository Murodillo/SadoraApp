import { useEffect, useState } from 'react'
import { useFeatureUsage, useFeatures, useFlags, useUpdateFeature, useUpdateFlag } from '../api/hooks'
import type { AdminFeatureUsage, AdminFlag, FeatureDefinition } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ArtTile } from '../components/art'
import { useToast } from '../components/toast'
import { ConfirmDialog, Empty, ErrorNotice, Loading, Spinner, Switch } from '../components/ui'
import { FlagAdvanced } from './FlagAdvanced'
import {
  audienceOf,
  buildFeatureGroups,
  cardState,
  matchesSearch,
  planSwitchOff,
  planSwitchOn,
  showsLimits,
} from './featureCatalog'
import type { Audience, CatalogEntry, FeatureCard, SwitchPlan, SwitchState } from './featureCatalog'

const stateLabels: Record<SwitchState, string> = {
  on: 'Ishlayapti',
  partial: 'Qisman',
  off: "O'chiq",
}

const USAGE_DAYS = 7

/**
 * Everything the app can switch, one card per feature: whether it works, who gets it,
 * how often, and how much it was used this week. It replaces the flags page and the
 * entitlement table, which held the same features under raw keys on two pages.
 *
 * Owner and Admin edit; Analyst reads. A change reaches phones without a release — the
 * app reads flags every five minutes and entitlements on its next request.
 */
export function FeaturesPage() {
  const flags = useFlags()
  const features = useFeatures()
  const usage = useFeatureUsage(USAGE_DAYS)
  const { can } = useAuth()
  const editable = can(['OWNER', 'ADMIN'])
  const [search, setSearch] = useState('')
  const [onlyOff, setOnlyOff] = useState(false)

  if (flags.isLoading || features.isLoading) return <Loading rows={8} />
  const failure = flags.error ?? features.error
  if (failure) return <ErrorNotice error={failure} />

  const groups = buildFeatureGroups(flags.data ?? [], features.data ?? [])
  const usageByKey = new Map((usage.data ?? []).map((row) => [row.key, row]))
  const cards = groups.flatMap((group) => group.cards)
  const notOn = cards.filter((card) => cardState(card) !== 'on').length
  const visible = groups
    .map((group) => ({
      ...group,
      cards: group.cards.filter((card) => matchesSearch(card, search) && (!onlyOff || cardState(card) !== 'on')),
    }))
    .filter((group) => group.cards.length > 0)

  return (
    <div className="grid" style={{ gap: 18 }}>
      <div className="row features-toolbar">
        <input
          type="search"
          placeholder="Qidirish: chat, skaner, Payme…"
          aria-label="Funksiyani qidirish"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <div className="segmented" role="group" aria-label="Ko'rsatish">
          <button className={!onlyOff ? 'active' : undefined} aria-pressed={!onlyOff} onClick={() => setOnlyOff(false)}>
            Hammasi · {cards.length}
          </button>
          <button className={onlyOff ? 'active' : undefined} aria-pressed={onlyOff} onClick={() => setOnlyOff(true)}>
            O'chiq yoki qisman · {notOn}
          </button>
        </div>
        <span className="faint features-hint">O'zgarish ilovani yangilamasdan, 5 daqiqa ichida hamma telefonga yetadi.</span>
      </div>

      {visible.map((group) => (
        <section key={group.title} className="feature-group" aria-label={group.title}>
          <h2 className="feature-group-title">{group.title}</h2>
          <div className="feature-grid">
            {group.cards.map((card) => (
              <FeatureCardView
                key={card.id}
                card={card}
                editable={editable}
                usage={card.feature ? usageByKey.get(card.feature.key) : undefined}
              />
            ))}
          </div>
        </section>
      ))}

      {!visible.length && <Empty>Bunday funksiya topilmadi</Empty>}
    </div>
  )
}

function FeatureCardView({
  card,
  editable,
  usage,
}: {
  card: FeatureCard
  editable: boolean
  usage?: AdminFeatureUsage
}) {
  const state = cardState(card)
  const [flip, setFlip] = useState<'on' | 'off' | null>(null)
  const [advanced, setAdvanced] = useState(false)
  const advancedFlags = [
    ...(card.flag ? [{ flag: card.flag, title: card.entry.title, heading: undefined as string | undefined }] : []),
    ...card.platforms.map((platform) => ({
      flag: platform.flag,
      title: `${card.entry.title} — ${platform.label}`,
      heading: platform.label,
    })),
  ]
  const keys = [card.flag?.key, card.feature?.key, ...card.platforms.map((platform) => platform.flag.key)].filter(Boolean)

  return (
    <article className={`card feature-card ${state}`}>
      <div className="feature-head">
        <ArtTile name={card.entry.art} size={40} />
        <div className="feature-title">
          <div className="feature-name">
            {card.entry.title}{' '}
            <span className={`badge ${state === 'on' ? 'ok' : state === 'partial' ? 'warn' : 'free'}`}>{stateLabels[state]}</span>
          </div>
          <div className="faint">{card.entry.about}</div>
        </div>
        <Switch
          ariaLabel={`${card.entry.title}: ${stateLabels[state]}`}
          checked={state !== 'off'}
          disabled={!editable}
          onChange={(on) => setFlip(on ? 'on' : 'off')}
        />
      </div>

      {state === 'partial' && (
        <div className="faint">Qoidalar bo'yicha faqat bir qismiga ochiq — «Kengaytirilgan»da ko'ring.</div>
      )}

      {card.feature && audienceOf(card.feature) !== 'none' && (
        <AccessEditor entry={card.entry} feature={card.feature} editable={editable} />
      )}

      {card.platforms.length > 0 && <PlatformSwitches card={card} editable={editable} />}

      <div className="feature-foot">
        <span className="faint">
          {usage ? `${USAGE_DAYS} kunda: ${usage.uses.toLocaleString('ru-RU')} marta · ${usage.users.toLocaleString('ru-RU')} kishi` : ''}
        </span>
        <button className="link-btn" aria-expanded={advanced} onClick={() => setAdvanced((open) => !open)}>
          Kengaytirilgan {advanced ? '▴' : '▾'}
        </button>
      </div>

      {advanced && (
        <div className="feature-advanced">
          <div className="faint">
            Kalitlar: <span className="mono">{keys.join(' · ')}</span>
          </div>
          {advancedFlags.map(({ flag, title, heading }) => (
            <FlagAdvanced key={flag.key} flag={flag} title={title} heading={heading} editable={editable} />
          ))}
          {!advancedFlags.length && (
            <div className="faint">Bu funksiyada umumiy o'chirgich yo'q: uni «Kim uchun» boshqaradi.</div>
          )}
        </div>
      )}

      {flip && <SwitchDialog card={card} turnOn={flip === 'on'} onClose={() => setFlip(null)} />}
    </article>
  )
}

/** The card's one switch, confirmed: it reaches every phone, so it never flips on a stray click. */
function SwitchDialog({ card, turnOn, onClose }: { card: FeatureCard; turnOn: boolean; onClose: () => void }) {
  const updateFlag = useUpdateFlag()
  const updateFeature = useUpdateFeature()
  const { notify } = useToast()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const plan: SwitchPlan = turnOn ? planSwitchOn(card) : planSwitchOff(card)
  const title = card.entry.title

  async function apply() {
    setPending(true)
    setError(null)
    try {
      if (plan.flag) await updateFlag.mutateAsync(plan.flag)
      if (plan.feature) await updateFeature.mutateAsync(plan.feature)
      notify(`${title}: ${turnOn ? 'yoqildi' : "o'chirildi"}`, turnOn ? 'ok' : 'info')
      onClose()
    } catch (failure) {
      setError(failure)
    } finally {
      setPending(false)
    }
  }

  return (
    <ConfirmDialog
      title={turnOn ? `«${title}» yoqilsinmi?` : `«${title}» hamma uchun o'chirilsinmi?`}
      confirmLabel={turnOn ? 'Yoqish' : "Hamma uchun o'chirish"}
      pendingLabel={turnOn ? 'Yoqilmoqda…' : "O'chirilmoqda…"}
      danger={!turnOn}
      pending={pending}
      onClose={onClose}
      onConfirm={() => void apply()}
    >
      <p className="muted" style={{ margin: 0 }}>
        {turnOn ? switchOnCopy(card, plan) : switchOffCopy(card)}
      </p>
      <ErrorNotice error={error} />
    </ConfirmDialog>
  )
}

function switchOffCopy(card: FeatureCard): string {
  return card.flag
    ? "Funksiya ilovada hamma uchun darhol yopiladi. Qolgan sozlamalari saqlanib qoladi — qayta yoqsangiz, avvalgidek ishlaydi."
    : "Funksiya Bepul va Premium foydalanuvchilar uchun yopiladi."
}

function switchOnCopy(card: FeatureCard, plan: SwitchPlan): string {
  if (plan.feature) {
    return "Funksiya Premium foydalanuvchilarga ochiladi. Bepul foydalanuvchilarga ham ochish uchun keyin «Hamma»ni tanlang."
  }
  if (plan.flag?.defaultValue && !card.flag?.defaultValue) {
    return 'Funksiya hamma foydalanuvchi uchun darhol yoqiladi.'
  }
  return 'Funksiya avvalgi sozlamalari bilan yana ishlay boshlaydi.'
}

const audienceLabels: Record<Exclude<Audience, 'none'>, string> = {
  all: 'Hamma',
  premium: 'Faqat Premium',
  free: 'Faqat bepul',
}

/**
 * Who gets the feature and how many times, edited as a draft and saved with one button.
 * The draft follows the server only when the server's row changes, so saving another
 * card does not wipe what was typed here.
 */
function AccessEditor({ entry, feature, editable }: { entry: CatalogEntry; feature: FeatureDefinition; editable: boolean }) {
  const update = useUpdateFeature()
  const { notify } = useToast()
  const saved = JSON.stringify(feature)
  const [draft, setDraft] = useState<FeatureDefinition>(feature)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => setDraft(feature), [saved])

  const audience = audienceOf(draft)
  const dirty = JSON.stringify(draft) !== saved
  const options: Exclude<Audience, 'none'>[] = audience === 'free' ? ['all', 'premium', 'free'] : ['all', 'premium']
  const limits = showsLimits(entry, draft)

  function edit(patch: Partial<FeatureDefinition>) {
    setDraft((current) => ({ ...current, ...patch }))
  }

  return (
    <div className="grid" style={{ gap: 8 }}>
      <div className="feature-row">
        <span className="feature-label">Kim uchun</span>
        <div className="segmented" role="group" aria-label={`${entry.title}: kim uchun`}>
          {options.map((option) => (
            <button
              key={option}
              className={audience === option ? 'active' : undefined}
              aria-pressed={audience === option}
              disabled={!editable}
              onClick={() =>
                edit(
                  option === 'all'
                    ? { freeEnabled: true, premiumEnabled: true }
                    : option === 'premium'
                      ? { freeEnabled: false, premiumEnabled: true }
                      : { freeEnabled: true, premiumEnabled: false },
                )
              }
            >
              {audienceLabels[option]}
            </button>
          ))}
        </div>
      </div>

      {limits && draft.freeEnabled && (
        <LimitRow
          label="Bepul"
          title={entry.title}
          daily={draft.freeDailyLimit}
          monthly={draft.freeMonthlyLimit}
          editable={editable}
          onChange={(daily, monthly) => edit({ freeDailyLimit: daily, freeMonthlyLimit: monthly })}
        />
      )}
      {limits && draft.premiumEnabled && (
        <LimitRow
          label="Premium"
          title={entry.title}
          daily={draft.premiumDailyLimit}
          monthly={draft.premiumMonthlyLimit}
          editable={editable}
          onChange={(daily, monthly) => edit({ premiumDailyLimit: daily, premiumMonthlyLimit: monthly })}
        />
      )}

      {limits && !entry.metered && (
        <div className="faint">Server bu funksiyaning ishlatilishini sanamaydi — limit hozircha hech narsani cheklamaydi.</div>
      )}

      {dirty && (
        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <ErrorNotice error={update.error} />
          <button className="btn small ghost" disabled={update.isPending} onClick={() => setDraft(feature)}>
            Bekor qilish
          </button>
          <button
            className="btn small primary"
            disabled={update.isPending}
            onClick={() => update.mutate(draft, { onSuccess: () => notify(`${entry.title}: saqlandi`) })}
          >
            {update.isPending && <Spinner />}
            Saqlash
          </button>
        </div>
      )}
    </div>
  )
}

function LimitRow({
  label,
  title,
  daily,
  monthly,
  editable,
  onChange,
}: {
  label: string
  title: string
  daily?: number | null
  monthly?: number | null
  editable: boolean
  onChange: (daily: number | null, monthly: number | null) => void
}) {
  return (
    <div className="feature-row">
      <span className="feature-label">{label}</span>
      <span className="faint">kuniga</span>
      <LimitInput label={`${title}, ${label}: kunlik limit`} value={daily} disabled={!editable} onChange={(value) => onChange(value, monthly ?? null)} />
      <span className="faint">oyiga</span>
      <LimitInput label={`${title}, ${label}: oylik limit`} value={monthly} disabled={!editable} onChange={(value) => onChange(daily ?? null, value)} />
    </div>
  )
}

/** Empty is unlimited, shown as ∞; anything else is a whole number of uses. */
function LimitInput({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string
  value?: number | null
  onChange: (value: number | null) => void
  disabled: boolean
}) {
  return (
    <input
      className="limit"
      type="number"
      min={0}
      placeholder="∞"
      aria-label={label}
      title="Bo'sh — cheksiz"
      disabled={disabled}
      value={value ?? ''}
      onChange={(event) => onChange(event.target.value === '' ? null : Math.max(0, Math.round(Number(event.target.value))))}
    />
  )
}

/** A feature that is two flags, one per phone platform: each switches on its own. */
function PlatformSwitches({ card, editable }: { card: FeatureCard; editable: boolean }) {
  const [flip, setFlip] = useState<{ flag: AdminFlag; label: string; on: boolean } | null>(null)
  return (
    <div className="feature-row">
      <span className="feature-label">Platformalar</span>
      {card.platforms.map(({ flag, label }) => {
        const on = cardState({ flag }) !== 'off'
        return (
          <Switch
            key={flag.key}
            label={label}
            ariaLabel={`${card.entry.title} — ${label}`}
            checked={on}
            disabled={!editable}
            onChange={(next) => setFlip({ flag, label, on: next })}
          />
        )
      })}
      {flip && (
        <SwitchDialog
          card={{ ...card, flag: flip.flag, feature: undefined, entry: { ...card.entry, title: `${card.entry.title} — ${flip.label}` } }}
          turnOn={flip.on}
          onClose={() => setFlip(null)}
        />
      )}
    </div>
  )
}
