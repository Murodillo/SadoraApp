import { useState } from 'react'
import { useAuthorizedImage } from '../api/photos'
import type { LabFlag, LabPhoto, LabReport, LabValue } from '../api/types'
import { Modal } from './ui'

/*
 * A patient's lab sheet ("tahlil natijalari"): a card in the thread, a table of its rows,
 * and the pages she photographed. Whether a row is low or high is the laboratory's own
 * printed range, worked out by the server; a row without a range has no flag at all,
 * which is drawn as nothing rather than as "normal".
 */

/** The sheet's page `page` (0-based), as an API path to fetch with her token. */
export type LabPhotoPath = (page: number) => string

const encode = encodeURIComponent

/** A page of the sheet on a lab line in the thread. */
export function labLinePhotoPath(conversationId: string, messageId: string): LabPhotoPath {
  return (page) => `/v1/community/conversations/${encode(conversationId)}/messages/${encode(messageId)}/lab-photos/${page}`
}

/** A page of a sheet inside an attached record; served only while the consultation is open. */
export function labRecordPhotoPath(conversationId: string, recordMessageId: string, reportId: string): LabPhotoPath {
  return (page) =>
    `/v1/community/conversations/${encode(conversationId)}/messages/${encode(recordMessageId)}/record/labs/${encode(reportId)}/photos/${page}`
}

/** `2026-10-01` -> `01.10.2026`, read from the string so no timezone can move the day. */
export function formatLabDate(value: string): string {
  const [year, month, day] = value.split('-')
  return year && month && day ? `${day.padStart(2, '0')}.${month.padStart(2, '0')}.${year}` : value
}

function number(value: number): string {
  return String(Math.round(value * 1000) / 1000)
}

/** "120–150", "< 5", "> 30", or the range as printed. */
export function labRange(row: LabValue): string {
  if (row.refLow != null && row.refHigh != null) return `${number(row.refLow)}–${number(row.refHigh)}`
  if (row.refHigh != null) return `< ${number(row.refHigh)}`
  if (row.refLow != null) return `> ${number(row.refLow)}`
  return row.refText?.trim() || '—'
}

function labResult(row: LabValue): string {
  const shown = row.value != null ? number(row.value) : row.valueText?.trim() || '—'
  return [shown, row.unit].filter(Boolean).join(' ')
}

const isOut = (flag: LabFlag | null | undefined) => flag === 'low' || flag === 'high'

export function flaggedCount(report: LabReport): number {
  return (report.values ?? []).filter((row) => isOut(row.flag)).length
}

/** "8 ta ko'rsatkich · 2 tasi normadan tashqarida". */
export function labCounts(report: LabReport): string {
  const total = report.values?.length ?? 0
  const out = flaggedCount(report)
  return [`${total} ta ko'rsatkich`, out > 0 && `${out} tasi normadan tashqarida`].filter(Boolean).join(' · ')
}

function labTitleLine(report: LabReport): string {
  return [formatLabDate(report.takenOn), report.place?.trim()].filter(Boolean).join(' · ')
}

function FlagBadge({ flag }: { flag: LabFlag | null | undefined }) {
  if (flag === 'low') return <span className="badge warn">Past</span>
  if (flag === 'high') return <span className="badge warn">Yuqori</span>
  if (flag === 'normal') return <span className="faint lab-normal">Normada</span>
  return null
}

/**
 * Every row of a sheet. `compact` (the patient panel) keeps a known marker's explanation
 * in the name's tooltip; the full view prints it under the name as well.
 */
