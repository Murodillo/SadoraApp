import { useState } from 'react'
import { useReportContext, useReportImage, useResolveReport, useRestrictSender } from '../api/hooks'
import { limits } from '../api/limits'
import type { ModerationReport, ReportContextMessage, ReportContextView } from '../api/types'
import { useToast } from '../components/toast'
import { ErrorNotice, Field, formatDateTime, Loading, Modal, Spinner, useObjectUrl } from '../components/ui'
import {
  canOpenImage,
  contextLineAuthor,
  contextLineText,
  conversationLabel,
  isDoctorLine,
  PRIVACY_LINE,
  reasonLabels,
  restrictDone,
  restrictDurations,
} from './messageReports'

/**
 * The reported private message with the lines around it, as chat bubbles: the reported
 * side on the left, the one who reported on the right. Opening this dialog is what the
 * server audits, so it fetches once and forgets the thread when it closes.
 *
 * `moderate` is Owner/Admin; Support only reads.
 */
export function ReportContextModal({
  report,
  moderate,
  onClose,
}: {
  report: ModerationReport
  moderate: boolean
  onClose: () => void
}) {
  const context = useReportContext(report.id)
  const resolve = useResolveReport()
  const { notify } = useToast()
  const [restricting, setRestricting] = useState(false)
  const view = context.data
  const open = !report.resolvedAt

  return (
    <Modal title="Xabar konteksti" onClose={onClose} wide>
      <div className="notice privacy">{PRIVACY_LINE}</div>

      {context.isLoading ? (
        <Loading rows={5} />
      ) : context.error || !view ? (
        <ErrorNotice error={context.error ?? 'Topilmadi'} />
      ) : (
        <>
          <div className="row" style={{ flexWrap: 'wrap', gap: 8 }}>
            <span className={`badge ${view.consultation ? 'consult' : 'premium'}`}>{conversationLabel(view)}</span>
            <span className="badge warn">{reasonLabels[report.reason] ?? report.reason}</span>
            <span className="faint">
              Shikoyat qilingan: <strong className="muted">{view.reported}</strong> · Shikoyat qilgan:{' '}
              <strong className="muted">{view.reporter}</strong>
            </span>
          </div>
          {report.note && <p className="muted" style={{ margin: 0 }}>Izoh: {report.note}</p>}
          <ContextThread view={view} reportId={report.id} />
        </>
      )}

      {resolve.error && <ErrorNotice error={resolve.error} />}

      {moderate && restricting ? (
        <RestrictSenderForm reportId={report.id} onDone={() => setRestricting(false)} onCancel={() => setRestricting(false)} />
      ) : (
        moderate && (
          <div className="row" style={{ justifyContent: 'flex-end', flexWrap: 'wrap' }}>
            <button className="btn ghost" onClick={() => setRestricting(true)} title="Yuboruvchi yozishdan cheklanadi; kimligi ko'rinmaydi">
              Yuboruvchini cheklash
            </button>
            {open && (
              <>
                <button
                  className="btn"
                  disabled={resolve.isPending}
                  onClick={() =>
                    resolve.mutate(
                      { id: report.id, action: 'dismiss' },
                      {
                        onSuccess: () => {
                          notify('Shikoyat rad etildi')
                          onClose()
                        },
                      },
                    )
                  }
                >
                  Rad etish
                </button>
                <button
                  className="btn danger"
                  disabled={resolve.isPending}
                  onClick={() =>
                    resolve.mutate(
                      { id: report.id, action: 'hide', reason: `Shikoyat: ${reasonLabels[report.reason]}` },
                      {
                        onSuccess: () => {
                          notify('Xabar yashirildi, shikoyat yopildi', 'info')
                          onClose()
                        },
                      },
                    )
                  }
                >
                  {resolve.isPending && <Spinner />}
                  Yashirish
                </button>
              </>
            )}
          </div>
        )
      )}
    </Modal>
  )
}

