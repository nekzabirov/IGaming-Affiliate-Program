<script setup lang="ts">
import { computed, onBeforeUnmount, ref, useId, watch } from 'vue'
import type { StatsDay } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import { toNumber } from '@/lib/decimal'
import { formatCompact, formatCount, formatDay, formatDayLong, formatMoney } from '@/lib/format'
import { METRICS, metricMeta, type Metric } from '@/lib/metrics'

/**
 * One measure by day on one axis: a 2 px line in the measure's colour over a faint wash of it, like a live
 * trace. Hovering, touching or arrowing through the plot reads any day out under a crosshair; the table view
 * carries every value of every measure for anyone who would rather not aim.
 */
const props = defineProps<{ daily: StatsDay[]; currency: string; metric: Metric }>()

const TOP = 16

const AXIS = 30

const RIGHT = 12

const asTable = ref(false)

const current = computed(() => metricMeta(props.metric))

const values = computed(() => props.daily.map((day) => Number(toNumber(String(day[props.metric])))))

const count = computed(() => values.value.length)

const empty = computed(() => values.value.every((value) => value === 0))

const box = ref<HTMLElement | null>(null)

const width = ref(0)

const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(([entry]) => entry && (width.value = entry.contentRect.width))

watch(box, (element, previous) => {
  if (previous) observer?.unobserve(previous)
  if (element) {
    width.value = element.clientWidth
    observer?.observe(element)
  }
})

onBeforeUnmount(() => observer?.disconnect())

const height = computed(() => (width.value < 640 ? 220 : 280))

function niceStep(span: number, ticks: number): number {
  const rough = span / ticks
  const power = 10 ** Math.floor(Math.log10(rough))
  const unit = rough / power
  return (unit <= 1 ? 1 : unit <= 2 ? 2 : unit <= 2.5 ? 2.5 : unit <= 5 ? 5 : 10) * power
}

const scale = computed(() => {
  const low = Math.min(0, ...values.value)
  const high = Math.max(0, ...values.value)
  const span = high - low || 1
  // At most four intervals: the top tick stays close to the tallest day instead of doubling the headroom
  let step = niceStep(span, 4)
  if (!current.value.money) step = Math.max(1, Math.round(step))
  const lo = Math.floor(low / step) * step
  const hi = Math.max(Math.ceil(high / step) * step, lo + step)
  const ticks: number[] = []
  for (let tick = lo; tick <= hi + step / 2; tick += step) ticks.push(Number(tick.toFixed(9)))
  return { lo, hi, ticks }
})

const tickLabel = (value: number) => (current.value.money && scale.value.hi < 10 ? value.toFixed(2) : formatCompact(value))

const left = computed(() => Math.max(24, ...scale.value.ticks.map((tick) => tickLabel(tick).length * 7)) + 14)

const plotWidth = computed(() => Math.max(0, width.value - left.value - RIGHT))

const plotHeight = computed(() => height.value - TOP - AXIS)

const y = (value: number) => TOP + plotHeight.value * (1 - (value - scale.value.lo) / (scale.value.hi - scale.value.lo))

const x = (index: number) => left.value + (count.value <= 1 ? plotWidth.value / 2 : (plotWidth.value * index) / (count.value - 1))

/**
 * A monotone cubic through every day (Fritsch–Carlson): the trace reads as one calm line, still passes
 * exactly through each day's value, and never bulges past a day's neighbours, so no peak is invented
 * between two days.
 */
function monotonePath(points: [number, number][]): string {
  const n = points.length
  const at = (index: number) => points[index]!
  if (n < 3) return points.map(([px, py], index) => `${index ? 'L' : 'M'}${px.toFixed(1)},${py.toFixed(1)}`).join('')
  const slopes: number[] = []
  for (let i = 0; i < n - 1; i++) slopes.push((at(i + 1)[1] - at(i)[1]) / (at(i + 1)[0] - at(i)[0] || 1))
  const tangents: number[] = [slopes[0]!]
  for (let i = 1; i < n - 1; i++) tangents.push(slopes[i - 1]! * slopes[i]! <= 0 ? 0 : (slopes[i - 1]! + slopes[i]!) / 2)
  tangents.push(slopes[n - 2]!)
  for (let i = 0; i < n - 1; i++) {
    const slope = slopes[i]!
    if (slope === 0) {
      tangents[i] = 0
      tangents[i + 1] = 0
      continue
    }
    const a = tangents[i]! / slope
    const b = tangents[i + 1]! / slope
    const length = a * a + b * b
    if (length > 9) {
      const tau = 3 / Math.sqrt(length)
      tangents[i] = tau * a * slope
      tangents[i + 1] = tau * b * slope
    }
  }
  let path = `M${at(0)[0].toFixed(1)},${at(0)[1].toFixed(1)}`
  for (let i = 0; i < n - 1; i++) {
    const [x0, y0] = at(i)
    const [x1, y1] = at(i + 1)
    const third = (x1 - x0) / 3
    path += `C${(x0 + third).toFixed(1)},${(y0 + tangents[i]! * third).toFixed(1)} ${(x1 - third).toFixed(1)},${(y1 - tangents[i + 1]! * third).toFixed(1)} ${x1.toFixed(1)},${y1.toFixed(1)}`
  }
  return path
}

