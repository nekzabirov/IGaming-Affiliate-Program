<script setup lang="ts">
import { computed } from 'vue'
import type { Postback } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import type { IconName } from '@/components/ui/icons'
import { formatDateTime, formatRelative } from '@/lib/format'

/** What happened to the last event sent to this tracker, in the words the server recorded. */
const props = defineProps<{ postback: Postback }>()

interface State {
  icon: IconName
  label: string
  tone: string
  detail: string | null
}

const state = computed<State>(() => {
  const { lastSentAt, lastStatus, lastError } = props.postback
  if (!lastSentAt && lastStatus === null && !lastError) {
    const detail = props.postback.active
      ? 'The next registration or deposit with a click id is sent here.'
      : 'Paused: nothing is sent here until you turn it back on.'
    return { icon: 'clock', label: 'No deliveries yet', tone: 'text-text-faint', detail }
  }
  if (lastError?.startsWith('Skipped')) return { icon: 'info', label: 'Skipped', tone: 'text-brand-soft', detail: lastError }
  if (!lastError && lastStatus !== null && lastStatus >= 200 && lastStatus < 300) {
    return { icon: 'check', label: `Delivered · HTTP ${lastStatus}`, tone: 'text-signal', detail: null }
  }
  const label = lastStatus === null ? 'Failed' : `Failed · HTTP ${lastStatus}`
  return { icon: 'warning', label, tone: 'text-danger', detail: lastError && lastError !== `HTTP ${lastStatus}` ? lastError : null }
})
</script>

<template>
  <div class="text-body">
    <p class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
      <span class="inline-flex items-center gap-1.5 font-extrabold" :class="state.tone">
        <Icon :name="state.icon" :size="14" />
        {{ state.label }}
      </span>
      <time v-if="postback.lastSentAt" :datetime="postback.lastSentAt" :title="formatDateTime(postback.lastSentAt)" class="text-text-faint">
        {{ formatRelative(postback.lastSentAt) }}
      </time>
    </p>
    <p v-if="state.detail" class="mt-1 text-text-muted">{{ state.detail }}</p>
  </div>
</template>
