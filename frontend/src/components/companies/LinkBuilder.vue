<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Company } from '@/api/types'
import CopyButton from '@/components/ui/CopyButton.vue'
import Icon from '@/components/ui/Icon.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiPills from '@/components/ui/UiPills.vue'
import UiSwitch from '@/components/ui/UiSwitch.vue'
import UrlText from '@/components/ui/UrlText.vue'
import { copyText } from '@/lib/clipboard'
import { partnerLink } from '@/lib/companies'
import { config } from '@/lib/session'

/**
 * The partner link, shaped for where it goes: with the tracker's click macro so postbacks can find the
 * click, and optionally straight onto the sign-up form. Copying it is the screen's one call to action.
 */
const props = defineProps<{ company: Company }>()

const { siteUrl, clickParam } = config()

type Click = 'keitaro' | 'custom' | 'none'

const CLICK_OPTIONS: { value: Click; label: string }[] = [
  { value: 'keitaro', label: 'Keitaro {subid}' },
  { value: 'custom', label: 'Other tracker' },
  { value: 'none', label: 'No tracker' },
]

const click = ref<Click>('keitaro')

const macro = ref('{clickid}')

const signUp = ref(false)

/** A macro goes into the query as it is typed, so it may not break the query apart. */
const macroValid = computed(() => /^[^\s&#?=]{1,64}$/.test(macro.value))

const link = computed(() => {
  if (click.value === 'keitaro' && !signUp.value) return props.company.link
  const value = click.value === 'keitaro' ? '{subid}' : click.value === 'custom' && macroValid.value ? macro.value : null
  return partnerLink(siteUrl, props.company.tag, clickParam, value, signUp.value)
})

const copied = ref(false)

let timer = 0

async function copy() {
  copied.value = await copyText(link.value)
  window.clearTimeout(timer)
  timer = window.setTimeout(() => (copied.value = false), 2000)
}
</script>

<template>
  <section class="surface flex flex-col p-4 lg:p-6">
    <div>
      <h2 class="text-section font-extrabold text-text">Partner link</h2>
      <p class="mt-1 text-body text-text-faint">Send players here, or use it as the offer URL in your tracker.</p>
    </div>

    <div class="mt-4 space-y-3">
      <div>
        <p class="micro-label mb-2 text-micro text-text-faint">Click id</p>
        <UiPills v-model="click" :options="CLICK_OPTIONS" label="Click id" />
      </div>
      <label v-if="click === 'custom'" class="block">
        <span class="sr-only">Your tracker's click macro</span>
        <input
          v-model.trim="macro"
          type="text"
          spellcheck="false"
          autocapitalize="off"
          placeholder="{clickid}"
          class="num h-11 w-full rounded-md border bg-page/60 px-3 text-input text-text outline-none"
          :class="macroValid ? 'border-line focus:border-brand/70' : 'border-danger'"
        />
        <span class="mt-1.5 block text-micro" :class="macroValid ? 'text-text-faint' : 'font-bold text-danger'">
          {{ macroValid ? 'Your tracker replaces it with its click id.' : 'No spaces, &, #, ? or =.' }}
        </span>
      </label>
      <p v-if="click === 'none'" class="text-micro text-text-faint">Players are still counted, but postbacks are skipped for them: there is no click to report.</p>
      <div class="flex items-center justify-between gap-3">
        <div>
          <p class="text-name font-bold text-text">Open the sign-up form</p>
          <p class="text-micro text-text-faint">The site opens with its registration form already on screen.</p>
        </div>
        <UiSwitch v-model="signUp" label="Open the sign-up form" />
      </div>
    </div>

    <p class="num mt-4 rounded-md border border-line-soft bg-page/60 p-3.5 text-body text-text select-all"><UrlText :url="link" /></p>

    <div class="mt-4 flex items-center gap-3">
      <UiButton variant="primary" :icon="copied ? 'check' : 'link'" class="flex-1 sm:flex-none" @click="copy">{{ copied ? 'Copied' : 'Copy link' }}</UiButton>
      <div class="flex min-w-0 items-center gap-1 rounded-md border border-line-soft bg-page/60 pl-3">
        <span class="micro-label text-micro text-text-faint">Tag</span>
        <span class="num truncate text-body text-text-muted">{{ company.tag }}</span>
        <CopyButton :value="company.tag" label="tag" />
      </div>
    </div>

    <p v-if="company.status !== 'ACTIVE'" class="mt-4 flex gap-2 text-body text-text-muted">
      <Icon name="info" :size="16" class="mt-0.5 shrink-0 text-brand-soft" />
      The link already works, but players are counted only while the company is active.
    </p>
  </section>
</template>
