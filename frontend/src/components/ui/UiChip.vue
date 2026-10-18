<script setup lang="ts">
import type { Tone } from '@/lib/companies'

/**
 * A state badge. The label always carries the meaning; the tone and the slanted tick (the mark's seam, in
 * the state's colour) only make it faster to read.
 */
withDefaults(defineProps<{ tone?: Tone | 'brand' }>(), { tone: 'neutral' })

const TONES = {
  good: { box: 'bg-signal/10 text-signal ring-signal/20', tick: 'bg-signal' },
  wait: { box: 'bg-brand/14 text-brand-soft ring-brand/25', tick: 'bg-brand-soft' },
  bad: { box: 'bg-danger/10 text-danger ring-danger/20', tick: 'bg-danger' },
  neutral: { box: 'bg-wash text-text-muted ring-line-strong', tick: 'bg-text-faint' },
  brand: { box: 'bg-brand/14 text-brand-soft ring-brand/25', tick: 'bg-brand-soft' },
}
</script>

<template>
  <span class="inline-flex h-6 shrink-0 items-center gap-1.5 rounded-xs px-2 text-micro font-bold whitespace-nowrap ring-1 ring-inset" :class="TONES[tone].box">
    <span class="chip-tick h-2.5 w-[3px] shrink-0 rounded-[1px]" :class="TONES[tone].tick" aria-hidden="true" />
    <slot />
  </span>
</template>

<style scoped>
.chip-tick {
  transform: skewX(calc(-1 * var(--slant)));
}
</style>
