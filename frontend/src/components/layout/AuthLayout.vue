<script setup lang="ts">
import BrandLockup from '@/components/brand/BrandLockup.vue'
import Icon from '@/components/ui/Icon.vue'
import type { IconName } from '@/components/ui/icons'
import { AUTH_ART } from '@/lib/art'
import { config } from '@/lib/session'

/**
 * The sign-in and sign-up screens: the form with the programme told plainly under it, beside the portal's
 * picture. From 1024 px the picture takes half the screen; on a phone it is a short band the logo sits on.
 */
const { supportUrl, currency } = config()

/** One `sizes` for the band and the half-screen panel, so both pick the same file and it downloads once. */
const SIZES = '(min-width: 64rem) 50vw, 100vw'

const POINTS: { icon: IconName; title: string; text: string }[] = [
  {
    icon: 'layers',
    title: 'Your terms, reviewed',
    text: 'Propose CPA or RevShare for each company you run. We review every company before it starts counting players.',
  },
  {
    icon: 'chart',
    title: 'Every number by day',
    text: 'Registrations, first deposits, deposits, turnover and earnings, for all your companies or for one.',
  },
  {
    icon: 'send',
    title: 'Postbacks to your tracker',
    text: 'Registrations and deposits reach Keitaro as server-to-server postbacks.',
  },
  {
    icon: 'wallet',
    title: `Payouts in ${currency}`,
    text: 'Request a payout from your balance whenever you like. We review it and send it by hand.',
  },
]
</script>

<template>
  <div class="min-h-dvh lg:grid lg:grid-cols-2">
    <div class="relative flex min-h-dvh flex-col lg:px-12 lg:pt-8 lg:pb-10 xl:px-20">
      <div class="flex h-topbar items-center justify-between px-gutter pt-safe lg:h-auto lg:px-0 lg:pt-0">
        <span class="flex lg:hidden"><BrandLockup :height="32" /></span>
        <span class="hidden lg:flex"><BrandLockup :height="40" /></span>
        <a
          v-if="supportUrl"
          :href="supportUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="flex h-11 items-center gap-1.5 rounded-sm px-1 text-body font-bold text-text-muted transition-colors duration-default hover:text-text"
        >
          <Icon name="send" :size="16" />
          Support
        </a>
      </div>

      <!-- The phone's band: the picture's subject, melting into the page above and below -->
      <div class="band relative -mt-2 h-[188px] overflow-hidden lg:hidden" aria-hidden="true">
        <img
          :src="AUTH_ART.src"
          :srcset="AUTH_ART.srcset"
          :sizes="SIZES"
          alt=""
          width="720"
          height="960"
          class="absolute inset-0 size-full object-cover object-[50%_27%] select-none"
        />
      </div>

      <main class="flex flex-1 flex-col px-gutter pt-2 pb-10 lg:items-start lg:justify-center lg:px-0 lg:py-12">
        <div class="w-full lg:max-w-[400px]">
          <slot />
        </div>
      </main>

      <ul class="grid grid-cols-1 gap-x-10 gap-y-5 border-t border-line-soft px-gutter pt-8 pb-12 sm:grid-cols-2 lg:px-0 lg:pb-0">
        <li v-for="point in POINTS" :key="point.title" class="flex gap-3.5">
          <span class="grid size-9 shrink-0 place-items-center rounded-sm border border-line-strong bg-wash text-brand-soft">
            <Icon :name="point.icon" :size="18" />
          </span>
          <span>
            <span class="block text-name font-bold text-text">{{ point.title }}</span>
            <span class="mt-0.5 block text-body text-text-muted">{{ point.text }}</span>
          </span>
        </li>
      </ul>
    </div>

    <aside class="relative hidden overflow-hidden lg:block" aria-hidden="true">
      <img
        :src="AUTH_ART.src"
        :srcset="AUTH_ART.srcset"
        :sizes="SIZES"
        alt=""
        width="720"
        height="960"
        class="absolute inset-0 size-full object-cover object-[50%_35%] select-none"
      />
    </aside>
  </div>
</template>

<style scoped>
/* The band is cut from the middle of the picture, so both of its edges fade into the page */
.band::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, var(--color-page), transparent 18%, transparent 70%, var(--color-page));
}
</style>
