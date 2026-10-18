<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import Icon from './Icon.vue'
import type { IconName } from './icons'
import UiSpinner from './UiSpinner.vue'

/**
 * `primary` is lime and reserved for the one call to action of a screen (or of an open sheet); everything
 * else is `secondary`, `ghost` or `danger`. Labels are plain sentence case: the portal is a working tool,
 * its buttons do not shout.
 */
const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
    size?: 'md' | 'sm'
    type?: 'button' | 'submit'
    icon?: IconName
    iconEnd?: IconName
    loading?: boolean
    disabled?: boolean
    block?: boolean
    to?: RouteLocationRaw
    href?: string
    /** With `href`: the link is a file to save, not a page to open beside the cabinet */
    download?: boolean
  }>(),
  { variant: 'secondary', size: 'md', type: 'button' },
)

const VARIANTS = {
  primary: 'bg-primary font-extrabold text-on-primary hover:bg-primary-hover disabled:bg-raise disabled:text-text-disabled',
  secondary: 'border border-line-strong bg-wash font-bold text-text hover:border-text/15 hover:bg-text/7 disabled:text-text-disabled',
  ghost: 'font-bold text-text-muted hover:bg-wash hover:text-text disabled:text-text-disabled',
  danger: 'border border-danger/30 bg-danger/10 font-bold text-danger hover:bg-danger/16 disabled:opacity-50',
}

const SIZES = {
  md: 'h-control px-5 text-name',
  sm: 'h-11 px-4 text-body',
}

const isButton = computed(() => !props.to && !props.href)

const tag = computed(() => (props.to ? RouterLink : props.href ? 'a' : 'button'))

const inactive = computed(() => props.disabled || props.loading)

const iconSize = computed(() => (props.size === 'md' ? 18 : 16))
</script>

<template>
  <component
    :is="tag"
    :to="to"
    :href="href"
    :type="isButton ? type : undefined"
    :disabled="isButton ? inactive : undefined"
    :aria-disabled="inactive || undefined"
    :aria-busy="loading || undefined"
    :target="href && !download ? '_blank' : undefined"
    :rel="href ? 'noopener noreferrer' : undefined"
    :download="href && download ? '' : undefined"
    class="pressable relative inline-flex shrink-0 items-center justify-center gap-2 rounded-md whitespace-nowrap select-none disabled:cursor-not-allowed"
    :class="[VARIANTS[variant], SIZES[size], block && 'w-full', inactive && !isButton && 'pointer-events-none opacity-60']"
  >
    <UiSpinner v-if="loading" :size="iconSize" />
    <Icon v-else-if="icon" :name="icon" :size="iconSize" />
    <span><slot /></span>
    <Icon v-if="iconEnd" :name="iconEnd" :size="iconSize" />
  </component>
</template>
