<script setup lang="ts">
import { useRouter } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'
import UiSheet from '@/components/ui/UiSheet.vue'
import { formatMoney } from '@/lib/format'
import { hasPromo } from '@/lib/promo'
import { config, isAdmin, session, signOut } from '@/lib/session'
import { ADMIN_NAV } from './nav'
import SupportCard from './SupportCard.vue'

/** The phone's account menu: who is signed in, the balance, and the screens the bottom bar has no room for. */
const open = defineModel<boolean>('open', { required: true })

const router = useRouter()

const supportUrl = config().supportUrl

async function leave() {
  open.value = false
  await signOut()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <UiSheet v-model:open="open" title="Account">
    <div v-if="session.me" class="space-y-5">
      <div class="glass flex items-center gap-3 p-4">
        <span class="grid size-11 shrink-0 place-items-center rounded-sm bg-brand/16 text-section font-extrabold text-brand-soft uppercase ring-1 ring-brand/25 ring-inset">
          {{ session.me.login.charAt(0) }}
        </span>
        <div class="min-w-0 flex-1">
          <p class="truncate text-name font-extrabold text-text">{{ session.me.login }}</p>
          <p class="text-body text-text-faint">{{ session.me.role === 'ADMIN' ? 'Administrator' : 'Partner' }}</p>
        </div>
        <div class="text-right">
          <p class="micro-label text-micro text-text-faint">Balance</p>
          <p class="num text-name font-semibold text-text">{{ formatMoney(session.me.balance, session.me.currency) }}</p>
        </div>
      </div>

      <nav class="flex flex-col" aria-label="Account">
        <RouterLink to="/earnings" class="sheet-link text-text" @click="open = false">
          <Icon name="list" class="text-text-faint" />
          <span class="flex-1">Earnings</span>
          <Icon name="chevronRight" :size="18" class="text-text-faint" />
        </RouterLink>
        <RouterLink v-if="hasPromo()" to="/promo" class="sheet-link text-text" @click="open = false">
          <Icon name="image" class="text-text-faint" />
          <span class="flex-1">Promo</span>
          <Icon name="chevronRight" :size="18" class="text-text-faint" />
        </RouterLink>
        <template v-if="isAdmin()">
          <RouterLink v-for="item in ADMIN_NAV" :key="item.to" :to="item.to" class="sheet-link text-text" @click="open = false">
            <Icon :name="item.icon" class="text-text-faint" />
            <span class="flex-1">{{ item.label }}</span>
            <Icon name="chevronRight" :size="18" class="text-text-faint" />
          </RouterLink>
        </template>
        <button type="button" class="sheet-link text-danger" @click="leave">
          <Icon name="logout" />
          <span class="flex-1 text-left">Sign out</span>
        </button>
      </nav>

      <SupportCard v-if="supportUrl" :url="supportUrl" />
    </div>
  </UiSheet>
</template>

<style scoped>
@reference '../../styles/main.css';

.sheet-link {
  @apply flex h-13 items-center gap-3 rounded-md px-2 text-name font-bold transition-colors duration-default hover:bg-wash;
}
</style>
