import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Separate from vite.config.ts so the production build never loads test tooling.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
    unstubGlobals: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
      // json-summary feeds the pull request comment; lcov and html are for people.
      reporter: ['text-summary', 'json-summary', 'lcov', 'html'],
      reportsDirectory: 'coverage',
      // Floors for the code that has tests — the client every page goes through, the
      // session, and the limits mirrored from the server. Pages are covered by the
      // backend's API tests and a person's eyes for now; add a floor when a page gets tests.
      thresholds: {
        'src/api/client.ts': { lines: 95, branches: 90, functions: 95 },
        'src/api/limits.ts': { lines: 100, functions: 100 },
        'src/auth/AuthContext.tsx': { lines: 95, functions: 95 },
      },
    },
  },
})
