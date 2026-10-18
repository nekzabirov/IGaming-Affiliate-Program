import type { CompanyStatus, CompanyType } from '@/api/types'
import type { IconName } from '@/components/ui/icons'
import { isPositive } from './decimal'
import { formatMoney, formatPercent } from './format'

export const COMPANY_TYPES: CompanyType[] = ['CPA', 'REVSHARE_DEPOSIT', 'REVSHARE_TURNOVER']

export interface TypeMeta {
  label: string
  percent: boolean
}

/** The models are told apart by name everywhere: colour in the portal belongs to the chart's measures. */
export const TYPE_META: Record<CompanyType, TypeMeta> = {
  CPA: { label: 'CPA', percent: false },
  REVSHARE_DEPOSIT: { label: 'RevShare · deposits', percent: true },
  REVSHARE_TURNOVER: { label: 'RevShare · turnover', percent: true },
}

/** What a model pays for, in the words of the accrual rules (real money only, first deposit only for CPA). */
export function typeExplainer(type: CompanyType, cpaMinDeposit: string, currency: string): string {
  switch (type) {
    case 'CPA':
      return `A fixed amount for each player whose first deposit is ${formatMoney(cpaMinDeposit, currency)} or more.`
    case 'REVSHARE_DEPOSIT':
      return 'A percent of every deposit your players make.'
    case 'REVSHARE_TURNOVER':
      return "A percent of your players' real-money stakes. Bonus money is not counted, and cancelled bets are taken back."
  }
}

/** The terms in two parts: the figure ("25.00 USDT", "30%") and what it is paid on. */
export function termsParts(type: CompanyType, amount: string, currency: string): { value: string; basis: string } {
  switch (type) {
    case 'CPA':
      return { value: formatMoney(amount, currency), basis: 'per qualified player' }
    case 'REVSHARE_DEPOSIT':
      return { value: formatPercent(amount), basis: 'of deposits' }
    case 'REVSHARE_TURNOVER':
      return { value: formatPercent(amount), basis: 'of turnover' }
  }
}

/** "25.00 USDT per qualified player", "30% of deposits". */
export function termsText(type: CompanyType, amount: string, currency: string): string {
  const { value, basis } = termsParts(type, amount, currency)
  return `${value} ${basis}`
}

/** Terms of 0, e.g. for a partner paid up front: the company is tracked like any other and earns nothing. */
export const isZeroTerms = (amount: string) => !isPositive(amount)

/** Said wherever terms are typed, so 0 is chosen knowing what it keeps and what it does not. */
export const ZERO_TERMS_HINT = 'At 0 the company still counts players and sends postbacks, but earns no commission.'

export type Tone = 'good' | 'wait' | 'bad' | 'neutral'

export interface StatusMeta {
  label: string
  /** good = lime (live), wait = violet (with us), neutral = off. */
  tone: Tone
  icon: IconName
  explainer: string
}

export const STATUS_META: Record<CompanyStatus, StatusMeta> = {
  IN_REVIEW: {
    label: 'In review',
    tone: 'wait',
    icon: 'clock',
    explainer: 'We are reviewing the terms you proposed and may adjust them. Players are counted once the company is active.',
  },
  ACTIVE: {
    label: 'Active',
    tone: 'good',
    icon: 'check',
    explainer: 'Players who sign up through your link are counted and earn you commission.',
  },
  DISABLED: {
    label: 'Disabled',
    tone: 'neutral',
    icon: 'ban',
    explainer: 'While the company is disabled, new players are not counted and no commission accrues.',
  },
}

/** The partner link as the site reads it; `click` is the tracker macro, or nothing for a plain link. */
export function partnerLink(siteUrl: string, tag: string, clickParam: string, click: string | null, signUp = false): string {
  const base = siteUrl.replace(/\/+$/, '')
  const parts = [`affiliate=${tag}`]
  if (click) parts.push(`${clickParam}=${click}`)
  if (signUp) parts.push('modal=register')
  return `${base}/?${parts.join('&')}`
}
