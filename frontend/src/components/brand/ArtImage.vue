<script setup lang="ts">
import { SQUARE_ART, type SquareArtName } from '@/lib/art'

/**
 * One of the square pictures at the size it is shown. Decoration only: the words around it carry the
 * meaning. Its backdrop is the page colour, so `lighten` lets a card or panel show through it instead of a
 * dark square.
 */
withDefaults(defineProps<{ name: SquareArtName; size: number; eager?: boolean }>(), {})
</script>

<template>
  <img
    :src="SQUARE_ART[name].src"
    :srcset="SQUARE_ART[name].srcset"
    :sizes="`${size}px`"
    :width="size"
    :height="size"
    alt=""
    aria-hidden="true"
    :loading="eager ? 'eager' : 'lazy'"
    decoding="async"
    class="pointer-events-none block shrink-0 mix-blend-lighten select-none"
  />
</template>
