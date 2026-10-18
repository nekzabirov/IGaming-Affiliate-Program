<script setup lang="ts" generic="T extends string">
import { computed } from 'vue'

/** A short set of equal choices; the selection wears the brand's violet and the thumb slides to it. */
const props = defineProps<{
  options: { value: T; label: string }[]
  label: string
}>()

const model = defineModel<T>({ required: true })

const index = computed(() => Math.max(0, props.options.findIndex((option) => option.value === model.value)))

function move(step: number) {
  const next = props.options[(index.value + step + props.options.length) % props.options.length]
  if (next) model.value = next.value
}
</script>

<template>
  <div
    role="radiogroup"
    :aria-label="label"
    class="relative flex h-11 rounded-md border border-line-soft bg-panel p-1"
    @keydown.left.prevent="move(-1)"
    @keydown.right.prevent="move(1)"
  >
    <span
      aria-hidden="true"
      class="absolute top-1 bottom-1 left-1 rounded-sm border border-brand/45 bg-brand/18 transition-transform duration-enter ease-move motion-reduce:transition-none"
      :style="{ width: `calc((100% - 8px) / ${options.length})`, transform: `translateX(${index * 100}%)` }"
    />
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :aria-checked="option.value === model"
      :tabindex="option.value === model ? 0 : -1"
      class="hit-area min-w-0 flex-1 truncate rounded-sm px-2 text-body font-extrabold transition-colors duration-default ease-default"
      :class="option.value === model ? 'text-text' : 'text-text-muted hover:text-text'"
      @click="model = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>
