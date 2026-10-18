<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import type { Company } from '@/api/types'
import CompanyCard from '@/components/companies/CompanyCard.vue'
import CreateCompanySheet from '@/components/companies/CreateCompanySheet.vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { useResource } from '@/lib/resource'
import { config } from '@/lib/session'
import { toast } from '@/lib/toast'

const route = useRoute()

const router = useRouter()

const { currency } = config()

const { data, error, run } = useResource((signal) => api.companies(signal))

const creating = ref(false)

onMounted(() => {
  void run()
  // `?new=1` is how other screens ask for the create sheet; the address forgets it once it is open
  if (route.query.new === '1') {
    creating.value = true
    const query = { ...route.query }
    delete query.new
    void router.replace({ query })
  }
})

function created(company: Company) {
  toast('Company created. We will review the terms.')
  void router.push(`/companies/${company.identity}`)
}
</script>

<template>
  <div class="space-y-8">
    <PageHeader title="Companies" subtitle="Each company is a traffic source with its own link, terms and statistics." class="rise">
      <template v-if="data?.length" #actions>
        <UiButton variant="primary" icon="plus" @click="creating = true">New company</UiButton>
      </template>
    </PageHeader>

    <UiError v-if="error" :error="error" :retry="run" />

    <div v-else-if="data?.length" class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      <CompanyCard
        v-for="(company, index) in data"
        :key="company.identity"
        :company="company"
        :currency="currency"
        class="rise"
        :style="{ '--rise': Math.min(index, 8) + 1 }"
      />
    </div>

    <div v-else-if="data" class="surface rise">
      <UiEmpty
        art="companies"
        title="Create your first company"
        text="A company gives you a partner link and its own statistics. You propose the terms; we review them before the company starts counting players."
      >
        <UiButton variant="primary" icon="plus" @click="creating = true">Create a company</UiButton>
      </UiEmpty>
    </div>

    <div v-else class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
      <UiSkeleton v-for="index in 3" :key="index" class="h-44" />
    </div>

    <CreateCompanySheet v-model:open="creating" @created="created" />
  </div>
</template>
