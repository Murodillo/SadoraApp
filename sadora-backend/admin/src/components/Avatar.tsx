import { useState } from 'react'
import { useDoctorPhoto } from '../api/hooks'
import { initialsOf } from './initials'
import { Modal, useObjectUrl } from './ui'

/**
 * A doctor's face: her photo when she has one, her initials otherwise — while it loads,
 * when she has none, and when it cannot be fetched. The photo endpoint needs the admin
 * bearer header, so the bytes come through the API client as a blob (cached by the
 * versioned URL) and are shown through an object URL revoked when this unmounts.
 *
 * `zoomable` makes the photo a button that opens it full size.
 */
export function Avatar({
  name,
  photoUrl,
  size = 32,
  zoomable = false,
}: {
  name: string
  photoUrl?: string | null
  size?: number
  zoomable?: boolean
}) {
  const photo = useDoctorPhoto(photoUrl)
  const url = useObjectUrl(photo.data)
  const [open, setOpen] = useState(false)
  const style = { width: size, height: size, fontSize: Math.max(11, Math.round(size * 0.38)) }

  const face = url ? (
    <img src={url} alt={name} />
  ) : (
    <span aria-hidden="true">{initialsOf(name)}</span>
  )

  if (!zoomable || !url) {
    return (
      <span className="avatar" style={style} role={url ? undefined : 'img'} aria-label={url ? undefined : name} title={name}>
        {face}
      </span>
    )
  }

  return (
    <>
      <button
        type="button"
        className="avatar zoomable"
        style={style}
        onClick={(event) => {
          event.stopPropagation()
          setOpen(true)
        }}
        aria-label={`${name} — rasmni kattalashtirish`}
      >
        {face}
      </button>
      {open && (
        <Modal title={name} onClose={() => setOpen(false)}>
          <img src={url} alt={name} className="doc-full" />
        </Modal>
      )}
    </>
  )
}
