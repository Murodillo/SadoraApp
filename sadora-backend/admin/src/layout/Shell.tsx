import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { Art } from '../components/art'
import { CommandPalette } from '../components/CommandPalette'
import { SadoraTile } from '../components/Logo'
import { groups, titles } from './nav'

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

/**
 * [setupOnly]: 2FA is mandatory and she has not enrolled, or she is still on the Owner's
 * temporary password, so the server refuses every page but her own account. The rail
 * shows only that one instead of a list of 403s.
 */
export function Shell({ setupOnly = false }: { setupOnly?: boolean }) {
  const { session, signOut, can } = useAuth()
  const location = useLocation()
  const [theme, setTheme] = useState(() => localStorage.getItem('sadora.admin.theme') ?? 'dark')
  const [flip, setFlip] = useState(false)
  const [navOpen, setNavOpen] = useState(false)
  const [paletteOpen, setPaletteOpen] = useState(false)

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

  // Ctrl+K (⌘K on a Mac) from anywhere in the panel, but not before her account is set up:
  // every page it could open would refuse her.
  useEffect(() => {
    if (setupOnly) return
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setPaletteOpen(true)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [setupOnly])

  const title =
    titles[location.pathname] ??
    (location.pathname.startsWith('/users/')
      ? 'Foydalanuvchi kartochkasi'
      : location.pathname.startsWith('/pm/docs/')
        ? 'Hujjatlar'
        : location.pathname.startsWith('/pm/')
          ? decodeURIComponent(location.pathname.slice(4)).toUpperCase()
          : 'SADORA')

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
          {!setupOnly && (
            <button className="btn ghost small search-trigger" onClick={() => setPaletteOpen(true)} aria-keyshortcuts="Control+K Meta+K">
              <span aria-hidden="true">⌕</span> Qidirish <kbd>{isMac ? '⌘K' : 'Ctrl K'}</kbd>
            </button>
          )}
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

        {paletteOpen && <CommandPalette onClose={() => setPaletteOpen(false)} />}

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
