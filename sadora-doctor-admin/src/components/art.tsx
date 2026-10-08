import type { CSSProperties } from 'react'

/*
 * The colour clay icons the apps are drawn in (`public/art`, cut from the same Gemini
 * sheets as the client's `ic3d_*`), so the panel reads as the same family as the phone.
 * They carry their own colour: nothing tints them, and they are decoration — the text
 * beside each one says what it is.
 */

export type ArtName =
  | 'ic3d_chats'
  | 'ic3d_message'
  | 'ic3d_notebook'
  | 'ic3d_bulb'
  | 'ic3d_insights'
  | 'ic3d_gem'
  | 'ic3d_calendar'
  | 'ic3d_profile'
  | 'ic3d_empty'
  | 'ic3d_star'
  | 'ic3d_bell'
  | 'ic3d_clock'
  | 'ic3d_lock'
  | 'ic3d_trophy'
  | 'ic3d_heart'
  | 'ic3d_camera'
  | 'ic3d_partner'
  | 'ic3d_energy'
  | 'ic3d_record'
  | 'art_shield'
  | 'badge_laurel'
  | 'badge_helper'
  | 'badge_journal'
  | 'badge_doctor'
  | 'badge_loved'
  | 'badge_loyal'

export function artUrl(name: ArtName): string {
  return `${import.meta.env.BASE_URL}art/${name}.png`
}

export function Art({
  name,
  size = 24,
  className,
  style,
}: {
  name: ArtName
  size?: number
  className?: string
  style?: CSSProperties
}) {
  return (
    <img
      src={artUrl(name)}
      width={size}
      height={size}
      alt=""
      aria-hidden="true"
      draggable={false}
      className={`art${className ? ` ${className}` : ''}`}
      style={style}
    />
  )
}

/** [Art] on a washed-out disc of [tint], the way the app leads a card row. */
export function ArtTile({ name, size = 40, tint = 'var(--primary)' }: { name: ArtName; size?: number; tint?: string }) {
  return (
    <span className="art-tile" style={{ width: size, height: size, ['--tile' as string]: tint }} aria-hidden="true">
      <Art name={name} size={Math.round(size * 0.74)} />
    </span>
  )
}
