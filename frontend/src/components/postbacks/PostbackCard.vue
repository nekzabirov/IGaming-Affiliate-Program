<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@/api'
import { asApiError } from '@/api/client'
import type { Postback } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiChip from '@/components/ui/UiChip.vue'
import UiField from '@/components/ui/UiField.vue'
import UiSheet from '@/components/ui/UiSheet.vue'
import UiSwitch from '@/components/ui/UiSwitch.vue'
import UrlText from '@/components/ui/UrlText.vue'
import { errorField, errorMessage } from '@/lib/errors'
import { toast } from '@/lib/toast'
import { trackerFor } from '@/postbacks/trackers'
import DeliveryStatus from './DeliveryStatus.vue'
import HowWeSend from './HowWeSend.vue'

const props = defineProps<{ postback: Postback; identity: string }>()

const emit = defineEmits<{ changed: [Postback]; deleted: [number] }>()

const tracker = computed(() => trackerFor(props.postback.type))

const expanded = ref(false)

const toggling = ref(false)

async function toggle(active: boolean) {
  toggling.value = true
  try {
    emit('changed', await api.updatePostback(props.identity, props.postback.id, { active }))
    toast(active ? 'Postback resumed' : 'Postback paused')
  } catch (cause) {
    toast(errorMessage(asApiError(cause)), 'error')
  } finally {
    toggling.value = false
  }
}

const editing = ref(false)

const draft = ref('')

const draftError = ref<string | null>(null)

const saving = ref(false)

watch(draft, () => (draftError.value = null))

function edit() {
  draft.value = props.postback.url
  editing.value = true
}

async function save() {
  const url = draft.value.trim()
  if (!url) {
    draftError.value = 'Paste the postback URL.'
    return
  }
  saving.value = true
  try {
    emit('changed', await api.updatePostback(props.identity, props.postback.id, { url }))
    editing.value = false
    toast('Postback URL saved')
  } catch (cause) {
    const error = asApiError(cause)
    if (errorField(error) === 'url') draftError.value = errorMessage(error)
    else toast(errorMessage(error), 'error')
  } finally {
    saving.value = false
  }
}

const confirming = ref(false)

const removing = ref(false)

async function remove() {
  removing.value = true
  try {
    await api.deletePostback(props.identity, props.postback.id)
    confirming.value = false
    emit('deleted', props.postback.id)
    toast('Postback deleted')
  } catch (cause) {
    toast(errorMessage(asApiError(cause)), 'error')
  } finally {
    removing.value = false
  }
}
</script>

<template>
  <article class="surface">
    <div class="flex items-start gap-3 p-4 lg:gap-4 lg:p-5">
      <span class="grid size-10 shrink-0 place-items-center rounded-md border border-line-strong bg-wash text-section font-extrabold text-text">
        {{ tracker.name.charAt(0) }}
      </span>
      <div class="min-w-0 flex-1">
        <p class="flex flex-wrap items-center gap-2">
          <span class="text-name font-extrabold text-text">{{ tracker.name }}</span>
          <UiChip v-if="!postback.active">Paused</UiChip>
        </p>
        <p class="num mt-1 text-body text-text-muted"><UrlText :url="postback.url" /></p>
        <DeliveryStatus :postback="postback" class="mt-3" />
      </div>
      <UiSwitch :model-value="postback.active" :disabled="toggling" label="Send postbacks to this tracker" class="-mt-1.5 -mr-2" @update:model-value="toggle" />
    </div>

    <div class="flex flex-wrap items-center gap-1 border-t border-line-soft px-2 py-1.5 lg:px-3">
      <button type="button" class="action text-text-muted hover:text-text" :aria-expanded="expanded" @click="expanded = !expanded">
        How we send
        <Icon name="chevronDown" :size="16" class="transition-transform duration-default" :class="expanded && 'rotate-180'" />
      </button>
      <button type="button" class="action text-text-muted hover:text-text" @click="edit">
        <Icon name="edit" :size="16" />
        Edit URL
      </button>
      <button type="button" class="action ml-auto text-text-muted hover:text-danger" @click="confirming = true">
        <Icon name="trash" :size="16" />
        Delete
      </button>
    </div>

    <Transition enter-from-class="opacity-0 -translate-y-1" enter-active-class="transition duration-enter ease-default motion-reduce:transition-none">
      <div v-if="expanded" class="border-t border-line-soft p-4 lg:p-5">
        <HowWeSend :tracker="tracker" :examples="postback.examples" />
      </div>
    </Transition>

    <UiSheet v-model:open="editing" title="Edit postback URL" :description="`The ${tracker.name} postback URL for ${identity}.`">
      <form id="edit-postback" novalidate @submit.prevent="save">
        <div data-autofocus>
          <UiField v-model="draft" :label="tracker.url.label" :hint="tracker.url.hint" :error="draftError" inputmode="url" mono />
        </div>
      </form>
      <template #footer>
        <div class="flex gap-3">
          <UiButton variant="ghost" @click="editing = false">Cancel</UiButton>
          <UiButton variant="primary" type="submit" form="edit-postback" :loading="saving" class="flex-1">Save URL</UiButton>
        </div>
      </template>
    </UiSheet>

    <UiSheet v-model:open="confirming" title="Delete this postback?">
      <p class="text-name text-text-muted">{{ tracker.name }} stops hearing about this company's players. Events that happen meanwhile are not sent later.</p>
      <template #footer>
        <div class="flex gap-3">
          <UiButton variant="ghost" @click="confirming = false">Keep it</UiButton>
          <UiButton variant="danger" icon="trash" :loading="removing" class="flex-1" @click="remove">Delete postback</UiButton>
        </div>
      </template>
    </UiSheet>
  </article>
</template>

<style scoped>
@reference '../../styles/main.css';

.action {
  @apply inline-flex h-11 items-center gap-1.5 rounded-md px-3 text-body font-bold transition-colors duration-default hover:bg-wash;
}
</style>
