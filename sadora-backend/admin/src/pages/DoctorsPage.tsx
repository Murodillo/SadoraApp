import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useDoctor, useDoctorCounts, useDoctorDocument, useDoctors, useRemoveDoctorPhoto, useReviewDoctor } from '../api/hooks'
import { limits } from '../api/limits'
import type { AdminDoctorDetail, AdminDoctorDocument, AdminDoctorQuality, DoctorReviewAction, DoctorStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Avatar } from '../components/Avatar'
import { useToast } from '../components/toast'
import {
  Card,
  Empty,
  ErrorNotice,
  Field,
  formatDate,
  formatDateTime,
  Loading,
  Modal,
  Spinner,
  TabPanel,
  Tabs,
  useObjectUrl,
} from '../components/ui'
import {
  allowedReviewActions,
  canRemovePhoto,
  documentKindLabels,
  doctorStatusLabels,
  doctorStatusOrder,
  formatBytes,
  photoRemovalReason,
  photoRemovalReasons,
  reviewActionDone,
  reviewActionLabels,
  reviewNeedsNote,
  reviewNoteValid,
  specialtyLabels,
} from './doctorReview'
import { DoctorQualityTable, EarningsSection } from './DoctorMoney'

const PAGE_SIZE = 25

/**
 * The doctor verification queue. A doctor who applies in the app lands here as pending
 * with her diploma and licence scans; an Owner or Admin approves or rejects her, and
 * can later suspend and reinstate. Support reads the queue and the documents but has no
 * buttons — the server would refuse them anyway.
 *
 * The last tab, "Shifokorlar sifati", sets every working doctor side by side — reply
 * times, unanswered windows, ratings and money — and opens a doctor's card from a row.
 * The card carries her earnings and the payout form for Owner and Admin.
 */
type DoctorsTab = DoctorStatus | 'quality'

export function DoctorsPage() {
  const counts = useDoctorCounts()
  const [tab, setTab] = useState<DoctorsTab>('pending')
  const [offset, setOffset] = useState(0)
  const [selected, setSelected] = useState<string | null>(null)

  function changeTab(next: DoctorsTab) {
    setTab(next)
    setOffset(0)
    setSelected(null)
  }

  /** From the quality table to her card, on the tab of her status. */
  function openFromQuality(doctor: AdminDoctorQuality) {
    if (doctor.status === 'none') return
    setTab(doctor.status)
    setOffset(0)
    setSelected(doctor.doctorId)
  }

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Tabs<DoctorsTab>
        value={tab}
        onChange={changeTab}
        items={[
          ...doctorStatusOrder.map((key) => {
            const count = counts.data?.[key]
            return {
              key: key as DoctorsTab,
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
          }),
          { key: 'quality' as DoctorsTab, label: 'Shifokorlar sifati' },
        ]}
      />
      {counts.error && <ErrorNotice error={counts.error} />}

      <TabPanel id={tab}>
        {tab === 'quality' ? (
          <DoctorQualityTable onOpen={openFromQuality} />
        ) : (
          <div className="two-col even">
            <DoctorList status={tab} offset={offset} onOffset={setOffset} selected={selected} onSelect={setSelected} />
            {selected ? (
              <DoctorDetail id={selected} onClose={() => setSelected(null)} />
            ) : (
              <Card>
                <Empty>Tafsilotlarni ko'rish uchun ro'yxatdan shifokorni tanlang.</Empty>
              </Card>
            )}
          </div>
        )}
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
                    <td>
                      <div className="person">
                        <Avatar name={doctor.fullName} photoUrl={doctor.photoUrl} size={32} />
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontWeight: 600 }}>{doctor.fullName}</div>
                          {status === 'pending' && !doctor.photoUrl && (
                            <span className="badge warn" title="Shifokordan rasm qo'shishni so'rang">
                              Rasm yo'q
                            </span>
                          )}
                        </div>
                      </div>
                    </td>
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
  const [removingPhoto, setRemovingPhoto] = useState(false)

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
      <div className="doctor-head">
        <Avatar name={doctor.fullName} photoUrl={doctor.photoUrl} size={88} zoomable />
        <div className="doctor-head-info">
          <div className="row">
            <span className={`badge ${status.tone}`}>{status.text}</span>
            <span className="mono faint">{doctor.id}</span>
          </div>
          {doctor.photoUrl ? (
            canRemovePhoto(session?.role) && (
              <button className="btn danger small" onClick={() => setRemovingPhoto(true)}>
                Rasmni olib tashlash
              </button>
            )
          ) : (
            <span className="faint">
              {doctor.status === 'pending'
                ? "Rasm yo'q — tasdiqlashdan oldin shifokordan rasm qo'shishni so'rang."
                : "Rasm yo'q — ilovada ismining bosh harflari ko'rinadi."}
            </span>
          )}
        </div>
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

      <ConsultationsSection doctor={doctor} />

      <EarningsSection doctorId={doctor.id} />

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
      {removingPhoto && <RemovePhotoDialog doctor={doctor} onClose={() => setRemovingPhoto(false)} />}
    </Card>
  )
}

