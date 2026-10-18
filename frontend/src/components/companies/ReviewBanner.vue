<script setup lang="ts">
import ArtImage from '@/components/brand/ArtImage.vue'

/**
 * A company waiting for its terms to be reviewed: what that means for its players now, and what is worth
 * doing meanwhile. Shown on the company's page, which is also where a new company lands.
 */
</script>

<template>
  <section class="glass flex flex-col items-center gap-2 overflow-hidden p-5 text-center sm:flex-row sm:gap-5 sm:p-6 sm:text-left" aria-labelledby="review-title">
    <ArtImage name="review" :size="152" eager class="-my-3 size-32 sm:size-38" />
    <div class="min-w-0 flex-1">
      <p class="micro-label text-micro text-brand-soft">Under review</p>
      <h2 id="review-title" class="mt-1.5 text-section font-extrabold text-text">We are reviewing the terms you proposed</h2>
      <p class="mt-2 max-w-2xl text-name text-text-muted">We may adjust them. Players are counted once the company is active.</p>
      <p class="mt-2 max-w-2xl text-body text-text-faint">
        Meanwhile you can put the link into your tracker and add a postback, so everything is ready the moment it turns active.
      </p>
    </div>
  </section>
</template>
