<script setup lang="ts" generic="T extends string">
/** A row of choices of uneven length that scrolls sideways on a phone instead of squeezing its labels. */
defineProps<{
  options: { value: T; label: string }[]
  label: string
}>()

const model = defineModel<T>({ required: true })
</script>

<template>
  <div role="radiogroup" :aria-label="label" class="pills no-scrollbar -my-1 -ml-1 flex gap-1.5 overflow-x-auto py-1 pr-6 pl-1 lg:pr-1">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :aria-checked="option.value === model"
      class="hit-area h-9 shrink-0 rounded-full px-3.5 text-body font-extrabold whitespace-nowrap ring-1 transition-colors duration-default ease-default ring-inset"
      :class="
        option.value === model
          ? 'bg-brand/18 text-brand-soft ring-brand/50'
          : 'bg-transparent text-text-muted ring-line hover:text-text hover:ring-line-strong'
      "
      @click="model = option.value"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<style scoped>
/* The row fades out at its right edge on a phone, so a cut-off choice reads as "scroll for more" */
@media (width < 64rem) {
  .pills {
    mask-image: linear-gradient(to right, black calc(100% - 32px), transparent);
  }
}
</style>