/**
 * How busy her consultations are, in counts. What was said in them never reaches the
 * panel — a message is seen only through a report, on the Chat page.
 */
function ConsultationsSection({ doctor }: { doctor: AdminDoctorDetail }) {
  const stats = doctor.consultations
  return (
    <>
      <div className="row" style={{ justifyContent: 'space-between', marginTop: 16, marginBottom: 8 }}>
        <h2 style={{ margin: 0 }}>Konsultatsiyalar</h2>
        {doctor.acceptsConsultations ? (
          <span className="badge ok">Qabul qilmoqda</span>
        ) : (
          <span className="badge free">Qabul qilmayapti</span>
        )}
      </div>
      {stats ? (
        <table>
          <tbody>
            <Row label="Jami" value={stats.total} />
            <Row label="Ochiq" value={stats.open} />
            <Row label="Shifokor xabarlari" value={stats.messagesFromDoctor} />
            <Row label="Bemor xabarlari" value={stats.messagesFromPatients} />
            <Row label="Oxirgi faollik" value={formatDateTime(stats.lastMessageAt)} />
          </tbody>
        </table>
      ) : (
        <p className="faint" style={{ margin: 0 }}>
          Konsultatsiya bo'lmagan.
        </p>
      )}
    </>
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

/**
 * Takes a doctor's photo down. Her photo is public — her page, the directory, her
 * bylines — so a face that is missing, blurred or not hers comes down here; she gets a
 * push with the reason and can upload another from the app.
 */
function RemovePhotoDialog({ doctor, onClose }: { doctor: AdminDoctorDetail; onClose: () => void }) {
  const remove = useRemoveDoctorPhoto()
  const { notify } = useToast()
  const [reason, setReason] = useState('')

  function submit() {
    remove.mutate(
      { id: doctor.id, reason: photoRemovalReason(reason) },
      {
        onSuccess: () => {
          notify('Rasm olib tashlandi', 'info')
          onClose()
        },
        onError: (error) => notify(error instanceof Error ? error.message : String(error), 'error'),
      },
    )
  }

  return (
    <Modal title={`Rasmni olib tashlash: ${doctor.fullName}`} onClose={onClose}>
      <div className="photo-preview">
        <Avatar name={doctor.fullName} photoUrl={doctor.photoUrl} size={120} />
      </div>
      <p className="faint" style={{ margin: 0 }}>
        Rasm shifokor sahifasidan, katalogdan va konsultatsiyalardan olib tashlanadi, o'rnida ismining bosh harflari
        ko'rinadi. Shifokorga bildirishnoma boradi va u yangi rasm yuklay oladi.
      </p>
      <div className="reason-chips">
        {photoRemovalReasons.map((preset) => (
          <button
            key={preset}
            type="button"
            className={`btn small${reason.trim() === preset ? ' primary' : ' ghost'}`}
            onClick={() => setReason(preset)}
          >
            {preset}
          </button>
        ))}
      </div>
      <Field label={`Sabab (shifokor ko'radi, ixtiyoriy, ${reason.trim().length}/${limits.photoReasonMax})`}>
        <textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} maxLength={limits.photoReasonMax} />
      </Field>
      {remove.error && <ErrorNotice error={remove.error} />}
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn ghost" onClick={onClose}>
          Bekor qilish
        </button>
        <button className="btn danger" disabled={remove.isPending} onClick={submit}>
          {remove.isPending && <Spinner />}
          {remove.isPending ? 'Yuborilmoqda…' : 'Olib tashlash'}
        </button>
      </div>
    </Modal>
  )
}
