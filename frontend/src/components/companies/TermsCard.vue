<script setup lang="ts">
import { computed } from 'vue'
import type { Company } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import UiChip from '@/components/ui/UiChip.vue'
import { STATUS_META, TYPE_META, isZeroTerms, termsParts } from '@/lib/companies'
import { formatCount, formatDate, formatMoney } from '@/lib/format'
import { config } from '@/lib/session'

/**
 * The terms the company runs on, what its status means for the players it brings, and its all-time totals.
 * A company in review explains itself in the banner above, so the card does not say it twice.
 */
const props = defineProps<{ company: Company }>()

const { currency, cpaMinDeposit } = config()

// An active company on terms of 0 is counted like any other, but must not be promised a commission
const explainer = computed(() =>
  props.company.status === 'ACTIVE' && isZeroTerms(props.company.amount)
    ? 'Players who sign up through your link are counted and reach your postbacks as usual, but terms of 0 earn no commission.'
    : STATUS_META[props.company.status].explainer,
)
</script>

<template>
  <section class="surface flex flex-col p-4 lg:p-6">
    <div class="flex items-center justify-between gap-3">
      <h2 class="text-section font-extrabold text-text">Terms</h2>
      <UiChip :tone="STATUS_META[company.status].tone">{{ STATUS_META[company.status].label }}</UiChip>
    </div>

    <p class="mt-5 text-body text-text-faint">{{ TYPE_META[company.type].label }}{{ company.status === 'IN_REVIEW' ? ', as proposed' : '' }}</p>
    <p class="mt-1 flex flex-wrap items-baseline gap-x-2">
      <span class="num text-amount font-semibold text-text">{{ termsParts(company.type, company.amount, currency).value }}</span>
      <span class="text-name text-text-muted">{{ termsParts(company.type, company.amount, currency).basis }}</span>
    </p>
    <p v-if="company.type === 'CPA'" class="mt-1.5 text-body text-text-faint">
      A player qualifies with a first deposit of {{ formatMoney(cpaMinDeposit, currency) }} or more.
    </p>

    <p v-if="company.status !== 'IN_REVIEW'" class="mt-4 flex gap-2.5 rounded-md border border-line-soft bg-page/50 p-3 text-body text-text-muted">
      <Icon name="info" :size="18" class="mt-px shrink-0 text-text-faint" />
      {{ explainer }}
    </p>

    <!-- Pins the totals to the card's foot when the link builder beside it is taller -->
    <div class="min-h-5 flex-1" aria-hidden="true" />

    <dl class="grid grid-cols-3 gap-3 border-t border-line-soft pt-4">
      <div class="col-span-3 -mt-1 flex items-center justify-between">
        <dt class="micro-label text-micro text-text-faint">All time</dt>
        <dd class="text-micro text-text-faint">Created {{ formatDate(company.createdAt) }}</dd>
      </div>
      <div>
        <dt class="text-body text-text-faint">Players</dt>
        <dd class="num mt-0.5 text-name font-semibold text-text">{{ formatCount(company.stats.registrations) }}</dd>
      </div>
      <div>
        <dt class="text-body text-text-faint">First deposits</dt>
        <dd class="num mt-0.5 text-name font-semibold text-text">{{ formatCount(company.stats.firstDeposits) }}</dd>
      </div>
      <div class="min-w-0">
        <dt class="text-body text-text-faint">Earned</dt>
        <dd class="num mt-0.5 truncate text-name font-semibold text-text">{{ formatMoney(company.stats.earned) }}</dd>
      </div>
    </dl>
    <RouterLink
      :to="{ path: '/earnings', query: { company: company.identity } }"
      class="hit-area mt-4 inline-flex h-9 items-center gap-1.5 self-start text-body font-bold text-brand-soft transition-colors duration-default hover:text-text"
    >
      See its commissions
      <Icon name="arrowRight" :size="16" />
    </RouterLink>
  </section>
</template>
