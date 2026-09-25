import { useState } from 'react'

export type Theme = 'light' | 'dark'

const THEME_KEY = 'sadora.doctor.theme'

/**
 * Her last choice, kept in `localStorage` like the staff panel keeps an operator's, and
 * dark until she makes one — the staff panel's default, so the two look like one product.
 */
export function initialTheme(): Theme {
  try {
    const stored = localStorage.getItem(THEME_KEY)
    if (stored === 'light' || stored === 'dark') return stored
  } catch {
    // No storage: the default holds.
  }
  return 'dark'
}

/**
 * Applied on <html> before the first paint (main.tsx) — earlier than the staff panel,
 * which sets it from its shell — so sign-in and the status page follow the choice too.
 */
export function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
}

function currentTheme(): Theme {
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'dark'
}

/** The sun and the moon, swapping with a half turn — the staff panel's toggle. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme)
  const [flip, setFlip] = useState(false)

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    applyTheme(next)
    try {
      localStorage.setItem(THEME_KEY, next)
    } catch {
      // The choice holds for this visit only.
    }
    setTheme(next)
    setFlip(true)
    window.setTimeout(() => setFlip(false), 720)
  }

  return (
    <button
      className="btn ghost small theme-toggle"
      type="button"
      onClick={toggle}
      title="Mavzuni almashtirish"
      aria-label="Mavzuni almashtirish"
    >
      <span className={`glyph${flip ? ' flip' : ''}`} key={theme}>
        {theme === 'dark' ? '☾' : '☀'}
      </span>
    </button>
  )
}
