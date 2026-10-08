import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { uniqueRows, useBadges, useConversations, useMarkBadgesSeen, useQuestions } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import { useApprovedDoctor } from '../auth/doctor'
import { Art } from '../components/art'
import type { ArtName } from '../components/art'
import { BadgeUnlockOverlay } from '../components/badges'
import { SadoraTile } from '../components/Logo'
import { specialtyLabel } from '../components/labels'
import { ThemeToggle } from '../components/theme'
import { Avatar, VerifiedMark } from '../components/ui'

interface NavEntry {
  to: string
  label: string
  /** The clay icon the app uses for the same place. */
  art: ArtName
}

interface NavGroup {
  title: string
  entries: NavEntry[]
}

// The staff panel's rail, cut down to what a doctor does: answer, write, keep her page,
// and run her practice — her ready answers, her numbers, her money, her hours and price.
const groups: NavGroup[] = [
  {
    title: 'Chat',
    entries: [
      { to: '/', label: 'Savollar', art: 'ic3d_chats' },
      { to: '/messages', label: 'Xabarlar', art: 'ic3d_message' },
      { to: '/posts', label: 'Postlarim', art: 'ic3d_notebook' },
    ],
  },
  {
    title: 'Ish',
    entries: [
      { to: '/quick-replies', label: 'Tayyor javoblar', art: 'ic3d_bulb' },
      { to: '/stats', label: 'Statistika', art: 'ic3d_insights' },
      { to: '/earnings', label: 'Daromad', art: 'ic3d_gem' },
      { to: '/badges', label: 'Nishonlar', art: 'badge_laurel' },
    ],
  },
  {
    title: 'Hisob',
    entries: [
      { to: '/settings', label: 'Ish vaqti va narx', art: 'ic3d_calendar' },
      { to: '/profile', label: 'Profil', art: 'ic3d_profile' },
    ],
  },
]

const titles: Record<string, string> = {
  '/': 'Savollar — javob kutayotganlar',
  '/messages': 'Xabarlar — bemorlar bilan konsultatsiyalar',
  '/posts': 'Postlarim',
  '/profile': 'Profil',
  '/settings': 'Ish vaqti va narx',
  '/quick-replies': 'Tayyor javoblar',
  '/stats': 'Statistika',
  '/earnings': 'Daromad',
  '/badges': 'Nishonlar',
}

