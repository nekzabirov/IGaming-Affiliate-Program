<script setup lang="ts">
import type { StatsTotals } from '@/api/types'
import { isNegative } from '@/lib/decimal'
import { formatConversion, formatCount, formatMoney } from '@/lib/format'
import { METRICS, type Metric } from '@/lib/metrics'
import EarnedByType from './EarnedByType.vue'

/**
 * The period in five instruments: what it earned first, then the funnel that earned it. They are also the
 * chart's switch, so the measure on the chart is always the instrument lit in signal right above it.
 */
const props = defineProps<{ totals: StatsTotals; currency: string; breakdown?: boolean }>()

const selected = defineModel<Metric>({ required: true })

function figure(key: Metric): string {
  const totals = props.totals
  switch (key) {
    case 'registrations':
    case 'firstDeposits':
      return formatCount(totals[key])
    default:
      return formatMoney(totals[key])
  }
}

function move(step: number) {
  const index = METRICS.findIndex((item) => item.key === selected.value)
  const next = METRICS[(index + step + METRICS.length) % METRICS.length]
  if (next) selected.value = next.key
}

function focusSelected(event: KeyboardEvent) {
  const group = event.currentTarget as HTMLElement
  requestAnimationFrame(() => group.querySelector<HTMLElement>('[aria-checked="true"]')?.focus())
}
</script>

<template>
  <div
    role="radiogroup"
    aria-label="Measure on the chart"
    class="grid grid-cols-2 gap-3"
    :class="breakdown ? 'xl:grid-cols-4' : 'xl:grid-cols-5'"
    @keydown.left.prevent="move(-1), focusSelected($event)"
    @keydown.right.prevent="move(1), focusSelected($event)"
  >
    <button
      v-for="metric in METRICS"
      :key="metric.key"
      type="button"
      role="radio"
      :aria-checked="selected === metric.key"
      :tabindex="selected === metric.key ? 0 : -1"
      class="tile glass flex min-w-0 flex-col items-stretch p-4 text-left lg:p-5"
      :class="[selected === metric.key ? 'glass-signal' : 'hover:border-line-strong', metric.key === 'earned' && (breakdown ? 'col-span-2 xl:row-span-2' : 'col-span-2 xl:col-span-1')]"
      @click="selected = metric.key"
    >
      <span class="flex items-center gap-2">
        <span class="tile-key" :style="{ backgroundColor: metric.color }" aria-hidden="true" />
        <span class="micro-label truncate text-micro text-text-faint">{{ metric.label }}</span>
      </span>

      <span class="mt-3 flex min-w-0 items-baseline gap-2">
        <span
          class="tile-figure num font-semibold"
          :class="[metric.key === 'earned' && isNegative(totals.earned) ? 'text-danger' : 'text-text', metric.key === 'earned' && breakdown && 'tile-figure-lead']"
          :style="{ '--chars': figure(metric.key).length + (metric.key === 'earned' ? currency.length + 1 : 0) }"
        >
          {{ figure(metric.key) }}
        </span>
        <span v-if="metric.key === 'earned'" class="num text-body text-text-faint">{{ currency }}</span>
      </span>

      <span v-if="metric.key === 'earned' && breakdown" class="mt-auto block pt-4">
        <EarnedByType :by-type="totals.earnedByType" />
      </span>
      <span v-else-if="metric.key === 'earned'" class="mt-1.5 text-body text-text-faint">Credited to your balance</span>
      <span v-else-if="metric.key === 'registrations'" class="mt-1.5 text-body text-text-faint">Players who signed up</span>
      <span v-else-if="metric.key === 'firstDeposits'" class="mt-1.5 text-body text-text-faint">
        Conversion <span class="num text-text-muted">{{ formatConversion(totals.conversion) }}</span>
      </span>
      <span v-else-if="metric.key === 'depositAmount'" class="mt-1.5 text-body text-text-faint">
        <span class="num text-text-muted">{{ formatCount(totals.depositCount) }}</span>
        {{ totals.depositCount === 1 ? 'deposit' : 'deposits' }} · {{ currency }}
      </span>
      <span v-else class="mt-1.5 text-body text-text-faint">Real-money stakes · {{ currency }}</span>
    </button>
  </div>
</template>

<style scoped>
.tile {
  /* The tile is the box its figure measures itself against (cqi below) */
  container-type: inline-size;
  transition-property: border-color, box-shadow, scale;
  transition-duration: var(--transition-duration-default);
  transition-timing-function: var(--ease-default);
}

.tile:active {
  scale: 0.985;
}

/* A mono digit is 0.6 em wide, so the figure takes the largest size at which all of its characters fit
   the tile; a short count stays big, a seven-figure turnover shrinks instead of running out of a phone's
   half-width tile */
.tile-figure {
  font-size: min(var(--type-amount), calc(100cqi / (var(--chars) * 0.62)));
  line-height: 1.15;
  white-space: nowrap;
}

/* The lead instrument of the dashboard: the period's earnings, a size up */
@media (width >= 80rem) {
  .tile-figure-lead {
    font-size: min(2rem, calc(100cqi / (var(--chars) * 0.62)));
  }
}

/* The measure's colour, cut at the mark's angle: the same key its line wears on the chart */
.tile-key {
  width: 3px;
  height: 12px;
  border-radius: 1px;
  flex-shrink: 0;
  transform: skewX(calc(-1 * var(--slant)));
}

@media (prefers-reduced-motion: reduce) {
  .tile:active {
    scale: 1;
  }
}
</style>
