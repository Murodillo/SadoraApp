import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The API is proxied rather than called cross-origin: the browser then talks to one
// origin, which is how this is deployed anyway, and the server's CORS list does not
// have to learn about the doctor panel at all.
const proxy = {
  '/v1': {
    target: process.env.SADORA_API ?? 'http://localhost:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [react()],
  server: {
    // 5173 is the staff admin panel; the two run side by side against one backend.
    port: 5174,
    proxy,
    // A tunnel publishes the panel under a hostname this config cannot know, and Vite
    // refuses unknown Host headers by default. Only ever a development server.
    allowedHosts: true,
  },
  // `preview` serves the built bundle — what a tunnelled demo should show.
  preview: {
    port: 4174,
    proxy,
    allowedHosts: true,
  },
})
