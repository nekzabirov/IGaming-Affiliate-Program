<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api } from '@/api'
import { asApiError } from '@/api/client'
import type { Me, Payout } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiSheet from '@/components/ui/UiSheet.vue'
import { floorTo, isPositive } from '@/lib/decimal'
import { errorField, errorMessage } from '@/lib/errors'
import { formatMoney } from '@/lib/format'
import { addressError, network, networkName } from '@/lib/networks'
import { normalizeNumber, payoutAmountError } from '@/lib/rules'
import { config, refreshMe } from '@/lib/session'
import { toast } from '@/lib/toast'

/**
 * A payout request: amount, network, address — then a confirmation that shows all three once more, since
 * a payout sent to a wrong address cannot be called back.
 */
const props = defineProps<{ me: Me }>()

const emit = defineEmits<{ requested: [Payout] }>()

const { payoutNetworks } = config()

const form = reactive({ amount: '', network: payoutNetworks[0] ?? '', address: '' })

const errors = reactive<Record<'amount' | 'network' | 'address', string | null>>({ amount: null, network: null, address: null })

const formError = ref<string | null>(null)

const confirming = ref(false)

const busy = ref(false)

const funded = computed(() => isPositive(props.me.balance))

const max = computed(() => floorTo(props.me.balance, 2))

const current = computed(() => network(form.network))

watch(
  () => form.amount,
  (value) => {
    const normal = normalizeNumber(value)
    if (normal !== value) form.amount = normal
    errors.amount = null
  },
)

watch(
  () => form.address,
  () => (errors.address = null),
)

watch(
  () => form.network,
  () => (errors.address = form.address ? addressError(form.network, form.address.trim()) : null),
)

function review() {
  formError.value = null
  errors.amount = payoutAmountError(form.amount, props.me.balance)
  errors.address = addressError(form.network, form.address.trim())
  errors.network = form.network ? null : 'Pick a network.'
  if (!errors.amount && !errors.address && !errors.network) confirming.value = true
}

async function send() {
  busy.value = true
  try {
    const payout = await api.requestPayout({ amount: form.amount, network: form.network, address: form.address.trim() })
    confirming.value = false
    form.amount = ''
    form.address = ''
    emit('requested', payout)
    toast('Payout requested. We will send it by hand.')
    await refreshMe().catch(() => undefined)
  } catch (cause) {
    const error = asApiError(cause)
    const field = errorField(error)
    confirming.value = false
    if (field === 'amount' || field === 'network' || field === 'address') errors[field] = errorMessage(error)
    else formError.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section class="surface p-4 lg:p-6">
    <h2 class="text-section font-extrabold text-text">Request a payout</h2>
    <p class="mt-1 text-body text-text-faint">In {{ me.currency }}, to your own wallet.</p>

    <form class="mt-5 space-y-5" novalidate @submit.prevent="review">
      <UiField
        v-model="form.amount"
        label="Amount"
        inputmode="decimal"
        :suffix="me.currency"
        :maxlength="16"
        mono
        :disabled="!funded"
        :error="errors.amount"
        :hint="funded ? `Up to ${formatMoney(max, me.currency)}.` : undefined"
      >
        <template #end>
          <button
            type="button"
            class="pressable hit-area h-9 shrink-0 rounded-sm border border-brand/40 bg-brand/14 px-3 text-body font-bold text-brand-soft hover:bg-brand/22 disabled:opacity-40"
            :disabled="!funded"
            @click="form.amount = max"
          >
            Max
          </button>
        </template>
      </UiField>

      <fieldset>
        <legend class="micro-label text-micro text-text-faint">Network</legend>
        <div role="radiogroup" aria-label="Network" class="mt-2 grid grid-cols-2 gap-2">
          <button
            v-for="code in payoutNetworks"
            :key="code"
            type="button"
            role="radio"
            :aria-checked="form.network === code"
            :disabled="!funded"
            class="pressable flex h-12 items-center gap-2 rounded-md border px-3 text-left disabled:opacity-50"
            :class="form.network === code ? 'border-brand/60 bg-brand/12' : 'border-line bg-page/40 hover:border-line-strong'"
            @click="form.network = code"
          >
            <span class="min-w-0 flex-1">
              <span class="num block text-body font-bold text-text">{{ code }}</span>
              <span class="block truncate text-micro text-text-faint">{{ network(code).chain || 'USDT' }}</span>
            </span>
            <Icon v-if="form.network === code" name="check" :size="16" class="text-brand-soft" />
          </button>
        </div>
        <p v-if="errors.network" class="mt-1.5 text-micro font-bold text-danger">{{ errors.network }}</p>
      </fieldset>

      <UiField
        v-model="form.address"
        label="Wallet address"
        :placeholder="current.placeholder"
        :maxlength="128"
        mono
        :disabled="!funded"
        :error="errors.address"
        :hint="current.hint"
      />

      <ul class="space-y-2 rounded-md border border-line-soft bg-page/50 p-3.5 text-body text-text-muted">
        <li class="flex gap-2.5">
          <Icon name="lock" :size="16" class="mt-0.5 shrink-0 text-text-faint" />
          The amount is reserved from your balance the moment you send the request.
        </li>
        <li class="flex gap-2.5">
          <Icon name="clock" :size="16" class="mt-0.5 shrink-0 text-text-faint" />
          We review each payout and send it by hand; your payout history shows when it is paid.
        </li>
      </ul>

      <p v-if="formError" class="text-name font-bold text-danger" role="alert">{{ formError }}</p>
      <p v-if="!funded" class="text-body text-text-muted">Your balance is empty. Earnings land here as your players deposit and play.</p>

      <UiButton variant="primary" type="submit" block :disabled="!funded">Request payout</UiButton>
    </form>

    <UiSheet v-model:open="confirming" title="Confirm the payout" description="Check every line: a payout sent to a wrong address cannot be returned.">
      <dl class="divide-y divide-line-soft rounded-md border border-line-soft bg-card">
        <div class="flex items-baseline justify-between gap-4 p-4">
          <dt class="text-body text-text-faint">Amount</dt>
          <dd class="num text-amount font-semibold text-text">{{ formatMoney(form.amount, me.currency) }}</dd>
        </div>
        <div class="flex items-baseline justify-between gap-4 p-4">
          <dt class="text-body text-text-faint">Network</dt>
          <dd class="text-right text-name font-bold text-text">{{ networkName(form.network) }}</dd>
        </div>
        <div class="p-4">
          <dt class="text-body text-text-faint">Address</dt>
          <dd class="num mt-1.5 text-name break-all text-text">{{ form.address.trim() }}</dd>
        </div>
      </dl>
      <template #footer>
        <div class="flex gap-3">
          <UiButton variant="ghost" @click="confirming = false">Back</UiButton>
          <UiButton variant="primary" :loading="busy" class="flex-1" @click="send">Confirm payout</UiButton>
        </div>
      </template>
    </UiSheet>
  </section>
</template>
