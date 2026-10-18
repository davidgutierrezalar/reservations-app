import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Same-origin /api in dev, like nginx does in Docker, so the backend needs no CORS setup
  server: { proxy: { '/api': 'http://localhost:8080' } },
})
