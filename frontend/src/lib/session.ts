import { reactive } from 'vue'
import { api } from '@/api'
import { ApiError } from '@/api/client'
import type { Config, Me } from '@/api/types'

interface Session {
  me: Me | null
  /** Why the sign-in screen is shown, when it is not the visitor's own choice. */
  notice: 'blocked' | 'expired' | null
}

export const session = reactive<Session>({ me: null, notice: null })

let loaded: Config | null = null

/** The public configuration; it is loaded before the app mounts and never changes while it runs. */
export function config(): Config {
  if (!loaded) throw new Error('The configuration is read before boot')
  return loaded
}

async function currentAccount(): Promise<Me | null> {
  try {
    return await api.me()
  } catch (error) {
    if (error instanceof ApiError && (error.code === 'UNAUTHENTICATED' || error.code === 'ACCOUNT_BLOCKED')) return null
    throw error
  }
}

export async function boot(): Promise<void> {
  const [settings, me] = await Promise.all([api.config(), currentAccount()])
  loaded = settings
  session.me = me
}

export async function refreshMe(): Promise<void> {
  session.me = await api.me()
}

export function signedIn(me: Me) {
  session.me = me
  session.notice = null
}

export async function signOut(): Promise<void> {
  try {
    await api.logout()
  } finally {
    session.me = null
  }
}

export const isAdmin = () => session.me?.role === 'ADMIN'
