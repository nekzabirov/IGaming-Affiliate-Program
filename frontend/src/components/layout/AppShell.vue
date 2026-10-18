<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BrandLockup from '@/components/brand/BrandLockup.vue'
import BrandMark from '@/components/brand/BrandMark.vue'
import Icon from '@/components/ui/Icon.vue'
import { formatMoney } from '@/lib/format'
import { hasPromo } from '@/lib/promo'
import { config, isAdmin, session, signOut } from '@/lib/session'
import AccountSheet from './AccountSheet.vue'
import { ADMIN_NAV, ADMIN_TAB, PARTNER_NAV, PARTNER_TABS, isCurrent } from './nav'
import SupportCard from './SupportCard.vue'

/**
 * From 1024 px a sidebar holds the logo, the sections, support and the account; below it a top bar carries
 * the mark and the balance, and a bottom tab bar the sections a thumb reaches for.
 */
const route = useRoute()

const router = useRouter()

const menuOpen = ref(false)

const supportUrl = config().supportUrl

const partnerNav = PARTNER_NAV.filter((item) => item.to !== '/promo' || hasPromo())

const tabs = computed(() => (isAdmin() ? [...PARTNER_TABS, ADMIN_TAB] : PARTNER_TABS))

async function leave() {
  await signOut()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="min-h-dvh">
    <aside class="fixed inset-y-0 left-0 z-30 hidden w-sidebar flex-col border-r border-line-soft bg-panel lg:flex" aria-label="Sidebar">
      <div class="px-4 pt-7 pb-9">
        <BrandLockup :height="40" />
      </div>

      <nav class="flex flex-col gap-0.5 px-3" aria-label="Main">
        <RouterLink
          v-for="item in partnerNav"
          :key="item.to"
          :to="item.to"
          class="side-link"
          :class="isCurrent(item, route.path) && 'side-link-current'"
          :aria-current="isCurrent(item, route.path) ? 'page' : undefined"
        >
          <span class="seam side-seam" aria-hidden="true" />
          <Icon :name="item.icon" />
          {{ item.label }}
        </RouterLink>
      </nav>

      <nav v-if="isAdmin()" class="mt-8 flex flex-col gap-0.5 px-3" aria-label="Admin">
        <p class="micro-label px-4 pb-2 text-micro text-text-faint">Admin</p>
        <RouterLink
          v-for="item in ADMIN_NAV"
          :key="item.to"
          :to="item.to"
          class="side-link"
          :class="isCurrent(item, route.path) && 'side-link-current'"
          :aria-current="isCurrent(item, route.path) ? 'page' : undefined"
        >
          <span class="seam side-seam" aria-hidden="true" />
          <Icon :name="item.icon" />
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="mt-auto space-y-2 p-3">
        <SupportCard v-if="supportUrl" :url="supportUrl" />

        <div v-if="session.me" class="flex items-center gap-3 rounded-md px-2 py-2">
          <span class="grid size-9 shrink-0 place-items-center rounded-sm bg-brand/16 text-name font-extrabold text-brand-soft uppercase ring-1 ring-brand/25 ring-inset">
            {{ session.me.login.charAt(0) }}
          </span>
          <div class="min-w-0 flex-1">
            <p class="truncate text-body font-bold text-text">{{ session.me.login }}</p>
            <p class="num truncate text-micro text-text-muted">{{ formatMoney(session.me.balance, session.me.currency) }}</p>
          </div>
          <button
            type="button"
            class="hit-area grid size-10 shrink-0 place-items-center rounded-sm text-text-faint transition-colors duration-default hover:bg-wash hover:text-text"
            aria-label="Sign out"
            title="Sign out"
            @click="leave"
          >
            <Icon name="logout" :size="18" />
          </button>
        </div>
      </div>
    </aside>

    <header class="sticky top-0 z-30 border-b border-line-soft bg-page/85 pt-safe backdrop-blur-xl lg:hidden">
      <div class="flex h-topbar items-center justify-between gap-3 px-gutter">
        <BrandMark :height="26" />
        <button
          v-if="session.me"
          type="button"
          class="pressable flex h-10 min-w-0 items-center gap-2.5 rounded-full border border-line-strong bg-wash pr-1 pl-3.5"
          aria-label="Account menu"
          @click="menuOpen = true"
        >
          <span class="num truncate text-body font-semibold text-text">{{ formatMoney(session.me.balance) }}</span>
          <span class="grid size-8 shrink-0 place-items-center rounded-full bg-brand/16 text-body font-extrabold text-brand-soft uppercase">
            {{ session.me.login.charAt(0) }}
          </span>
        </button>
      </div>
    </header>

    <main class="pb-tabbar lg:pb-20 lg:pl-sidebar">
      <div class="mx-auto w-full max-w-content px-gutter pt-6 lg:px-10 lg:pt-10">
        <RouterView v-slot="{ Component, route: current }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="current.path" />
          </Transition>
        </RouterView>
      </div>
    </main>

    <nav class="fixed inset-x-0 bottom-0 z-30 border-t border-line-soft bg-panel/90 pb-safe backdrop-blur-xl lg:hidden" aria-label="Main">
      <div class="grid h-tabbar" :style="{ gridTemplateColumns: `repeat(${tabs.length}, minmax(0, 1fr))` }">
        <RouterLink
          v-for="item in tabs"
          :key="item.to"
          :to="item.to"
          class="relative flex flex-col items-center justify-center gap-1 text-micro font-bold transition-colors duration-default"
          :class="isCurrent(item, route.path) ? 'text-text' : 'text-text-faint'"
          :aria-current="isCurrent(item, route.path) ? 'page' : undefined"
        >
          <span class="tab-seam" :class="isCurrent(item, route.path) && 'tab-seam-on'" aria-hidden="true" />
          <Icon :name="item.icon" :size="22" :class="isCurrent(item, route.path) && 'text-signal'" />
          {{ item.label }}
        </RouterLink>
      </div>
    </nav>

    <AccountSheet v-model:open="menuOpen" />
  </div>
</template>

<style scoped>
@reference '../../styles/main.css';

.side-link {
  @apply relative flex h-11 items-center gap-3 rounded-md pr-3 pl-4 text-name font-semibold text-text-muted transition-colors duration-default hover:bg-wash hover:text-text;
}

.side-link :deep(svg) {
  color: var(--color-text-faint);
  transition: color var(--transition-duration-default) var(--ease-default);
}

.side-link:hover :deep(svg) {
  color: var(--color-text-muted);
}

/* The section the reader is in: white text on a whisper of white, marked by the lime seam at its edge */
.side-link-current {
  @apply bg-wash font-bold text-text;
}

.side-link-current :deep(svg),
.side-link-current:hover :deep(svg) {
  color: var(--color-text);
}

.side-seam {
  position: absolute;
  top: 50%;
  left: 2px;
  translate: 0 -50%;
  opacity: 0;
  transition: opacity var(--transition-duration-default) var(--ease-default);
}

.side-link-current .side-seam {
  opacity: 1;
}

/* The phone's active tab wears the seam along its top edge */
.tab-seam {
  position: absolute;
  top: -1px;
  left: 50%;
  width: 28px;
  height: 3px;
  border-radius: 1px;
  background: var(--color-signal);
  translate: -50% 0;
  transform: skewX(calc(-1 * var(--slant)));
  opacity: 0;
  transition: opacity var(--transition-duration-default) var(--ease-default);
}

.tab-seam-on {
  opacity: 1;
}

.page-enter-active {
  transition:
    opacity 140ms var(--ease-default),
    transform 140ms var(--ease-default);
}
.page-leave-active {
  transition: opacity 80ms linear;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.page-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .page-enter-from {
    transform: none;
  }
}
</style>
