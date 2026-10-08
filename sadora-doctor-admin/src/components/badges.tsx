import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'
import { createPortal } from 'react-dom'
import type { BadgeState, BadgeUnlock } from '../api/types'
import { Art } from './art'
import type { ArtName } from './art'

/*
 * Her badges, as the doctor app draws them: a medal with a metal rim, a soft face and the
 * clay icon living on it, each icon with a gesture of its own. The keys and thresholds
 * are the server's (`DoctorBadges` in the contract); the words and the art are here.
 */

/** How each badge's icon moves; the same gestures as the app's `BadgeGesture`. */
type Gesture = 'tick' | 'turn' | 'chatter' | 'swing' | 'heartbeat' | 'flicker' | 'shine' | 'bounce' | 'hover'

interface BadgeInfo {
  name: string
  art: ArtName
  gesture: Gesture
  /** What a tier asks for, with its threshold in it. */
  goal: (target: number) => string
}

export const BADGES: Record<string, BadgeInfo> = {
  verified: { name: 'Tasdiqlangan shifokor', art: 'art_shield', gesture: 'tick', goal: () => 'Sadora sizni shifokor sifatida tasdiqladi' },
  photo: { name: 'Tanish yuz', art: 'ic3d_camera', gesture: 'turn', goal: () => "Sahifangizga rasm qo'ying" },
  answers: { name: 'Savollarga javob', art: 'badge_helper', gesture: 'chatter', goal: (n) => `Chatda ${n} ta savolga javob bering` },
  posts: { name: 'Muallif', art: 'badge_journal', gesture: 'turn', goal: (n) => `${n} ta post yozing` },
  consults: { name: 'Konsultant', art: 'badge_doctor', gesture: 'swing', goal: (n) => `${n} ta konsultatsiya o'tkazing` },
  patients: { name: 'Bemorlar ishonchi', art: 'ic3d_partner', gesture: 'heartbeat', goal: (n) => `${n} nafar bemor bilan ishlang` },
  fast_reply: {
    name: 'Tezkor javob',
    art: 'ic3d_energy',
    gesture: 'flicker',
    goal: (n) => `${n} ta konsultatsiyada birinchi javobni 1 soat ichida bering`,
  },
  messages: { name: 'Suhbatdosh', art: 'ic3d_message', gesture: 'chatter', goal: (n) => `Konsultatsiyalarda ${n} ta xabar yozing` },
  rated: { name: 'Baholangan', art: 'ic3d_star', gesture: 'shine', goal: (n) => `Bemorlardan ${n} ta baho oling` },
  five_stars: { name: 'Besh yulduz', art: 'ic3d_trophy', gesture: 'bounce', goal: (n) => `${n} ta 5 yulduzli baho oling` },
  records: { name: 'Diqqatli shifokor', art: 'ic3d_record', gesture: 'hover', goal: (n) => `Bemorlarning ${n} ta yozuvini oching` },
  notes: { name: 'Eslatmalar', art: 'ic3d_notebook', gesture: 'turn', goal: (n) => `${n} nafar bemorga eslatma yozing` },
  quick_replies: { name: 'Tayyor javoblar', art: 'ic3d_bulb', gesture: 'shine', goal: (n) => `${n} ta tayyor javob saqlang` },
  thanked: { name: 'Minnatdorlik', art: 'badge_loved', gesture: 'heartbeat', goal: (n) => `Postlaringiz ${n} ta yoqtirish olsin` },
  tenure: { name: 'Sadoqat', art: 'badge_loyal', gesture: 'shine', goal: (n) => `Sadorada ${n} kun` },
}

export const BADGE_PRINCIPLE =
  "Nishonlar qilgan ishingiz uchun beriladi — javoblar, konsultatsiyalar, bemorlarga g'amxo'rlik. Daromad yoki narx uchun nishon yo'q."

/** Which metal a tier is struck in; a one-moment badge is rose gold. */
export type Metal = 'locked' | 'bronze' | 'silver' | 'gold' | 'rose'

export function metalOf(tier: number, maxTier: number): Metal {
  if (tier <= 0) return 'locked'
  if (maxTier === 1) return 'rose'
  if (tier === 1) return 'bronze'
  if (tier === 2) return 'silver'
  return 'gold'
}

export function tierName(tier: number, maxTier: number): string {
  if (maxTier === 1) return 'Maxsus'
  if (tier <= 1) return 'Bronza'
  if (tier === 2) return 'Kumush'
  return 'Oltin'
}

/** 0..1 toward the next tier, from the tier below it; 1 once the last is reached. */
export function nextProgress(badge: BadgeState): number {
  const next = badge.thresholds[badge.tier]
  if (next === undefined) return 1
  const previous = badge.thresholds[badge.tier - 1] ?? 0
  return Math.min(1, Math.max(0, (badge.progress - previous) / Math.max(1, next - previous)))
}

