<script setup lang="ts">
import { computed } from 'vue'
import type { StatsCompany } from '@/api/types'
import Icon from '@/components/ui/Icon.vue'
import UiChip from '@/components/ui/UiChip.vue'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import { STATUS_META, TYPE_META } from '@/lib/companies'
import { compareDecimal } from '@/lib/decimal'
import { formatCount, formatMoney } from '@/lib/format'

/** Each company's share of the period, best earner first: a table from 1024 px, a stack of rows below. */
const props = defineProps<{ companies: StatsCompany[]; currency: string }>()

const sorted = computed(() =>
  [...props.companies].sort((a, b) => compareDecimal(b.earned, a.earned) || b.registrations - a.registrations || a.identity.localeCompare(b.identity)),
)
</script>

<template>
  <section class="surface">
    <header class="px-4 pt-4 lg:px-5 lg:pt-5">
      <h2 class="text-section font-extrabold text-text">Companies</h2>
      <p class="mt-1 text-body text-text-faint">The period by company, in {{ currency }}</p>
    </header>

    <UiEmpty v-if="!sorted.length" icon="layers" title="No company activity" text="Numbers show up here once your companies bring players." compact />

    <template v-else>
      <!-- A narrow desktop scrolls the table inside its card rather than letting it spill over the next one -->
      <div class="scroll-thin mt-3 hidden overflow-x-auto lg:block">
        <table class="w-full min-w-[620px] text-body">
          <thead>
            <tr class="text-left text-text-faint">
              <th class="micro-label py-2.5 pr-3 pl-5 text-micro font-bold">Company</th>
              <th class="micro-label px-3 py-2.5 text-right text-micro font-bold">Reg.</th>
              <th class="micro-label px-3 py-2.5 text-right text-micro font-bold">FTD</th>
              <th class="micro-label px-3 py-2.5 text-right text-micro font-bold">Deposits</th>
              <th class="micro-label px-3 py-2.5 text-right text-micro font-bold">Turnover</th>
              <th class="micro-label py-2.5 pr-5 pl-3 text-right text-micro font-bold">Earned</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="company in sorted" :key="company.identity" class="group border-t border-line-soft transition-colors duration-default hover:bg-wash">
              <td class="py-3 pr-3 pl-5">
                <RouterLink :to="`/companies/${company.identity}`" class="flex min-w-0 flex-col">
                  <span class="flex min-w-0 items-center gap-2">
                    <span class="truncate font-bold text-text group-hover:text-brand-soft">{{ company.identity }}</span>
                    <UiChip v-if="company.status !== 'ACTIVE'" :tone="STATUS_META[company.status].tone">{{ STATUS_META[company.status].label }}</UiChip>
                  </span>
                  <span class="text-micro text-text-faint">{{ TYPE_META[company.type].label }}</span>
                </RouterLink>
              </td>
              <td class="num px-3 py-3 text-right text-text-muted">{{ formatCount(company.registrations) }}</td>
              <td class="num px-3 py-3 text-right text-text-muted">{{ formatCount(company.firstDeposits) }}</td>
              <td class="num px-3 py-3 text-right text-text-muted">{{ formatMoney(company.depositAmount) }}</td>
              <td class="num px-3 py-3 text-right text-text-muted">{{ formatMoney(company.turnover) }}</td>
              <td class="num py-3 pr-5 pl-3 text-right font-semibold text-text">{{ formatMoney(company.earned) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <ul class="mt-3 divide-y divide-line-soft border-t border-line-soft lg:hidden">
        <li v-for="company in sorted" :key="company.identity">
          <RouterLink :to="`/companies/${company.identity}`" class="block px-4 py-3.5 transition-colors duration-default active:bg-wash">
            <div class="flex items-center gap-2.5">
              <span class="flex min-w-0 flex-1 items-center gap-2">
                <span class="truncate text-name font-bold text-text">{{ company.identity }}</span>
                <UiChip v-if="company.status !== 'ACTIVE'" :tone="STATUS_META[company.status].tone">{{ STATUS_META[company.status].label }}</UiChip>
              </span>
              <span class="num text-name font-semibold text-text">{{ formatMoney(company.earned) }}</span>
              <Icon name="chevronRight" :size="16" class="text-text-faint" />
            </div>
            <p class="mt-0.5 text-micro text-text-faint">{{ TYPE_META[company.type].label }}</p>
            <dl class="mt-2 grid grid-cols-4 gap-2 text-micro">
              <div>
                <dt class="text-text-faint">Reg.</dt>
                <dd class="num text-text-muted">{{ formatCount(company.registrations) }}</dd>
              </div>
              <div>
                <dt class="text-text-faint">FTD</dt>
                <dd class="num text-text-muted">{{ formatCount(company.firstDeposits) }}</dd>
              </div>
              <div class="min-w-0">
                <dt class="text-text-faint">Deposits</dt>
                <dd class="num truncate text-text-muted">{{ formatMoney(company.depositAmount) }}</dd>
              </div>
              <div class="min-w-0">
                <dt class="text-text-faint">Turnover</dt>
                <dd class="num truncate text-text-muted">{{ formatMoney(company.turnover) }}</dd>
              </div>
            </dl>
          </RouterLink>
        </li>
      </ul>
    </template>
  </section>
</template>
