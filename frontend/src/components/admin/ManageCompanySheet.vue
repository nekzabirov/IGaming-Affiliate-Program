<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import { asApiError } from '@/api/client'
import type { AdminCompany, AdminCompanyChange, CompanyStatus } from '@/api/types'
import UiButton from '@/components/ui/UiButton.vue'
import UiChip from '@/components/ui/UiChip.vue'
import UiField from '@/components/ui/UiField.vue'
import UiSheet from '@/components/ui/UiSheet.vue'
import { STATUS_META, TYPE_META, ZERO_TERMS_HINT, termsParts } from '@/lib/companies'
import { compareDecimal, plain } from '@/lib/decimal'
import { errorMessage } from '@/lib/errors'
import { formatDate } from '@/lib/format'
import { normalizeNumber, termsError } from '@/lib/rules'
import { config } from '@/lib/session'
import { toast } from '@/lib/toast'

/**
 * One company under review: the terms as proposed, an amount the admin may adjust, and the status change
 * the company's state allows. The model itself is the partner's choice and is not edited here.
 */
const props = defineProps<{ company: AdminCompany | null }>()

const open = defineModel<boolean>('open', { required: true })

const emit = defineEmits<{ updated: [AdminCompany] }>()

const { currency } = config()

const amount = ref('')

const amountError = ref<string | null>(null)

const busy = ref<CompanyStatus | 'terms' | null>(null)

watch(
  () => props.company,
  (company) => {
    amount.value = company ? plain(company.amount) : ''
    amountError.value = null
  },
  { immediate: true },
)

watch(amount, (value) => {
  const normal = normalizeNumber(value)
  if (normal !== value) amount.value = normal
  amountError.value = null
})

const changed = computed(() => {
  const company = props.company
  if (!company || !amount.value) return false
  // Input the rule refuses would compare as 0 and leave Save silently disabled on a company already at 0
  if (termsError(company.type, amount.value)) return true
  return compareDecimal(amount.value, company.amount) !== 0
})

const percent = computed(() => props.company?.type !== 'CPA')

const amountHint = computed(() => {
  const effect = changed.value ? 'The new amount applies to commissions from now on.' : 'Change it before approving to adjust the terms.'
  return `${effect} ${ZERO_TERMS_HINT}`
})

async function apply(status: CompanyStatus | null) {
  const company = props.company
  if (!company) return
  const change: AdminCompanyChange = {}
  if (status !== 'DISABLED') {
    amountError.value = termsError(company.type, amount.value)
    if (amountError.value) return
    if (changed.value) change.amount = amount.value
  }
  if (status) change.status = status
  if (!change.status && !change.amount) return
  busy.value = status ?? 'terms'
  try {
    const updated = await api.admin.updateCompany(company.identity, change)
    emit('updated', { ...company, ...updated, owner: updated.owner ?? company.owner })
    open.value = false
    toast(status === 'ACTIVE' ? `${company.identity} is active` : status === 'DISABLED' ? `${company.identity} is disabled` : 'Terms saved')
  } catch (cause) {
    const error = asApiError(cause)
    if (error.field === 'amount') amountError.value = errorMessage(error)
    else toast(errorMessage(error), 'error')
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <UiSheet
    v-model:open="open"
    :title="company ? (company.status === 'IN_REVIEW' ? `Review ${company.identity}` : `Manage ${company.identity}`) : 'Company'"
    description="The partner proposed the model and the amount; you set the amount it runs on."
  >
    <div v-if="company" class="space-y-5">
      <dl class="divide-y divide-line-soft rounded-md border border-line-soft bg-card">
        <div class="flex items-center justify-between gap-4 px-4 py-3">
          <dt class="text-body text-text-faint">Status</dt>
          <dd><UiChip :tone="STATUS_META[company.status].tone">{{ STATUS_META[company.status].label }}</UiChip></dd>
        </div>
        <div class="flex items-center justify-between gap-4 px-4 py-3">
          <dt class="text-body text-text-faint">Partner</dt>
          <dd class="flex items-center gap-2 text-name font-bold text-text">
            {{ company.owner.login }}
            <UiChip v-if="!company.owner.active" tone="bad">Blocked</UiChip>
          </dd>
        </div>
        <div class="flex items-center justify-between gap-4 px-4 py-3">
          <dt class="text-body text-text-faint">Model</dt>
          <dd class="text-name font-bold text-text">{{ TYPE_META[company.type].label }}</dd>
        </div>
        <div class="flex items-center justify-between gap-4 px-4 py-3">
          <dt class="text-body text-text-faint">{{ company.status === 'IN_REVIEW' ? 'Proposed' : 'Current terms' }}</dt>
          <dd class="text-right">
            <span class="num text-name font-semibold text-text">{{ termsParts(company.type, company.amount, currency).value }}</span>
            <span class="ml-1.5 text-body text-text-muted">{{ termsParts(company.type, company.amount, currency).basis }}</span>
          </dd>
        </div>
        <div class="flex items-center justify-between gap-4 px-4 py-3">
          <dt class="text-body text-text-faint">Created</dt>
          <dd class="text-name text-text-muted">{{ formatDate(company.createdAt) }}</dd>
        </div>
      </dl>

      <div data-autofocus>
        <UiField
          v-model="amount"
          :label="percent ? 'Percent' : 'Amount per qualified player'"
          :suffix="percent ? '%' : currency"
          inputmode="decimal"
          :maxlength="16"
          mono
          :error="amountError"
          :hint="amountHint"
        />
      </div>
    </div>

    <template #footer>
      <div v-if="company" class="flex flex-col-reverse gap-3 sm:flex-row">
        <UiButton v-if="company.status !== 'DISABLED'" variant="danger" :loading="busy === 'DISABLED'" :disabled="!!busy" @click="apply('DISABLED')">
          Disable
        </UiButton>
        <UiButton v-if="company.status === 'ACTIVE'" variant="primary" :loading="busy === 'terms'" :disabled="!changed || !!busy" class="w-full sm:w-auto sm:flex-1" @click="apply(null)">
          Save terms
        </UiButton>
        <UiButton v-else variant="primary" icon="check" :loading="busy === 'ACTIVE'" :disabled="!!busy" class="w-full sm:w-auto sm:flex-1" @click="apply('ACTIVE')">
          {{ company.status === 'IN_REVIEW' ? 'Approve' : 'Re-activate' }}
        </UiButton>
      </div>
    </template>
  </UiSheet>
</template>
