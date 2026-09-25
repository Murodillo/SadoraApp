import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useDoctor, useDoctorCounts, useDoctorDocument, useDoctors, useReviewDoctor } from '../api/hooks'
import { limits } from '../api/limits'
import type { AdminDoctorDetail, AdminDoctorDocument, DoctorReviewAction, DoctorStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useToast } from '../components/toast'
import { Card, Empty, ErrorNotice, Field, formatDate, formatDateTime, Loading, Modal, Spinner, TabPanel, Tabs } from '../components/ui'
import {
  allowedReviewActions,
  documentKindLabels,
  doctorStatusLabels,
  doctorStatusOrder,
  formatBytes,
  reviewActionDone,
  reviewActionLabels,
  reviewNeedsNote,
  reviewNoteValid,
  specialtyLabels,
} from './doctorReview'

const PAGE_SIZE = 25

/**
 * The doctor verification queue. A doctor who applies in the app lands here as pending
 * with her diploma and licence scans; an Owner or Admin approves or rejects her, and
 * can later suspend and reinstate. Support reads the queue and the documents but has no
 * buttons — the server would refuse them anyway.
 */
export function DoctorsPage() {
  const counts = useDoctorCounts()
  const [status, setStatus] = useState<DoctorStatus>('pending')
  const [offset, setOffset] = useState(0)
  const [selected, setSelected] = useState<string | null>(null)

  function changeStatus(next: DoctorStatus) {
    setStatus(next)
    setOffset(0)
    setSelected(null)
  }

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Tabs<DoctorStatus>
        value={status}
        onChange={changeStatus}
        items={doctorStatusOrder.map((key) => {
          const count = counts.data?.[key]
          return {
            key,
            label: (
              <>
                {doctorStatusLabels[key].text}
                {count !== undefined && (
                  <span className={`badge ${key === 'pending' && count > 0 ? 'warn' : 'free'}`} style={{ marginLeft: 6 }}>
                    {count}
                  </span>
                )}
              </>
            ),
          }
        })}
      />
      {counts.error && <ErrorNotice error={counts.error} />}

      <TabPanel id={status}>
        <div className="two-col even">
          <DoctorList status={status} offset={offset} onOffset={setOffset} selected={selected} onSelect={setSelected} />
          {selected ? (
            <DoctorDetail id={selected} onClose={() => setSelected(null)} />
          ) : (
            <Card>
              <Empty>Tafsilotlarni ko'rish uchun ro'yxatdan shifokorni tanlang.</Empty>
            </Card>
          )}
        </div>
      </TabPanel>
    </div>
  )
}

// ---------------------------------------------------------------- list

