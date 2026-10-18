<script setup lang="ts">
import type { ApiError } from '@/api/client'
import { errorMessage } from '@/lib/errors'
import Icon from './Icon.vue'

/** A request that failed where its data should be, with the way to ask again. */
defineProps<{ error: ApiError; retry?: () => void }>()
</script>

<template>
  <div class="flex flex-col items-start gap-3 rounded-md border border-danger/25 bg-danger/8 p-4 sm:flex-row sm:items-center" role="alert">
    <Icon name="warning" class="text-danger" />
    <p class="min-w-0 flex-1 text-name text-text">{{ errorMessage(error) }}</p>
    <button
      v-if="retry"
      type="button"
      class="pressable h-11 shrink-0 rounded-md px-4 text-body font-bold text-brand-soft hover:bg-wash"
      @click="retry"
    >
      Try again
    </button>
  </div>
</template>
