<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import type { Commission } from '@/api/types'
import PageHeader from '@/components/layout/PageHeader.vue'
import Icon from '@/components/ui/Icon.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiPager from '@/components/ui/UiPager.vue'
import UiSelect from '@/components/ui/UiSelect.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { TYPE_META } from '@/lib/companies'
import { isNegative, sumDecimals } from '@/lib/decimal'
import { formatDayLong, formatMoney } from '@/lib/format'
import { useResource } from '@/lib/resource'
import { config, session } from '@/lib/session'

/** The commission ledger: every amount credited to (or taken back from) the balance, by UTC day. */
const route = useRoute()

const router = useRouter()

const { currency } = config()

const SIZE = 50

const page = ref(0)

const company = computed({
  get: () => (typeof route.query.company === 'string' ? route.query.company : ''),
  set: (value: string) => {
    const query = { ...route.query }
    if (value) query.company = value
    else delete query.company
    void router.replace({ query })
  },
})

const companies = useResource((signal) => api.companies(signal))

const companyOptions = computed(() => [
  { value: '', label: 'All companies' },
  ...(companies.data.value ?? []).map((item) => ({ value: item.identity, label: item.identity })),
  // A filter from a link may name a company the list has not loaded yet
  ...(company.value && !companies.data.value?.some((item) => item.identity === company.value) ? [{ value: company.value, label: company.value }] : []),
])

const { data, error, loading, run } = useResource((signal) => api.commissions({ page: page.value, size: SIZE, company: company.value || undefined }, signal))

watch(company, () => (page.value === 0 ? run() : (page.value = 0)))

watch(page, run)

onMounted(() => {
  void companies.run()
  void run()
})

const time = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' })

/** Rows of one UTC day under one heading, with that day's sum on this page. */
const days = computed(() => {
  const groups: { day: string; rows: Commission[]; sum: string }[] = []
  for (const row of data.value?.items ?? []) {
    const last = groups[groups.length - 1]
    if (last && last.day === row.day) last.rows.push(row)
    else groups.push({ day: row.day, rows: [row], sum: '0' })
  }
  return groups.map((group) => ({ ...group, sum: sumDecimals(group.rows.map((row) => row.amount)) }))
})
</script>

<template>
  <div class="space-y-8">
    <PageHeader title="Earnings" subtitle="Every commission credited to your balance, newest first." class="rise" />

    <div class="rise flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between" style="--rise: 1">
      <UiSelect v-model="company" :options="companyOptions" label="Company" class="w-full sm:w-72" />
      <div v-if="session.me" class="sm:text-right">
        <p class="micro-label text-micro text-text-faint">Earned, all time</p>
        <p class="mt-1 flex items-baseline gap-2 sm:justify-end">
          <span class="num text-amount font-semibold text-text">{{ formatMoney(session.me.totalEarned) }}</span>
          <span class="num text-body text-text-faint">{{ currency }}</span>
        </p>
      </div>
    </div>

    <UiError v-if="error" :error="error" :retry="run" />

    <section v-else-if="data && data.items.length" class="surface rise overflow-hidden" style="--rise: 2">
      <div class="transition-opacity duration-default" :class="loading && 'opacity-55'">
        <div v-for="group in days" :key="group.day" class="border-b border-line-soft last:border-b-0">
          <div class="flex items-center justify-between gap-3 bg-page/40 px-4 py-2.5 lg:px-6">
            <p class="text-body font-bold text-text-muted">
              {{ formatDayLong(group.day) }} <span class="micro-label ml-1 text-micro text-text-faint">UTC</span>
            </p>
            <p class="num text-body text-text-muted">{{ formatMoney(group.sum) }}</p>
          </div>
          <ul class="divide-y divide-line-soft">
            <li v-for="row in group.rows" :key="row.id" class="flex items-center gap-3 px-4 py-3 lg:px-6">
              <div class="min-w-0 flex-1">
                <p class="truncate text-name font-bold text-text">{{ TYPE_META[row.type].label }}</p>
                <p class="mt-0.5 flex min-w-0 items-center gap-1.5 text-body text-text-faint">
                  <RouterLink :to="`/companies/${row.company}`" class="truncate font-bold text-brand-soft hover:text-text">{{ row.company }}</RouterLink>
                  <span aria-hidden="true">·</span>
                  <span class="num shrink-0">player #{{ row.playerId }}</span>
                </p>
              </div>
              <div class="shrink-0 text-right">
                <p class="num text-name font-semibold" :class="isNegative(row.amount) ? 'text-danger' : 'text-signal'">
                  {{ isNegative(row.amount) ? '' : '+' }}{{ formatMoney(row.amount) }}
                </p>
                <p class="mt-0.5 text-micro text-text-faint" :title="row.updatedAt">updated {{ time.format(new Date(row.updatedAt)) }}</p>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <div v-else-if="data" class="surface">
      <UiEmpty
        icon="list"
        :title="company ? 'Nothing for this company yet' : 'No commissions yet'"
        text="Commissions appear here as your players qualify for CPA, deposit and play."
      />
    </div>

    <div v-else class="space-y-2">
      <UiSkeleton v-for="index in 5" :key="index" class="h-16" />
    </div>

    <UiPager v-if="data" v-model:page="page" :size="SIZE" :total="data.total" :busy="loading" />

    <section class="rounded-lg border border-line-soft p-4 lg:p-6" aria-labelledby="how-title">
      <h2 id="how-title" class="flex items-center gap-2 text-name font-extrabold text-text">
        <Icon name="info" :size="18" class="text-text-faint" />
        How commissions appear
      </h2>
      <ul class="mt-3 grid grid-cols-1 gap-3 text-body text-text-muted md:grid-cols-3">
        <li><strong class="font-bold text-text">CPA</strong>: one entry per player, on a first deposit that qualifies.</li>
        <li><strong class="font-bold text-text">RevShare on deposits</strong>: one entry per deposit.</li>
        <li>
          <strong class="font-bold text-text">RevShare on turnover</strong>: one entry per player per day that grows as they play; a cancelled bet takes its
          share back.
        </li>
      </ul>
    </section>
  </div>
</template>