function DoctorList({
  status,
  offset,
  onOffset,
  selected,
  onSelect,
}: {
  status: DoctorStatus
  offset: number
  onOffset: (offset: number) => void
  selected: string | null
  onSelect: (id: string) => void
}) {
  const doctors = useDoctors(status, PAGE_SIZE, offset)
  const page = doctors.data

  return (
    <Card>
      {doctors.error && <ErrorNotice error={doctors.error} />}
      {doctors.isLoading && !page ? (
        <Loading rows={6} />
      ) : !page?.items.length ? (
        <Empty>{status === 'pending' ? "Kutilayotgan ariza yo'q — navbat bo'sh." : "Bu holatda shifokor yo'q."}</Empty>
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Ism</th>
                  <th>Mutaxassislik</th>
                  <th>Ish joyi</th>
                  <th>Tajriba</th>
                  <th>Hujjat</th>
                  <th>Yuborilgan</th>
                </tr>
              </thead>
              <tbody>
                {page.items.map((doctor) => (
                  <tr
                    key={doctor.id}
                    className={`clickable${selected === doctor.id ? ' selected' : ''}`}
                    onClick={() => onSelect(doctor.id)}
                    aria-selected={selected === doctor.id}
                  >
                    <td style={{ fontWeight: 600 }}>{doctor.fullName}</td>
                    <td className="muted">{specialtyLabels[doctor.specialty] ?? doctor.specialty}</td>
                    <td className="muted">{doctor.workplace}</td>
                    <td style={{ whiteSpace: 'nowrap' }}>{doctor.experienceYears} yil</td>
                    <td style={{ textAlign: 'right' }}>{doctor.documentCount}</td>
                    <td className="faint" style={{ whiteSpace: 'nowrap' }}>
                      {formatDate(doctor.submittedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="row" style={{ justifyContent: 'space-between', marginTop: 12 }}>
            <span className="faint">
              {offset + 1}–{offset + page.items.length} / {page.total}
            </span>
            <div className="row">
              <button className="btn small" disabled={offset === 0} onClick={() => onOffset(Math.max(0, offset - PAGE_SIZE))}>
                Oldingi
              </button>
              <button
                className="btn small"
                disabled={offset + page.items.length >= page.total}
                onClick={() => onOffset(offset + PAGE_SIZE)}
              >
                Keyingi
              </button>
            </div>
          </div>
        </>
      )}
    </Card>
  )
}

// ---------------------------------------------------------------- detail

function DoctorDetail({ id, onClose }: { id: string; onClose: () => void }) {
  const detail = useDoctor(id)
  const { session } = useAuth()
  const [action, setAction] = useState<DoctorReviewAction | null>(null)

  if (detail.isLoading) {
    return (
      <Card>
        <Loading rows={6} />
      </Card>
    )
  }
  if (detail.error || !detail.data) {
    return (
      <Card>
        <ErrorNotice error={detail.error ?? 'Topilmadi'} />
      </Card>
    )
  }

  const doctor = detail.data
  const status = doctorStatusLabels[doctor.status] ?? { text: doctor.status, tone: 'free' }
  const actions = allowedReviewActions(doctor.status, session?.role)

  return (
    <Card
      title={doctor.fullName}
      action={
        <button className="btn ghost small" onClick={onClose} aria-label="Yopish">
          ✕
        </button>
      }
    >
      <div className="row" style={{ marginBottom: 12 }}>
        <span className={`badge ${status.tone}`}>{status.text}</span>
        <span className="mono faint">{doctor.id}</span>
      </div>

      <table>
        <tbody>
          <Row label="Mutaxassislik" value={specialtyLabels[doctor.specialty] ?? doctor.specialty} />
          <Row label="Ish joyi" value={doctor.workplace} />
          <Row label="Tajriba" value={`${doctor.experienceYears} yil`} />
          <Row label="Litsenziya raqami" value={doctor.licenseNumber} mono />
          <Row label="Telefon" value={doctor.phone ? <a href={`tel:${doctor.phone}`}>{doctor.phone}</a> : '—'} mono />
          <Row
            label="Hisob"
            value={
              <Link to={`/users/${doctor.userId}`}>
                {doctor.accountName || 'Ismsiz'} — kartochka →
              </Link>
            }
          />
          <Row label="Yuborilgan" value={formatDateTime(doctor.submittedAt)} />
          <Row label="Ko'rib chiqilgan" value={formatDateTime(doctor.reviewedAt)} />
          <Row label="Tasdiqlangan" value={formatDateTime(doctor.verifiedAt)} />
        </tbody>
      </table>

      {doctor.bio && (
        <>
          <h2 style={{ marginTop: 16 }}>O'zi haqida</h2>
          <p className="muted" style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
            {doctor.bio}
          </p>
        </>
      )}

      {doctor.reviewNote && (
        <div className="notice" style={{ marginTop: 16 }}>
          <strong>Ko'rib chiqish izohi:</strong> {doctor.reviewNote}
        </div>
      )}

      <h2 style={{ marginTop: 16 }}>Hujjatlar</h2>
      {doctor.documents.length === 0 ? (
        <p className="faint" style={{ margin: 0 }}>
          Hujjat yuklanmagan.
        </p>
      ) : (
        <div className="doc-thumbs">
          {doctor.documents.map((document) => (
            <DocumentThumb key={document.id} doctorId={doctor.id} document={document} />
          ))}
        </div>
      )}

      {actions.length > 0 && (
        <div className="row" style={{ justifyContent: 'flex-end', marginTop: 16 }}>
          {actions.map((next) => (
            <button
              key={next}
              className={`btn${next === 'approve' || next === 'reinstate' ? ' primary' : ' danger'}`}
              onClick={() => setAction(next)}
            >
              {reviewActionLabels[next]}
            </button>
          ))}
        </div>
      )}

      {action && <ReviewDialog doctor={doctor} action={action} onClose={() => setAction(null)} />}
    </Card>
  )
}

function Row({ label, value, mono }: { label: string; value: React.ReactNode; mono?: boolean }) {
  return (
    <tr>
      <td className="faint" style={{ width: 160 }}>
        {label}
      </td>
      <td className={mono ? 'mono' : undefined}>{value}</td>
    </tr>
  )
}

/**
 * The scan comes over an authenticated request, so it is shown through an object URL
 * that lives exactly as long as the thumbnail does.
 */
function useObjectUrl(blob: Blob | undefined): string | null {
  const [url, setUrl] = useState<string | null>(null)
  useEffect(() => {
    if (!blob) {
      setUrl(null)
      return
    }
    const next = URL.createObjectURL(blob)
    setUrl(next)
    return () => URL.revokeObjectURL(next)
  }, [blob])
  return url
}

function DocumentThumb({ doctorId, document }: { doctorId: string; document: AdminDoctorDocument }) {
  const file = useDoctorDocument(doctorId, document.id)
  const url = useObjectUrl(file.data)
  const [open, setOpen] = useState(false)
  const kind = documentKindLabels[document.kind] ?? document.kind

  return (
    <figure className="doc-thumb">
      <button type="button" className="doc-thumb-image" onClick={() => url && setOpen(true)} disabled={!url} aria-label={`${kind} — kattalashtirish`}>
        {url ? (
          <img src={url} alt={kind} />
        ) : file.error ? (
          <span className="faint">Yuklab bo'lmadi</span>
        ) : (
          <Spinner />
        )}
      </button>
      <figcaption>
        <strong>{kind}</strong>
        <span className="faint">
          {formatBytes(document.sizeBytes)} · {formatDate(document.createdAt)}
        </span>
      </figcaption>

      {open && url && (
        <Modal title={kind} onClose={() => setOpen(false)} wide>
          <img src={url} alt={kind} className="doc-full" />
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <a href={url} target="_blank" rel="noreferrer">
              Yangi oynada ochish ↗
            </a>
          </div>
        </Modal>
      )}
    </figure>
  )
}

function ReviewDialog({
  doctor,
  action,
  onClose,
}: {
  doctor: AdminDoctorDetail
  action: DoctorReviewAction
  onClose: () => void
}) {
  const review = useReviewDoctor()
  const { notify } = useToast()
  const [note, setNote] = useState('')
  const needsNote = reviewNeedsNote(action)
  const destructive = action === 'reject' || action === 'suspend'

  function submit() {
    review.mutate(
      { id: doctor.id, action, note: needsNote ? note.trim() : undefined },
      {
        onSuccess: () => {
          notify(reviewActionDone[action], destructive ? 'info' : 'ok')
          onClose()
        },
        onError: (error) => notify(error instanceof Error ? error.message : String(error), 'error'),
      },
    )
  }

  return (
    <Modal title={`${reviewActionLabels[action]}: ${doctor.fullName}`} onClose={onClose}>
      {needsNote ? (
        <>
          <p className="faint" style={{ margin: 0 }}>
            {action === 'reject'
              ? "Ariza rad etiladi. Izohni shifokorning o'zi o'qiydi — nima tuzatish kerakligini yozing."
              : "Shifokor ro'yxatdan chiqariladi va foydalanuvchilarga ko'rinmaydi. Izohni shifokorning o'zi o'qiydi."}
          </p>
          <Field label={`Izoh (majburiy, ${note.trim().length}/${limits.reasonMax})`}>
            <textarea rows={4} value={note} onChange={(event) => setNote(event.target.value)} maxLength={limits.reasonMax} autoFocus />
          </Field>
        </>
      ) : (
        <p className="muted" style={{ margin: 0 }}>
          {action === 'approve'
            ? `${doctor.fullName} tasdiqlanadi va ilovada shifokor sifatida ko'rinadi. Davom etasizmi?`
            : `${doctor.fullName} qayta tiklanadi va yana ilovada ko'rinadi. Davom etasizmi?`}
        </p>
      )}
      {review.error && <ErrorNotice error={review.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button
          className={`btn${destructive ? ' danger' : ' primary'}`}
          disabled={!reviewNoteValid(action, note) || review.isPending}
          onClick={submit}
        >
          {review.isPending && <Spinner />}
          {review.isPending ? 'Yuborilmoqda…' : reviewActionLabels[action]}
        </button>
      </div>
    </Modal>
  )
}
