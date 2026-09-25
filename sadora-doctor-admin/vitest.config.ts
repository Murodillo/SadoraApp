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
    // JUnit beside the coverage, so a pull request comment can count these tests too.
    reporters: ['default', 'junit'],
    outputFile: { junit: 'coverage/junit/TEST-doctor-admin.xml' },
    unstubGlobals: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx'],
      reporter: ['text-summary', 'json-summary', 'lcov', 'html'],
      reportsDirectory: 'coverage',
      // Floors for the code every screen stands on: the client with its token renewal,
      // the session, and the phone rules mirrored from the contract. Pages are held by
      // their flow tests rather than a number; raise these as more gets tested.
      thresholds: {
        'src/api/client.ts': { lines: 90, branches: 80, functions: 90 },
        'src/api/phone.ts': { lines: 100, functions: 100 },
        'src/auth/AuthContext.tsx': { lines: 85, functions: 80 },
      },
    },
  },
})
