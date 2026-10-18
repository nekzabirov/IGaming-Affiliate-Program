<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
import Icon from '@/components/ui/Icon.vue'

defineProps<{ title: string; subtitle?: string; back?: { to: RouteLocationRaw; label: string } }>()
</script>

<template>
  <header class="flex flex-wrap items-end justify-between gap-x-6 gap-y-4">
    <div class="min-w-0">
      <RouterLink
        v-if="back"
        :to="back.to"
        class="hit-area -ml-1 mb-2 inline-flex h-9 items-center gap-1.5 rounded-sm px-1 text-body font-bold text-text-muted transition-colors duration-default hover:text-text"
      >
        <Icon name="arrowLeft" :size="16" />
        {{ back.label }}
      </RouterLink>
      <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
        <h1 class="headline text-title break-words text-text">{{ title }}</h1>
        <slot name="status" />
      </div>
      <p v-if="subtitle" class="mt-2.5 max-w-2xl text-name text-text-muted">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="flex shrink-0 items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
