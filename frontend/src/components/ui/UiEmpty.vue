<script setup lang="ts">
import ArtImage from '@/components/brand/ArtImage.vue'
import type { SquareArtName } from '@/lib/art'
import Icon from './Icon.vue'
import type { IconName } from './icons'

/**
 * An empty state: what is missing and the one thing that fills it. The screens a partner starts from
 * (companies, postbacks, payouts) carry their picture; every other empty list gets a quiet glyph.
 */
withDefaults(defineProps<{ art?: SquareArtName; icon?: IconName; title: string; text?: string; compact?: boolean }>(), { icon: 'info' })
</script>

<template>
  <div class="flex flex-col items-center px-6 text-center" :class="compact ? 'gap-3 py-8' : 'gap-4 py-10 lg:py-12'">
    <ArtImage v-if="art" :name="art" :size="compact ? 160 : 208" :class="compact ? 'size-40' : 'size-44 lg:size-52'" />
    <span v-else class="grid size-12 place-items-center rounded-md border border-line-strong bg-wash text-text-faint" aria-hidden="true">
      <Icon :name="icon" :size="22" />
    </span>
    <div class="max-w-sm">
      <h3 class="text-section font-extrabold text-text">{{ title }}</h3>
      <p v-if="text" class="mt-1.5 text-name text-text-muted">{{ text }}</p>
    </div>
    <div v-if="$slots.default" class="mt-1">
      <slot />
    </div>
  </div>
</template>
