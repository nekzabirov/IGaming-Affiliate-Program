import type { Day, Instant } from '@/api/types'
import { formatDecimal, plain } from './decimal'

const COUNT = new Intl.NumberFormat('en-US')

const COMPACT = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 })

// Dates follow the reader's locale; UTC days are pinned to UTC so a day never drifts across midnight
const DATE = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' })

const DATE_TIME = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })

const DAY_SHORT = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' })

const DAY_LONG = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })

const RELATIVE = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

const REGIONS = new Intl.DisplayNames(['en'], { type: 'region' })

export const formatCount = (value: number) => COUNT.format(value)

/** Axis ticks: 950, 1.2K, 3M. */
export const formatCompact = (value: number) => COMPACT.format(value)

/** Two decimals, grouped, with the currency when given: "1,234.50 USDT". */
export function formatMoney(value: string | null | undefined, currency?: string): string {
  const amount = formatDecimal(value, 2)
  return currency ? `${amount} ${currency}` : amount
}

export const formatPercent = (value: string) => `${plain(value)}%`

/** The API's conversion string ("12.5" or "12.5%") at one decimal. */
export function formatConversion(value: string | null | undefined): string {
  const number = Number((value ?? '').replace('%', '').trim())
  if (!Number.isFinite(number)) return '—'
  return `${Number(number.toFixed(1))}%`
}

const dayDate = (day: Day) => new Date(`${day}T00:00:00Z`)

export const formatDate = (instant: Instant) => DATE.format(new Date(instant))

export const formatDateTime = (instant: Instant) => DATE_TIME.format(new Date(instant))

export const formatDay = (day: Day) => DAY_SHORT.format(dayDate(day))

export const formatDayLong = (day: Day) => DAY_LONG.format(dayDate(day))

export function formatRelative(instant: Instant, now = Date.now()): string {
  const seconds = Math.round((new Date(instant).getTime() - now) / 1000)
  const magnitude = Math.abs(seconds)
  if (magnitude < 45) return 'just now'
  if (magnitude < 3600) return RELATIVE.format(Math.round(seconds / 60), 'minute')
  if (magnitude < 86400) return RELATIVE.format(Math.round(seconds / 3600), 'hour')
  if (magnitude < 7 * 86400) return RELATIVE.format(Math.round(seconds / 86400), 'day')
  return formatDate(instant)
}

export function countryName(code: string | null | undefined): string {
  if (!code) return 'Unknown'
  try {
    return REGIONS.of(code.toUpperCase()) ?? code
  } catch {
    return code
  }
}

/** Keeps both ends of a long identifier readable: "TQn9Y2Rt…3xLk8a". */
export function middle(text: string, head = 8, tail = 6): string {
  return text.length <= head + tail + 1 ? text : `${text.slice(0, head)}…${text.slice(-tail)}`
}
