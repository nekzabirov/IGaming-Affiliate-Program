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
import { errorField, errorMessage } from '@/lib/errors'
import { LOGIN_RULE, loginError, passwordError } from '@/lib/rules'
import { signedIn } from '@/lib/session'
import { afterSignIn } from '@/router'

const route = useRoute()

const router = useRouter()

const form = reactive({ login: '', password: '' })

const errors = reactive<Record<'login' | 'password', string | null>>({ login: null, password: null })

const formError = ref<string | null>(null)

const busy = ref(false)

const shown = ref(false)

// Logins are stored lowercase, so the field shows the login as it will be
watch(
  () => form.login,
  (value) => {
    const lower = value.toLowerCase()
    if (lower !== value) form.login = lower
    errors.login = null
  },
)

watch(
  () => form.password,
  () => (errors.password = null),
)

async function submit() {
  formError.value = null
  const login = form.login.trim()
  errors.login = loginError(login)
  errors.password = passwordError(form.password)
  if (errors.login || errors.password) return
  busy.value = true
  try {
    signedIn(await api.register({ login, password: form.password }))
    await router.replace(afterSignIn(route.query.next))
  } catch (cause) {
    const error = asApiError(cause)
    const field = errorField(error)
    if (field === 'login' || field === 'password') errors[field] = errorMessage(error)
    else formError.value = errorMessage(error)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthLayout>
    <h1 class="headline text-title text-text">Create account</h1>
    <p class="mt-3 text-name text-text-muted">A login and a password are all it takes. No email, no phone number.</p>

    <form class="mt-8 space-y-4" novalidate @submit.prevent="submit">
      <UiField
        v-model="form.login"
        label="Login"
        autocomplete="username"
        :maxlength="32"
        :error="errors.login"
        :hint="LOGIN_RULE"
        @blur="errors.login = form.login ? loginError(form.login.trim()) : null"
      />
      <UiField
        v-model="form.password"
        label="Password"
        :type="shown ? 'text' : 'password'"
        autocomplete="new-password"
        :maxlength="72"
        :error="errors.password"
        hint="8–72 characters; letters outside a–z count double. There is no reset by email, so keep it somewhere safe."
        @blur="errors.password = form.password ? passwordError(form.password) : null"
      >
        <template #end>
          <PasswordToggle v-model="shown" />
        </template>
      </UiField>

      <p v-if="formError" class="flex items-center gap-2 text-name font-bold text-danger" role="alert">
        <Icon name="warning" :size="18" />
        {{ formError }}
      </p>

      <UiButton variant="primary" type="submit" block :loading="busy" class="mt-2">Create account</UiButton>
    </form>

    <p class="mt-7 text-name text-text-muted">
      Already a partner?
      <RouterLink :to="{ name: 'login', query: route.query }" class="font-bold text-brand-soft hover:text-text">Sign in</RouterLink>
    </p>
  </AuthLayout>
</template>
