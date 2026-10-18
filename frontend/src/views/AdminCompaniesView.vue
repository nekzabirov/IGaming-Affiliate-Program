<script setup lang="ts">
import { onMounted, ref, shallowRef, watch } from 'vue'
import { api } from '@/api'
import type { AdminCompany, CompanyStatus } from '@/api/types'
import AdminTabs from '@/components/admin/AdminTabs.vue'
import ManageCompanySheet from '@/components/admin/ManageCompanySheet.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import Icon from '@/components/ui/Icon.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiChip from '@/components/ui/UiChip.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiPager from '@/components/ui/UiPager.vue'
import UiPills from '@/components/ui/UiPills.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { STATUS_META, TYPE_META, termsParts } from '@/lib/companies'
import { formatCount, formatDate, formatMoney } from '@/lib/format'
import { useResource } from '@/lib/resource'
import { config } from '@/lib/session'

/** Every partner's companies, the ones waiting for review first. */
const { currency } = config()

const SIZE = 50

const STATUS_OPTIONS: { value: CompanyStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'IN_REVIEW', label: 'In review' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'DISABLED', label: 'Disabled' },
]

const status = ref<CompanyStatus | 'ALL'>('ALL')

const search = ref('')

const query = ref('')

const page = ref(0)

let typing = 0

// The list follows the search box after a short pause, not on every keystroke
watch(search, (value) => {
  window.clearTimeout(typing)
  typing = window.setTimeout(() => (query.value = value.trim()), 300)
})

const { data, error, loading, run } = useResource((signal) =>
  api.admin.companies({ page: page.value, size: SIZE, status: status.value === 'ALL' ? undefined : status.value, q: query.value || undefined }, signal),
)

watch([status, query], () => (page.value === 0 ? run() : (page.value = 0)))

watch(page, run)

onMounted(run)

const selected = shallowRef<AdminCompany | null>(null)

const managing = ref(false)

function manage(company: AdminCompany) {
  selected.value = company
  managing.value = true
}

function updated(company: AdminCompany) {
  if (data.value) data.value = { ...data.value, items: data.value.items.map((item) => (item.identity === company.identity ? company : item)) }
}
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="Admin" subtitle="Review the terms partners propose and keep their companies in order." class="rise" />
    <AdminTabs class="rise" style="--rise: 1" />

    <div class="rise flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between" style="--rise: 2">
      <UiPills v-model="status" :options="STATUS_OPTIONS" label="Status" />
      <label class="relative block w-full lg:w-80">
        <span class="sr-only">Search companies or partners</span>
        <Icon name="search" :size="18" class="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-text-faint" />
        <input
          v-model="search"
          type="search"
          placeholder="Company or partner"
          spellcheck="false"
          autocapitalize="off"
          class="h-11 w-full rounded-md border border-line bg-card pr-3 pl-11 text-input text-text outline-none placeholder:text-text-faint focus:border-brand/70"
        />
      </label>
    </div>

    <UiError v-if="error" :error="error" :retry="run" />

    <template v-else-if="data && data.items.length">
      <ul class="space-y-3 transition-opacity duration-default" :class="loading && 'opacity-55'">
        <li
          v-for="company in data.items"
          :key="company.identity"
          class="flex flex-col gap-4 rounded-lg border bg-panel p-4 lg:flex-row lg:items-center lg:gap-6 lg:p-5"
          :class="company.status === 'IN_REVIEW' ? 'border-brand/35' : 'border-line-soft'"
        >
          <div class="min-w-0 flex-1">
            <p class="flex flex-wrap items-center gap-2">
              <span class="truncate text-section font-extrabold text-text">{{ company.identity }}</span>
              <UiChip :tone="STATUS_META[company.status].tone">{{ STATUS_META[company.status].label }}</UiChip>
            </p>
            <p class="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-body text-text-muted">
              <span class="inline-flex items-center gap-1.5">
                <Icon name="user" :size="14" class="text-text-faint" />
                <span class="font-bold text-text">{{ company.owner.login }}</span>
              </span>
              <UiChip v-if="!company.owner.active" tone="bad">Blocked</UiChip>
              <span aria-hidden="true" class="text-text-faint">·</span>
              <span>created {{ formatDate(company.createdAt) }}</span>
            </p>
            <p class="mt-2.5 flex flex-wrap items-baseline gap-x-2 text-name text-text">
              <span class="text-body text-text-faint">{{ TYPE_META[company.type].label }}</span>
              <span class="num font-semibold">{{ termsParts(company.type, company.amount, currency).value }}</span>
              <span class="text-body text-text-muted">{{ termsParts(company.type, company.amount, currency).basis }}</span>
              <span v-if="company.status === 'IN_REVIEW'" class="text-body text-text-faint">· proposed</span>
            </p>
          </div>
          <dl class="grid grid-cols-3 gap-4 border-t border-line-soft pt-3 lg:w-80 lg:border-t-0 lg:pt-0">
            <div>
              <dt class="micro-label text-micro text-text-faint">Players</dt>
              <dd class="num mt-0.5 text-name text-text">{{ formatCount(company.stats.registrations) }}</dd>
            </div>
            <div>
              <dt class="micro-label text-micro text-text-faint">FTD</dt>
              <dd class="num mt-0.5 text-name text-text">{{ formatCount(company.stats.firstDeposits) }}</dd>
            </div>
            <div class="min-w-0">
              <dt class="micro-label text-micro text-text-faint">Earned</dt>
              <dd class="num mt-0.5 truncate text-name text-text">{{ formatMoney(company.stats.earned) }}</dd>
            </div>
          </dl>
          <UiButton :icon="company.status === 'IN_REVIEW' ? 'check' : 'settings'" size="sm" class="lg:w-32" @click="manage(company)">
            {{ company.status === 'IN_REVIEW' ? 'Review' : 'Manage' }}
          </UiButton>
        </li>
      </ul>
      <UiPager v-model:page="page" :size="SIZE" :total="data.total" :busy="loading" />
    </template>

    <div v-else-if="data" class="surface">
      <UiEmpty
        icon="shield"
        :title="status === 'IN_REVIEW' && !query ? 'Nothing to review' : 'No companies found'"
        :text="status === 'IN_REVIEW' && !query ? 'New companies wait here until someone looks at their terms.' : 'Try another status or search.'"
      />
    </div>

    <div v-else class="space-y-3">
      <UiSkeleton v-for="index in 4" :key="index" class="h-28" />
    </div>

    <ManageCompanySheet v-model:open="managing" :company="selected" @updated="updated" />
  </div>
</template>
