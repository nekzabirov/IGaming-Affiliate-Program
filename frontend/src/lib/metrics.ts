import type { StatsDay } from '@/api/types'

/** The five measures of a period, as the stats answer carries them by day. */
export type Metric = Exclude<keyof StatsDay, 'day'>

export interface MetricMeta {
  key: Metric
  label: string
  money: boolean
  /** The measure's series colour from the brand notes' chart order: it keeps it on the tile, the line and the key. */
  color: string
}

export const METRICS: MetricMeta[] = [
  { key: 'earned', label: 'Earned', money: true, color: 'var(--color-series-earned)' },
  { key: 'registrations', label: 'Registrations', money: false, color: 'var(--color-series-registrations)' },
  { key: 'firstDeposits', label: 'First deposits', money: false, color: 'var(--color-series-ftd)' },
  { key: 'depositAmount', label: 'Deposits', money: true, color: 'var(--color-series-deposits)' },
  { key: 'turnover', label: 'Turnover', money: true, color: 'var(--color-series-turnover)' },
]

export const metricMeta = (key: Metric): MetricMeta => METRICS.find((item) => item.key === key) ?? METRICS[0]!
