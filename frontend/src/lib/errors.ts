import type { ApiError } from '@/api/client'

const MESSAGES: Partial<Record<string, string>> = {
  INVALID_CREDENTIALS: 'Wrong login or password.',
  ACCOUNT_BLOCKED: 'This account is blocked.',
  TOO_MANY_REQUESTS: 'Too many attempts. Wait a few minutes and try again.',
  LOGIN_TAKEN: 'This login is taken. Try another one.',
  IDENTITY_TAKEN: 'This identity is taken. Try another one.',
  INSUFFICIENT_BALANCE: 'This is more than your available balance.',
  LIMIT_REACHED: 'A company can have up to 10 postbacks.',
  SELF_BLOCK: 'You cannot block your own account.',
  NOT_FOUND: 'Not found. It may have been removed.',
  FORBIDDEN: 'You do not have access to this.',
}

/** Codes that belong to one form field even when the API does not name it. */
const FIELDS: Partial<Record<string, string>> = {
  LOGIN_TAKEN: 'login',
  IDENTITY_TAKEN: 'identity',
  INSUFFICIENT_BALANCE: 'amount',
}

export const errorMessage = (error: ApiError) => MESSAGES[error.code] ?? error.message

export const errorField = (error: ApiError) => error.field ?? FIELDS[error.code]
