<script setup lang="ts">
import mark from '@/assets/brand/logo-mark.svg'
import { BRAND } from '@/brand'

/** The mark alone, for the places the lockup does not fit: the phone's top bar, the account sheet. */
withDefaults(defineProps<{ height?: number; to?: string | null }>(), { height: 28, to: '/' })
</script>

<template>
  <RouterLink v-if="to" :to="to" class="inline-flex shrink-0 rounded-xs" :aria-label="`${BRAND.name}, home`">
    <img :src="mark" alt="" :width="Math.round(height * 1.2)" :height="height" class="block max-w-none" />
  </RouterLink>
  <img v-else :src="mark" alt="" aria-hidden="true" :width="Math.round(height * 1.2)" :height="height" class="block max-w-none shrink-0" />
</template>
