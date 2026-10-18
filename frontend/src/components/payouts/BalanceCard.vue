<script setup lang="ts">
import { computed } from 'vue'
import type { Me } from '@/api/types'
import { DASHBOARD_ART } from '@/lib/art'
import { isNegative } from '@/lib/decimal'
import { formatMoney } from '@/lib/format'

/**
 * The balance as the screen's hero number, with the three figures that explain it on glass. On the
 * dashboard it is the welcome card and stands on the portal's signal art; elsewhere it is plain glass.
 */
const props = defineProps<{ me: Me; art?: boolean }>()

const negative = computed(() => isNegative(props.me.balance))

const figures = computed(() => [
  { label: 'Earned', value: props.me.totalEarned },
  { label: 'Pending', value: props.me.pendingPayouts },
  { label: 'Paid out', value: props.me.paidOut },
])
</script>

<template>
  <section class="relative isolate overflow-hidden" :class="art ? 'rounded-xl border border-line-soft bg-page' : 'glass'">
    <div v-if="art" class="balance-art pointer-events-none" aria-hidden="true">
      <img :src="DASHBOARD_ART.src" :srcset="DASHBOARD_ART.srcset" sizes="(min-width: 64rem) 960px, 100vw" alt="" width="960" height="540" class="select-none" />
    </div>

    <div class="relative flex flex-col gap-6 p-5 sm:p-6 lg:p-8" :class="art ? 'balance-content' : 'xl:flex-row xl:items-end xl:justify-between xl:gap-10'">
      <div class="min-w-0">
        <p class="micro-label text-micro text-text-faint">Available balance</p>
        <p class="mt-3 flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span class="headline text-hero" :class="negative ? 'text-danger' : 'text-text'">{{ formatMoney(me.balance) }}</span>
          <span class="num text-section font-semibold text-text-faint">{{ me.currency }}</span>
        </p>
        <p v-if="negative" class="mt-3 max-w-md text-body text-text-muted">Below zero after a reversed commission: new earnings cover it first.</p>
        <div v-if="$slots.default" class="mt-6 flex flex-wrap gap-3">
          <slot />
        </div>
      </div>

      <dl class="grid shrink-0 grid-cols-3 gap-2 sm:gap-3" :class="!art && 'xl:w-[480px]'">
        <div v-for="figure in figures" :key="figure.label" class="glass min-w-0 rounded-md px-3 py-2.5 sm:px-4 sm:py-3">
          <dt class="micro-label truncate text-micro text-text-faint">{{ figure.label }}</dt>
          <dd class="num mt-1 truncate text-name font-semibold text-text">{{ formatMoney(figure.value) }}</dd>
        </div>
      </dl>
    </div>
  </section>
</template>

<style scoped>
/* The art keeps its subject right of centre: on a phone it is a band across the top of the card, from
   1024 px it fills the card behind the left-aligned figures */
.balance-art {
  position: absolute;
  inset: 0 0 auto 0;
  height: 168px;
  z-index: -1;
}

.balance-art img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 72% 50%;
}

/* The band is cut out of the middle of the picture, so its lower edge melts into the card */
.balance-art::after {
  content: '';
  position: absolute;
  inset: auto 0 0 0;
  height: 64px;
  background: linear-gradient(to bottom, transparent, var(--color-page));
}

.balance-content {
  padding-top: 132px;
}

@media (width >= 64rem) {
  .balance-art {
    inset: 0;
    height: auto;
  }

  .balance-art::after {
    content: none;
  }

  .balance-art img {
    position: absolute;
    top: 50%;
    right: -4%;
    width: auto;
    height: 150%;
    translate: 0 -50%;
    object-fit: initial;
  }

  /* The figures keep to the art's quiet side: most of a narrow desktop card, the left 46 % of a wide one */
  .balance-content {
    max-width: min(560px, 64%);
    padding-top: 2rem;
  }
}

@media (width >= 80rem) {
  .balance-content {
    max-width: 46%;
  }
}
</style>