const linePath = computed(() => monotonePath(values.value.map((value, index) => [x(index), y(value)])))

const areaPath = computed(() => (count.value ? `${linePath.value}L${x(count.value - 1).toFixed(1)},${y(0).toFixed(1)}L${x(0).toFixed(1)},${y(0).toFixed(1)}Z` : ''))

const washId = useId()

const xLabels = computed(() => {
  if (!count.value || !plotWidth.value) return []
  const fits = Math.max(2, Math.floor(plotWidth.value / 72))
  const step = Math.max(1, Math.ceil(count.value / fits))
  const picked: number[] = []
  // Counted back from the last day, so the newest day always carries a label
  for (let index = count.value - 1; index >= 0; index -= step) picked.push(index)
  return picked.reverse().map((index) => ({
    index,
    x: Math.min(Math.max(x(index), left.value + 22), width.value - RIGHT - 22),
    text: formatDay(props.daily[index]?.day ?? ''),
  }))
})

const active = ref<number | null>(null)

function onPointer(event: PointerEvent) {
  const offset = event.clientX - (event.currentTarget as Element).getBoundingClientRect().left - left.value
  const index = Math.round((offset / Math.max(1, plotWidth.value)) * (count.value - 1))
  active.value = Math.min(count.value - 1, Math.max(0, index))
}

function onKey(event: KeyboardEvent) {
  const last = count.value - 1
  const at = active.value ?? last
  const next = { ArrowLeft: at - 1, ArrowRight: at + 1, Home: 0, End: last }[event.key]
  if (next === undefined) {
    if (event.key === 'Escape') active.value = null
    return
  }
  event.preventDefault()
  active.value = Math.min(last, Math.max(0, next))
}

function valueText(day: StatsDay, which: Metric = props.metric): string {
  const value = day[which]
  return metricMeta(which).money ? formatMoney(String(value), props.currency) : formatCount(Number(value))
}

const tip = computed(() => {
  if (active.value === null) return null
  const day = props.daily[active.value]
  if (!day) return null
  // The readout sits above the day it reads, or under it when the day is too close to the top to fit
  const point = y(values.value[active.value] ?? 0)
  return {
    day: formatDayLong(day.day),
    value: valueText(day),
    left: Math.min(Math.max(x(active.value), 84), width.value - 84),
    top: point > 72 ? point - 64 : point + 14,
  }
})

const summary = computed(() => {
  const first = props.daily[0]
  const last = props.daily[count.value - 1]
  if (!first || !last) return ''
  let best = 0
  values.value.forEach((value, index) => (value > (values.value[best] ?? 0) ? (best = index) : null))
  const top = props.daily[best]
  return `${current.value.label} by day, ${formatDay(first.day)} to ${formatDay(last.day)}.${top && !empty.value ? ` Highest: ${valueText(top)} on ${formatDay(top.day)}.` : ''}`
})

/** The draw-in replays when what is drawn changes, never on a resize. */
const drawKey = computed(() => `${props.metric}:${props.daily[0]?.day}:${count.value}`)

const rows = computed(() => [...props.daily].reverse())
</script>

