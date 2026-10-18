<script setup lang="ts">
import ArtImage from '@/components/brand/ArtImage.vue'
import Icon from '@/components/ui/Icon.vue'

/** The way to a person: the Telegram support link, as a small glass card with its picture. */
defineProps<{ url: string }>()
</script>

<template>
  <a
    :href="url"
    target="_blank"
    rel="noopener noreferrer"
    class="glass group flex items-center gap-1 overflow-hidden py-1 pr-4 pl-1 transition-[border-color] duration-default ease-default hover:border-brand/40"
  >
    <ArtImage name="support" :size="96" class="-my-2 size-24 transition-transform duration-enter ease-default group-hover:-translate-y-0.5 motion-reduce:transition-none" />
    <span class="min-w-0">
      <span class="block text-body font-extrabold text-text">Questions?</span>
      <span class="mt-0.5 block text-body text-text-muted">Support answers on Telegram.</span>
      <span class="mt-1.5 inline-flex items-center gap-1 text-body font-bold text-brand-soft group-hover:text-text">
        Open chat
        <Icon name="external" :size="14" />
      </span>
    </span>
  </a>
</template>
