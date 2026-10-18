<script setup lang="ts">
import { computed } from 'vue'
import { formatCount } from '@/lib/format'
import Icon from './Icon.vue'

const props = defineProps<{ size: number; total: number; busy?: boolean }>()

const page = defineModel<number>('page', { required: true })

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

const first = computed(() => (props.total === 0 ? 0 : page.value * props.size + 1))

const last = computed(() => Math.min(props.total, (page.value + 1) * props.size))
</script>

<template>
  <nav v-if="total > size" class="flex items-center justify-between gap-3" aria-label="Pages">
    <p class="num text-body text-text-faint">
      <span class="text-text-muted">{{ formatCount(first) }}–{{ formatCount(last) }}</span> of {{ formatCount(total) }}
    </p>
    <div class="flex gap-1.5">
      <button
        type="button"
        class="pressable grid size-11 place-items-center rounded-md border border-line-strong bg-wash text-text hover:bg-text/7 disabled:border-line-soft disabled:bg-transparent disabled:text-text-disabled"
        :disabled="page === 0 || busy"
        aria-label="Previous page"
        @click="page -= 1"
      >
        <Icon name="chevronLeft" />
      </button>
      <button
        type="button"
        class="pressable grid size-11 place-items-center rounded-md border border-line-strong bg-wash text-text hover:bg-text/7 disabled:border-line-soft disabled:bg-transparent disabled:text-text-disabled"
        :disabled="page >= pages - 1 || busy"
        aria-label="Next page"
        @click="page += 1"
      >
        <Icon name="chevronRight" />
      </button>
    </div>
  </nav>
</template>
