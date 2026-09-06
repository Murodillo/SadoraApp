import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The API is proxied rather than called cross-origin: the browser then talks to one
// origin, which is how this is deployed anyway, and CORS stops being a dev-only concern.
const proxy = {
  '/v1': {
    target: process.env.SADORA_API ?? 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy,
    // A tunnel publishes the panel under a hostname this config cannot know, and Vite
    // refuses unknown Host headers by default. Only ever a development server.
    allowedHosts: true,
  },
  // `preview` serves the built bundle, which is what a tunnelled demo should show: no
  // HMR socket to keep alive over the tunnel, and the same files the client would get.
  preview: {
    port: 4173,
    proxy,
    allowedHosts: true,
  },
})