function ContextThread({ view, reportId }: { view: ReportContextView; reportId: string }) {
  if (!view.messages.length) return <p className="faint" style={{ margin: 0 }}>Xabarlar topilmadi.</p>
  return (
    <ol className="chat-context" aria-label="Suhbat parchasi">
      {view.messages.map((line) => (
        <ContextBubble key={line.id} view={view} line={line} reportId={reportId} />
      ))}
    </ol>
  )
}

function ContextBubble({ view, line, reportId }: { view: ReportContextView; line: ReportContextMessage; reportId: string }) {
  const classes = [
    'chat-line',
    line.fromReported ? 'from-reported' : 'from-reporter',
    isDoctorLine(view, line) ? 'doctor' : '',
    line.reported ? 'target' : '',
    line.hidden ? 'hidden' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <li className={classes} aria-current={line.reported ? 'true' : undefined}>
      <div className="chat-meta">
        <strong>{contextLineAuthor(view, line)}</strong>
        <span className="faint">{formatDateTime(line.createdAt)}</span>
      </div>
      <div className={`chat-bubble${line.kind !== 'text' ? ' attachment' : ''}`}>{contextLineText(line)}</div>
      {(line.reported || line.hidden) && (
        <div className="row" style={{ gap: 6 }}>
          {line.reported && <span className="badge danger">Shikoyat qilingan xabar</span>}
          {line.hidden && <span className="badge free">Yashirilgan</span>}
        </div>
      )}
      {canOpenImage(line) && <ReportedImage reportId={reportId} />}
    </li>
  )
}

/** The reported photo, fetched only on request — that fetch is audited as well. */
function ReportedImage({ reportId }: { reportId: string }) {
  const image = useReportImage()
  const url = useObjectUrl(image.data)
  if (url) return <img src={url} alt="Shikoyat qilingan rasm" className="chat-image" />
  return (
    <div className="row" style={{ gap: 8 }}>
      <button className="btn small" disabled={image.isPending} onClick={() => image.mutate(reportId)}>
        {image.isPending && <Spinner />}
        Rasmni ochish
      </button>
      {image.error && <span className="faint">Yuklab bo'lmadi: {image.error.message}</span>}
    </div>
  )
}

/**
 * Restricts whoever sent the reported message from writing. The report reaches the
 * sender; the moderator never learns who she is.
 */
export function RestrictSenderForm({ reportId, onDone, onCancel }: { reportId: string; onDone: () => void; onCancel: () => void }) {
  const restrict = useRestrictSender()
  const { notify } = useToast()
  const [reason, setReason] = useState('')
  const [duration, setDuration] = useState('7')
  const days = restrictDurations.find((option) => option.value === duration)?.days ?? null

  return (
    <div className="grid" style={{ gap: 12 }}>
      <p className="faint" style={{ margin: 0 }}>
        Cheklov yuboruvchiga xabar, post va izoh yozishni yopadi; o'qish ochiq qoladi. Siz uning hisobini ko'rmaysiz.
      </p>
      <Field label="Sabab (majburiy)">
        <input value={reason} onChange={(event) => setReason(event.target.value)} maxLength={limits.reasonMax} autoFocus />
      </Field>
      <Field label="Muddat">
        <select value={duration} onChange={(event) => setDuration(event.target.value)}>
          {restrictDurations.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </Field>
      {restrict.error && <ErrorNotice error={restrict.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onCancel}>
          Bekor qilish
        </button>
        <button
          className="btn danger"
          disabled={!reason.trim() || restrict.isPending}
          onClick={() =>
            restrict.mutate(
              { reportId, reason: reason.trim(), days },
              {
                onSuccess: () => {
                  notify(restrictDone(days), 'info')
                  onDone()
                },
              },
            )
          }
        >
          {restrict.isPending && <Spinner />}
          {restrict.isPending ? 'Yuborilmoqda…' : 'Cheklash'}
        </button>
      </div>
    </div>
  )
}

export function RestrictSenderDialog({ report, onClose }: { report: ModerationReport; onClose: () => void }) {
  return (
    <Modal title="Yuboruvchini cheklash" onClose={onClose}>
      <p className="faint" style={{ margin: 0 }}>“{report.excerpt}”</p>
      <RestrictSenderForm reportId={report.id} onDone={onClose} onCancel={onClose} />
    </Modal>
  )
}
