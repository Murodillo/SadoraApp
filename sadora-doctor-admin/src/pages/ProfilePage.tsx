import { useEffect, useRef, useState } from 'react'
import { fieldsOf, messageOf } from '../api/client'
import { useRemoveDoctorPhoto, useSetDoctorPhoto, useUpdateDoctorProfile } from '../api/hooks'
import { ImageProblem, PHOTO_ACCEPT, preparePhoto } from '../api/image'
import type { PreparedPhoto } from '../api/image'
import { lengthProblem, limits } from '../api/limits'
import type { DoctorAccount } from '../api/types'
import { useApprovedDoctor } from '../auth/doctor'
import { specialtyLabel } from '../components/labels'
import { useToast } from '../components/toast'
import { Avatar, Card, Counter, Field, formatDate, Modal, Spinner, VerifiedMark } from '../components/ui'

/**
 * Her details. What an admin checked against her documents — name, specialty, experience,
 * licence — is shown and not editable; workplace and bio are hers to change without a
 * new review, which is exactly what the server allows.
 */
export function ProfilePage() {
  const doctor = useApprovedDoctor()
  return (
    <div className="two-col even">
      <div className="grid" style={{ gap: 16, alignContent: 'start' }}>
        <PhotoCard doctor={doctor} />

        <Card title="Ma'lumotlarim">
          <dl className="details">
            <dt>Ism-sharif</dt>
            <dd className="row" style={{ gap: 6 }}>
              {doctor.fullName ?? '—'}
              <VerifiedMark />
            </dd>
            <dt>Mutaxassislik</dt>
            <dd>{specialtyLabel(doctor.specialty)}</dd>
            <dt>Tajriba</dt>
            <dd>{doctor.experienceYears != null ? `${doctor.experienceYears} yil` : '—'}</dd>
            <dt>Litsenziya raqami</dt>
            <dd className="mono">{doctor.licenseNumber ?? '—'}</dd>
            <dt>Hujjatlar</dt>
            <dd>{doctor.documentCount} ta</dd>
            <dt>Ariza yuborilgan</dt>
            <dd>{formatDate(doctor.submittedAt)}</dd>
            <dt>Tasdiqlangan</dt>
            <dd>{formatDate(doctor.reviewedAt)}</dd>
          </dl>
          <p className="faint" style={{ marginBottom: 0 }}>
            Ism, mutaxassislik, tajriba va litsenziya hujjatlaringiz bo'yicha tekshirilgan, shuning uchun bu yerda
            o'zgartirilmaydi.
          </p>
        </Card>

        <ConsultationSwitch doctor={doctor} />
      </div>

      <ProfileForm doctor={doctor} />
    </div>
  )
}

/** How big her photo is drawn here: big enough to judge the crop. */
const PHOTO_SIZE = 96

/**
 * Her photo — public: her page, the directory, the bylines under her answers and her
 * consultations all show it. A chosen file is shrunk and re-encoded here, shown as the
 * circle patients will see, and only sent when she saves it; the server's own refusal
 * (not a picture, too small, too big) is shown under it in its words.
 */
