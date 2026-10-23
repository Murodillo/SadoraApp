import { useEffect, useMemo, useRef, useState } from 'react'
import type { ClipboardEvent, DragEvent, MouseEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { requestBlob } from '../api/client'
import { useToast } from '../components/toast'
import { Spinner } from '../components/ui'
import { uploadPmImage } from './api'
import { imageMarkdown, renderMarkdown } from './markdown'

/** Object URLs of pictures already fetched this session, so a re-render does not fetch again. */
const pictures = new Map<string, Promise<string>>()

function pictureUrl(id: string): Promise<string> {
  let url = pictures.get(id)
  if (!url) {
    url = requestBlob(`/v1/admin/pm/images/${encodeURIComponent(id)}`).then((blob) => URL.createObjectURL(blob))
    url.catch(() => pictures.delete(id))
    pictures.set(id, url)
  }
  return url
}

/**
 * Rendered Markdown. A link to a task or a doc stays inside the panel; a pasted picture is
 * fetched with the operator's token, since the browser cannot send it on an `<img src>`.
 */
export function Markdown({ text, empty = 'Matn yo‘q' }: { text: string; empty?: string }) {
  const html = useMemo(() => renderMarkdown(text), [text])
  const box = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

  useEffect(() => {
    let cancelled = false
    box.current?.querySelectorAll<HTMLImageElement>('img[data-pm-image]').forEach((image) => {
      const id = image.dataset.pmImage!
      pictureUrl(id)
        .then((url) => {
          if (!cancelled) image.src = url
        })
        .catch(() => {
          if (!cancelled) image.alt = `${image.alt} (rasm ochilmadi)`
        })
    })
    return () => {
      cancelled = true
    }
  }, [html])

  function onClick(event: MouseEvent<HTMLDivElement>) {
    const anchor = (event.target as HTMLElement).closest('a')
    const href = anchor?.getAttribute('href')
    if (href?.startsWith('/') && !event.metaKey && !event.ctrlKey) {
      event.preventDefault()
      navigate(href)
    }
  }

  if (!text.trim()) return <p className="faint">{empty}</p>
  // eslint-disable-next-line react/no-danger -- sanitised by renderMarkdown
  return <div ref={box} className="pm-markdown" onClick={onClick} dangerouslySetInnerHTML={{ __html: html }} />
}

/**
 * A Markdown field with its preview beside it (stacked on a narrow screen). A picture
 * pasted or dropped into the text is uploaded and its Markdown put where the cursor was.
 */
export function MarkdownEditor({
  value,
  onChange,
  rows = 12,
  placeholder,
  label,
  autoFocus,
}: {
  value: string
  onChange: (next: string) => void
  rows?: number
  placeholder?: string
  label: string
  autoFocus?: boolean
}) {
  const area = useRef<HTMLTextAreaElement>(null)
  const [uploading, setUploading] = useState(0)
  const [view, setView] = useState<'write' | 'split' | 'preview'>('split')
  const { notify } = useToast()

  async function insertPictures(files: File[]) {
    const images = files.filter((file) => file.type.startsWith('image/'))
    if (!images.length) return false
    const at = area.current?.selectionStart ?? value.length
    setUploading((count) => count + images.length)
    try {
      const parts: string[] = []
      for (const file of images) {
        const ref = await uploadPmImage(file)
        parts.push(imageMarkdown(ref.id, file.name.replace(/\.[a-z0-9]+$/i, '') || 'rasm'))
      }
      const insertion = parts.join('\n')
      const current = area.current?.value ?? value
      onChange(current.slice(0, at) + insertion + current.slice(at))
    } catch (error) {
      notify(error instanceof Error ? error.message : "Rasm yuklanmadi", 'error')
    } finally {
      setUploading((count) => count - images.length)
    }
    return true
  }

  function onPaste(event: ClipboardEvent<HTMLTextAreaElement>) {
    const files = Array.from(event.clipboardData.files)
    if (files.some((file) => file.type.startsWith('image/'))) {
      event.preventDefault()
      void insertPictures(files)
    }
  }

  function onDrop(event: DragEvent<HTMLTextAreaElement>) {
    const files = Array.from(event.dataTransfer.files)
    if (files.length) {
      event.preventDefault()
      void insertPictures(files)
    }
  }

  return (
    <div className="pm-editor">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <span className="pm-editor-label">{label}</span>
        <div className="row" style={{ gap: 8 }}>
          {uploading > 0 && (
            <span className="faint row" style={{ gap: 6 }}>
              <Spinner /> Rasm yuklanmoqda…
            </span>
          )}
          <div className="segmented" role="group" aria-label="Ko'rinish">
            {(
              [
                ['write', 'Yozish'],
                ['split', 'Ikkalasi'],
                ['preview', "Ko'rish"],
              ] as const
            ).map(([key, text]) => (
              <button key={key} type="button" className={view === key ? 'active' : ''} aria-pressed={view === key} onClick={() => setView(key)}>
                {text}
              </button>
            ))}
          </div>
        </div>
      </div>
      <div className={`pm-editor-body ${view}`}>
        {view !== 'preview' && (
          <textarea
            ref={area}
            aria-label={label}
            value={value}
            rows={rows}
            placeholder={placeholder ?? 'Markdown: # sarlavha, **qalin**, - ro‘yxat, `kod`. SAD-12 — taskka havola. Rasmni shu yerga tashlang yoki paste qiling.'}
            onChange={(event) => onChange(event.target.value)}
            onPaste={onPaste}
            onDrop={onDrop}
            onDragOver={(event) => event.preventDefault()}
            autoFocus={autoFocus}
          />
        )}
        {view !== 'write' && (
          <div className="pm-editor-preview">
            <Markdown text={value} empty="Ko‘rinish shu yerda chiqadi" />
          </div>
        )}
      </div>
    </div>
  )
}
