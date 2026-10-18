import { onScopeDispose, ref, shallowRef } from 'vue'
import { asApiError, type ApiError } from '@/api/client'

/**
 * One remote value with its loading and error state. A new run aborts the one in flight, so a slow answer
 * to an old filter can never overwrite the answer to the current one.
 */
export function useResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const data = shallowRef<T | null>(null)
  const error = shallowRef<ApiError | null>(null)
  const loading = ref(false)
  let controller: AbortController | null = null

  async function run(): Promise<void> {
    controller?.abort()
    const current = new AbortController()
    controller = current
    loading.value = true
    error.value = null
    try {
      const result = await load(current.signal)
      if (!current.signal.aborted) data.value = result
    } catch (cause) {
      if (!current.signal.aborted) error.value = asApiError(cause)
    } finally {
      if (controller === current) loading.value = false
    }
  }

  onScopeDispose(() => controller?.abort())

  return { data, error, loading, run }
}
