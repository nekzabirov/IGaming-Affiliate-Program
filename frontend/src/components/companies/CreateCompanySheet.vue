<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from '@/api'
import { asApiError } from '@/api/client'
import type { Company, CompanyType } from '@/api/types'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiSheet from '@/components/ui/UiSheet.vue'
import UrlText from '@/components/ui/UrlText.vue'
import Icon from '@/components/ui/Icon.vue'
import { ZERO_TERMS_HINT, partnerLink } from '@/lib/companies'
import { errorField, errorMessage } from '@/lib/errors'
import { formatMoney } from '@/lib/format'
import { IDENTITY_RULE, identityError, normalizeNumber, termsError } from '@/lib/rules'
import { config } from '@/lib/session'
import TypePicker from './TypePicker.vue'

const open = defineModel<boolean>('open', { required: true })

const emit = defineEmits<{ created: [Company] }>()

const { siteUrl, tagPrefix, clickParam, currency, cpaMinDeposit } = config()

const form = reactive({ identity: '', type: 'CPA' as CompanyType, amount: '' })

const errors = reactive<Record<'identity' | 'amount', string | null>>({ identity: null, amount: null })

const formError = ref<string | null>(null)

const busy = ref(false)

// The identity is the tag players carry for good: it is lowercase on the way in, as the API stores it
watch(
  () => form.identity,
  (value) => {
    const lower = value.toLowerCase()
    if (lower !== value) form.identity = lower
    errors.identity = null
  },
)

watch(
  () => form.amount,
  (value) => {
    const normal = normalizeNumber(value)
    if (normal !== value) form.amount = normal
    errors.amount = null
  },
)

watch(
  () => form.type,
  () => (errors.amount = null),
)

const tag = computed(() => `${tagPrefix}${form.identity || 'your-identity'}`)

const link = computed(() => partnerLink(siteUrl, tag.value, clickParam, '{subid}'))

const cpa = computed(() => form.type === 'CPA')

const amountHint = computed(() => {
  const rule = cpa.value ? `Paid once for each player whose first deposit is ${formatMoney(cpaMinDeposit, currency)} or more.` : 'From 0 to 100, with at most 2 decimals.'
  return `${rule} ${ZERO_TERMS_HINT}`
})

async function submit() {
  formError.value = null
  errors.identity = identityError(form.identity)
  errors.amount = termsError(form.type, form.amount)
  if (errors.identity || errors.amount) return
  busy.value = true
  try {
    const company = await api.createCompany({ identity: form.identity, type: form.type, amount: form.amount })
    emit('created', company)
    open.value = false
  } catch (cause) {
    const error = asApiError(cause)
    const field = errorField(error)
    if (field === 'identity' || field === 'amount') errors[field] = errorMessage(error)
    else formError.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}

function reset() {
  Object.assign(form, { identity: '', type: 'CPA', amount: '' })
  Object.assign(errors, { identity: null, amount: null })
  formError.value = null
}
</script>

<template>
  <UiSheet v-model:open="open" title="New company" description="One traffic source or campaign, with its own link and statistics." wide @closed="reset">
    <form id="create-company" class="space-y-7" novalidate @submit.prevent="submit">
      <section class="space-y-3">
        <div data-autofocus>
          <UiField
            v-model="form.identity"
            label="Identity"
            :error="errors.identity"
            :hint="IDENTITY_RULE"
            :maxlength="48"
            mono
            @blur="errors.identity = form.identity ? identityError(form.identity) : null"
          />
        </div>
        <div class="space-y-2.5 rounded-md border border-line-soft bg-page/60 p-3.5" aria-live="polite">
          <p class="flex items-baseline gap-3 text-body">
            <span class="micro-label w-10 shrink-0 text-micro text-text-faint">Tag</span>
            <span class="num min-w-0 wrap-anywhere" :class="form.identity ? 'text-text' : 'text-text-faint'">{{ tag }}</span>
          </p>
          <p class="flex items-baseline gap-3 text-body">
            <span class="micro-label w-10 shrink-0 text-micro text-text-faint">Link</span>
            <span class="num min-w-0" :class="form.identity ? 'text-text-muted' : 'text-text-faint'"><UrlText :url="link" /></span>
          </p>
        </div>
      </section>

      <section class="space-y-3">
        <h3 class="micro-label text-micro text-text-faint">Commission model</h3>
        <TypePicker v-model="form.type" />
      </section>

      <section class="space-y-3">
        <UiField
          v-model="form.amount"
          :label="cpa ? 'Amount per qualified player' : 'Percent'"
          :suffix="cpa ? currency : '%'"
          inputmode="decimal"
          :maxlength="16"
          mono
          :error="errors.amount"
          :hint="amountHint"
        />
        <p class="flex gap-2.5 rounded-md border border-brand/25 bg-brand/8 p-3.5 text-body text-text-muted">
          <Icon name="clock" :size="18" class="mt-px shrink-0 text-brand-soft" />
          <span>
            These are the terms you <strong class="font-bold text-text">propose</strong>. We review every company and may adjust the terms; it
            starts counting players once it is active.
          </span>
        </p>
      </section>

      <p v-if="formError" class="text-name font-bold text-danger" role="alert">{{ formError }}</p>
    </form>

    <template #footer>
      <div class="flex gap-3">
        <UiButton variant="ghost" @click="open = false">Cancel</UiButton>
        <UiButton variant="primary" type="submit" form="create-company" :loading="busy" class="flex-1">Create company</UiButton>
      </div>
    </template>
  </UiSheet>
</template>
