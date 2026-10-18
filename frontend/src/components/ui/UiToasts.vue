<script setup lang="ts">
import { dismiss, toasts } from '@/lib/toast'
import Icon from './Icon.vue'

const ICONS = { success: 'check', error: 'warning', info: 'info' } as const

const TONES = { success: 'text-signal', error: 'text-danger', info: 'text-brand-soft' }
</script>

<template>
  <div
    class="pointer-events-none fixed inset-x-0 z-[60] flex flex-col items-center gap-2 px-gutter bottom-[calc(env(safe-area-inset-bottom)+var(--spacing-tabbar)+12px)] lg:bottom-6 lg:pl-sidebar"
    aria-live="polite"
    role="status"
  >
    <TransitionGroup enter-from-class="toast-hidden" leave-to-class="toast-gone" enter-active-class="toast-move" leave-active-class="toast-move">
      <div
        v-for="item in toasts"
        :key="item.id"
        class="pointer-events-auto flex max-w-md items-center gap-3 rounded-md border border-line bg-raise py-2.5 pr-2 pl-3.5 shadow-pop"
      >
        <Icon :name="ICONS[item.tone]" :size="18" :class="TONES[item.tone]" />
        <p class="text-name font-semibold text-text">{{ item.message }}</p>
        <button
          type="button"
          class="hit-area grid size-10 shrink-0 place-items-center rounded-sm text-text-faint transition-colors hover:text-text"
          aria-label="Dismiss"
          @click="dismiss(item.id)"
        >
          <Icon name="close" :size="16" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-move {
  transition:
    opacity 200ms var(--ease-default),
    transform 200ms var(--ease-default);
}
.toast-hidden {
  opacity: 0;
  transform: translateY(8px) scale(0.98);
}
.toast-gone {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .toast-hidden {
    transform: none;
  }
}
</style>
