import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The API is proxied rather than called cross-origin: the browser then talks to one
// origin, which is how this is deployed anyway, and CORS stops being a dev-only concern.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/v1': {
        target: process.env.SADORA_API ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
