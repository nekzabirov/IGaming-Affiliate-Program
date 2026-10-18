<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { api } from '@/api'
import ArtImage from '@/components/brand/ArtImage.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import BalanceCard from '@/components/payouts/BalanceCard.vue'
import StatsPanel from '@/components/stats/StatsPanel.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiError from '@/components/ui/UiError.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { isPositive } from '@/lib/decimal'
import { useResource } from '@/lib/resource'
import { config, refreshMe, session } from '@/lib/session'

const companies = useResource((signal) => api.companies(signal))

const me = computed(() => session.me)

const canWithdraw = computed(() => isPositive(me.value?.balance))

const { currency } = config()

const STEPS = [
  { title: 'Create a company', text: 'Propose CPA or RevShare terms. We review them before the company starts counting players.' },
  { title: 'Send players with its link', text: 'Add your tracker’s click id to the link so postbacks can report registrations and deposits.' },
  { title: `Withdraw in ${currency}`, text: 'Commissions land on your balance. Request a payout whenever you like; we send it by hand.' },
]

onMounted(() => {
  void companies.run()
  // The balance moves while the cabinet is open; a failed refresh keeps the one already on screen
  refreshMe().catch(() => undefined)
})
</script>

<template>
  <div v-if="me" class="space-y-8 lg:space-y-10">
    <PageHeader title="Dashboard" subtitle="Your balance, and how your companies are doing." class="rise" />

    <BalanceCard :me="me" art class="rise" style="--rise: 1">
      <UiButton v-if="canWithdraw && companies.data.value?.length" variant="primary" to="/payouts" icon-end="arrowRight">Withdraw</UiButton>
      <UiButton v-else to="/payouts" icon-end="arrowRight">Payouts</UiButton>
    </BalanceCard>

    <UiError v-if="companies.error.value" :error="companies.error.value" :retry="companies.run" />

    <section v-else-if="companies.data.value && !companies.data.value.length" class="surface rise overflow-hidden" style="--rise: 2" aria-labelledby="first-step">
      <div class="flex flex-col gap-2 p-5 sm:p-6 lg:flex-row lg:items-center lg:gap-10 lg:p-8">
        <ArtImage name="companies" :size="208" eager class="mx-auto size-44 lg:order-last lg:mx-0 lg:size-52" />
        <div class="min-w-0 flex-1">
          <p class="micro-label text-micro text-signal">First step</p>
          <h2 id="first-step" class="headline mt-2 text-amount text-text">Start with a company</h2>
          <p class="mt-3 max-w-xl text-name text-text-muted">
            A company is one traffic source or campaign, with its own partner link and its own statistics.
          </p>
          <ol class="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
            <li v-for="(step, index) in STEPS" :key="step.title" class="flex gap-3">
              <span class="num grid size-7 shrink-0 place-items-center rounded-xs border border-line-strong text-body font-semibold text-text-muted">{{ index + 1 }}</span>
              <span>
                <span class="block text-name font-bold text-text">{{ step.title }}</span>
                <span class="mt-0.5 block text-body text-text-muted">{{ step.text }}</span>
              </span>
            </li>
          </ol>
          <UiButton variant="primary" icon="plus" to="/companies?new=1" class="mt-7">Create a company</UiButton>
        </div>
      </div>
    </section>

    <section v-else-if="companies.data.value" class="rise" style="--rise: 2" aria-label="Statistics">
      <StatsPanel />
    </section>

    <div v-else class="space-y-4">
      <UiSkeleton class="h-11 w-full sm:w-[420px]" />
      <UiSkeleton class="h-72" />
    </div>
  </div>
</template>
