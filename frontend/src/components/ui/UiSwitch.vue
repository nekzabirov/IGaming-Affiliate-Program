<script setup lang="ts">
defineProps<{ label: string; disabled?: boolean }>()

const model = defineModel<boolean>({ required: true })
</script>

<template>
  <button
    type="button"
    role="switch"
    :aria-checked="model"
    :aria-label="label"
    :disabled="disabled"
    class="group grid h-11 w-14 shrink-0 place-items-center disabled:opacity-50"
    @click="model = !model"
  >
    <!-- On is the live state, so it lights in signal lime -->
    <span class="relative h-6 w-10 rounded-full transition-colors duration-default ease-default" :class="model ? 'bg-signal' : 'bg-line'">
      <span
        class="absolute top-1 left-1 size-4 rounded-full shadow-card transition-[translate,background-color] duration-default ease-default motion-reduce:transition-none"
        :class="model ? 'translate-x-4 bg-on-primary' : 'bg-text-muted'"
      />
    </span>
  </button>
</template>