export function Shell() {
  const { signOut } = useAuth()
  const doctor = useApprovedDoctor()
  const location = useLocation()
  const [navOpen, setNavOpen] = useState(false)
  // The same query the questions page reads with no filter, so the count costs nothing
  // extra there and keeps itself fresh everywhere else.
  const questions = useQuestions()
  // Polled here as well as on the page, so an unread line shows wherever she is.
  const conversations = useConversations()
  // Her badges: read on every page change (the query keeps it to one read per 15 s), so a
  // tier her last answer crossed plays wherever she goes next.
  const badges = useBadges()
  const markSeen = useMarkBadgesSeen()
  const { refetch: refetchBadges, dataUpdatedAt: badgesReadAt } = badges
  useEffect(() => {
    if (Date.now() - badgesReadAt > BADGE_READ_GAP_MS) void refetchBadges()
    // Only a page change asks; the read time is checked, not watched.
  }, [location.pathname])
  const unseen = badges.data?.unseen ?? []
  const unlock = unseen[0]
  const unlockTarget = unlock
    ? badges.data?.badges.find((badge) => badge.key === unlock.key)?.thresholds[unlock.tier - 1] ?? 0
    : 0

  // The rail closes itself after a choice on a narrow screen, and on Escape.
  useEffect(() => setNavOpen(false), [location.pathname])
  useEffect(() => {
    if (!navOpen) return
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setNavOpen(false)
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [navOpen])

  const title = titles[location.pathname] ?? 'SADORA'
  const waiting = uniqueRows(questions.data?.pages, (question) => question.id).length
  const unread = conversations.data?.reduce((sum, conversation) => sum + (conversation.unread ?? 0), 0) ?? 0

  return (
    <div className="shell">
      {navOpen && <div className="nav-scrim" onClick={() => setNavOpen(false)} role="presentation" />}
      <nav className={`nav${navOpen ? ' open' : ''}`} aria-label="Bo'limlar">
        <div className="brand">
          <SadoraTile size={34} />
          <div>
            <div className="brand-name">SADORA</div>
            <div className="brand-sub">Shifokor paneli</div>
          </div>
        </div>

        {groups.map((group) => (
          <div key={group.title}>
            <div className="nav-section">{group.title}</div>
            {group.entries.map((entry) => (
              <NavLink
                key={entry.to}
                to={entry.to}
                end={entry.to === '/'}
                className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
              >
                <span className="glyph" aria-hidden="true">
                  {/* Her own face on her own page's entry, once she has one. */}
                  {entry.to === '/profile' && doctor.photoUrl ? (
                    <Avatar name={doctor.fullName ?? ''} doctor url={doctor.photoUrl} size={18} />
                  ) : (
                    <Art name={entry.art} size={22} />
                  )}
                </span>
                {entry.label}
                {entry.to === '/' && waiting > 0 && (
                  // Re-keyed on the number, so a question arriving or leaving bumps it.
                  <span key={waiting} className="badge warn nav-count" aria-label={`${waiting} ta javobsiz savol`}>
                    {questions.hasNextPage ? `${waiting}+` : waiting}
                  </span>
                )}
                {entry.to === '/messages' && unread > 0 && (
                  <span key={unread} className="badge danger nav-count" aria-label={`${unread} ta o'qilmagan xabar`}>
                    {unread > 99 ? '99+' : unread}
                  </span>
                )}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>

      <div className="main">
        <header className="header">
          <button
            className="btn ghost small nav-toggle"
            onClick={() => setNavOpen((open) => !open)}
            aria-label="Bo'limlar"
            aria-expanded={navOpen}
          >
            ☰
          </button>
          <h1 key={title} className="page-title">
            {title}
          </h1>
          <div className="spacer" />
          <ThemeToggle />
          <span className="faint who">
            <Link to="/profile" className="who-link" aria-label="Profil" title="Profil">
              <Avatar name={doctor.fullName ?? ''} doctor url={doctor.photoUrl} size={28} />
              <span className="who-name">
                {doctor.fullName}
                <VerifiedMark />
              </span>
            </Link>{' '}
            · <span className="badge free">{specialtyLabel(doctor.specialty)}</span>
          </span>
          <button className="btn small" onClick={signOut}>
            Chiqish
          </button>
        </header>

        {!doctor.photoUrl && location.pathname !== '/profile' && <PhotoNudge profileId={doctor.profileId} />}

        <main className="content">
          {/* Keyed on the path so every page plays its entrance, not only the first. */}
          <div className="page" key={location.pathname}>
            <Outlet />
          </div>
        </main>
      </div>

      {unlock && (
        <BadgeUnlockOverlay
          key={`${unlock.key}:${unlock.tier}`}
          unlock={unlock}
          target={unlockTarget}
          remaining={unseen.length - 1}
          onNext={() => markSeen.mutate({ keys: [unlock.key] })}
          onSkipAll={() => markSeen.mutate({ keys: [] })}
        />
      )}
    </div>
  )
}

/** The shortest gap between two badge reads a page change asks for. */
const BADGE_READ_GAP_MS = 15_000

const NUDGE_KEY = 'sadora.doctor.photo-nudge-dismissed'

function readDismissed(profileId: string | null | undefined): boolean {
  try {
    return sessionStorage.getItem(NUDGE_KEY) === (profileId ?? 'me')
  } catch {
    return false
  }
}

/**
 * Asks a doctor with no photo to add one, on every page but the one where she would.
 * Closing it lasts for this tab's session only — the next sign-in asks again, because
 * a face is what makes a patient write to her. Keyed by her profile, so on a shared
 * computer the next doctor to sign in is asked in her own right.
 */
function PhotoNudge({ profileId }: { profileId: string | null | undefined }) {
  const [dismissed, setDismissed] = useState(() => readDismissed(profileId))
  if (dismissed) return null

  function dismiss() {
    setDismissed(true)
    try {
      sessionStorage.setItem(NUDGE_KEY, profileId ?? 'me')
    } catch {
      // Storage refused: it stays closed until the page reloads, which is enough.
    }
  }

  return (
    <div className="notice photo-nudge">
      <span className="photo-nudge-text">Rasmingizni qo'ying — bemorlar rasmli shifokorga ko'proq yozadi</span>
      <Link to="/profile" className="btn small primary">
        Rasm qo'yish
      </Link>
      <button type="button" className="btn ghost small" onClick={dismiss} aria-label="Eslatmani yopish">
        ✕
      </button>
    </div>
  )
}
