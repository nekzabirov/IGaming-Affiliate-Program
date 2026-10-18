<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { Payout } from '@/api/types'
import PageHeader from '@/components/layout/PageHeader.vue'
import BalanceCard from '@/components/payouts/BalanceCard.vue'
import PayoutForm from '@/components/payouts/PayoutForm.vue'
import PayoutHistory from '@/components/payouts/PayoutHistory.vue'
import UiButton from '@/components/ui/UiButton.vue'
import { refreshMe, session } from '@/lib/session'

const me = computed(() => session.me)

const history = ref<InstanceType<typeof PayoutHistory> | null>(null)

function requested(payout: Payout) {
  history.value?.prepend(payout)
}

// The balance moves while the cabinet is open; a failed refresh keeps the one already on screen
onMounted(() => refreshMe().catch(() => undefined))
</script>

<template>
  <div v-if="me" class="space-y-8 lg:space-y-10">
    <PageHeader title="Payouts" :subtitle="`Withdraw your balance in ${me.currency}. Every payout is reviewed and sent by hand.`" class="rise" />

    <BalanceCard :me="me" class="rise" style="--rise: 1">
      <UiButton to="/earnings" icon-end="arrowRight">Earnings</UiButton>
    </BalanceCard>

    <div class="rise grid grid-cols-1 items-start gap-4 lg:grid-cols-2 xl:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]" style="--rise: 2">
      <PayoutForm :me="me" @requested="requested" />
      <PayoutHistory ref="history" />
    </div>
  </div>
</template>
