import { useState } from 'react'
import { ApiFailure } from '../api/client'
import { Field } from '../components/ui'
import { useAuth } from './AuthContext'

export function LoginPage() {
  const { signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [totpCode, setTotpCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // The 2FA field appears only once the server has said the account needs it, so an
  // account without an enrolled authenticator is not asked for a code it cannot produce.
  const [needsTotp, setNeedsTotp] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await signIn(email, password, totpCode || undefined)
    } catch (cause) {
      if (cause instanceof ApiFailure) {
        if (cause.message.includes('2FA')) setNeedsTotp(true)
        setError(cause.message)
      } else {
        setError('Kutilmagan xatolik')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="card" onSubmit={submit}>
        <div className="brand" style={{ padding: 0 }}>
          <div className="brand-mark">✦</div>
          <div>
            <div className="brand-name">SADORA</div>
            <div className="brand-sub">Admin panel</div>
          </div>
        </div>

        <Field label="Email">
          <input
            type="email"
            value={email}
            autoComplete="username"
            required
            onChange={(event) => setEmail(event.target.value)}
          />
        </Field>

        <Field label="Parol">
          <input
            type="password"
            value={password}
            autoComplete="current-password"
            required
            onChange={(event) => setPassword(event.target.value)}
          />
        </Field>

        {needsTotp && (
          <Field label="2FA kodi">
            <input
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              value={totpCode}
              autoComplete="one-time-code"
              onChange={(event) => setTotpCode(event.target.value)}
            />
          </Field>
        )}

        {error && <div className="notice error">{error}</div>}

        <button className="btn primary" type="submit" disabled={busy}>
          {busy ? 'Kirilmoqda…' : 'Kirish'}
        </button>

        <p className="faint" style={{ margin: 0 }}>
          Har bir kirish urinishi audit log'ga yoziladi. Besh marta xato parol — 15 daqiqaga
          blok.
        </p>
      </form>
    </div>
  )
}
