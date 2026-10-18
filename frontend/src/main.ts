import { createApp } from 'vue'
import { onSessionLost } from '@/api/client'
import { boot, session } from '@/lib/session'
import App from './App.vue'
import { router } from './router'
import './styles/main.css'

const RELOADED = 'reloaded-after-deploy'

// A tab left open across a deploy asks for chunks that no longer exist: one reload picks up the new build.
// Without session storage (a locked-down browser) there is no way to stop a reload loop, so none is tried
window.addEventListener('vite:preloadError', () => {
  try {
    if (sessionStorage.getItem(RELOADED)) return
    sessionStorage.setItem(RELOADED, '1')
  } catch {
    return
  }
  window.location.reload()
})

onSessionLost((error) => {
  if (error.code === 'ACCOUNT_BLOCKED') session.notice = 'blocked'
  if (!session.me) return
  session.me = null
  if (error.code === 'UNAUTHENTICATED') session.notice = 'expired'
  const current = router.currentRoute.value
  void router.replace({ name: 'login', query: current.meta.guest ? {} : { next: current.fullPath } })
})

/** Plain DOM: the app could not boot, so none of its components can be trusted to render. */
function unreachable() {
  const root = document.getElementById('app')
  if (!root) return
  const box = document.createElement('div')
  box.className = 'mx-auto flex min-h-dvh max-w-sm flex-col items-center justify-center gap-3 px-gutter text-center'
  const title = document.createElement('p')
  title.className = 'text-section font-extrabold text-text'
  title.textContent = 'The partner cabinet is not reachable'
  const text = document.createElement('p')
  text.className = 'text-name text-text-muted'
  text.textContent = 'Check your connection, then try again.'
  const button = document.createElement('button')
  button.type = 'button'
  button.className = 'mt-3 h-11 rounded-md border border-line-strong bg-wash px-5 text-body font-bold text-text'
  button.textContent = 'Try again'
  button.addEventListener('click', () => window.location.reload())
  box.append(title, text, button)
  root.replaceChildren(box)
}

async function start() {
  try {
    await boot()
  } catch {
    unreachable()
    return
  }
  try {
    sessionStorage.removeItem(RELOADED)
  } catch {
    // Nothing was stored without session storage either
  }
  createApp(App).use(router).mount('#app')
}

void start()
