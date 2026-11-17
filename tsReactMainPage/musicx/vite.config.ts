import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: '/',
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
  },
  server: {
    allowedHosts: [
      'relativistic-xzavier-subsimian.ngrok-free.dev',
      'musicxapp.duckdns.org'
    ]
  }
})