<template>
  <section class="surface">
    <header class="flex items-start justify-between gap-3 px-4 pt-4 lg:px-5 lg:pt-5">
      <div class="min-w-0">
        <h2 class="flex items-center gap-2.5 text-section font-extrabold text-text">
          <span class="h-0.5 w-4 shrink-0 rounded-full" :style="{ backgroundColor: current.color }" aria-hidden="true" />
          {{ current.label }} by day
        </h2>
        <p class="mt-1 text-body text-text-faint">{{ current.money ? `${currency} · ` : '' }}UTC days. Choose another figure above to chart it.</p>
      </div>
      <button
        type="button"
        class="pressable hit-area flex h-9 shrink-0 items-center gap-1.5 rounded-sm border px-3 text-body font-bold"
        :class="asTable ? 'border-brand/50 bg-brand/16 text-brand-soft' : 'border-line-strong text-text-muted hover:text-text'"
        :aria-pressed="asTable"
        @click="asTable = !asTable"
      >
        <Icon name="table" :size="16" />
        Table
      </button>
    </header>

    <div class="px-4 pt-4 pb-4 lg:px-5 lg:pb-5">
      <div v-if="asTable" class="scroll-thin max-h-80 overflow-auto rounded-md border border-line-soft">
        <table class="w-full min-w-[560px] text-body">
          <thead class="sticky top-0 bg-card">
            <tr class="text-left text-text-faint">
              <th class="micro-label px-3 py-2.5 text-micro font-bold">Day (UTC)</th>
              <th v-for="item in METRICS" :key="item.key" class="micro-label px-3 py-2.5 text-right text-micro font-bold">{{ item.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="day in rows" :key="day.day" class="border-t border-line-soft">
              <td class="px-3 py-2 whitespace-nowrap text-text-muted">{{ formatDayLong(day.day) }}</td>
              <td
                v-for="item in METRICS"
                :key="item.key"
                class="num px-3 py-2 text-right whitespace-nowrap"
                :class="item.key === metric ? 'text-text' : 'text-text-muted'"
              >
                {{ item.money ? formatMoney(String(day[item.key])) : formatCount(Number(day[item.key])) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-else-if="count <= 1" class="grid h-40 place-items-center rounded-md bg-page/50 px-6 text-center text-name text-text-muted">
        One day is selected and the figures above cover it. Pick a longer period to see the days side by side.
      </p>

      <div v-else ref="box" class="relative" :style="{ height: `${height}px` }">
        <svg
          v-if="width"
          :width="width"
          :height="height"
          class="block touch-pan-y outline-none select-none focus-visible:rounded-sm focus-visible:outline-2 focus-visible:outline-brand"
          role="img"
          :aria-label="summary"
          tabindex="0"
          @pointermove="onPointer"
          @pointerdown="onPointer"
          @pointerleave="active = null"
          @keydown="onKey"
          @blur="active = null"
        >
          <g aria-hidden="true">
            <line
              v-for="tick in scale.ticks"
              :key="tick"
              :x1="left"
              :x2="width - RIGHT"
              :y1="y(tick)"
              :y2="y(tick)"
              :class="tick === 0 ? 'stroke-line-strong' : 'stroke-grid'"
              stroke-width="1"
              shape-rendering="crispEdges"
            />
            <text
              v-for="tick in scale.ticks"
              :key="`label-${tick}`"
              :x="left - 12"
              :y="y(tick)"
              text-anchor="end"
              dominant-baseline="middle"
              class="num fill-text-faint text-[11px]"
            >
              {{ tickLabel(tick) }}
            </text>
            <text v-for="label in xLabels" :key="`day-${label.index}`" :x="label.x" :y="height - 8" text-anchor="middle" class="num fill-text-faint text-[11px]">
              {{ label.text }}
            </text>
          </g>

          <defs>
            <!-- The wash under the trace: the brand's signal fill (12-14 % of the colour) at the line, gone at the baseline -->
            <linearGradient :id="washId" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0" :style="{ stopColor: current.color }" stop-opacity="0.14" />
              <stop offset="1" :style="{ stopColor: current.color }" stop-opacity="0" />
            </linearGradient>
          </defs>

          <g :key="drawKey" class="draw-line" aria-hidden="true">
            <path :d="areaPath" :fill="`url(#${washId})`" />
            <path :d="linePath" :style="{ stroke: current.color }" fill="none" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
            <circle
              v-if="active === null"
              :cx="x(count - 1)"
              :cy="y(values[count - 1] ?? 0)"
              r="4"
              :style="{ fill: current.color }"
              class="stroke-panel"
              stroke-width="2"
            />
          </g>

          <g v-if="active !== null" aria-hidden="true">
            <line :x1="x(active)" :x2="x(active)" :y1="TOP" :y2="TOP + plotHeight" class="stroke-text-faint" stroke-opacity="0.5" stroke-width="1" />
            <circle :cx="x(active)" :cy="y(values[active] ?? 0)" r="4.5" :style="{ fill: current.color }" class="stroke-panel" stroke-width="2" />
          </g>
        </svg>

        <p v-if="empty" class="pointer-events-none absolute inset-x-0 top-[36%] text-center text-name text-text-muted">Nothing here yet for this period.</p>

        <div
          v-if="tip"
          class="pointer-events-none absolute z-10 min-w-36 -translate-x-1/2 rounded-md border border-line bg-raise px-3 py-2 shadow-pop"
          :style="{ left: `${tip.left}px`, top: `${tip.top}px` }"
        >
          <p class="num text-name font-semibold whitespace-nowrap text-text">{{ tip.value }}</p>
          <p class="mt-0.5 flex items-center gap-1.5 text-micro whitespace-nowrap text-text-faint">
            <span class="h-0.5 w-3 rounded-full" :style="{ backgroundColor: current.color }" aria-hidden="true" />
            {{ tip.day }}
          </p>
        </div>
        <p class="sr-only" aria-live="polite">{{ tip ? `${tip.day}: ${tip.value}` : '' }}</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.draw-line {
  animation: wipe-in 560ms var(--ease-default) both;
}
@keyframes wipe-in {
  from {
    clip-path: inset(0 100% 0 0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .draw-line {
    animation: none;
  }
}
</style>