export function LabTable({ report, compact = false }: { report: LabReport; compact?: boolean }) {
  const rows = report.values ?? []
  if (rows.length === 0) return <p className="faint lab-empty">Ko'rsatkichlar kiritilmagan</p>
  return (
    <div className="table-wrap">
      <table className={`lab-table${compact ? ' compact' : ''}`}>
        <thead>
          <tr>
            <th>Ko'rsatkich</th>
            <th>Natija</th>
            <th>Norma</th>
            <th aria-label="Holat" />
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id} className={isOut(row.flag) ? 'lab-out' : undefined}>
              <td className="lab-name" title={row.about ?? undefined}>
                <b>{row.name}</b>
                {!compact && row.about && <span className="faint lab-about">{row.about}</span>}
              </td>
              <td className="lab-result">{labResult(row)}</td>
              <td className="lab-range">{labRange(row)}</td>
              <td className="lab-flag">
                <FlagBadge flag={row.flag} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function LabThumb({ path, photo, page, onOpen }: { path: string; photo: LabPhoto; page: number; onOpen: () => void }) {
  const image = useAuthorizedImage(path)
  return (
    <button
      type="button"
      className="msg-photo lab-thumb"
      style={{ aspectRatio: `${photo.width || 3} / ${photo.height || 4}` }}
      disabled={!image.url}
      onClick={onOpen}
      aria-label={`${page + 1}-sahifani kattalashtirish`}
    >
      {image.url ? (
        <img src={image.url} alt={`Tahlil varag'i, ${page + 1}-sahifa`} />
      ) : image.failed ? (
        <span className="faint">Rasm ochilmadi</span>
      ) : (
        <span className="skeleton" />
      )}
    </button>
  )
}

/** The pages she photographed, as thumbnails. Nothing is drawn for a sheet she typed in. */
export function LabPhotos({ report, photoPath, onOpen }: { report: LabReport; photoPath: LabPhotoPath; onOpen: (page: number) => void }) {
  const photos = report.photos ?? []
  if (photos.length === 0) return null
  return (
    <div className="lab-thumbs" aria-label="Tahlil varag'i rasmlari">
      {photos.map((photo, page) => (
        <LabThumb key={page} path={photoPath(page)} photo={photo} page={page} onOpen={() => onOpen(page)} />
      ))}
    </div>
  )
}

function LabPage({ path, page, total }: { path: string; page: number; total: number }) {
  const image = useAuthorizedImage(path)
  if (image.url) return <img className="msg-full" src={image.url} alt={`Tahlil varag'i, ${page + 1}-sahifa`} />
  if (image.failed) return <div className="notice">Rasm ochilmadi</div>
  return <div className="skeleton lab-page-wait" aria-label={`${page + 1} / ${total}`} />
}

/**
 * A sheet opened: the details, every row, and the pages. A page opens larger in place
 * of the table, with a way back, rather than in a second dialog over this one.
 */
export function LabReportDialog({
  report,
  photoPath,
  initialPage = null,
  onClose,
}: {
  report: LabReport
  photoPath?: LabPhotoPath
  initialPage?: number | null
  onClose: () => void
}) {
  const [page, setPage] = useState<number | null>(photoPath ? initialPage : null)
  const pages = report.photos?.length ?? 0

  return (
    <Modal title="Tahlil natijalari" wide onClose={onClose}>
      <div className="lab-dialog">
        <div className="lab-meta">
          <b>{labTitleLine(report)}</b>
          <span className="faint">
            {labCounts(report)}
            {report.scanned ? ' · rasmdan o‘qilgan, bemor tekshirgan' : ''}
          </span>
        </div>
        {page != null && photoPath ? (
          <>
            <div className="row lab-pager">
              <button type="button" className="btn ghost small" onClick={() => setPage(null)}>
                ← Jadvalga qaytish
              </button>
              {pages > 1 && (
                <span className="row">
                  <button type="button" className="btn ghost small" disabled={page === 0} onClick={() => setPage(page - 1)} aria-label="Oldingi sahifa">
                    ‹
                  </button>
                  <span className="faint">
                    {page + 1} / {pages}
                  </span>
                  <button
                    type="button"
                    className="btn ghost small"
                    disabled={page >= pages - 1}
                    onClick={() => setPage(page + 1)}
                    aria-label="Keyingi sahifa"
                  >
                    ›
                  </button>
                </span>
              )}
            </div>
            <LabPage key={page} path={photoPath(page)} page={page} total={pages} />
          </>
        ) : (
          <>
            {report.note && <p className="rx-note">{report.note}</p>}
            <LabTable report={report} />
            {photoPath && <LabPhotos report={report} photoPath={photoPath} onOpen={setPage} />}
          </>
        )}
        <span className="faint rx-disclaimer">Me'yorlar laboratoriyaning o'z varag'idan olingan · bu tashxis emas</span>
      </div>
    </Modal>
  )
}

/** A lab line in the thread, as a card that opens the whole sheet. */
export function LabCard({ report, onOpen }: { report: LabReport; onOpen: () => void }) {
  const out = flaggedCount(report)
  return (
    <div className="rx-card lab-card">
      <div className="rx-head">
        <span className="rx-glyph" aria-hidden="true">
          🧪
        </span>
        <span>
          <b>Tahlil natijalari</b>
          <span className="faint">{labTitleLine(report)}</span>
        </span>
      </div>
      <div className="rx-foot">
        <span className={`rx-status${out > 0 ? ' lab-warn' : ''}`}>{labCounts(report)}</span>
        <button type="button" className="btn small" onClick={onOpen}>
          Ochish
        </button>
      </div>
    </div>
  )
}

/**
 * The sheets inside an attached record, newest first: each one folds open to its table
 * and its pages, the newest already open. Pages come from the record's own endpoint, so
 * they stop opening when the consultation closes, like the record itself.
 */
export function RecordLabs({ labs, photoPath }: { labs: LabReport[]; photoPath?: (reportId: string) => LabPhotoPath }) {
  const [viewing, setViewing] = useState<{ report: LabReport; page: number } | null>(null)
  return (
    <div className="lab-sheets">
      {labs.map((report, index) => {
        const out = flaggedCount(report)
        return (
          <details key={report.id} className="lab-sheet" open={index === 0}>
            <summary>
              <b>{labTitleLine(report)}</b>
              <span className={out > 0 ? 'lab-warn' : 'faint'}>{labCounts(report)}</span>
            </summary>
            {report.note && <p className="faint lab-note">{report.note}</p>}
            <LabTable report={report} compact />
            {photoPath && (
              <LabPhotos report={report} photoPath={photoPath(report.id)} onOpen={(page) => setViewing({ report, page })} />
            )}
          </details>
        )
      })}
      {viewing && photoPath && (
        <LabReportDialog
          report={viewing.report}
          photoPath={photoPath(viewing.report.id)}
          initialPage={viewing.page}
          onClose={() => setViewing(null)}
        />
      )}
    </div>
  )
}
