<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '@/api'
import { asApiError } from '@/api/client'
import AuthLayout from '@/components/layout/AuthLayout.vue'
import PasswordToggle from '@/components/layout/PasswordToggle.vue'
import Icon from '@/components/ui/Icon.vue'
import UiButton from '@/components/ui/UiButton.vue'
import UiField from '@/components/ui/UiField.vue'
import { errorMessage } from '@/lib/errors'
import { config, session, signedIn } from '@/lib/session'
import { afterSignIn } from '@/router'

const route = useRoute()

const router = useRouter()

const { supportUrl } = config()

const form = reactive({ login: '', password: '' })

const errors = reactive<Record<'login' | 'password', string | null>>({ login: null, password: null })

const formError = ref<string | null>(null)

const busy = ref(false)

const shown = ref(false)

watch(
  () => form.login,
  () => (errors.login = null),
)

watch(
  () => form.password,
  () => (errors.password = null),
)

async function submit() {
  formError.value = null
  const login = form.login.trim().toLowerCase()
  errors.login = login ? null : 'Enter your login.'
  errors.password = form.password ? null : 'Enter your password.'
  if (errors.login || errors.password) return
  busy.value = true
  try {
    signedIn(await api.login({ login, password: form.password }))
    await router.replace(afterSignIn(route.query.next))
  } catch (cause) {
    formError.value = errorMessage(asApiError(cause))
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <h1 class="headline text-title text-text">Sign in</h1>
    <p class="mt-3 text-name text-text-muted">Your companies, numbers and payouts, in one place.</p>

    <p
      v-if="session.notice"
      class="mt-6 flex gap-2.5 rounded-md border p-3.5 text-body"
      :class="session.notice === 'blocked' ? 'border-danger/25 bg-danger/8 text-text' : 'border-brand/30 bg-brand/10 text-text'"
      role="status"
    >
      <Icon :name="session.notice === 'blocked' ? 'ban' : 'clock'" :size="18" :class="session.notice === 'blocked' ? 'text-danger' : 'text-brand-soft'" />
      <span v-if="session.notice === 'blocked'">
        This account is blocked.
        <template v-if="supportUrl">
          <a :href="supportUrl" target="_blank" rel="noopener noreferrer" class="font-bold text-brand-soft hover:text-text">Write to support</a> if you think it is a mistake.
        </template>
      </span>
      <span v-else>Your session has ended. Sign in again to carry on.</span>
    </p>

    <form class="mt-8 space-y-4" novalidate @submit.prevent="submit">
      <UiField v-model="form.login" label="Login" autocomplete="username" :maxlength="32" :error="errors.login" />
      <UiField
        v-model="form.password"
        label="Password"
        :type="shown ? 'text' : 'password'"
        autocomplete="current-password"
        :maxlength="72"
        :error="errors.password"
      >
        <template #end>
          <PasswordToggle v-model="shown" />
        </template>
      </UiField>

      <p v-if="formError" class="flex items-center gap-2 text-name font-bold text-danger" role="alert">
        <Icon name="warning" :size="18" />
        {{ formError }}
      </p>

      <UiButton variant="primary" type="submit" block :loading="busy" class="mt-2">Sign in</UiButton>
    </form>

    <p class="mt-7 text-name text-text-muted">
      New here?
      <RouterLink :to="{ name: 'register', query: route.query }" class="font-bold text-brand-soft hover:text-text">Create an account</RouterLink>
    </p>
    <p v-if="supportUrl" class="mt-3 text-body text-text-faint">
      Lost your password? There is no reset by email:
      <a :href="supportUrl" target="_blank" rel="noopener noreferrer" class="font-bold text-brand-soft hover:text-text">write to support on Telegram</a>.
    </p>
  </AuthLayout>
</template>
