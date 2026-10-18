<script setup lang="ts">
import type { PostbackExample } from '@/api/types'
import CopyButton from '@/components/ui/CopyButton.vue'
import UrlText from '@/components/ui/UrlText.vue'
import { EVENT_LABELS } from '@/postbacks/trackers'

/**
 * The requests exactly as the server builds them (it renders these with the same code that sends), with
 * the query taken apart so each parameter can be read at a glance.
 */
defineProps<{ examples: PostbackExample[] }>()

function split(url: string) {
  const at = url.indexOf('?')
  if (at < 0) return { base: url, params: [] }
  const params = url
    .slice(at + 1)
    .split('&')
    .filter(Boolean)
    .map((pair) => {
      const eq = pair.indexOf('=')
      return eq < 0 ? { key: pair, value: '' } : { key: pair.slice(0, eq), value: pair.slice(eq + 1) }
    })
  return { base: url.slice(0, at), params }
}
</script>

<template>
  <ul class="space-y-2.5">
    <li v-for="example in examples" :key="example.event" class="rounded-md border border-line-soft bg-page/60 py-2.5 pr-1.5 pl-3.5">
      <div class="flex items-center gap-2">
        <span class="micro-label text-micro text-text-muted">{{ EVENT_LABELS[example.event] }}</span>
        <span class="num ml-auto rounded-xs border border-brand/30 bg-brand/12 px-1.5 py-0.5 text-micro font-bold text-brand-soft">{{ example.method }}</span>
        <CopyButton :value="example.url" :label="`${EVENT_LABELS[example.event].toLowerCase()} request`" />
      </div>
      <p class="num pr-2 text-body leading-relaxed wrap-anywhere">
        <span class="text-text-muted"><UrlText :url="split(example.url).base" /></span>
        <template v-for="(param, index) in split(example.url).params" :key="index">
          <wbr />
          <span class="text-text-faint">{{ index ? '&' : '?' }}</span>
          <span class="text-brand-soft">{{ param.key }}</span>
          <span class="text-text-faint">=</span>
          <span class="text-text">{{ param.value }}</span>
        </template>
      </p>
    </li>
  </ul>
</template>
