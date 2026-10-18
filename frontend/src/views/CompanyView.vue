<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { api } from '@/api'
import { BRAND } from '@/brand'
import LinkBuilder from '@/components/companies/LinkBuilder.vue'
import PlayersTable from '@/components/companies/PlayersTable.vue'
import ReviewBanner from '@/components/companies/ReviewBanner.vue'
import TermsCard from '@/components/companies/TermsCard.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import PostbacksSection from '@/components/postbacks/PostbacksSection.vue'
import StatsPanel from '@/components/stats/StatsPanel.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { useResource } from '@/lib/resource'
import { config } from '@/lib/session'

const props = defineProps<{ identity: string }>()

const { currency } = config()

const { data: company, error, run } = useResource((signal) => api.company(props.identity, signal))

onMounted(run)

watch(company, (value) => {
  if (value) document.title = `${value.identity} · ${BRAND.name}`
})
</script>

<template>
  <div v-if="error?.code === 'NOT_FOUND'" class="surface">
    <UiEmpty icon="search" title="No such company" :text="`There is no company “${identity}” among yours.`">
      <UiButton to="/companies" icon="arrowLeft">All companies</UiButton>
    </UiEmpty>
  </div>

  <UiError v-else-if="error" :error="error" :retry="run" />

  <div v-else-if="company" class="space-y-10 lg:space-y-12">
    <div class="space-y-6">
      <PageHeader :title="company.identity" :back="{ to: '/companies', label: 'Companies' }" class="rise" />
      <ReviewBanner v-if="company.status === 'IN_REVIEW'" class="rise" style="--rise: 1" />
    </div>

    <div class="rise grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]" style="--rise: 2">
      <TermsCard :company="company" />
      <LinkBuilder :company="company" />
    </div>

    <section class="rise space-y-4" style="--rise: 3" aria-labelledby="analytics-title">
      <h2 id="analytics-title" class="text-section font-extrabold text-text">Analytics</h2>
      <StatsPanel :company="company.identity" />
    </section>

    <!-- Postbacks before the players: set up once, but easy to miss under a long table -->
    <PostbacksSection :company="company" />

    <PlayersTable :identity="company.identity" :currency="currency" />
  </div>

  <div v-else class="space-y-6">
    <UiSkeleton class="h-10 w-56" />
    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
      <UiSkeleton class="h-80" />
      <UiSkeleton class="h-80" />
    </div>
  </div>
</template>
