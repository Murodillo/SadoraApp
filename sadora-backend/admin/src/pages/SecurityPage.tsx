import { useState } from 'react'
import { useAdminMe, useConfirmTotp, useDisableTotp, useStartTotpEnrolment } from '../api/hooks'
import type { TotpEnrolment } from '../api/types'
import { useToast } from '../components/toast'
import { Card, ErrorNotice, Field, Loading, Spinner } from '../components/ui'

/**
 * The operator's own account: the one page here that is not about somebody else.
 *
 * Sign-in already demanded a TOTP code from any account with 2FA enabled, and nothing
 * could enable it — so in practice every operator account was a password. This is the
 * missing half.
 *
 * There is no QR code. Rendering one means either a dependency or three hundred lines of
 * encoder for five to ten people who enrol once; the secret and the otpauth:// link are
 * both here to copy, and every authenticator accepts a typed secret.
 */
export function SecurityPage() {
  const me = useAdminMe()

  if (me.isLoading) return <Loading rows={3} />
  if (me.error) return <ErrorNotice error={me.error} />
  if (!me.data) return null

  return (
    <div className="grid" style={{ gap: 16 }}>
      <Card title="Hisobim">
        <div className="filters">
          <Field label="Ism">
            <input value={me.data.name} readOnly />
          </Field>
          <Field label="Email">
            <input value={me.data.email} readOnly />
          </Field>
          <Field label="Rol">
            <input value={me.data.role} readOnly />
          </Field>
        </div>
      </Card>

      {me.data.totpEnabled ? <DisableCard /> : <EnrolCard email={me.data.email} />}
    </div>
  )
}

function EnrolCard({ email }: { email: string }) {
  const { notify } = useToast()
  const start = useStartTotpEnrolment()
  const confirm = useConfirmTotp()
  const [enrolment, setEnrolment] = useState<TotpEnrolment | null>(null)
  const [code, setCode] = useState('')

  const begin = async () => {
    setCode('')
    setEnrolment(await start.mutateAsync())
  }

  return (
    <Card title="Ikki bosqichli kirish">
      <div className="notice" style={{ marginBottom: 12 }}>
        Hozir hisobingiz faqat parol bilan himoyalangan. Operator tokeni mahsulotdagi har
        bir obunani ochadi, shuning uchun 2FA — tavsiya emas, zarurat.
      </div>

      {!enrolment ? (
        <>
          <button className="btn primary" disabled={start.isPending} onClick={begin}>
            {start.isPending && <Spinner />}
            {start.isPending ? 'Kalit yaratilmoqda…' : 'Sozlashni boshlash'}
          </button>
          {start.error ? <ErrorNotice error={start.error} /> : null}
        </>
      ) : (
        <div className="grid" style={{ gap: 12 }}>
          <p>
            Autentifikator ilovasiga ({email}) quyidagi kalitni qo'lda kiriting yoki
            havolani oching, so'ng u ko'rsatgan kodni yozing.
          </p>
          <Field label="Kalit">
            <input value={enrolment.secret} readOnly onFocus={(event) => event.target.select()} />
          </Field>
          <Field label="otpauth havolasi">
            <input value={enrolment.otpauthUri} readOnly onFocus={(event) => event.target.select()} />
          </Field>
          <div className="notice">
            Kalitni hoziroq saqlab qo'ying. U faqat shu yerda ko'rsatiladi — telefon
            yo'qolsa, 2FA'ni boshqa Owner o'chirib beradi.
          </div>
          <Field label="Ilovadagi kod">
            <input
              className="narrow"
              inputMode="numeric"
              maxLength={6}
              value={code}
              onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
            />
          </Field>
          <div>
            <button
              className="btn primary"
              disabled={code.length !== 6 || confirm.isPending}
              onClick={() => confirm.mutate(code, { onSuccess: () => notify('2FA yoqildi — keyingi kirishda kod so‘raladi') })}
            >
              {confirm.isPending && <Spinner />}
              {confirm.isPending ? 'Tekshirilmoqda…' : 'Yoqish'}
            </button>
          </div>
          {confirm.error ? <ErrorNotice error={confirm.error} /> : null}
        </div>
      )}
    </Card>
  )
}

function DisableCard() {
  const { notify } = useToast()
  const disable = useDisableTotp()
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')

  return (
    <Card title="Ikki bosqichli kirish — yoqilgan">
      <div className="notice" style={{ marginBottom: 12 }}>
        Kirishda parol bilan birga autentifikator kodi so'raladi.
      </div>
      <p>
        O'chirish uchun parol va joriy kod kerak: o'g'irlangan seans — 2FA to'sib turgan
        narsaning o'zi, shuning uchun uni seansning o'zi bilan olib tashlab bo'lmaydi.
      </p>
      <div className="filters">
        <Field label="Parol">
          <input
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </Field>
        <Field label="Kod">
          <input
            className="narrow"
            inputMode="numeric"
            maxLength={6}
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, ''))}
          />
        </Field>
      </div>
      <button
        className="btn danger"
        style={{ marginTop: 12 }}
        disabled={!password || code.length !== 6 || disable.isPending}
        onClick={() => disable.mutate({ password, code }, { onSuccess: () => notify("2FA o'chirildi", 'info') })}
      >
        {disable.isPending && <Spinner />}
        {disable.isPending ? "O'chirilmoqda…" : "2FA'ni o'chirish"}
      </button>
      {disable.error ? <ErrorNotice error={disable.error} /> : null}
    </Card>
  )
}
