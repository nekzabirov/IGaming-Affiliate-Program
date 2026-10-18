<script setup lang="ts">
import { useRoute } from 'vue-router'
import { ADMIN_NAV } from '@/components/layout/nav'

/** The two admin screens as one switch; each is its own address. The open one carries the lime seam. */
const route = useRoute()
</script>

<template>
  <nav class="flex h-11 w-full gap-1 rounded-md border border-line-soft bg-panel p-1 sm:inline-flex sm:w-auto" aria-label="Admin">
    <RouterLink
      v-for="item in ADMIN_NAV"
      :key="item.to"
      :to="item.to"
      class="hit-area flex flex-1 items-center justify-center gap-2.5 rounded-sm px-4 text-body font-bold transition-colors duration-default sm:flex-none"
      :class="route.path === item.to ? 'bg-wash text-text' : 'text-text-muted hover:text-text'"
      :aria-current="route.path === item.to ? 'page' : undefined"
    >
      <span class="seam transition-opacity duration-default" :class="route.path === item.to ? 'opacity-100' : 'opacity-0'" aria-hidden="true" />
      {{ item.label }}
    </RouterLink>
  </nav>
</template>
