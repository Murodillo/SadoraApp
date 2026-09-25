import { useState } from 'react'
import { fieldsOf, messageOf } from '../api/client'
import { useUpdateDoctorProfile } from '../api/hooks'
import { lengthProblem, limits } from '../api/limits'
import type { DoctorAccount } from '../api/types'
import { useApprovedDoctor } from '../auth/doctor'
import { specialtyLabel } from '../components/labels'
import { useToast } from '../components/toast'
import { Card, Counter, Field, formatDate, Spinner, VerifiedMark } from '../components/ui'

/**
 * Her details. What an admin checked against her documents — name, specialty, experience,
 * licence — is shown and not editable; workplace and bio are hers to change without a
 * new review, which is exactly what the server allows.
 */
export function ProfilePage() {
  const doctor = useApprovedDoctor()
  return (
    <div className="two-col even">
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

      <ProfileForm doctor={doctor} />
    </div>
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
