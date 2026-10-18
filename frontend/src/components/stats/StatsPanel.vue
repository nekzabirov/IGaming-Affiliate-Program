<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '@/api'
import UiError from '@/components/ui/UiError.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import type { Metric } from '@/lib/metrics'
import { usePeriod } from '@/lib/period'
import { useResource } from '@/lib/resource'
import { config } from '@/lib/session'
import CompanyStatsTable from './CompanyStatsTable.vue'
import CountryBars from './CountryBars.vue'
import DailyChart from './DailyChart.vue'
import KpiTiles from './KpiTiles.vue'
import PeriodPicker from './PeriodPicker.vue'

/**
 * Analytics for every company, or for one: the period filter on top scopes everything under it, so the
 * instruments, the chart and the tables always add up to the same slice.
 */
const props = defineProps<{ company?: string }>()

const { period, choose } = usePeriod()

const currency = config().currency

const metric = ref<Metric>('earned')

const { data, error, loading, run } = useResource((signal) => api.stats({ ...period.value.range, company: props.company }, signal))

watch(() => [period.value.range.from, period.value.range.to, props.company], run, { immediate: true })
</script>

<template>
  <div class="space-y-4">
    <PeriodPicker :period="period" @choose="choose" />

    <UiError v-if="error" :error="error" :retry="run" />

    <!-- A refetch keeps the last answer on screen, dimmed, instead of flashing placeholders -->
    <div v-if="data" class="space-y-4 transition-opacity duration-default" :class="loading && 'opacity-55'" :aria-busy="loading">
      <!-- One company runs on one model, so the split by model only means something across companies -->
      <KpiTiles v-model="metric" :totals="data.totals" :currency="currency" :breakdown="!company" />
      <template v-if="company">
        <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <DailyChart :daily="data.daily" :currency="currency" :metric="metric" />
          <CountryBars :countries="data.countries" />
        </div>
      </template>
      <template v-else>
        <DailyChart :daily="data.daily" :currency="currency" :metric="metric" />
        <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <CompanyStatsTable :companies="data.companies" :currency="currency" />
          <CountryBars :countries="data.countries" />
        </div>
      </template>
    </div>

    <div v-else-if="!error" class="space-y-4" aria-busy="true" aria-label="Loading statistics">
      <div class="grid grid-cols-2 gap-3" :class="company ? 'xl:grid-cols-5' : 'xl:grid-cols-4'">
        <UiSkeleton class="col-span-2 h-36" :class="company ? 'xl:col-span-1 xl:h-32' : 'xl:row-span-2 xl:h-auto'" />
        <UiSkeleton v-for="index in 4" :key="index" class="h-28 xl:h-32" />
      </div>
      <UiSkeleton class="h-80" />
    </div>
  </div>
</template>
