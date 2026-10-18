<script setup lang="ts">
import { ref } from 'vue'
import { copyText } from '@/lib/clipboard'
import Icon from './Icon.vue'

/** A quiet icon button beside a value; the icon turns into a tick for two seconds once copied. */
const props = defineProps<{ value: string; label: string }>()

const copied = ref(false)

let timer = 0

async function copy() {
  copied.value = await copyText(props.value)
  window.clearTimeout(timer)
  timer = window.setTimeout(() => (copied.value = false), 2000)
}
</script>

<template>
  <button
    type="button"
    class="grid size-11 shrink-0 place-items-center rounded-md transition-colors duration-default ease-default hover:bg-wash"
    :class="copied ? 'text-signal' : 'text-text-faint hover:text-text'"
    :aria-label="copied ? 'Copied' : `Copy ${label}`"
    @click.stop="copy"
  >
    <Icon :name="copied ? 'check' : 'copy'" :size="18" />
  </button>
</template>
