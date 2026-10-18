import type { CompanyType } from '@/api/types'
import { compareDecimal, isNegative, isPositive } from './decimal'

// The API's own rules, checked before a request so a typo costs no round trip; the API still has the last word

const LOGIN = /^[a-z0-9](?:[a-z0-9._-]{1,30}[a-z0-9])$/

const IDENTITY = /^[a-z0-9]+(?:[-_][a-z0-9]+)*$/

/** Money and percents are typed with at most two decimals. */
const DECIMAL_INPUT = /^\d+(?:\.\d{1,2})?$/

/** The largest amount the API takes; its nano units still fit the server's 64-bit sums. */
const MAX_AMOUNT = '1000000000'

/** bcrypt reads at most 72 bytes, so the API counts a password in UTF-8 bytes, not in characters. */
const PASSWORD_MAX_BYTES = 72

const UTF8 = new TextEncoder()

export const LOGIN_RULE = '3–32 characters: a–z, 0–9, dots, dashes, underscores. Starts and ends with a letter or digit.'

export const IDENTITY_RULE = '3–48 characters: a–z and 0–9, with single dashes or underscores between them.'

export function loginError(value: string): string | null {
  if (!value) return 'Enter a login.'
  return LOGIN.test(value) ? null : LOGIN_RULE
}

export function passwordError(value: string): string | null {
  if (!value) return 'Enter a password.'
  if (value.length < 8) return 'At least 8 characters.'
  if (UTF8.encode(value).length > PASSWORD_MAX_BYTES) return 'Too long: at most 72 bytes, and letters outside a–z count as two or more.'
  return null
}

export function identityError(value: string): string | null {
  if (!value) return 'Enter an identity.'
  return value.length >= 3 && value.length <= 48 && IDENTITY.test(value) ? null : IDENTITY_RULE
}

/** 0 is valid terms: a partner paid up front runs on them, tracked like any other company and earning nothing. */
export function termsError(type: CompanyType, value: string): string | null {
  const cpa = type === 'CPA'
  if (!value) return cpa ? 'Enter an amount.' : 'Enter a percent.'
  // Checked before the shape, which refuses a minus sign too but would only say "a number"
  if (isNegative(value)) return cpa ? 'The amount must be 0 or more.' : 'The percent must be 0 or more.'
  if (!DECIMAL_INPUT.test(value)) return 'A number with at most 2 decimals, like 25 or 12.5.'
  if (cpa && compareDecimal(value, MAX_AMOUNT) > 0) return 'At most 1,000,000,000.'
  if (!cpa && compareDecimal(value, '100') > 0) return 'At most 100%.'
  return null
}

export function payoutAmountError(value: string, balance: string): string | null {
  if (!value) return 'Enter an amount.'
  if (!DECIMAL_INPUT.test(value)) return 'A number with at most 2 decimals, like 150 or 99.5.'
  if (!isPositive(value)) return 'The amount must be more than 0.'
  if (compareDecimal(value, balance) > 0) return 'This is more than your available balance.'
  return null
}

/**
 * What a person types as a number: a comma decimal separator is read as a dot, spaces are dropped, and leading
 * zeros go ("05" is 5), because the API refuses an amount that starts with one.
 */
export const normalizeNumber = (value: string) =>
  value.replace(',', '.').replace(/\s+/g, '').replace(/^0+(?=\d)/, '')
