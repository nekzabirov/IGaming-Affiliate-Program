<script setup lang="ts">
import { computed } from 'vue'

/** A URL that wraps between its parts (after `/ ? & =`) and breaks inside a part only when one alone is too long. */
const props = defineProps<{ url: string }>()

const parts = computed(() => props.url.split(/(?<=[/?&=])/))
</script>

<template>
  <span class="wrap-anywhere"><template v-for="(part, index) in parts" :key="index">{{ part }}<wbr /></template></span>
</template>
