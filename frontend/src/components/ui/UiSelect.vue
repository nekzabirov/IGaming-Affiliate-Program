<script setup lang="ts" generic="T extends string">
import { useId } from 'vue'
import Icon from './Icon.vue'

/** The platform's own picker in the field's clothes: the best list a phone can offer, for free. */
defineProps<{
  label: string
  options: { value: T; label: string }[]
  compact?: boolean
}>()

const model = defineModel<T>({ required: true })

const id = useId()
</script>

<template>
  <div
    class="relative flex items-center rounded-md border border-line bg-card transition-colors duration-default ease-default focus-within:border-brand/70 hover:border-line-strong"
    :class="compact ? 'h-11' : 'h-14'"
  >
    <label
      v-if="!compact"
      :for="id"
      class="pointer-events-none absolute top-2.5 left-4 text-label font-semibold text-text-faint"
    >
      {{ label }}
    </label>
    <select
      :id="id"
      v-model="model"
      :aria-label="compact ? label : undefined"
      class="h-full w-full cursor-pointer appearance-none rounded-md bg-transparent pr-10 pl-4 font-semibold text-text outline-none"
      :class="compact ? 'text-body' : 'pt-4 text-input'"
    >
      <option v-for="option in options" :key="option.value" :value="option.value" class="bg-panel text-text">{{ option.label }}</option>
    </select>
    <Icon name="chevronDown" :size="18" class="pointer-events-none absolute right-3 text-text-faint" />
  </div>
</template>
