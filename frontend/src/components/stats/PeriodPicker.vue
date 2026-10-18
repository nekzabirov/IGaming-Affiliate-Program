<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Icon from '@/components/ui/Icon.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import { MAX_RANGE_DAYS, PRESETS, presetRange, rangeError, todayUtc, type Preset, type Range } from '@/lib/dates'
import { formatDay } from '@/lib/format'
import type { Period } from '@/lib/period'

/** The one filter row of a stats screen: presets first, a custom range behind the last segment. */
const props = defineProps<{ period: Period }>()

const emit = defineEmits<{ choose: [Period] }>()

type Choice = Preset | 'custom'

const OPTIONS: { value: Choice; label: string }[] = [
  ...PRESETS.map((preset) => ({ value: preset.key, label: preset.key === 'today' ? 'Today' : preset.key.toUpperCase() })),
  { value: 'custom', label: 'Custom' },
]

const choice = computed<Choice>({
  get: () => props.period.preset,
  set: (value) =>
    emit('choose', value === 'custom' ? { preset: 'custom', range: { ...props.period.range } } : { preset: value, range: presetRange(value) }),
})

const draft = ref<Range>({ ...props.period.range })

watch(
  () => props.period.range,
  (range) => (draft.value = { ...range }),
)

const draftError = computed(() => rangeError(draft.value))

const today = todayUtc()

function apply() {
  if (!draftError.value) emit('choose', { preset: 'custom', range: { ...draft.value } })
}

const summary = computed(() => {
  const { from, to } = props.period.range
  return from === to ? formatDay(from) : `${formatDay(from)} – ${formatDay(to)}`
})
</script>

<template>
  <div class="space-y-3">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <UiSegmented v-model="choice" :options="OPTIONS" label="Period" class="w-full sm:w-[420px]" />
      <p class="flex items-center gap-2 text-body text-text-muted">
        <Icon name="calendar" :size="16" class="text-text-faint" />
        <span class="num">{{ summary }}</span>
        <span class="micro-label rounded-xs border border-line-strong px-1.5 py-0.5 text-micro text-text-faint" title="Days are counted in UTC">UTC</span>
      </p>
    </div>
    <div v-if="period.preset === 'custom'" class="flex flex-wrap items-end gap-3">
      <label class="flex flex-col gap-1.5">
        <span class="micro-label text-micro text-text-faint">From</span>
        <input v-model="draft.from" type="date" :max="draft.to || today" class="date-input" @change="apply" />
      </label>
      <label class="flex flex-col gap-1.5">
        <span class="micro-label text-micro text-text-faint">To</span>
        <input v-model="draft.to" type="date" :min="draft.from" :max="today" class="date-input" @change="apply" />
      </label>
      <p class="pb-3 text-micro" :class="draftError ? 'font-bold text-danger' : 'text-text-faint'">
        {{ draftError ?? `Up to ${MAX_RANGE_DAYS} days, in UTC.` }}
      </p>
    </div>
  </div>
</template>

<style scoped>
@reference '../../styles/main.css';

.date-input {
  @apply num h-11 rounded-md border border-line bg-card px-3 text-input text-text outline-none focus:border-brand/70;
}
</style>
