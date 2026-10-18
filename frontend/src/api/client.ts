import type { ErrorBody } from './types'

/** Client-side codes for failures that never reached the API's own error body. */
export type TransportCode = 'NETWORK' | 'UNAVAILABLE'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ErrorBody['code'] | TransportCode,
    message: string,
    readonly field?: string,
    readonly rule?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

type QueryValue = string | number | boolean | null | undefined

type Query = Record<string, QueryValue>

interface RequestOptions {
  query?: object
  body?: unknown
  signal?: AbortSignal
}

/** Set once at boot: what to do when the session is gone (expired, signed out elsewhere, blocked). */
let sessionLost: (error: ApiError) => void = () => {}

export function onSessionLost(handler: (error: ApiError) => void) {
  sessionLost = handler
}

function queryString(query?: object): string {
  if (!query) return ''
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query as Query)) {
    if (value === undefined || value === null || value === '') continue
    params.set(key, String(value))
  }
  const text = params.toString()
  return text ? `?${text}` : ''
}

async function errorOf(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ErrorBody>
    if (typeof body.code === 'string') {
      return new ApiError(response.status, body.code, body.message ?? body.code, body.field, body.rule)
    }
  } catch {
    // A proxy's HTML page or an empty body: classified by status below
  }
  if (response.status >= 502 && response.status <= 504) {
    return new ApiError(response.status, 'UNAVAILABLE', 'The service is unavailable right now. Try again in a minute.')
  }
  return new ApiError(response.status, 'INTERNAL', 'Something went wrong. Try again.')
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const mutating = method !== 'GET'
  let response: Response
  try {
    response = await fetch(`/api${path}${queryString(options.query)}`, {
      method,
      credentials: 'same-origin',
      // The API refuses a mutating request that is not JSON (its CSRF guard), bodiless ones included
      headers: mutating ? { Accept: 'application/json', 'Content-Type': 'application/json' } : { Accept: 'application/json' },
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (cause) {
    if (options.signal?.aborted) throw cause
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Check your connection and try again.')
  }

  if (!response.ok) {
    const error = await errorOf(response)
    // The sign-in forms answer their own 401/403; anywhere else it means the session is over
    const lost = error.code === 'UNAUTHENTICATED' || (error.code === 'ACCOUNT_BLOCKED' && !path.startsWith('/auth/'))
    if (lost) sessionLost(error)
    throw error
  }

  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const http = {
  get: <T>(path: string, query?: object, signal?: AbortSignal) => request<T>('GET', path, { query, signal }),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, { body }),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, { body }),
  delete: <T>(path: string) => request<T>('DELETE', path),
}

export function asApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  return new ApiError(0, 'INTERNAL', 'Something went wrong. Try again.')
}
