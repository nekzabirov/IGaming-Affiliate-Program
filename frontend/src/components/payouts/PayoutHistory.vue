<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { api } from '@/api'
import { asApiError, type ApiError } from '@/api/client'
import type { Payout, PayoutStatus } from '@/api/types'
import CopyButton from '@/components/ui/CopyButton.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiChip from '@/components/ui/UiChip.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import type { Tone } from '@/lib/companies'
import { formatDateTime, formatMoney, middle } from '@/lib/format'
import { networkName } from '@/lib/networks'

const SIZE = 20

/** Pending is with us (violet), paid is done (lime), rejected came back to the balance (coral). */
const STATUS: Record<PayoutStatus, { label: string; tone: Tone }> = {
  PENDING: { label: 'Pending', tone: 'wait' },
  PAID: { label: 'Paid', tone: 'good' },
  REJECTED: { label: 'Rejected', tone: 'bad' },
}

const items = shallowRef<Payout[]>([])

const total = ref(0)

const page = ref(0)

const loaded = ref(false)

const loading = ref(false)

const error = shallowRef<ApiError | null>(null)

async function load(next: number) {
  loading.value = true
  error.value = null
  try {
    const result = await api.payouts({ page: next, size: SIZE })
    // A request prepended on this screen shifts the server's offsets by one, so the next page can repeat a row
    const shown = new Set(items.value.map((payout) => payout.id))
    items.value = next === 0 ? result.items : [...items.value, ...result.items.filter((payout) => !shown.has(payout.id))]
    total.value = result.total
    page.value = next
    loaded.value = true
  } catch (cause) {
    error.value = asApiError(cause)
  } finally {
    loading.value = false
  }
}

/** A new request goes on top without asking the server for the page again. */
function prepend(payout: Payout) {
  items.value = [payout, ...items.value]
  total.value += 1
}

defineExpose({ prepend })

onMounted(() => load(0))
</script>

<template>
  <section class="surface">
    <header class="px-4 pt-4 lg:px-6 lg:pt-6">
      <h2 class="text-section font-extrabold text-text">History</h2>
      <p class="mt-1 text-body text-text-faint">Newest first</p>
    </header>

    <div class="p-4 lg:p-6">
      <UiError v-if="error && !loaded" :error="error" :retry="() => load(0)" />

      <template v-else-if="items.length">
        <ul class="-mx-4 divide-y divide-line-soft lg:-mx-6">
          <li v-for="payout in items" :key="payout.id" class="px-4 py-4 lg:px-6">
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="flex items-baseline gap-2">
                  <span class="num text-section font-semibold text-text">{{ formatMoney(payout.amount) }}</span>
                  <span class="num text-body text-text-faint">{{ payout.currency }}</span>
                </p>
                <p class="mt-1 text-body text-text-faint">{{ networkName(payout.network) }} · {{ formatDateTime(payout.createdAt) }}</p>
              </div>
              <UiChip :tone="STATUS[payout.status].tone">{{ STATUS[payout.status].label }}</UiChip>
            </div>
            <div class="mt-2.5 flex items-center gap-2 rounded-md border border-line-soft bg-page/50 pl-3">
              <span class="micro-label shrink-0 text-micro text-text-faint">To</span>
              <span class="num min-w-0 flex-1 truncate text-body text-text-muted" :title="payout.address">{{ middle(payout.address, 10, 8) }}</span>
              <CopyButton :value="payout.address" label="address" />
            </div>
            <div v-if="payout.txHash" class="mt-2 flex items-center gap-2 rounded-md border border-line-soft bg-page/50 pl-3">
              <span class="micro-label shrink-0 text-micro text-text-faint">Tx</span>
              <span class="num min-w-0 flex-1 truncate text-body text-text-muted" :title="payout.txHash">{{ middle(payout.txHash, 12, 10) }}</span>
              <CopyButton :value="payout.txHash" label="transaction hash" />
            </div>
            <p v-if="payout.comment" class="mt-2 rounded-md border p-3 text-body" :class="payout.status === 'REJECTED' ? 'border-danger/20 bg-danger/8 text-text' : 'border-line-soft bg-page/50 text-text-muted'">
              {{ payout.comment }}
            </p>
          </li>
        </ul>
        <p v-if="error" class="mt-3 text-body font-bold text-danger">{{ error.message }}</p>
        <UiButton v-if="items.length < total" size="sm" :loading="loading" class="mt-4" block @click="load(page + 1)">Show more</UiButton>
      </template>

      <UiEmpty v-else-if="loaded" art="payouts" title="No payouts yet" text="Your payout requests and their status show up here." compact />

      <div v-else class="space-y-2">
        <UiSkeleton v-for="index in 3" :key="index" class="h-20" />
      </div>
    </div>
  </section>
</template>
