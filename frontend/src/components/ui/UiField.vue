<script setup lang="ts">
import { computed, ref, useId } from 'vue'

/**
 * A text field with its label inside: the label rests as a placeholder and rises when the field is focused
 * or filled. The value is 16 px on every screen so iOS never zooms into the form.
 */
const props = withDefaults(
  defineProps<{
    label: string
    type?: string
    error?: string | null
    hint?: string
    suffix?: string
    placeholder?: string
    autocomplete?: string
    inputmode?: 'text' | 'decimal' | 'numeric' | 'url' | 'email' | 'search'
    maxlength?: number
    mono?: boolean
    disabled?: boolean
    autofocus?: boolean
  }>(),
  { type: 'text', autocomplete: 'off' },
)

const model = defineModel<string>({ required: true })

const emit = defineEmits<{ blur: [] }>()

const id = useId()

const focused = ref(false)

const raised = computed(() => focused.value || model.value !== '')

const describedBy = computed(() => (props.error ? `${id}-error` : props.hint ? `${id}-hint` : undefined))

const input = ref<HTMLInputElement | null>(null)

defineExpose({ focus: () => input.value?.focus() })

function onBlur() {
  focused.value = false
  emit('blur')
}
</script>

<template>
  <div>
    <div
      class="relative flex h-14 items-center gap-2 rounded-md border bg-card pr-2 pl-4 transition-colors duration-default ease-default"
      :class="[error ? 'border-danger' : focused ? 'border-brand/70' : 'border-line hover:border-line-strong', disabled && 'opacity-60']"
    >
      <label
        :for="id"
        class="pointer-events-none absolute top-1/2 left-4 origin-left -translate-y-1/2 text-input font-semibold whitespace-nowrap transition-[translate,scale,color] duration-default ease-default"
        :class="[raised && 'field-raised', error ? 'text-danger' : 'text-text-faint']"
      >
        {{ label }}
      </label>
      <input
        :id="id"
        ref="input"
        v-model="model"
        :type="type"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :inputmode="inputmode"
        :maxlength="maxlength"
        :disabled="disabled"
        :autofocus="autofocus"
        :aria-invalid="error ? true : undefined"
        :aria-describedby="describedBy"
        spellcheck="false"
        autocapitalize="off"
        class="h-full min-w-0 flex-1 bg-transparent pt-4 text-input font-semibold text-text outline-none placeholder:text-transparent focus:placeholder:text-text-disabled"
        :class="mono && 'num font-medium tracking-tight'"
        @focus="focused = true"
        @blur="onBlur"
      />
      <span v-if="suffix" class="num shrink-0 pr-2 pt-4 text-name font-semibold text-text-faint">{{ suffix }}</span>
      <slot name="end" />
    </div>
    <p v-if="error" :id="`${id}-error`" class="mt-1.5 text-micro font-bold text-danger" role="alert">{{ error }}</p>
    <p v-else-if="hint" :id="`${id}-hint`" class="mt-1.5 text-micro text-text-faint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.field-raised {
  translate: 0 calc(-50% - 13px);
  scale: 0.6875;
}
</style>
