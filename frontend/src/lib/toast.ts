import { reactive } from 'vue'

export type ToastTone = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  message: string
  tone: ToastTone
}

const VISIBLE_MS = 3600

export const toasts = reactive<Toast[]>([])

let nextId = 1

export function dismiss(id: number) {
  const index = toasts.findIndex((item) => item.id === id)
  if (index >= 0) toasts.splice(index, 1)
}

export function toast(message: string, tone: ToastTone = 'success') {
  const id = nextId++
  // A burst of errors stacks three deep at most; older news is no longer news
  if (toasts.length >= 3) toasts.shift()
  toasts.push({ id, message, tone })
  window.setTimeout(() => dismiss(id), tone === 'error' ? VISIBLE_MS * 1.5 : VISIBLE_MS)
}
