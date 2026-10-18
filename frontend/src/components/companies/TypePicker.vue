<script setup lang="ts">
import type { CompanyType } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import { COMPANY_TYPES, typeExplainer } from '@/lib/companies'
import { config } from '@/lib/session'

/** The three commission models as cards: what each one pays for, in one sentence. */
const model = defineModel<CompanyType>({ required: true })

const { cpaMinDeposit, currency } = config()

const TITLES: Record<CompanyType, string> = {
  CPA: 'CPA',
  REVSHARE_DEPOSIT: 'RevShare on deposits',
  REVSHARE_TURNOVER: 'RevShare on turnover',
}

function move(step: number) {
  const index = COMPANY_TYPES.indexOf(model.value)
  const next = COMPANY_TYPES[(index + step + COMPANY_TYPES.length) % COMPANY_TYPES.length]
  if (next) model.value = next
}
</script>

<template>
  <div role="radiogroup" aria-label="Commission model" class="grid grid-cols-1 gap-2.5 sm:grid-cols-3" @keydown.left.prevent="move(-1)" @keydown.right.prevent="move(1)">
    <button
      v-for="type in COMPANY_TYPES"
      :key="type"
      type="button"
      role="radio"
      :aria-checked="model === type"
      :tabindex="model === type ? 0 : -1"
      class="pressable relative flex flex-col rounded-md border p-3.5 text-left"
      :class="model === type ? 'border-brand/60 bg-brand/12' : 'border-line bg-card hover:border-line-strong'"
      @click="model = type"
    >
      <span class="flex items-center gap-2">
        <span class="text-name font-extrabold text-text">{{ TITLES[type] }}</span>
        <span
          class="ml-auto grid size-5 place-items-center rounded-full transition-colors duration-default"
          :class="model === type ? 'bg-brand text-on-brand' : 'ring-1 ring-line-strong ring-inset'"
          aria-hidden="true"
        >
          <Icon v-if="model === type" name="check" :size="12" />
        </span>
      </span>
      <span class="mt-2 text-body text-text-muted">{{ typeExplainer(type, cpaMinDeposit, currency) }}</span>
    </button>
  </div>
</template>
