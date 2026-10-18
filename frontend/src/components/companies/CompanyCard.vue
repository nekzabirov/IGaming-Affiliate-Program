<script setup lang="ts">
import type { Company } from '@/api/types'
import UiChip from '@/components/ui/UiChip.vue'
import { STATUS_META, TYPE_META, termsParts } from '@/lib/companies'
import { formatCount, formatMoney } from '@/lib/format'

defineProps<{ company: Company; currency: string }>()
</script>

<template>
  <RouterLink
    :to="`/companies/${company.identity}`"
    class="surface group flex flex-col p-4 transition-[border-color,background-color] duration-default ease-default hover:border-brand/40 hover:bg-card lg:p-5"
  >
    <div class="flex items-start justify-between gap-3">
      <p class="min-w-0 truncate pt-0.5 text-section font-extrabold text-text transition-colors duration-default group-hover:text-brand-soft">{{ company.identity }}</p>
      <UiChip :tone="STATUS_META[company.status].tone">{{ STATUS_META[company.status].label }}</UiChip>
    </div>
    <p class="mt-1.5 flex min-w-0 flex-wrap items-baseline gap-x-1.5 text-body">
      <span class="text-text-faint">{{ TYPE_META[company.type].label }}</span>
      <span class="text-text-faint" aria-hidden="true">·</span>
      <span class="num font-semibold text-text">{{ termsParts(company.type, company.amount, currency).value }}</span>
      <span class="text-text-muted">{{ termsParts(company.type, company.amount, currency).basis }}</span>
    </p>
    <p v-if="company.status === 'IN_REVIEW'" class="mt-1.5 text-body text-text-faint">Players are counted once we approve the terms.</p>

    <!-- Keeps the numbers on one baseline across a row of cards of different heights -->
    <div class="min-h-5 flex-1" aria-hidden="true" />

    <dl class="grid grid-cols-3 gap-3 border-t border-line-soft pt-4">
      <div class="min-w-0">
        <dt class="micro-label text-micro text-text-faint">Players</dt>
        <dd class="num mt-1 text-name font-semibold text-text">{{ formatCount(company.stats.registrations) }}</dd>
      </div>
      <div class="min-w-0">
        <dt class="micro-label text-micro text-text-faint">FTD</dt>
        <dd class="num mt-1 text-name font-semibold text-text">{{ formatCount(company.stats.firstDeposits) }}</dd>
      </div>
      <div class="min-w-0">
        <dt class="micro-label text-micro text-text-faint">Earned</dt>
        <dd class="num mt-1 truncate text-name font-semibold text-text">{{ formatMoney(company.stats.earned) }}</dd>
      </div>
    </dl>
  </RouterLink>
</template>