/**
 * One medal. Locked, it is a pale disc with the icon in grey and the way to its first
 * tier as an arc on the rim — and it holds still. [phase] desynchronises a board's
 * neighbours so they never pulse in step.
 */
export function Medal({
  badgeKey,
  tier,
  maxTier,
  size = 78,
  progress = 0,
  phase = 0,
  still = false,
  className,
  style,
}: {
  badgeKey: string
  tier: number
  maxTier: number
  size?: number
  progress?: number
  phase?: number
  still?: boolean
  className?: string
  style?: CSSProperties
}) {
  const info = BADGES[badgeKey]
  const metal = metalOf(tier, maxTier)
  const locked = metal === 'locked'
  return (
    <span
      className={`medal ${metal}${still || locked ? ' still' : ''}${className ? ` ${className}` : ''}`}
      style={{
        width: size,
        height: size,
        ['--phase' as string]: `${-(phase * 0.41) % 4}s`,
        ['--progress' as string]: `${Math.round(progress * 360)}deg`,
        ...style,
      }}
      aria-hidden="true"
    >
      <span className="medal-halo" />
      <span className="medal-rim" />
      <span className="medal-face" />
      {info && <Art name={info.art} size={Math.round(size * 0.6)} className={`medal-art g-${info.gesture}`} />}
      {!locked && <span className="medal-shine" />}
      {(metal === 'gold' || metal === 'rose') && (
        <>
          <span className="medal-spark s1" />
          <span className="medal-spark s2" />
          <span className="medal-spark s3" />
        </>
      )}
      {locked && <Art name="ic3d_lock" size={Math.round(size * 0.26)} className="medal-lock" />}
    </span>
  )
}

/** The tier's name on a strip of its own metal. */
export function TierPill({ tier, maxTier, label }: { tier: number; maxTier: number; label?: string }) {
  const metal = metalOf(tier, maxTier)
  return <span className={`tier-pill ${metal}`}>{label ?? (metal === 'locked' ? 'Hali ochilmagan' : tierName(tier, maxTier))}</span>
}

/**
 * A tier just reached, struck rather than shown: the medal spins in like a tossed coin,
 * lands with a ring of light, rays turn behind it and sparkles fly. One at a time; the
 * rest wait behind "Ajoyib!", with a way to close them all.
 */
export function BadgeUnlockOverlay({
  unlock,
  target,
  remaining,
  onNext,
  onSkipAll,
}: {
  unlock: BadgeUnlock
  /** The count this tier asked for, from the board's thresholds. */
  target: number
  remaining: number
  onNext: () => void
  onSkipAll: () => void
}) {
  const info = BADGES[unlock.key]
  const metal = metalOf(unlock.tier, unlock.maxTier)
  const [leaving, setLeaving] = useState(false)

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape' || event.key === 'Enter') {
        event.preventDefault()
        onNext()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onNext])

  // Re-keyed by the caller on key and tier, so the next in the queue plays from the start.
  useEffect(() => setLeaving(false), [unlock.key, unlock.tier])
  if (!info) return null

  return createPortal(
    <div className={`unlock-scrim${leaving ? ' leaving' : ''}`} role="dialog" aria-modal="true" aria-label={`Yangi nishon: ${info.name}`}>
      <div className={`unlock-card ${metal}`}>
        <div className="unlock-stage">
          <span className="unlock-rays" />
          <span className="unlock-glow" />
          <span className="unlock-flash" />
          {Array.from({ length: 10 }, (_, index) => (
            <span key={index} className="unlock-fly" style={{ ['--a' as string]: `${index * 36 + 17}deg` }} />
          ))}
          <Medal badgeKey={unlock.key} tier={unlock.tier} maxTier={unlock.maxTier} size={150} className="unlock-medal" />
        </div>
        <div className="unlock-kicker">YANGI NISHON!</div>
        <h2 className="unlock-name">{info.name}</h2>
        <TierPill tier={unlock.tier} maxTier={unlock.maxTier} label={`${tierName(unlock.tier, unlock.maxTier)} bosqich`} />
        <p className="unlock-goal">{info.goal(target)}</p>
        <button
          className="btn primary"
          type="button"
          autoFocus
          onClick={() => {
            setLeaving(true)
            onNext()
          }}
        >
          Ajoyib!
        </button>
        {remaining > 0 && (
          <div className="unlock-more">
            <span>Yana {remaining} ta nishon</span>
            <button className="btn ghost small" type="button" onClick={onSkipAll}>
              Hammasini yopish
            </button>
          </div>
        )}
      </div>
    </div>,
    document.body,
  )
}
