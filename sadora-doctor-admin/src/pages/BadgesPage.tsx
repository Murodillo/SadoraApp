import { useState } from 'react'
import { useBadges } from '../api/hooks'
import type { BadgeState } from '../api/types'
import { Art } from '../components/art'
import { BADGE_PRINCIPLE, BADGES, Medal, nextProgress, tierName, TierPill } from '../components/badges'
import { Card, ErrorNotice, Loading, Modal, useCountUp } from '../components/ui'

/**
 * Her badges, as the app's page has them: the laurel with how many tiers of how many and
 * a bar, what she has earned (furthest along first), and what is still ahead. A badge
 * opens its ladder — every tier, what it asks, and how far she is toward the next.
 */
export function BadgesPage() {
  const board = useBadges()
  const [open, setOpen] = useState<BadgeState | null>(null)

  if (board.error) return <ErrorNotice error={board.error} onRetry={() => void board.refetch()} />
  if (!board.data) {
    return (
      <div className="page">
        <Loading rows={3} height={120} />
      </div>
    )
  }

  const known = board.data.badges.filter((badge) => BADGES[badge.key])
  const earned = known
    .filter((badge) => badge.tier > 0)
    .sort((a, b) => b.tier / b.thresholds.length - a.tier / a.thresholds.length || (b.earnedAt ?? '').localeCompare(a.earnedAt ?? ''))
  const locked = known.filter((badge) => badge.tier === 0)
  const reached = known.reduce((sum, badge) => sum + badge.tier, 0)
  const total = known.reduce((sum, badge) => sum + badge.thresholds.length, 0)

  return (
    <div className="page">
      <div className="grid">
        <BadgesHero reached={reached} total={total} />
        {earned.length > 0 && <BadgeGrid title="Olingan" badges={earned} onOpen={setOpen} />}
        {locked.length > 0 && <BadgeGrid title="Keyingi maqsadlar" badges={locked} onOpen={setOpen} offset={earned.length} />}
        <p className="faint badge-principle">{BADGE_PRINCIPLE}</p>
      </div>
      {open && <BadgeDetail badge={open} onClose={() => setOpen(null)} />}
    </div>
  )
}

function BadgesHero({ reached, total }: { reached: number; total: number }) {
  const shown = useCountUp(reached)
  return (
    <section className="card badges-hero">
      <Art name="badge_laurel" size={72} />
      <div className="badges-hero-text">
        <div className="faint">Qilgan ishingiz uchun — daromad uchun emas</div>
        <div className="badges-count">
          <span className="value">{shown}</span>
          <span className="faint"> / {total}</span>
        </div>
        <div className="clay-track" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={reached} aria-label="Olingan bosqichlar">
          <span className="clay-fill brand" style={{ width: `${total ? (reached / total) * 100 : 0}%` }} />
        </div>
      </div>
    </section>
  )
}

function BadgeGrid({
  title,
  badges,
  onOpen,
  offset = 0,
}: {
  title: string
  badges: BadgeState[]
  onOpen: (badge: BadgeState) => void
  offset?: number
}) {
  return (
    <Card title={title} action={<span className="faint">{badges.length}</span>}>
      <ul className="badge-grid">
        {badges.map((badge, index) => {
          const info = BADGES[badge.key]
          if (!info) return null
          const max = badge.thresholds.length
          return (
            <li key={badge.key} style={{ animationDelay: `${Math.min(index + offset, 12) * 40}ms` }}>
              <button
                type="button"
                className="badge-cell"
                onClick={() => onOpen(badge)}
                aria-label={`${info.name}, ${badge.tier > 0 ? tierName(badge.tier, max) : 'hali ochilmagan'}`}
              >
                <Medal
                  badgeKey={badge.key}
                  tier={badge.tier}
                  maxTier={max}
                  progress={badge.tier === 0 ? nextProgress(badge) : 0}
                  phase={index + offset}
                />
                <span className="badge-name">{info.name}</span>
                <span className="tier-pips" aria-hidden="true">
                  {badge.thresholds.map((_, tier) => (
                    <span key={tier} className={tier < badge.tier ? `on ${metalClass(badge.tier, max)}` : ''} />
                  ))}
                </span>
              </button>
            </li>
          )
        })}
      </ul>
    </Card>
  )
}

function metalClass(tier: number, max: number): string {
  if (max === 1) return 'rose'
  return tier === 1 ? 'bronze' : tier === 2 ? 'silver' : 'gold'
}

/** One badge up close: the medal large, its ladder, and how far to the next tier. */
function BadgeDetail({ badge, onClose }: { badge: BadgeState; onClose: () => void }) {
  const info = BADGES[badge.key]
  const max = badge.thresholds.length
  const next = badge.thresholds[badge.tier]
  if (!info) return null
  return (
    <Modal title={info.name} onClose={onClose}>
      <div className="badge-detail">
        <Medal badgeKey={badge.key} tier={badge.tier} maxTier={max} size={140} progress={badge.tier === 0 ? nextProgress(badge) : 0} />
        <TierPill tier={badge.tier} maxTier={max} />
      </div>
      <ul className="badge-ladder">
        {badge.thresholds.map((target, index) => {
          const tier = index + 1
          const done = badge.tier >= tier
          return (
            <li key={tier} className={done ? 'done' : ''}>
              <Medal badgeKey={badge.key} tier={done ? tier : 0} maxTier={max} size={40} still />
              <div className="badge-ladder-text">
                <strong>{tierName(tier, max)}</strong>
                <span className="faint">{info.goal(target)}</span>
              </div>
              {done ? (
                <span className="badge-done" aria-label="Olingan">
                  ✓
                </span>
              ) : (
                <span className="faint">
                  {Math.min(badge.progress, target)} / {target}
                </span>
              )}
            </li>
          )
        })}
      </ul>
      {next !== undefined ? (
        <div className="badge-next">
          <div>
            Keyingisi: {tierName(badge.tier + 1, max)} — {badge.progress} / {next}
          </div>
          <div className="clay-track">
            <span className="clay-fill brand" style={{ width: `${nextProgress(badge) * 100}%` }} />
          </div>
        </div>
      ) : (
        <p className="badge-all-done">Barcha bosqichlar olindi!</p>
      )}
      <p className="faint badge-principle">{BADGE_PRINCIPLE}</p>
    </Modal>
  )
}
