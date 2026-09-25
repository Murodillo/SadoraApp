import type { ReactNode } from 'react'
import { display } from '../api/phone'
import type { DoctorAccount } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { SadoraTile } from '../components/Logo'
import { specialtyLabel, statusLabels } from '../components/labels'
import { ThemeToggle } from '../components/theme'
import { formatDate, Spinner } from '../components/ui'

/**
 * Everything short of approved. The panel is where an approved doctor works; applying
 * happens in the Sadora Doctor app, where the phone's camera photographs the diploma.
 * So this page never offers a form — it says where she stands and what comes next.
 */
export function StatusPage({
  account,
  refreshing,
  onRefresh,
}: {
  account: DoctorAccount
  refreshing: boolean
  onRefresh: () => void
}) {
  const { session, signOut } = useAuth()
  const label = statusLabels[account.status] ?? { text: account.status, tone: 'free' }
  const { title, body } = copyFor(account, session?.phone ?? null)

  return (
    <div className="login status-page">
      <div className="login-bloom" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>

      <div className="card status-card">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <div className="brand" style={{ padding: 0 }}>
            <SadoraTile size={34} />
            <div>
              <div className="brand-name">SADORA</div>
              <div className="brand-sub">Shifokor paneli</div>
            </div>
          </div>
          <ThemeToggle />
        </div>

        <div className="grid" style={{ gap: 10 }}>
          <span className={`badge ${label.tone}`} style={{ justifySelf: 'start' }}>
            {label.text}
          </span>
          <h1 className="status-title">{title}</h1>
          {body}
        </div>

        <div className="row" style={{ justifyContent: 'space-between' }}>
          <button className="btn ghost" type="button" onClick={signOut}>
            Chiqish
          </button>
          <button className="btn primary" type="button" onClick={onRefresh} disabled={refreshing}>
            {refreshing && <Spinner />}
            Holatni tekshirish
          </button>
        </div>
      </div>
    </div>
  )
}

function copyFor(account: DoctorAccount, phone: string | null): { title: string; body: ReactNode } {
  switch (account.status) {
    case 'none':
      return {
        title: 'Ariza Sadora Doctor ilovasida topshiriladi',
        body: (
          <>
            <p className="muted" style={{ margin: 0 }}>
              Bu panel tasdiqlangan shifokorlar uchun: bu yerda ayollarning chatdagi savollariga javob berasiz va
              post yozasiz. Buning uchun avval shifokor ekaningizni tasdiqlash kerak.
            </p>
            <ol className="steps">
              <li>Telefoningizga Sadora Doctor ilovasini o'rnating.</li>
              <li>
                {phone ? (
                  <>
                    Shu raqam bilan kiring: <b>{display(phone)}</b>.
                  </>
                ) : (
                  'Shu panelga kirgan raqamingiz bilan kiring.'
                )}
              </li>
              <li>Mutaxassislik, ish joyi va litsenziya raqamini kiriting, diplom va litsenziya rasmlarini yuboring.</li>
              <li>Sadora jamoasi arizani tekshirib tasdiqlagach, shu sahifada panel ochiladi.</li>
            </ol>
          </>
        ),
      }
    case 'pending':
      return {
        title: "Arizangiz ko'rib chiqilmoqda",
        body: (
          <>
            <p className="muted" style={{ margin: 0 }}>
              Sadora jamoasi hujjatlaringizni tekshirmoqda. Tasdiqlangach, shu panelda savollarga javob bera olasiz —
              "Holatni tekshirish" tugmasini bosing yoki sahifani keyinroq oching.
            </p>
            <Summary account={account} />
          </>
        ),
      }
    case 'rejected':
      return {
        title: 'Ariza rad etildi',
        body: (
          <>
            <ReviewNote note={account.reviewNote} />
            <p className="muted" style={{ margin: 0 }}>
              Kamchiliklarni tuzatib, arizani Sadora Doctor ilovasida qayta topshirishingiz mumkin.
            </p>
          </>
        ),
      }
    case 'suspended':
      return {
        title: "Shifokor hisobingiz to'xtatilgan",
        body: (
          <>
            <ReviewNote note={account.reviewNote} />
            <p className="muted" style={{ margin: 0 }}>
              Hisob to'xtatilgan paytda postlaringiz chatda ko'rinmaydi va savollarga javob bera olmaysiz. Savollar
              bo'lsa, Sadora jamoasiga murojaat qiling.
            </p>
          </>
        ),
      }
    default:
      return {
        title: 'Panel hozircha yopiq',
        body: (
          <p className="muted" style={{ margin: 0 }}>
            Shifokor hisobingiz holati: {account.status}. Batafsil ma'lumot uchun Sadora jamoasiga murojaat qiling.
          </p>
        ),
      }
  }
}

function ReviewNote({ note }: { note?: string | null }) {
  if (!note) return null
  return (
    <div className="notice review-note">
      <div className="faint">Sadora jamoasining izohi</div>
      <div className="review-note-text">{note}</div>
    </div>
  )
}

function Summary({ account }: { account: DoctorAccount }) {
  return (
    <dl className="details">
      <dt>Ism-sharif</dt>
      <dd>{account.fullName ?? '—'}</dd>
      <dt>Mutaxassislik</dt>
      <dd>{specialtyLabel(account.specialty)}</dd>
      <dt>Hujjatlar</dt>
      <dd>{account.documentCount} ta</dd>
      <dt>Yuborilgan</dt>
      <dd>{formatDate(account.submittedAt)}</dd>
    </dl>
  )
}
