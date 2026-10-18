<script setup lang="ts">
import lockup from '@/assets/brand/logo-lockup.svg'
import { BRAND } from '@/brand'

/**
 * The full logo: the mark and the name, outlined so it needs no font. 40 px tall by default, the height at which
 * its smaller words still read; never below 32 px (the mark takes over there).
 */
withDefaults(defineProps<{ height?: 32 | 36 | 40 | 48; to?: string | null }>(), { height: 40, to: '/' })
</script>

<template>
  <RouterLink v-if="to" :to="to" class="inline-flex shrink-0 rounded-xs" :aria-label="`${BRAND.name}, home`">
    <img :src="lockup" alt="" :width="height * 6" :height="height" class="block max-w-none" />
  </RouterLink>
  <img v-else :src="lockup" :alt="BRAND.name" :width="height * 6" :height="height" class="block max-w-none shrink-0" />
</template>
