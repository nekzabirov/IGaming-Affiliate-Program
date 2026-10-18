<script setup lang="ts">
import CopyRow from '@/components/ui/CopyRow.vue'
import type { GuideStep } from '@/postbacks/trackers'
import GuideText from './GuideText.vue'

/** A tracker's setup, step by step; a step can carry a value to copy, and a slot for a field of ours. */
defineProps<{ steps: GuideStep[] }>()
</script>

<template>
  <ol class="space-y-6">
    <li v-for="(step, index) in steps" :key="step.title" class="relative grid grid-cols-[2rem_minmax(0,1fr)] gap-3.5">
      <span
        v-if="index < steps.length - 1"
        class="absolute top-10 -bottom-4 left-4 w-px -translate-x-1/2 bg-line"
        aria-hidden="true"
      />
      <span class="num relative grid size-8 place-items-center rounded-sm border border-brand/35 bg-brand/14 text-body font-bold text-brand-soft">
        {{ index + 1 }}
      </span>
      <div class="min-w-0 pt-1">
        <p class="text-name font-extrabold text-text">{{ step.title }}</p>
        <p class="mt-1 text-body leading-relaxed text-text-muted"><GuideText :parts="step.text" /></p>
        <CopyRow v-if="step.copy" :label="step.copy.label" :value="step.copy.value" wrap class="mt-3" />
        <p v-if="step.aside" class="mt-2.5 text-body text-text-faint"><GuideText :parts="step.aside" /></p>
        <slot :name="`step-${index}`" />
      </div>
    </li>
  </ol>
</template>
