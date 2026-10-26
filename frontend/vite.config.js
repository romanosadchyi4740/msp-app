import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { alphaTab } from '@coderline/alphatab-vite'

// https://vite.dev/config/
export default defineConfig({
  // alphaTab: copies its music fonts into the build and keeps its web worker loadable
  plugins: [react(), alphaTab()],
  // alphaTab's worker uses import.meta, which only ES module workers support
  worker: {
    format: 'es',
  },
  // Pre-bundling moves alphaTab into .vite/deps, where it no longer finds its fonts and worker in development
  optimizeDeps: {
    exclude: ['@coderline/alphatab'],
  },
  define: {
    global: 'window',
  },
  server: {
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin-allow-popups',
    },
  },
})
