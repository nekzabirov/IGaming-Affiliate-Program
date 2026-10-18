import { ApiError } from '@/api/client'
import { config } from '@/lib/session'

/**
 * The banners live on a site of their own, full quality, beside the catalogue that lists them: a new banner reaches
 * partners with a deploy of that site, not of the cabinet. The server names the catalogue (PROMO_MANIFEST_URL); a
 * deployment without one has no Promo screen.
 */
const manifestUrl = (): string => {
  const url = config().promoManifestUrl
  if (url === null) throw new Error('This deployment has no promo library')
  return url
}

export const hasPromo = () => config().promoManifestUrl !== null

export type PromoFormat = 'square' | 'story'

export interface PromoItem {
  id: string
  topic: string
  title: string
  language: string
  format: PromoFormat
  width: number
  height: number
  file: string
  preview: string
  bytes: number
}

export interface PromoCatalogue {
  topics: { id: string; title: string }[]
  formats: { id: PromoFormat; label: string; width: number; height: number }[]
  items: PromoItem[]
}

/** A file of the library: its path in the catalogue is relative to the catalogue's own folder. */
export const promoUrl = (path: string) => {
  const url = manifestUrl()
  return `${url.slice(0, url.lastIndexOf('/'))}/${path}`
}

export async function loadPromo(signal: AbortSignal): Promise<PromoCatalogue> {
  let response: Response
  try {
    response = await fetch(manifestUrl(), { signal, cache: 'no-cache' })
  } catch (cause) {
    if (signal.aborted) throw cause
    throw new ApiError(0, 'NETWORK', 'Could not reach the promo library. Check your connection and try again.')
  }
  if (!response.ok) throw new ApiError(response.status, 'UNAVAILABLE', 'The promo library is unavailable right now. Try again in a minute.')
  return (await response.json()) as PromoCatalogue
}

export const formatBytes = (bytes: number) => (bytes >= 1_000_000 ? `${(bytes / 1_000_000).toFixed(1)} MB` : `${Math.round(bytes / 1000)} KB`)
