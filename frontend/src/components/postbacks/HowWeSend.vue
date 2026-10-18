<script setup lang="ts">
import type { PostbackExample } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import type { Tracker } from '@/postbacks/trackers'
import ExampleRequests from './ExampleRequests.vue'
import GuideText from './GuideText.vue'

/**
 * How the sender behaves, for one tracker: the method, when each event fires, what every parameter means,
 * and — once a URL is saved — the exact requests. The facts in the list mirror the server's sender.
 */
defineProps<{ tracker: Tracker; examples?: PostbackExample[] }>()

const FACTS = [
  { label: 'Retries', value: 'None' },
  { label: 'Timeout', value: '10 seconds' },
  { label: 'Redirects', value: 'Not followed' },
]
</script>

<template>
  <div class="space-y-6">
    <dl class="grid grid-cols-2 gap-2 sm:grid-cols-4">
      <div class="rounded-md border border-line-soft bg-page/60 px-3 py-2.5">
        <dt class="micro-label text-micro text-text-faint">Method</dt>
        <dd class="num mt-0.5 text-name font-bold text-brand-soft">{{ tracker.method }}</dd>
      </div>
      <div v-for="fact in FACTS" :key="fact.label" class="rounded-md border border-line-soft bg-page/60 px-3 py-2.5">
        <dt class="micro-label text-micro text-text-faint">{{ fact.label }}</dt>
        <dd class="mt-0.5 text-body font-bold text-text">{{ fact.value }}</dd>
      </div>
    </dl>

    <div v-if="tracker.events.length">
      <h4 class="micro-label text-micro text-text-faint">When we send</h4>
      <ul class="mt-2.5 divide-y divide-line-soft rounded-md border border-line-soft">
        <li v-for="rule in tracker.events" :key="rule.event" class="grid grid-cols-1 gap-1 px-3.5 py-3 sm:grid-cols-[10rem_minmax(0,1fr)] sm:gap-4">
          <p class="text-name font-extrabold text-text">{{ rule.title }}</p>
          <div class="text-body text-text-muted">
            <p>{{ rule.when }}</p>
            <p class="mt-1 text-text-faint">Sends <GuideText :parts="rule.sends" />.</p>
          </div>
        </li>
      </ul>
    </div>

    <div v-if="tracker.parameters.length">
      <h4 class="micro-label text-micro text-text-faint">Parameters</h4>
      <dl class="mt-2.5 divide-y divide-line-soft rounded-md border border-line-soft">
        <div v-for="parameter in tracker.parameters" :key="parameter.name" class="grid grid-cols-1 gap-1 px-3.5 py-2.5 sm:grid-cols-[6rem_minmax(0,1fr)] sm:gap-4">
          <dt class="num text-body font-bold text-brand-soft">{{ parameter.name }}</dt>
          <dd class="text-body text-text-muted">{{ parameter.meaning }}</dd>
        </div>
      </dl>
    </div>

    <ul class="space-y-2 text-body text-text-muted">
      <li class="flex gap-2.5">
        <Icon name="info" :size="16" class="mt-0.5 shrink-0 text-text-faint" />
        Each event is sent once, right after it happens. An event your tracker did not accept is not sent again.
      </li>
      <li class="flex gap-2.5">
        <Icon name="info" :size="16" class="mt-0.5 shrink-0 text-text-faint" />
        A player who arrived without a click id gets no postback; the delivery status shows it as skipped.
      </li>
      <li class="flex gap-2.5">
        <Icon name="info" :size="16" class="mt-0.5 shrink-0 text-text-faint" />
        Spins and bets are sent with the whole stake, bonus money included. Your commission is never sent; it is in your statistics here.
      </li>
      <li class="flex gap-2.5">
        <Icon name="info" :size="16" class="mt-0.5 shrink-0 text-text-faint" />
        Any 2xx answer counts as delivered. Paste the final address: if your tracker redirects http to https, use https.
      </li>
      <li class="flex gap-2.5">
        <Icon name="info" :size="16" class="mt-0.5 shrink-0 text-text-faint" />
        <span>Requests carry the User-Agent <code class="num rounded-xs bg-raise px-1 py-px text-[0.92em] text-brand-soft">affiliate-app-postback/1.0</code>.</span>
      </li>
    </ul>

    <div>
      <h4 class="micro-label text-micro text-text-faint">Exact requests</h4>
      <ExampleRequests v-if="examples?.length" :examples="examples" class="mt-2.5" />
      <p v-else class="mt-2.5 rounded-md border border-line-soft bg-page/60 p-3.5 text-body text-text-muted">
        They appear as soon as the URL is saved: the server builds them with the same code that sends them, for a sample click id, deposit and
        payout.
      </p>
    </div>
  </div>
</template>