function PhotoCard({ doctor }: { doctor: DoctorAccount }) {
  const upload = useSetDoctorPhoto()
  const remove = useRemoveDoctorPhoto()
  const { notify } = useToast()
  const input = useRef<HTMLInputElement>(null)
  const [prepared, setPrepared] = useState<PreparedPhoto | null>(null)
  const [preparing, setPreparing] = useState(false)
  const [problem, setProblem] = useState<string | null>(null)
  const [removing, setRemoving] = useState(false)
  const busy = preparing || upload.isPending || remove.isPending
  const hasPhoto = Boolean(doctor.photoUrl)

  // The preview is an object URL: let it go when it is replaced, saved or left behind.
  useEffect(() => () => {
    if (prepared) URL.revokeObjectURL(prepared.previewUrl)
  }, [prepared])

  async function choose(file: File | undefined) {
    if (!file) return
    setProblem(null)
    setPreparing(true)
    try {
      setPrepared(await preparePhoto(file))
    } catch (error) {
      setProblem(error instanceof ImageProblem ? error.message : "Rasmni o'qib bo'lmadi")
    } finally {
      setPreparing(false)
    }
  }

  function save() {
    if (!prepared) return
    setProblem(null)
    upload.mutate(prepared.upload, {
      onSuccess: () => {
        setPrepared(null)
        notify('Rasm saqlandi')
      },
      onError: (error) => setProblem(fieldsOf(error).image ?? messageOf(error)),
    })
  }

  function confirmRemove() {
    setProblem(null)
    remove.mutate(undefined, {
      onSuccess: () => {
        setRemoving(false)
        notify('Rasm olib tashlandi')
      },
      onError: (error) => {
        setRemoving(false)
        setProblem(messageOf(error))
      },
    })
  }

  return (
    <Card title="Rasmim">
      <div className="photo-editor">
        {prepared ? (
          <span
            className="avatar doctor photo preview"
            style={{ width: PHOTO_SIZE, height: PHOTO_SIZE }}
            role="img"
            aria-label="Yangi rasm"
          >
            <img src={prepared.previewUrl} alt="" />
          </span>
        ) : (
          <Avatar name={doctor.fullName ?? ''} doctor url={doctor.photoUrl} size={PHOTO_SIZE} />
        )}

        <div className="photo-actions">
          <p className="muted" style={{ margin: 0 }}>
            Yuzingiz aniq ko'rinadigan, professional rasm. Sadora xodimlari mos bo'lmagan rasmni olib tashlashi mumkin.
          </p>
          <p className="faint" style={{ margin: 0 }}>
            {prepared
              ? "Bemorlar rasmingizni shu doira ichida ko'radi. Saqlang yoki boshqasini tanlang."
              : "JPEG yoki PNG. Rasm ochiq sahifangizda, shifokorlar ro'yxatida va javoblaringiz yonida ko'rinadi."}
          </p>

          {problem && (
            <span className="field-error" role="alert">
              {problem}
            </span>
          )}

          <div className="row">
            {prepared ? (
              <>
                <button className="btn primary" type="button" onClick={save} disabled={busy}>
                  {upload.isPending && <Spinner />}
                  {upload.isPending ? 'Yuklanmoqda…' : 'Rasmni saqlash'}
                </button>
                <button className="btn" type="button" onClick={() => input.current?.click()} disabled={busy}>
                  Boshqasini tanlash
                </button>
                <button
                  className="btn ghost"
                  type="button"
                  onClick={() => {
                    setPrepared(null)
                    setProblem(null)
                  }}
                  disabled={upload.isPending}
                >
                  Bekor qilish
                </button>
              </>
            ) : (
              <>
                <button
                  className={`btn${hasPhoto ? '' : ' primary'}`}
                  type="button"
                  onClick={() => input.current?.click()}
                  disabled={busy}
                >
                  {preparing && <Spinner />}
                  {hasPhoto ? 'Rasmni almashtirish' : 'Rasm yuklash'}
                </button>
                {hasPhoto && (
                  <button className="btn ghost danger" type="button" onClick={() => setRemoving(true)} disabled={busy}>
                    Olib tashlash
                  </button>
                )}
              </>
            )}
          </div>

          <input
            ref={input}
            type="file"
            accept={PHOTO_ACCEPT}
            hidden
            aria-label="Rasm fayli"
            onChange={(event) => {
              const file = event.target.files?.[0]
              // Cleared, so choosing the same file again is still a change.
              event.target.value = ''
              void choose(file)
            }}
          />
        </div>
      </div>

      {removing && (
        <Modal title="Rasmni olib tashlash" onClose={() => setRemoving(false)}>
          <p className="muted" style={{ margin: 0 }}>
            Rasmingiz ochiq sahifangizdan, ro'yxatdan va javoblaringiz yonidan olib tashlanadi. O'rnida ismingizning bosh harfi
            ko'rinadi.
          </p>
          <div className="row" style={{ justifyContent: 'flex-end' }}>
            <button type="button" className="btn ghost" onClick={() => setRemoving(false)} disabled={remove.isPending}>
              Bekor qilish
            </button>
            <button type="button" className="btn danger" onClick={confirmRemove} disabled={remove.isPending}>
              {remove.isPending && <Spinner />}
              Olib tashlash
            </button>
          </div>
        </Modal>
      )}
    </Card>
  )
}

/**
 * Whether patients may open a consultation with her now. Switching it off leaves the
 * consultations already open alone; it only stops new ones, which is what the server does.
 */
