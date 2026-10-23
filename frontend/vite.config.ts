import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The frontend talks to the backend only through `/api`. In dev we proxy it to
// the local Quarkus process; in docker compose the nginx image does the same so
// the browser always sees a single origin (keeps SSE and later cookies simple).
// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    proxy: {
      '/api': {
        target: process.env.BACKEND_ORIGIN ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
