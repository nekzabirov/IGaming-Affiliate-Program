<script setup lang="ts">
import type { Inline } from '@/postbacks/trackers'

defineProps<{ parts: Inline[] }>()
</script>

<template>
  <template v-for="(part, index) in parts" :key="index">
    <template v-if="typeof part === 'string'">{{ part }}</template>
    <strong v-else-if="'path' in part" class="font-bold whitespace-nowrap text-text">{{ part.path }}</strong>
    <code v-else class="num rounded-xs bg-raise px-1 py-px text-[0.92em] whitespace-nowrap text-brand-soft">{{ part.code }}</code>
  </template>
</template>
