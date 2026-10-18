<script setup lang="ts">
import UiButton from '@/components/ui/UiButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
</script>

<template>
  <div class="surface">
    <UiEmpty icon="search" title="This page does not exist" text="The link may be old, or the address has a typo.">
      <UiButton to="/" icon="arrowLeft">Back to the dashboard</UiButton>
    </UiEmpty>
  </div>
</template>
