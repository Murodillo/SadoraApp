import { useEffect, useRef, useState } from 'react'
import { ApiFailure, fieldsOf, messageOf } from '../api/client'
import { accept, display, format, isIncomplete, isValid, toE164 } from '../api/phone'
import type { OtpChallenge } from '../api/types'
import { SadoraMark, SadoraWordmark } from '../components/Logo'
import { ThemeToggle } from '../components/theme'
import { useToast } from '../components/toast'
import { Field, Spinner } from '../components/ui'
import { useAuth } from './AuthContext'

/** The server's refusals that are about the code she typed, so they belong under the field. */
const CODE_FAILURES = new Set(['otp_invalid', 'otp_expired', 'otp_too_many_attempts'])

/** The server's default is six digits; the field lets a shorter or longer setting through. */
const CODE_MIN = 4
const CODE_MAX = 8

/**
 * Sign-in: the number, then the SMS code. A doctor is an ordinary Sadora account, so this
 * is the app's own phone sign-in, not the staff panel's email and password.
 */
export function LoginPage() {
  const { requestCode, verifyCode } = useAuth()
  const { notify } = useToast()
  const [phone, setPhone] = useState('')
  const [challenge, setChallenge] = useState<OtpChallenge | null>(null)
  const [sentTo, setSentTo] = useState('')
  const [code, setCode] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [resendAt, setResendAt] = useState(0)
  // Which way the last step change went, so the next step slides in from that side. None
  // on first paint: the card itself pops in then, as the staff panel's does.
  const [direction, setDirection] = useState<'none' | 'forward' | 'back'>('none')
  const now = useNow(challenge !== null)
  const codeInput = useRef<HTMLInputElement>(null)

  // The number is judged once it is whole: an error while she is still typing the
  // fourth digit is noise.
  const phoneError =
    errors.phone ?? (!isIncomplete(phone) && !isValid(phone) ? "Bunday operator kodi yo'q — raqamni tekshiring" : null)

  useEffect(() => {
    if (challenge) codeInput.current?.focus()
  }, [challenge])

  async function sendCode() {
    const e164 = toE164(phone)
    if (!e164) {
      setErrors({ phone: isIncomplete(phone) ? "To'qqiz xonali raqamni kiriting" : "Bunday operator kodi yo'q — raqamni tekshiring" })
      return
    }
    setBusy(true)
    setErrors({})
    try {
      const next = await requestCode(e164)
      setDirection('forward')
      setChallenge(next)
      setSentTo(e164)
      // A dev or stage server hands the code back; typing it from the server log is busywork.
      setCode(next.devCode ?? '')
      setResendAt(Date.now() + Math.max(0, next.resendAfterSeconds) * 1000)
    } catch (cause) {
      const fields = fieldsOf(cause)
      if (Object.keys(fields).length) setErrors(fields)
      else notify(messageOf(cause), 'error')
    } finally {
      setBusy(false)
    }
  }

  async function submitPhone(event: React.FormEvent) {
    event.preventDefault()
    await sendCode()
  }

  async function submitCode(event: React.FormEvent) {
    event.preventDefault()
    if (!challenge) return
    setBusy(true)
    setErrors({})
    try {
      await verifyCode(challenge.challengeId, code)
    } catch (cause) {
      const fields = fieldsOf(cause)
      if (cause instanceof ApiFailure && CODE_FAILURES.has(cause.code)) setErrors({ code: cause.message })
      else if (Object.keys(fields).length) setErrors(fields)
      else notify(messageOf(cause), 'error')
      setBusy(false)
    }
    // On success the whole page is replaced; nothing to reset.
  }

  function changeNumber() {
    setDirection('back')
    setChallenge(null)
    setCode('')
    setErrors({})
  }

  const secondsLeft = Math.max(0, Math.ceil((resendAt - now) / 1000))
  const codeReady = code.length >= CODE_MIN

  return (
    <div className="login">
      <div className="login-bloom" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <div className="login-corner">
        <ThemeToggle />
      </div>

      <div className="card login-card">
        <Brand />
        {challenge === null ? (
          <form key="phone" className={`login-step step-${direction}`} onSubmit={submitPhone} noValidate>
            <p className="muted login-lead">Sadora Doctor ilovasida ro'yxatdan o'tgan raqamingiz bilan kiring.</p>

            <Field label="Telefon raqami" prefix="+998" error={phoneError}>
              <input
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                placeholder="90 123 45 67"
                value={format(phone)}
                autoFocus
                onChange={(event) => {
                  setPhone(accept(event.target.value))
                  if (errors.phone) setErrors({})
                }}
              />
            </Field>

            <button className="btn primary" type="submit" disabled={busy || !isValid(phone)}>
              {busy && <Spinner />}
              {busy ? 'Yuborilmoqda…' : 'Kod olish'}
            </button>

            <p className="faint" style={{ margin: 0 }}>
              Kirish kodi SMS orqali keladi. Panel Sadora'da tasdiqlangan shifokorlar uchun.
            </p>
          </form>
        ) : (
          <form key="code" className={`login-step step-${direction}`} onSubmit={submitCode} noValidate>
            <p className="muted login-lead">
              Kod <b>{display(sentTo)}</b> raqamiga yuborildi.
            </p>

            {challenge.devCode && (
              <div className="notice">Sinov serveri: kod o'zi to'ldirildi.</div>
            )}

            <Field label="SMS kod" error={errors.code ?? errors.challengeId}>
              <input
                ref={codeInput}
                inputMode="numeric"
                autoComplete="one-time-code"
                pattern="[0-9]*"
                maxLength={CODE_MAX}
                className="code-input"
                value={code}
                onChange={(event) => {
                  setCode(event.target.value.replace(/\D/g, '').slice(0, CODE_MAX))
                  if (errors.code) setErrors({})
                }}
              />
            </Field>

            <button className="btn primary" type="submit" disabled={busy || !codeReady}>
              {busy && <Spinner />}
              {busy ? 'Tekshirilmoqda…' : 'Kirish'}
            </button>

            <div className="row" style={{ justifyContent: 'space-between' }}>
              <button className="btn ghost small" type="button" onClick={changeNumber} disabled={busy}>
                ← Raqamni o'zgartirish
              </button>
              <button className="btn ghost small" type="button" onClick={() => void sendCode()} disabled={busy || secondsLeft > 0}>
                {secondsLeft > 0 ? `Qayta yuborish · ${formatSeconds(secondsLeft)}` : 'Kodni qayta yuborish'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

function Brand() {
  return (
    <div className="login-brand">
      <SadoraMark size={84} animated />
      <div className="login-word">
        <SadoraWordmark width={150} animated />
        <div className="login-tagline">SHIFOKOR PANELI</div>
      </div>
    </div>
  )
}

/** `75` -> `1:15`. */
function formatSeconds(total: number): string {
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

/** The clock, ticking once a second while `active` — for the resend countdown. */
function useNow(active: boolean): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!active) return
    setNow(Date.now())
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [active])
  return now
}
