<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import { asApiError } from '@/api/client'
import type { Company, Postback, PostbackType } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import UiSheet from '@/components/ui/UiSheet.vue'
import { partnerLink } from '@/lib/companies'
import { errorField, errorMessage } from '@/lib/errors'
import { config } from '@/lib/session'
import { trackerFor } from '@/postbacks/trackers'
import ExampleRequests from './ExampleRequests.vue'
import HowWeSend from './HowWeSend.vue'
import TrackerGuide from './TrackerGuide.vue'

/**
 * Adding a postback: pick the tracker, follow its setup with the URL field right where step one asks for
 * it, read how we send, save. Once saved, the server's own example requests show exactly what will arrive.
 */
const props = defineProps<{ company: Company }>()

const open = defineModel<boolean>('open', { required: true })

const emit = defineEmits<{ created: [Postback] }>()

const { postbackTypes, siteUrl, clickParam, postbackSourceIp } = config()

const type = ref<PostbackType>(postbackTypes[0] ?? 'KEITARO')

const tracker = computed(() => trackerFor(type.value))

const steps = computed(() =>
  tracker.value.steps({
    offerUrl: partnerLink(siteUrl, props.company.tag, clickParam, tracker.value.clickMacro || null),
    clickParam,
    sourceIp: postbackSourceIp,
  }),
)

const url = ref('')

const urlError = ref<string | null>(null)

const formError = ref<string | null>(null)

const busy = ref(false)

const created = ref<Postback | null>(null)

watch(url, () => (urlError.value = null))

function localUrlError(value: string): string | null {
  if (!value) return 'Paste the postback URL.'
  try {
    const parsed = new URL(value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? null : 'The URL must start with http:// or https://.'
  } catch {
    return 'This does not look like a URL. Copy it from your tracker as it is.'
  }
}

async function submit() {
  formError.value = null
  const value = url.value.trim()
  urlError.value = localUrlError(value)
  if (urlError.value) return
  busy.value = true
  try {
    created.value = await api.createPostback(props.company.identity, { type: type.value, url: value })
    emit('created', created.value)
  } catch (cause) {
    const error = asApiError(cause)
    if (errorField(error) === 'url') urlError.value = errorMessage(error)
    else formError.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}

function reset() {
  url.value = ''
  urlError.value = null
  formError.value = null
  created.value = null
}
</script>

<template>
  <UiSheet
    v-model:open="open"
    :title="created ? 'Postback added' : 'Add postback'"
    :description="created ? undefined : `Report the registrations, deposits, spins and bets of ${company.identity} to your tracker.`"
    wide
    @closed="reset"
  >
    <div v-if="created" class="space-y-6">
      <p class="flex gap-3 rounded-md border border-signal-line bg-signal/6 p-3.5 text-name text-text">
        <Icon name="check" class="shrink-0 text-signal" />
        {{ tracker.name }} now hears about every registration and deposit of players who arrived with a click id.
      </p>
      <section>
        <h3 class="micro-label text-micro text-text-faint">Exactly what we send</h3>
        <ExampleRequests :examples="created.examples" class="mt-3" />
        <p class="mt-3 text-body text-text-faint">The click id, deposit id and payout in these requests are samples; real ones carry the real values.</p>
      </section>
    </div>

    <div v-else class="space-y-8">
      <section>
        <h3 class="micro-label text-micro text-text-faint">Tracker</h3>
        <div role="radiogroup" aria-label="Tracker" class="mt-3 grid grid-cols-1 gap-2.5 sm:grid-cols-3">
          <button
            v-for="option in postbackTypes"
            :key="option"
            type="button"
            role="radio"
            :aria-checked="type === option"
            class="pressable flex h-14 items-center gap-3 rounded-md border px-3.5 text-left"
            :class="type === option ? 'border-brand/60 bg-brand/12' : 'border-line bg-card hover:border-line-strong'"
            @click="type = option"
          >
            <span class="grid size-8 shrink-0 place-items-center rounded-sm border border-line-strong bg-wash text-name font-extrabold text-text">
              {{ trackerFor(option).name.charAt(0) }}
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-name font-extrabold text-text">{{ trackerFor(option).name }}</span>
              <span class="block text-micro text-text-faint">S2S, {{ trackerFor(option).method }}</span>
            </span>
            <Icon v-if="type === option" name="check" :size="18" class="text-brand-soft" />
          </button>
        </div>
      </section>

      <section>
        <h3 class="micro-label text-micro text-text-faint">Set it up in {{ tracker.name }}</h3>
        <form id="add-postback" class="mt-4" novalidate @submit.prevent="submit">
          <TrackerGuide v-if="steps.length" :steps="steps">
            <template #step-0>
              <div class="mt-3" data-autofocus>
                <UiField
                  v-model="url"
                  :label="tracker.url.label"
                  :placeholder="tracker.url.placeholder"
                  :hint="tracker.url.hint"
                  :error="urlError"
                  inputmode="url"
                  mono
                />
              </div>
            </template>
          </TrackerGuide>
          <div v-else data-autofocus>
            <UiField v-model="url" :label="tracker.url.label" :placeholder="tracker.url.placeholder" :error="urlError" inputmode="url" mono />
          </div>
        </form>
      </section>

      <section>
        <h3 class="micro-label text-micro text-text-faint">How we send</h3>
        <HowWeSend :tracker="tracker" class="mt-4" />
      </section>
    </div>

    <template #footer>
      <p v-if="formError" class="mb-3 text-name font-bold text-danger" role="alert">{{ formError }}</p>
      <UiButton v-if="created" block @click="open = false">Done</UiButton>
      <div v-else class="flex gap-3">
        <UiButton variant="ghost" @click="open = false">Cancel</UiButton>
        <UiButton variant="primary" type="submit" form="add-postback" :loading="busy" class="flex-1">Save postback</UiButton>
      </div>
    </template>
  </UiSheet>
</template>
