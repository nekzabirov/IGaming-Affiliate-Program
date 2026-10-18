<script setup lang="ts">
import { onMounted, ref, shallowRef, watch } from 'vue'
import { api } from '@/api'
import { asApiError } from '@/api/client'
import type { AdminPartner } from '@/api/types'
import AdminTabs from '@/components/admin/AdminTabs.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import Icon from '@/components/ui/Icon.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiChip from '@/components/ui/UiChip.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiPager from '@/components/ui/UiPager.vue'
import UiSheet from '@/components/ui/UiSheet.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { isNegative } from '@/lib/decimal'
import { errorMessage } from '@/lib/errors'
import { formatCount, formatDate, formatMoney } from '@/lib/format'
import { useResource } from '@/lib/resource'
import { config, session } from '@/lib/session'
import { toast } from '@/lib/toast'

const { currency } = config()

const SIZE = 50

const search = ref('')

const query = ref('')

const page = ref(0)

let typing = 0

// The list follows the search box after a short pause, not on every keystroke
watch(search, (value) => {
  window.clearTimeout(typing)
  typing = window.setTimeout(() => (query.value = value.trim()), 300)
})

const { data, error, loading, run } = useResource((signal) => api.admin.partners({ page: page.value, size: SIZE, q: query.value || undefined }, signal))

watch(query, () => (page.value === 0 ? run() : (page.value = 0)))

watch(page, run)

onMounted(run)

const target = shallowRef<AdminPartner | null>(null)

const confirming = ref(false)

const busy = ref<number | null>(null)

async function setActive(partner: AdminPartner, active: boolean) {
  busy.value = partner.id
  try {
    const updated = await api.admin.updatePartner(partner.id, active)
    if (data.value) data.value = { ...data.value, items: data.value.items.map((item) => (item.id === partner.id ? { ...item, ...updated } : item)) }
    confirming.value = false
    toast(active ? `${partner.login} is unblocked` : `${partner.login} is blocked`)
  } catch (cause) {
    toast(errorMessage(asApiError(cause)), 'error')
  } finally {
    busy.value = null
  }
}

function askToBlock(partner: AdminPartner) {
  target.value = partner
  confirming.value = true
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Admin" subtitle="Find a partner, see what they have, block or unblock the account." class="rise" />
    <AdminTabs class="rise" style="--rise: 1" />

    <label class="rise relative block w-full lg:w-96" style="--rise: 2">
      <span class="sr-only">Search partners by login</span>
      <Icon name="search" :size="18" class="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-text-faint" />
      <input
        v-model="search"
        type="search"
        placeholder="Partner login"
        spellcheck="false"
        autocapitalize="off"
        class="h-11 w-full rounded-md border border-line bg-card pr-3 pl-11 text-input text-text outline-none placeholder:text-text-faint focus:border-brand/70"
      />
    </label>

    <UiError v-if="error" :error="error" :retry="run" />

    <template v-else-if="data && data.items.length">
      <ul class="surface divide-y divide-line-soft transition-opacity duration-default" :class="loading && 'opacity-55'">
        <li v-for="partner in data.items" :key="partner.id" class="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:gap-6 lg:px-5">
          <div class="flex min-w-0 flex-1 items-center gap-3">
            <span
              class="grid size-10 shrink-0 place-items-center rounded-sm text-name font-extrabold uppercase ring-1 ring-inset"
              :class="partner.active ? 'bg-brand/16 text-brand-soft ring-brand/25' : 'bg-wash text-text-disabled ring-line-strong'"
              aria-hidden="true"
            >
              {{ partner.login.charAt(0) }}
            </span>
            <div class="min-w-0">
              <p class="flex flex-wrap items-center gap-2">
                <span class="truncate text-name font-extrabold" :class="partner.active ? 'text-text' : 'text-text-muted'">{{ partner.login }}</span>
                <UiChip v-if="partner.role === 'ADMIN'" tone="brand">Admin</UiChip>
                <UiChip v-if="!partner.active" tone="bad">Blocked</UiChip>
              </p>
              <p class="mt-0.5 text-body text-text-faint">
                <span class="num">#{{ partner.id }}</span> · joined {{ formatDate(partner.createdAt) }} · {{ formatCount(partner.companies) }}
                {{ partner.companies === 1 ? 'company' : 'companies' }}
              </p>
            </div>
          </div>
          <div class="flex items-center justify-between gap-4 sm:justify-end">
            <p class="sm:text-right">
              <span class="micro-label block text-micro text-text-faint">Balance</span>
              <span class="num text-name font-semibold" :class="isNegative(partner.balance) ? 'text-danger' : 'text-text'">{{ formatMoney(partner.balance, currency) }}</span>
            </p>
            <!-- Calm in the list; the red lives in the confirmation, where the decision is made -->
            <UiButton
              v-if="partner.active"
              size="sm"
              icon="ban"
              class="w-32"
              :disabled="partner.id === session.me?.id"
              :title="partner.id === session.me?.id ? 'You cannot block your own account' : undefined"
              @click="askToBlock(partner)"
            >
              Block
            </UiButton>
            <UiButton v-else size="sm" icon="check" class="w-32" :loading="busy === partner.id" @click="setActive(partner, true)">Unblock</UiButton>
          </div>
        </li>
      </ul>
      <UiPager v-model:page="page" :size="SIZE" :total="data.total" :busy="loading" />
    </template>

    <div v-else-if="data" class="surface">
      <UiEmpty icon="users" title="No partners found" text="Try another login." />
    </div>

    <div v-else class="space-y-2">
      <UiSkeleton v-for="index in 5" :key="index" class="h-18" />
    </div>

    <UiSheet v-model:open="confirming" :title="target ? `Block ${target.login}?` : 'Block partner'">
      <p class="text-name text-text-muted">
        They are signed out and cannot sign in. While the account is blocked, their companies count no new players and earn no commission. You
        can unblock them at any time.
      </p>
      <template #footer>
        <div class="flex gap-3">
          <UiButton variant="ghost" @click="confirming = false">Cancel</UiButton>
          <UiButton variant="danger" icon="ban" class="flex-1" :loading="busy === target?.id" @click="target && setActive(target, false)">Block account</UiButton>
        </div>
      </template>
    </UiSheet>
  </div>
</template>
