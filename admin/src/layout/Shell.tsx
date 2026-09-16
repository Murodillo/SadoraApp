import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import type { AdminRole } from '../api/types'
import { SadoraTile } from '../components/Logo'

interface NavEntry {
  to: string
  label: string
  glyph: string
  roles: AdminRole[]
}

interface NavGroup {
  title: string
  entries: NavEntry[]
}

const ALL: AdminRole[] = ['OWNER', 'ADMIN', 'SUPPORT', 'ANALYST']

const groups: NavGroup[] = [
  {
    title: 'Umumiy',
    entries: [{ to: '/', label: 'Dashboard', glyph: '◧', roles: ALL }],
  },
  {
    title: 'Foydalanuvchilar',
    entries: [{ to: '/users', label: "Ro'yxat", glyph: '◎', roles: ALL }],
  },
  {
    title: 'Kontent',
    entries: [
      { to: '/community', label: 'Chat', glyph: '◉', roles: ALL },
      { to: '/content', label: 'Bilim — maqolalar', glyph: '❑', roles: ALL },
      { to: '/ai', label: 'AI xarajati', glyph: '✦', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/notifications', label: 'Bildirishnomalar', glyph: '◔', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/wearables', label: 'Wearable providerlar', glyph: '◐', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
    ],
  },
  {
    title: 'Gul',
    entries: [
      { to: '/rewards', label: 'Mukofotlar va streak', glyph: '✷', roles: ALL },
      { to: '/shop', label: "Do'kon — chegirmalar", glyph: '⌘', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
    ],
  },
  {
    title: 'Sozlamalar',
    entries: [
      { to: '/billing', label: 'Obunalar va to‘lovlar', glyph: '₴', roles: ALL },
      { to: '/features', label: 'Entitlements va limitlar', glyph: '◈', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/flags', label: 'Feature flags', glyph: '⚑', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
    ],
  },
  {
    title: 'Xavfsizlik',
    entries: [
      // Every role: 2FA is each operator's own to switch on, and one that cannot enrol
      // is one whose account stays a password.
      { to: '/security', label: 'Hisobim va 2FA', glyph: '⚿', roles: ALL },
      { to: '/audit', label: 'Audit log', glyph: '☰', roles: ['OWNER'] },
    ],
  },
]

const titles: Record<string, string> = {
  '/': 'Dashboard',
  '/users': 'Foydalanuvchilar',
  '/community': 'Chat — moderatsiya',
  '/content': 'Bilim — maqolalar',
  '/ai': 'AI xarajati',
  '/billing': "Obunalar va to'lovlar",
  '/notifications': 'Bildirishnomalar',
  '/wearables': 'Wearable providerlar',
  '/rewards': 'Gul — mukofotlar va streak',
  '/shop': "Gul do'koni — mahsulot va chegirmalar",
  '/features': 'Entitlements va limitlar',
  '/flags': 'Feature flags',
  '/security': 'Hisobim va 2FA',
  '/audit': 'Audit log va xavfsizlik',
}

export function Shell() {
  const { session, signOut, can } = useAuth()
  const location = useLocation()
  const [theme, setTheme] = useState(() => localStorage.getItem('sadora.admin.theme') ?? 'dark')

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('sadora.admin.theme', theme)
  }, [theme])

  const title = titles[location.pathname] ?? (location.pathname.startsWith('/users/') ? 'Foydalanuvchi kartochkasi' : 'SADORA')

  return (
    <div className="shell">
      <nav className="nav">
        <div className="brand">
          <SadoraTile size={34} />
          <div>
            <div className="brand-name">SADORA</div>
            <div className="brand-sub">Admin</div>
          </div>
        </div>

        {groups.map((group) => {
          const visible = group.entries.filter((entry) => can(entry.roles))
          if (!visible.length) return null
          return (
            <div key={group.title}>
              <div className="nav-section">{group.title}</div>
              {visible.map((entry) => (
                <NavLink
                  key={entry.to}
                  to={entry.to}
                  end={entry.to === '/'}
                  className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
                >
                  <span className="glyph">{entry.glyph}</span>
                  {entry.label}
                </NavLink>
              ))}
            </div>
          )
        })}

      </nav>

      <div className="main">
        <header className="header">
          <h1 key={title} className="page-title">{title}</h1>
          <div className="spacer" />
          <button
            className="btn ghost small"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            title="Mavzuni almashtirish"
          >
            {theme === 'dark' ? '☾' : '☀'}
          </button>
          <span className="faint">
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
