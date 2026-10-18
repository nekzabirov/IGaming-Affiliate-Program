import { readFileSync } from 'node:fs'
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig, type Plugin } from 'vite'
import { BRAND } from './src/brand.ts'

/** The page colour, the same as theme.css's: the manifest's splash screen and the browser's chrome. */
const PAGE_COLOR = '#0b0a0f'

const escapeHtml = (text: string) => text.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const manifest = () =>
  `${JSON.stringify(
    {
      name: BRAND.name,
      short_name: BRAND.shortName,
      description: BRAND.appDescription,
      start_url: '/',
      scope: '/',
      display: 'standalone',
      background_color: PAGE_COLOR,
      theme_color: PAGE_COLOR,
      icons: [
        { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
        { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
      ],
    },
    null,
    2,
  )}\n`

/**
 * Writes the brand (src/brand.ts, src/assets/brand/logo-mark.svg) into what no component renders: the HTML shell,
 * whose loading mark shows before any script runs, and the web app manifest.
 */
function brand(): Plugin {
  const mark = () =>
    readFileSync(fileURLToPath(new URL('./src/assets/brand/logo-mark.svg', import.meta.url)), 'utf8')
      .trim()
      .replace(/^<svg xmlns="http:\/\/www\.w3\.org\/2000\/svg"/, '<svg width="48" height="40" aria-hidden="true"')
  return {
    name: 'brand',
    transformIndexHtml: (html) =>
      html
        .replaceAll('%BRAND_NAME%', escapeHtml(BRAND.name))
        .replaceAll('%BRAND_DESCRIPTION%', escapeHtml(BRAND.description))
        .replace('%BRAND_MARK%', mark()),
    configureServer(server) {
      server.middlewares.use('/manifest.webmanifest', (_request, response) => {
        response.setHeader('Content-Type', 'application/manifest+json')
        response.end(manifest())
      })
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: 'manifest.webmanifest', source: manifest() })
    },
  }
}

// The boot jar serves the SPA, so the build lands straight in the app's static resources
export default defineConfig({
  plugins: [vue(), tailwindcss(), brand()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      // VITE_API_TARGET points the dev server at another backend (a stand, a mock) without editing this file
      '/api': { target: process.env.VITE_API_TARGET ?? 'http://localhost:8080', changeOrigin: true },
    },
  },
})
