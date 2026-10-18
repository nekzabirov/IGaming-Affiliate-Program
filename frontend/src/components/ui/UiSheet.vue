<script lang="ts">
/** Open sheets, innermost last: only the top one answers Escape and keeps the focus inside. */
const stack: symbol[] = []

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
</script>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import Icon from './Icon.vue'

/**
 * A bottom sheet on a phone, a centred dialog from 640 px up. It slides in on the brand's sheet curve and
 * leaves faster than it came; with reduced motion it only fades.
 */
withDefaults(defineProps<{ title: string; description?: string; wide?: boolean }>(), {})

const open = defineModel<boolean>('open', { required: true })

const emit = defineEmits<{ closed: [] }>()

const titleId = useId()

const panel = ref<HTMLElement | null>(null)

const token = Symbol('sheet')

let returnFocus: HTMLElement | null = null

function trapTab(event: KeyboardEvent, root: HTMLElement) {
  const items = [...root.querySelectorAll<HTMLElement>(FOCUSABLE)].filter((item) => item.offsetParent !== null)
  const first = items[0]
  const last = items[items.length - 1]
  if (!first || !last) {
    event.preventDefault()
    root.focus()
    return
  }
  if (event.shiftKey && (document.activeElement === first || document.activeElement === root)) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

function onKey(event: KeyboardEvent) {
  if (stack[stack.length - 1] !== token) return
  if (event.key === 'Escape') {
    event.preventDefault()
    open.value = false
  } else if (event.key === 'Tab' && panel.value) {
    trapTab(event, panel.value)
  }
}

function hold() {
  returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
  stack.push(token)
  document.documentElement.style.overflow = 'hidden'
  document.addEventListener('keydown', onKey)
}

function release() {
  const index = stack.indexOf(token)
  if (index < 0) return
  stack.splice(index, 1)
  if (!stack.length) document.documentElement.style.overflow = ''
  document.removeEventListener('keydown', onKey)
  returnFocus?.focus({ preventScroll: true })
  returnFocus = null
}

watch(
  open,
  async (value) => {
    if (!value) return release()
    hold()
    await nextTick()
    // A phone keeps its keyboard down until the person taps a field; a desktop gets the marked field ready
    const marked = window.matchMedia('(pointer: fine)').matches ? panel.value?.querySelector<HTMLElement>('[data-autofocus]') : null
    const field = marked?.matches('input, select, textarea, button') ? marked : marked?.querySelector<HTMLElement>('input, select, textarea, button')
    ;(field ?? panel.value)?.focus({ preventScroll: true })
  },
  { immediate: true },
)

onBeforeUnmount(release)
</script>

<template>
  <Teleport to="body">
    <Transition
      :duration="{ enter: 320, leave: 200 }"
      enter-from-class="sheet-hidden"
      leave-to-class="sheet-hidden"
      enter-active-class="sheet-entering"
      leave-active-class="sheet-leaving"
      @after-leave="emit('closed')"
    >
      <div v-if="open" class="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-6">
        <div class="sheet-backdrop absolute inset-0 bg-page/75 backdrop-blur-[2px]" aria-hidden="true" @click="open = false" />
        <section
          ref="panel"
          role="dialog"
          aria-modal="true"
          :aria-labelledby="titleId"
          tabindex="-1"
          class="sheet-panel relative flex max-h-[92dvh] w-full flex-col overflow-hidden rounded-t-xl border border-line-strong bg-panel shadow-pop outline-none sm:rounded-xl"
          :class="wide ? 'sm:max-w-2xl' : 'sm:max-w-lg'"
        >
          <div class="mx-auto mt-2 h-1 w-10 shrink-0 rounded-full bg-line-strong sm:hidden" aria-hidden="true" />
          <header class="flex shrink-0 items-start gap-3 px-5 pt-4 pb-4 sm:pt-5">
            <div class="min-w-0 flex-1 pt-1.5">
              <h2 :id="titleId" class="text-section font-extrabold text-text">{{ title }}</h2>
              <p v-if="description" class="mt-1 text-body text-text-muted">{{ description }}</p>
            </div>
            <button
              type="button"
              class="-mt-0.5 -mr-2 grid size-11 shrink-0 place-items-center rounded-md text-text-faint transition-colors duration-default hover:bg-wash hover:text-text"
              aria-label="Close"
              @click="open = false"
            >
              <Icon name="close" />
            </button>
          </header>
          <div class="scroll-thin min-h-0 flex-1 overflow-y-auto overscroll-contain px-5" :class="$slots.footer ? 'pb-5' : 'pb-[max(20px,env(safe-area-inset-bottom))]'">
            <slot />
          </div>
          <footer v-if="$slots.footer" class="shrink-0 border-t border-line-soft px-5 pt-4 pb-[max(16px,env(safe-area-inset-bottom))]">
            <slot name="footer" />
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.sheet-entering .sheet-backdrop,
.sheet-entering .sheet-panel {
  transition:
    opacity 320ms var(--ease-sheet),
    transform 320ms var(--ease-sheet);
}
.sheet-leaving .sheet-backdrop,
.sheet-leaving .sheet-panel {
  transition:
    opacity 200ms var(--ease-default),
    transform 200ms var(--ease-default);
}
.sheet-hidden .sheet-backdrop {
  opacity: 0;
}
.sheet-hidden .sheet-panel {
  transform: translateY(100%);
}
@media (width >= 40rem) {
  .sheet-hidden .sheet-panel {
    opacity: 0;
    transform: scale(0.96);
  }
}
@media (prefers-reduced-motion: reduce) {
  .sheet-hidden .sheet-panel {
    opacity: 0;
    transform: none;
  }
}
</style>
