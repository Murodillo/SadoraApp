import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { uniqueRows, useConversations, useQuestions } from '../api/hooks'
import { useAuth } from '../auth/AuthContext'
import { useApprovedDoctor } from '../auth/doctor'
import { SadoraTile } from '../components/Logo'
import { specialtyLabel } from '../components/labels'
import { ThemeToggle } from '../components/theme'
import { VerifiedMark } from '../components/ui'

interface NavEntry {
  to: string
  label: string
  glyph: string
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
      { to: '/', label: 'Savollar', glyph: '◉' },
      { to: '/messages', label: 'Xabarlar', glyph: '✉' },
      { to: '/posts', label: 'Postlarim', glyph: '❑' },
    ],
  },
  {
    title: 'Ish',
    entries: [
      { to: '/quick-replies', label: 'Tayyor javoblar', glyph: '⚡' },
      { to: '/stats', label: 'Statistika', glyph: '▤' },
      { to: '/earnings', label: 'Daromad', glyph: '◈' },
    ],
  },
  {
    title: 'Hisob',
    entries: [
      { to: '/settings', label: 'Ish vaqti va narx', glyph: '◷' },
      { to: '/profile', label: 'Profil', glyph: '◎' },
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
                  {entry.glyph}
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
            <span className="who-name">
              {doctor.fullName}
              <VerifiedMark />
            </span>{' '}
            · <span className="badge free">{specialtyLabel(doctor.specialty)}</span>
          </span>
          <button className="btn small" onClick={signOut}>
            Chiqish
          </button>
        </header>

        <main className="content">
          {/* Keyed on the path so every page plays its entrance, not only the first. */}
          <div className="page" key={location.pathname}>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
