<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '@/api'
import type { Company, Postback } from '@/api/types'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { useResource } from '@/lib/resource'
import AddPostbackSheet from './AddPostbackSheet.vue'
import PostbackCard from './PostbackCard.vue'

const props = defineProps<{ company: Company }>()

/** The API's cap per company. */
const LIMIT = 10

const { data, error, run } = useResource((signal) => api.postbacks(props.company.identity, signal))

const adding = ref(false)

onMounted(run)

function created(postback: Postback) {
  data.value = [...(data.value ?? []), postback]
}

function changed(postback: Postback) {
  data.value = (data.value ?? []).map((item) => (item.id === postback.id ? postback : item))
}

function deleted(id: number) {
  data.value = (data.value ?? []).filter((item) => item.id !== id)
}
</script>

<template>
  <section class="space-y-4" aria-labelledby="postbacks-title">
    <header class="flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 id="postbacks-title" class="text-section font-extrabold text-text">Postbacks</h2>
        <p class="mt-1 text-body text-text-faint">
          Server-to-server reports to your tracker<template v-if="data?.length"> · {{ data.length }} of {{ LIMIT }}</template>
        </p>
      </div>
      <UiButton v-if="data?.length" icon="plus" size="sm" :disabled="data.length >= LIMIT" @click="adding = true">Add postback</UiButton>
    </header>

    <UiError v-if="error" :error="error" :retry="run" />

    <div v-else-if="data?.length" class="space-y-3">
      <PostbackCard v-for="postback in data" :key="postback.id" :postback="postback" :identity="company.identity" @changed="changed" @deleted="deleted" />
    </div>

    <div v-else-if="data" class="surface">
      <UiEmpty
        art="postbacks"
        title="Connect your tracker"
        text="Add your tracker's postback URL and we will report every registration, deposit, spin and bet of this company's players to it."
        compact
      >
        <UiButton icon="plus" @click="adding = true">Add postback</UiButton>
      </UiEmpty>
    </div>

    <UiSkeleton v-else class="h-32" />

    <AddPostbackSheet v-model:open="adding" :company="company" @created="created" />
  </section>
</template>