function ConsultationSwitch({ doctor }: { doctor: DoctorAccount }) {
  const update = useUpdateDoctorProfile()
  const { notify } = useToast()
  const accepts = doctor.acceptsConsultations ?? true

  function toggle() {
    const next = !accepts
    update.mutate(
      { acceptsConsultations: next },
      {
        onSuccess: () =>
          notify(next ? 'Konsultatsiyalar qabul qilinmoqda' : "Yangi konsultatsiyalar to'xtatildi"),
        onError: (error) => notify(messageOf(error), 'error'),
      },
    )
  }

  return (
    <Card title="Konsultatsiyalar">
      <label className="switch-row">
        <span className="switch-text">
          <b>Konsultatsiya qabul qilaman</b>
          <span className="faint">
            {accepts
              ? "Bemorlar sahifangizdan sizga shaxsiy yozishi mumkin. Har bir konsultatsiya 24 soat ochiq turadi."
              : "Yangi konsultatsiya ochilmaydi. Ochiq konsultatsiyalar muddati tugaguncha davom etadi."}
          </span>
        </span>
        <input
          type="checkbox"
          role="switch"
          className="switch"
          aria-label="Konsultatsiya qabul qilaman"
          checked={accepts}
          disabled={update.isPending}
          onChange={toggle}
        />
      </label>
    </Card>
  )
}

function ProfileForm({ doctor }: { doctor: DoctorAccount }) {
  const update = useUpdateDoctorProfile()
  const { notify } = useToast()
  const [workplace, setWorkplace] = useState(doctor.workplace ?? '')
  const [bio, setBio] = useState(doctor.bio ?? '')
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})

  const workplaceError = serverErrors.workplace ?? lengthProblem(workplace, 1, limits.doctorWorkplaceMax)
  const bioError = serverErrors.bio ?? lengthProblem(bio, 0, limits.doctorBioMax)
  const dirty = workplace.trim() !== (doctor.workplace ?? '') || bio.trim() !== (doctor.bio ?? '')

  function submit(event: React.FormEvent) {
    event.preventDefault()
    if (!dirty || workplaceError || bioError) return
    setServerErrors({})
    // An empty bio is sent as "" on purpose: the server reads that as "clear it", and an
    // omitted field as "leave it".
    update.mutate(
      { workplace: workplace.trim(), bio: bio.trim() },
      {
        onSuccess: (account) => {
          // What the server kept — trimmed, and an empty bio cleared — is what the form shows now.
          setWorkplace(account.workplace ?? '')
          setBio(account.bio ?? '')
          notify('Profil saqlandi')
        },
        onError: (error) => {
          notify(messageOf(error), 'error')
          setServerErrors(fieldsOf(error))
        },
      },
    )
  }

  function reset() {
    setWorkplace(doctor.workplace ?? '')
    setBio(doctor.bio ?? '')
    setServerErrors({})
  }

  return (
    <Card title="Ochiq sahifadagi ma'lumotlar">
      <form className="grid" style={{ gap: 12 }} onSubmit={submit} noValidate>
        <Field label="Ish joyi" error={workplaceError} hint={<Counter length={workplace.length} max={limits.doctorWorkplaceMax} />}>
          <input
            value={workplace}
            maxLength={limits.doctorWorkplaceMax}
            placeholder="Klinika yoki shifoxona"
            onChange={(event) => {
              setWorkplace(event.target.value)
              if (serverErrors.workplace) setServerErrors({})
            }}
          />
        </Field>

        <Field label="O'zim haqimda" error={bioError} hint={<Counter length={bio.length} max={limits.doctorBioMax} />}>
          <textarea
            rows={7}
            value={bio}
            maxLength={limits.doctorBioMax}
            placeholder="Qaysi masalalar bo'yicha maslahat berasiz, qayerda o'qigansiz…"
            onChange={(event) => {
              setBio(event.target.value)
              if (serverErrors.bio) setServerErrors({})
            }}
          />
        </Field>

        <p className="faint" style={{ margin: 0 }}>
          Bu ma'lumotlar chatdagi ochiq sahifangizda ko'rinadi. Bo'sh qoldirilgan "O'zim haqimda" o'chiriladi.
        </p>

        <div className="row" style={{ justifyContent: 'flex-end' }}>
          <button className="btn ghost" type="button" onClick={reset} disabled={!dirty || update.isPending}>
            Bekor qilish
          </button>
          <button className="btn primary" type="submit" disabled={!dirty || Boolean(workplaceError || bioError) || update.isPending}>
            {update.isPending && <Spinner />}
            {update.isPending ? 'Saqlanmoqda…' : 'Saqlash'}
          </button>
        </div>
      </form>
    </Card>
  )
}
