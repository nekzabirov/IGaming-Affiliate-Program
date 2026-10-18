<script setup lang="ts">
import CopyButton from '@/components/ui/CopyButton.vue'
import UiButton from '@/components/ui/UiButton.vue'
import { formatBytes, promoUrl, type PromoItem } from '@/lib/promo'

/** One banner: the preview, what it is, and the full-quality file to save or to link to. */
const props = defineProps<{ item: PromoItem }>()

const file = promoUrl(props.item.file)
</script>

<template>
  <article class="surface flex flex-col overflow-hidden">
    <!-- One frame for every format: a story stands in it whole, a square sits centred, the grid stays even -->
    <div class="relative aspect-[4/5] bg-page/60">
      <img
        :src="promoUrl(item.preview)"
        :alt="`${item.title}, ${item.width}×${item.height}`"
        :width="item.width"
        :height="item.height"
        loading="lazy"
        decoding="async"
        class="absolute inset-3 size-[calc(100%-1.5rem)] object-contain"
      />
    </div>
    <div class="flex flex-1 flex-col gap-3 border-t border-line-soft p-4">
      <div class="min-w-0">
        <h2 class="truncate text-name font-extrabold text-text">{{ item.title }}</h2>
        <p class="num mt-1 truncate text-body text-text-faint">{{ item.width }}×{{ item.height }} · {{ formatBytes(item.bytes) }}</p>
      </div>
      <div class="mt-auto flex items-center gap-1">
        <UiButton size="sm" icon="download" :href="file" download class="flex-1">Download</UiButton>
        <CopyButton :value="file" label="link to the file" />
      </div>
    </div>
  </article>
</template>
