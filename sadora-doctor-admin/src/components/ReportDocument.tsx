import { useState } from 'react'
import { requestBlob } from '../api/client'
import type { MessageDocument } from '../api/types'

/** "240 KB", "1,4 MB": under a megabyte in whole KB, above it in MB with one decimal. */
export function formatFileSize(bytes: number): string {
  const kilo = 1024
  if (bytes < kilo * kilo) return `${Math.max(1, Math.ceil(bytes / kilo))} KB`
  const tenths = Math.round((bytes * 10) / (kilo * kilo))
  return tenths % 10 === 0 ? `${tenths / 10} MB` : `${Math.floor(tenths / 10)},${tenths % 10} MB`
}

export function reportDocumentPath(conversationId: string, messageId: string): string {
  return `/v1/community/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/document`
}

/** How long an opened PDF's object URL is kept for the tab that shows it. */
const OBJECT_URL_KEPT_MS = 60_000

/**
 * The PDF report a patient sent: its name and size, and a button that fetches it with
 * the doctor's token and opens it in a new tab — or downloads it where a tab could not
 * be opened.
 */
export function ReportCard({
  conversationId,
  messageId,
  document: file,
}: {
  conversationId: string
  messageId: string
  document: MessageDocument
}) {
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)

  async function open() {
    setBusy(true)
    setFailed(false)
    // Opened now, inside the click, so the browser does not block it as a popup; the
    // file is put into it once it arrives.
    const tab = window.open('', '_blank')
    try {
      const blob = await requestBlob(reportDocumentPath(conversationId, messageId))
      const url = URL.createObjectURL(blob.type ? blob : new Blob([blob], { type: file.mimeType }))
      if (tab) {
        tab.location.href = url
      } else {
        const link = document.createElement('a')
        link.href = url
        link.download = file.name
        link.click()
      }
      window.setTimeout(() => URL.revokeObjectURL(url), OBJECT_URL_KEPT_MS)
    } catch {
      tab?.close()
      setFailed(true)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="rx-card report-card">
      <div className="rx-head">
        <span className="rx-glyph" aria-hidden="true">
          📄
        </span>
        <span>
          <b>Hisobot (PDF)</b>
          <span className="faint">
            {file.name} · {formatFileSize(file.sizeBytes)}
          </span>
        </span>
      </div>
      <div className="rx-foot">
        {failed ? (
          <span className="rx-status lab-warn" role="alert">
            Faylni ochib bo'lmadi
          </span>
        ) : (
          <span />
        )}
        <button type="button" className="btn small" onClick={open} disabled={busy}>
          {busy ? 'Yuklanmoqda…' : 'PDF ochish'}
        </button>
      </div>
    </div>
  )
}
