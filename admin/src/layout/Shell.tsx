import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import type { AdminRole } from '../api/types'

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
    title: 'Sozlamalar',
    entries: [
      { to: '/features', label: 'Entitlements va limitlar', glyph: '◈', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
      { to: '/flags', label: 'Feature flags', glyph: '⚑', roles: ['OWNER', 'ADMIN', 'ANALYST'] },
    ],
  },
  {
    title: 'Xavfsizlik',
    entries: [{ to: '/audit', label: 'Audit log', glyph: '☰', roles: ['OWNER'] }],
  },
]

/**
 * Pages the proposal specifies but whose backend arrives in sprint 2–3. Shown as
 * disabled rather than hidden: the team can see the shape of the finished panel, and
 * nobody files a bug asking where the AI cost page went.
 */
const pending = [
  'Obunalar',
  'AI xarajat va kill switch',
  'Kontent (Learn)',
  'Bildirishnomalar',
  'Wearable providerlar',
  "Ma'lumot moslashtirish",
  "Qo'llab-quvvatlash",
]

const titles: Record<string, string> = {
  '/': 'Dashboard',
  '/users': 'Foydalanuvchilar',
  '/features': 'Entitlements va limitlar',
  '/flags': 'Feature flags',
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
          <div className="brand-mark">✦</div>
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

        <div className="nav-section">Keyingi sprintlarda</div>
        {pending.map((label) => (
          <div key={label} className="nav-item locked" title="Backend 2–3-sprintda tayyor bo'ladi">
            <span className="glyph">◌</span>
            {label}
          </div>
        ))}
      </nav>

      <div className="main">
        <header className="header">
          <h1>{title}</h1>
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
          <Outlet />
        </main>
      </div>
    </div>
  )
}
