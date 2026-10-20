import { useEffect, useRef } from 'react'
import { limits } from '../api/limits'
import { mediaSummary, tileLabel, videoMeta } from '../api/media'
import type { DraftMedia } from '../api/mediaDraft'
import { Counter, Field, Modal, Spinner } from './ui'

/**
 * What she is about to send, before it goes: the picked photos and videos (any of them
 * can be taken out), a caption, and Send. More can be added while there is room —
 * with `chat_media` on, up to ten; with it off the one photo is all a message carries.
 */
export function MediaSendDialog({
  drafts,
  caption,
  onCaption,
  onRemove,
  onAdd,
  canAdd,
  adding,
  onSend,
  onClose,
}: {
  drafts: DraftMedia[]
  caption: string
  onCaption: (text: string) => void
  onRemove: (key: string) => void
  onAdd: () => void
  canAdd: boolean
  adding: boolean
  onSend: () => void
  onClose: () => void
}) {
  const box = useRef<HTMLTextAreaElement>(null)
  const tooLong = caption.trim().length > limits.messageMax
  const ready = drafts.length > 0 && !tooLong && !adding

  // The caption takes the focus once the dialog has put it on its first button.
  useEffect(() => {
    const timer = window.setTimeout(() => box.current?.focus(), 0)
    return () => window.clearTimeout(timer)
  }, [])

  function send() {
    if (ready) onSend()
  }

  return (
    <Modal title={`Yuborish: ${mediaSummary(drafts).replace(/^\S+\s/, '')}`} wide onClose={onClose}>
      <ul className="media-pick" aria-label="Tanlangan rasm va videolar">
        {drafts.map((draft, index) => (
          <li key={draft.key} className="media-pick-item">
            {draft.previewUrl ? <img src={draft.previewUrl} alt="" draggable={false} /> : <span className="album-blank" />}
            {draft.kind === 'video' && (
              <>
                <span className="album-play" aria-hidden="true">
                  ▶
                </span>
                <span className="album-meta">{videoMeta({ durationMs: draft.durationMs, sizeBytes: draft.blob.size })}</span>
              </>
            )}
            <button
              type="button"
              className="media-pick-remove"
              aria-label={`Olib tashlash: ${tileLabel(draft, index, drafts.length)}`}
              title="Olib tashlash"
              onClick={() => onRemove(draft.key)}
            >
              ✕
            </button>
          </li>
        ))}
        {canAdd && (
          <li>
            <button type="button" className="media-pick-add" onClick={onAdd} disabled={adding} aria-label="Yana qo'shish" title="Yana qo'shish">
              {adding ? <Spinner /> : '＋'}
            </button>
          </li>
        )}
      </ul>
      <Field label="Izoh (ixtiyoriy)" error={tooLong ? `Eng ko'pi ${limits.messageMax} belgi` : null} hint={<Counter length={caption.length} max={limits.messageMax} />}>
        <textarea
          ref={box}
          rows={2}
          value={caption}
          maxLength={limits.messageMax}
          placeholder="Rasm yoki videoga izoh…"
          onChange={(event) => onCaption(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
              event.preventDefault()
              send()
            }
          }}
        />
      </Field>
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button type="button" className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button type="button" className="btn primary" onClick={send} disabled={!ready}>
          Yuborish
        </button>
      </div>
    </Modal>
  )
}
