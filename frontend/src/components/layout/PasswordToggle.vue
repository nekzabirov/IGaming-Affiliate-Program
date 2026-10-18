<script setup lang="ts">
import Icon from '@/components/ui/Icon.vue'

const shown = defineModel<boolean>({ required: true })
</script>

<template>
  <button
    type="button"
    class="grid size-11 shrink-0 place-items-center rounded-md text-text-faint transition-colors duration-default hover:text-text"
    :aria-label="shown ? 'Hide password' : 'Show password'"
    :aria-pressed="shown"
    @click="shown = !shown"
  >
    <Icon :name="shown ? 'eyeOff' : 'eye'" :size="20" />
  </button>
</template>
