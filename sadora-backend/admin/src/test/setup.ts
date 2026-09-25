import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// No test globals, so Testing Library cannot register its own cleanup.
afterEach(() => {
  cleanup()
  sessionStorage.clear()
  localStorage.clear()
})
