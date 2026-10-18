<script setup lang="ts">
import { computed } from 'vue'
import type { StatsCountry } from '@/api/types'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import { countryName, formatCount } from '@/lib/format'

/**
 * Where the period's players came from: a thin bar per country for its registrations (the registrations
 * series colour, as on the chart), first deposits as a number beside it.
 */
const props = defineProps<{ countries: StatsCountry[] }>()

const peak = computed(() => Math.max(1, ...props.countries.map((country) => country.registrations)))
</script>

<template>
  <section class="surface">
    <header class="px-4 pt-4 lg:px-5 lg:pt-5">
      <h2 class="text-section font-extrabold text-text">Top countries</h2>
      <p class="mt-1 text-body text-text-faint">Players by country in the period</p>
    </header>
    <div v-if="countries.length" class="px-4 pt-4 pb-5 lg:px-5">
      <div class="micro-label flex gap-3 pb-2.5 text-micro text-text-faint" aria-hidden="true">
        <span class="flex-1">Country</span>
        <span class="w-12 text-right">Reg.</span>
        <span class="w-10 text-right">FTD</span>
      </div>
      <ol class="space-y-3">
        <li v-for="country in countries" :key="country.country ?? 'unknown'">
          <div class="flex items-baseline gap-3 text-body">
            <p class="flex min-w-0 flex-1 items-baseline gap-2">
              <span class="truncate font-semibold text-text">{{ countryName(country.country) }}</span>
              <span v-if="country.country" class="num text-micro text-text-faint">{{ country.country.toUpperCase() }}</span>
            </p>
            <span class="num w-12 text-right text-text">{{ formatCount(country.registrations) }}</span>
            <span class="num w-10 text-right text-text-muted">{{ formatCount(country.firstDeposits) }}</span>
          </div>
          <div class="mt-1.5 h-1 rounded-full bg-wash" aria-hidden="true">
            <div class="h-full rounded-full bg-series-registrations" :style="{ width: `max(4px, ${(country.registrations / peak) * 100}%)` }" />
          </div>
        </li>
      </ol>
    </div>
    <UiEmpty v-else icon="globe" title="No players yet" text="Countries appear here once players sign up through your links." compact />
  </section>
</template>
