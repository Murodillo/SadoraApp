import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import type { AdminRole } from '../api/types'
import { Art } from '../components/art'
import type { ArtName } from '../components/art'
import { SadoraTile } from '../components/Logo'

interface NavEntry {
  to: string
  label: string
  /** The clay icon the apps use for the same idea. */
  art: ArtName
  roles: AdminRole[]
}

interface NavGroup {
  title: string
  entries: NavEntry[]
}

const ALL: AdminRole[] = ['OWNER', 'ADMIN', 'SUPPORT', 'ANALYST']

/*
 * Grouped by the job an operator came to do, not by the server module behind the page:
 * people, content, money, the app's settings, and reports. A page's title in the
 * header is its name here, so the rail and the page always agree.
 */
const groups: NavGroup[] = [
  {
    title: '',
    entries: [{ to: '/', label: 'Bosh sahifa', art: 'ic3d_home', roles: ALL }],
  },
  {
    title: 'Odamlar',
    entries: [
      { to: '/users', label: 'Foydalanuvchilar', art: 'ic3d_profile', roles: ALL },
      { to: '/doctors', label: 'Shifokorlar', art: 'ic3d_doctor', roles: ['OWNER', 'ADMIN', 'SUPPORT'] },
      { to: '/consultations', label: 'Konsultatsiyalar', art: 'ic3d_message', roles: ['OWNER', 'ADMIN', 'SUPPORT'] },
    ],
  },
  {
    title: 'Kontent',
    entries: [
      { to: '/community', label: 'Chat', art: 'ic3d_chats', roles: ALL },
      { to: '/content', label: 'Maqolalar', art: 'ic3d_book', roles: ALL },
    ],
  },
  {
    title: 'Pul',
    entries: [
      { to: '/billing', label: "Obunalar va to'lovlar", art: 'ic3d_crown', roles: ALL },
      { to: '/shop', label: "Do'kon", art: 'ic3d_shop', roles: ALL },
    ],
  },
  {
    title: 'Ilova',
    entries: [
      { to: '/features', label: 'Funksiyalar', art: 'ic3d_bulb', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/rewards', label: 'Gul va ketma-ketlik', art: 'ic3d_trophy', roles: ALL },
      { to: '/notifications', label: 'Bildirishnomalar', art: 'ic3d_bell', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/wearables', label: 'Soat va qurilmalar', art: 'ic3d_watch', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/promo-video', label: 'Reklama videosi', art: 'ic3d_globe', roles: ALL },
    ],
  },
  {
    title: 'Hisobot',
    entries: [
      { to: '/analytics', label: 'Analitika', art: 'ic3d_insights', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/ai', label: 'AI xarajati', art: 'ic3d_ai', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/audit', label: 'Audit jurnali', art: 'ic3d_notebook', roles: ['OWNER'] },
    ],
  },
  {
    title: 'Hisob',
    entries: [
      // Every role: 2FA is each operator's own to switch on, and one that cannot enrol
      // is one whose account stays a password.
      { to: '/security', label: 'Hisobim va 2FA', art: 'ic3d_lock', roles: ALL },
    ],
  },
]

const titles: Record<string, string> = Object.fromEntries(
  groups.flatMap((group) => group.entries.map((entry) => [entry.to, entry.label])),
)

/**
 * [setupOnly]: 2FA is mandatory and she has not enrolled, so the server refuses every
 * page but her own account. The rail shows only that one instead of a list of 403s.
 */
export function Shell({ setupOnly = false }: { setupOnly?: boolean }) {
  const { session, signOut, can } = useAuth()
  const location = useLocation()
  const [theme, setTheme] = useState(() => localStorage.getItem('sadora.admin.theme') ?? 'dark')
  const [flip, setFlip] = useState(false)
  const [navOpen, setNavOpen] = useState(false)

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('sadora.admin.theme', theme)
  }, [theme])

  // The rail closes itself after a choice on a narrow screen, and on Escape.
  useEffect(() => setNavOpen(false), [location.pathname])
  useEffect(() => {
    if (!navOpen) return
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setNavOpen(false)
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [navOpen])

  const title = titles[location.pathname] ?? (location.pathname.startsWith('/users/') ? 'Foydalanuvchi kartochkasi' : 'SADORA')

  function toggleTheme() {
    setTheme(theme === 'dark' ? 'light' : 'dark')
    setFlip(true)
    window.setTimeout(() => setFlip(false), 720)
  }

  return (
    <div className="shell">
      {navOpen && <div className="nav-scrim" onClick={() => setNavOpen(false)} role="presentation" />}
      <nav className={`nav${navOpen ? ' open' : ''}`} aria-label="Bo'limlar">
        <div className="brand">
          <SadoraTile size={34} />
          <div>
            <div className="brand-name">SADORA</div>
            <div className="brand-sub">Admin</div>
          </div>
        </div>

        {groups.map((group) => {
          const visible = group.entries.filter(
            (entry) => can(entry.roles) && (!setupOnly || entry.to === '/security'),
          )
          if (!visible.length) return null
          return (
            <div key={group.title || 'home'}>
              {group.title && <div className="nav-section">{group.title}</div>}
              {visible.map((entry) => (
                <NavLink
                  key={entry.to}
                  to={entry.to}
                  end={entry.to === '/'}
                  className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
                >
                  <span className="glyph" aria-hidden="true">
                    <Art name={entry.art} size={22} />
                  </span>
                  {entry.label}
                </NavLink>
              ))}
            </div>
          )
        })}
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
          <button className="btn ghost small theme-toggle" onClick={toggleTheme} title="Mavzuni almashtirish" aria-label="Mavzuni almashtirish">
            <span className={`glyph${flip ? ' flip' : ''}`} key={theme}>
              {theme === 'dark' ? '☾' : '☀'}
            </span>
          </button>
          <span className="faint who">
            {session?.email} · <span className="badge free">{session?.role}</span>
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
