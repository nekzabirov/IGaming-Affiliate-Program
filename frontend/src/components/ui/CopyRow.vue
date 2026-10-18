<script setup lang="ts">
import { ref } from 'vue'
import { copyText } from '@/lib/clipboard'
import UrlText from './UrlText.vue'

/** A value to paste somewhere else, with its label; the whole row is the copy button. */
const props = defineProps<{ label: string; value: string; wrap?: boolean }>()

const state = ref<'idle' | 'copied' | 'failed'>('idle')

let timer = 0

async function copy() {
  state.value = (await copyText(props.value)) ? 'copied' : 'failed'
  window.clearTimeout(timer)
  timer = window.setTimeout(() => (state.value = 'idle'), 2000)
}
</script>

<template>
  <div>
    <p class="micro-label text-micro text-text-faint">{{ label }}</p>
    <button
      type="button"
      class="group mt-1.5 flex w-full items-center gap-3 rounded-md border border-line-soft bg-page/60 px-3 text-left transition-colors duration-default ease-default hover:border-brand/40"
      :class="wrap ? 'min-h-11 py-2.5' : 'h-11'"
      :aria-label="`Copy ${label.toLowerCase()}`"
      @click="copy"
    >
      <span v-if="wrap" class="num min-w-0 flex-1 text-body text-text select-all"><UrlText :url="value" /></span>
      <span v-else class="num min-w-0 flex-1 truncate text-body text-text select-all">{{ value }}</span>
      <span class="relative grid shrink-0 justify-items-end" aria-live="polite">
        <span class="micro-label copy-word col-start-1 row-start-1 text-micro text-brand-soft" :class="state !== 'idle' && 'copy-word-out'">Copy</span>
        <span
          class="micro-label copy-word col-start-1 row-start-1 text-micro"
          :class="[state === 'idle' && 'copy-word-out', state === 'failed' ? 'text-danger' : 'text-signal']"
        >
          {{ state === 'failed' ? 'Failed' : 'Copied' }}
        </span>
      </span>
    </button>
  </div>
</template>

<style scoped>
/* The two words cross-fade through a soft blur, so the change reads as one morph rather than a swap */
.copy-word {
  transition:
    opacity 200ms var(--ease-default),
    filter 200ms var(--ease-default);
}
.copy-word-out {
  opacity: 0;
  filter: blur(2px);
}
</style>
