<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import PromoCard from '@/components/promo/PromoCard.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiPills from '@/components/ui/UiPills.vue'
import UiSegmented from '@/components/ui/UiSegmented.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { loadPromo, type PromoFormat } from '@/lib/promo'
import { useResource } from '@/lib/resource'

const ALL = 'all'

const { data, error, run } = useResource(loadPromo)

const format = ref<PromoFormat | typeof ALL>(ALL)

const topic = ref<string>(ALL)

const formats = computed(() => [{ value: ALL, label: 'All' }, ...(data.value?.formats ?? []).map((item) => ({ value: item.id, label: item.label }))])

const topics = computed(() => [{ value: ALL, label: 'All topics' }, ...(data.value?.topics ?? []).map((item) => ({ value: item.id, label: item.title }))])

const items = computed(() =>
  (data.value?.items ?? []).filter((item) => (format.value === ALL || item.format === format.value) && (topic.value === ALL || item.topic === topic.value)),
)

onMounted(() => void run())
</script>

<template>
  <div class="space-y-6">
    <PageHeader
      title="Promo materials"
      subtitle="Ready banners for social media and messengers, in full quality. Post them with your company link."
      class="rise"
    />

    <UiError v-if="error" :error="error" :retry="run" />

    <template v-else-if="data">
      <div class="rise flex flex-col gap-3 lg:flex-row lg:items-center" style="--rise: 1">
        <UiSegmented v-model="format" :options="formats" label="Format" class="w-full lg:w-80" />
        <UiPills v-model="topic" :options="topics" label="Topic" class="min-w-0 flex-1" />
      </div>

      <div v-if="items.length" class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
        <PromoCard v-for="(item, index) in items" :key="item.id" :item="item" class="rise" :style="{ '--rise': Math.min(index, 8) + 2 }" />
      </div>

      <div v-else class="surface rise">
        <UiEmpty title="Nothing in this format yet" text="Pick another topic or format: new banners join the library as offers launch." />
      </div>
    </template>

    <div v-else class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-4">
      <UiSkeleton v-for="index in 8" :key="index" class="aspect-[4/6]" />
    </div>
  </div>
</template>
