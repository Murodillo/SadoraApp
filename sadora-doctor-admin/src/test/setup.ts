import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'
import { clearPhotoCache } from '../api/photos'

// jsdom's browser asks for en-US; the panel's doctors browse in Uzbek, and the requests
// the tests assert carry the language the panel picks from the browser.
Object.defineProperty(navigator, 'languages', { value: ['uz-UZ', 'ru'], configurable: true })
Object.defineProperty(navigator, 'language', { value: 'uz-UZ', configurable: true })

// No test globals, so Testing Library cannot register its own cleanup.
afterEach(() => {
  cleanup()
  clearPhotoCache()
  sessionStorage.clear()
  localStorage.clear()
})
