<script setup lang="ts">
import { ref, watch } from 'vue'
import { api } from '@/api'
import UiEmpty from '@/components/ui/UiEmpty.vue'
import UiError from '@/components/ui/UiError.vue'
import UiPager from '@/components/ui/UiPager.vue'
import UiSkeleton from '@/components/ui/UiSkeleton.vue'
import { countryName, formatCount, formatDate, formatMoney } from '@/lib/format'
import { useResource } from '@/lib/resource'

/** The players a company brought, newest sign-up first, each with what they have done since. */
const props = defineProps<{ identity: string; currency: string }>()

/** A page short enough that the company's page does not turn into one long table. */
const SIZE = 20

const page = ref(0)

const { data, error, loading, run } = useResource((signal) => api.players(props.identity, { page: page.value, size: SIZE }, signal))

watch(page, run, { immediate: true })
</script>

<template>
  <section class="surface">
    <header class="flex items-end justify-between gap-3 px-4 pt-4 lg:px-6 lg:pt-6">
      <div>
        <h2 class="text-section font-extrabold text-text">Players</h2>
        <p class="mt-1 text-body text-text-faint">
          <template v-if="data">{{ formatCount(data.total) }} {{ data.total === 1 ? 'player' : 'players' }}, all time · amounts in {{ currency }}</template>
          <template v-else>All time</template>
        </p>
      </div>
    </header>

    <div class="p-4 pt-3 lg:p-6 lg:pt-3">
      <UiError v-if="error" :error="error" :retry="run" />

      <template v-else-if="data && data.items.length">
        <!-- On a phone the rows run edge to edge of the card, so the wrapper takes the card's padding back -->
        <div class="-mx-4 transition-opacity duration-default lg:mx-0" :class="loading && 'opacity-55'">
          <!-- A narrow desktop folds the sign-up date under the player and drops the deposit date; the scroller is the last resort -->
          <div class="scroll-thin hidden overflow-x-auto lg:block">
            <table class="w-full min-w-[600px] text-body">
              <thead>
                <tr class="text-left text-text-faint">
                  <th class="micro-label whitespace-nowrap py-2.5 pr-3 text-micro font-bold">Player</th>
                  <th class="micro-label whitespace-nowrap px-3 py-2.5 text-micro font-bold">Click id</th>
                  <th class="micro-label whitespace-nowrap px-3 py-2.5 text-micro font-bold">Country</th>
                  <th class="micro-label whitespace-nowrap hidden px-3 py-2.5 text-micro font-bold xl:table-cell">Registered</th>
                  <th class="micro-label whitespace-nowrap px-3 py-2.5 text-micro font-bold">First deposit</th>
                  <th class="micro-label whitespace-nowrap px-3 py-2.5 text-right text-micro font-bold">Deposits</th>
                  <th class="micro-label whitespace-nowrap px-3 py-2.5 text-right text-micro font-bold">Turnover</th>
                  <th class="micro-label whitespace-nowrap py-2.5 pl-3 text-right text-micro font-bold">Earned</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="player in data.items" :key="player.playerId" class="border-t border-line-soft">
                  <td class="py-3 pr-3 whitespace-nowrap">
                    <span class="num text-text">#{{ player.playerId }}</span>
                    <span class="block text-micro text-text-faint xl:hidden">{{ formatDate(player.registeredAt) }}</span>
                  </td>
                  <td class="max-w-28 px-3 py-3 xl:max-w-36">
                    <span v-if="player.clickId" class="block truncate num text-text-muted" :title="player.clickId">{{ player.clickId }}</span>
                    <span v-else class="text-text-disabled">None</span>
                  </td>
                  <td class="px-3 py-3 text-text-muted">{{ countryName(player.country) }}</td>
                  <td class="hidden px-3 py-3 whitespace-nowrap text-text-muted xl:table-cell">{{ formatDate(player.registeredAt) }}</td>
                  <td class="px-3 py-3 whitespace-nowrap">
                    <template v-if="player.firstDepositAt">
                      <span class="num text-text">{{ formatMoney(player.firstDepositAmount) }}</span>
                      <span class="ml-2 hidden text-text-faint xl:inline">{{ formatDate(player.firstDepositAt) }}</span>
                    </template>
                    <span v-else class="text-text-disabled">Not yet</span>
                  </td>
                  <td class="px-3 py-3 text-right whitespace-nowrap">
                    <template v-if="player.depositCount">
                      <span class="num text-text-muted">{{ formatMoney(player.depositAmount) }}</span>
                      <span class="ml-1.5 num text-micro text-text-faint">×{{ formatCount(player.depositCount) }}</span>
                    </template>
                    <span v-else class="text-text-disabled">None</span>
                  </td>
                  <td class="px-3 py-3 text-right num whitespace-nowrap text-text-muted">{{ formatMoney(player.turnover) }}</td>
                  <td class="py-3 pl-3 text-right num font-semibold whitespace-nowrap text-text">{{ formatMoney(player.earned) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <ul class="divide-y divide-line-soft border-t border-line-soft lg:hidden">
            <li v-for="player in data.items" :key="player.playerId" class="px-4 py-3.5">
              <div class="flex items-baseline justify-between gap-3">
                <p class="num text-name font-semibold text-text">#{{ player.playerId }}</p>
                <p class="num text-name font-semibold text-text">{{ formatMoney(player.earned) }}</p>
              </div>
              <p class="mt-1 text-body text-text-faint">
                {{ countryName(player.country) }} · registered {{ formatDate(player.registeredAt) }}
              </p>
              <dl class="mt-2.5 grid grid-cols-3 gap-2 text-micro">
                <div class="min-w-0">
                  <dt class="text-text-faint">First deposit</dt>
                  <dd class="truncate num" :class="player.firstDepositAt ? 'text-text-muted' : 'text-text-disabled'">
                    {{ player.firstDepositAt ? formatMoney(player.firstDepositAmount) : 'Not yet' }}
                  </dd>
                </div>
                <div class="min-w-0">
                  <dt class="text-text-faint">Deposits<template v-if="player.depositCount"> ×{{ formatCount(player.depositCount) }}</template></dt>
                  <dd class="truncate num" :class="player.depositCount ? 'text-text-muted' : 'text-text-disabled'">
                    {{ player.depositCount ? formatMoney(player.depositAmount) : 'None' }}
                  </dd>
                </div>
                <div class="min-w-0">
                  <dt class="text-text-faint">Turnover</dt>
                  <dd class="truncate num text-text-muted">{{ formatMoney(player.turnover) }}</dd>
                </div>
              </dl>
              <p v-if="player.clickId" class="mt-2 truncate num text-micro text-text-faint">click_id {{ player.clickId }}</p>
            </li>
          </ul>
        </div>
        <UiPager v-model:page="page" :size="SIZE" :total="data.total" :busy="loading" class="mt-4" />
      </template>

      <UiEmpty
        v-else-if="data"
        icon="users"
        title="No players yet"
        text="Players show up here as soon as someone signs up through this company's link while it is active."
        compact
      />

      <div v-else class="space-y-2">
        <UiSkeleton v-for="index in 4" :key="index" class="h-12" />
      </div>
    </div>
  </section>
</template>
