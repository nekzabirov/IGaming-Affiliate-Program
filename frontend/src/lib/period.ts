import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PRESETS, isDay, presetRange, rangeError, type Preset, type Range } from './dates'

export interface Period {
  preset: Preset | 'custom'
  range: Range
}

const DEFAULT: Preset = '30d'

const isPreset = (value: unknown): value is Preset => PRESETS.some((preset) => preset.key === value)

/**
 * The stats period lives in the address (`?period=7d` or `?from=&to=`), so a reload or a shared link shows
 * the same numbers. The default, the last 30 days, keeps the address clean.
 */
export function usePeriod() {
  const route = useRoute()
  const router = useRouter()

  const period = computed<Period>(() => {
    const { period: preset, from, to } = route.query
    if (isPreset(preset)) return { preset, range: presetRange(preset) }
    if (isDay(from) && isDay(to) && !rangeError({ from, to })) return { preset: 'custom', range: { from, to } }
    return { preset: DEFAULT, range: presetRange(DEFAULT) }
  })

  function choose(next: Period) {
    const query = { ...route.query }
    delete query.period
    delete query.from
    delete query.to
    if (next.preset === 'custom') Object.assign(query, next.range)
    else if (next.preset !== DEFAULT) query.period = next.preset
    void router.replace({ query })
  }

  return { period, choose }
}
