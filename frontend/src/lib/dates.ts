import type { Day } from '@/api/types'

export type Preset = 'today' | '7d' | '30d' | '90d'

export interface Range {
  from: Day
  to: Day
}

export const PRESETS: { key: Preset; label: string; days: number }[] = [
  { key: 'today', label: 'Today', days: 1 },
  { key: '7d', label: '7 days', days: 7 },
  { key: '30d', label: '30 days', days: 30 },
  { key: '90d', label: '90 days', days: 90 },
]

/** The longest range the stats endpoint answers. */
export const MAX_RANGE_DAYS = 366

const DAY_MS = 86_400_000

export const isDay = (value: unknown): value is Day => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`))

/** Stats are kept by UTC day, so "today" is the UTC date, whatever the reader's clock says. */
export const todayUtc = (): Day => new Date().toISOString().slice(0, 10)

export function shiftDay(day: Day, delta: number): Day {
  return new Date(Date.parse(`${day}T00:00:00Z`) + delta * DAY_MS).toISOString().slice(0, 10)
}

/** Days in the range, both ends included. */
export const spanDays = (range: Range) => Math.round((Date.parse(`${range.to}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / DAY_MS) + 1

export function presetRange(preset: Preset): Range {
  const days = PRESETS.find((item) => item.key === preset)?.days ?? 30
  const to = todayUtc()
  return { from: shiftDay(to, -(days - 1)), to }
}

/** Why a custom range cannot be asked for, or null when it can. */
export function rangeError(range: Range): string | null {
  if (!isDay(range.from) || !isDay(range.to)) return 'Pick both days.'
  if (range.from > range.to) return 'The first day must come before the last one.'
  if (range.to > todayUtc()) return 'The range cannot end in the future.'
  if (spanDays(range) > MAX_RANGE_DAYS) return `At most ${MAX_RANGE_DAYS} days at once.`
  return null
}
