<script setup lang="ts">
import { computed } from 'vue'
import type { CompanyType } from '@/api/types'
import { COMPANY_TYPES, TYPE_META } from '@/lib/companies'
import { toNumber } from '@/lib/decimal'
import { formatMoney } from '@/lib/format'

/**
 * What each commission model brought in the period. It is all earned money, so every row is a meter in the
 * one signal colour on its own track: length carries the share, the label and the amount carry identity.
 */
const props = defineProps<{ byType: Record<CompanyType, string> }>()

const rows = computed(() => {
  const values = COMPANY_TYPES.map((type) => ({ type, value: Math.max(0, toNumber(props.byType[type])) }))
  const total = values.reduce((sum, row) => sum + row.value, 0)
  return values.map((row) => ({ ...row, share: total > 0 ? row.value / total : 0 }))
})
</script>

<template>
  <ul class="space-y-2.5" aria-label="Earned by commission model">
    <li v-for="row in rows" :key="row.type">
      <p class="flex min-w-0 items-baseline gap-2 text-body">
        <span class="truncate text-text-muted">{{ TYPE_META[row.type].label }}</span>
        <span class="num ml-auto shrink-0 text-text">{{ formatMoney(byType[row.type]) }}</span>
      </p>
      <span class="mt-1 block h-1 overflow-hidden rounded-full bg-signal-fill" aria-hidden="true">
        <span class="block h-full rounded-full bg-signal" :style="{ width: row.share > 0 ? `max(4px, ${row.share * 100}%)` : '0' }" />
      </span>
    </li>
  </ul>
</template>
