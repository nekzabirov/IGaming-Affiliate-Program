/**
 * The REST contract of affiliate-app, field for field. Money travels as plain decimal strings in the partner
 * currency ("12.5", "0.000123"); days are `YYYY-MM-DD` in UTC; instants are ISO-8601.
 */

/** A decimal string in the partner currency, or a percent for RevShare terms. */
export type Decimal = string

/** `YYYY-MM-DD`, a UTC day. */
export type Day = string

/** ISO-8601 instant. */
export type Instant = string

export type Role = 'PARTNER' | 'ADMIN'

export type CompanyType = 'CPA' | 'REVSHARE_DEPOSIT' | 'REVSHARE_TURNOVER'

export type CompanyStatus = 'IN_REVIEW' | 'ACTIVE' | 'DISABLED'

/** A tracker the API can send postbacks to; `GET /api/config` lists the ones it supports today. */
export type PostbackType = 'KEITARO' | (string & {})

export type PostbackEvent = 'REGISTRATION' | 'DEPOSIT' | 'SPIN' | 'BET'

export type PayoutStatus = 'PENDING' | 'PAID' | 'REJECTED'

export interface Config {
  currency: string
  supportUrl: string | null
  siteUrl: string
  tagPrefix: string
  clickParam: string
  cpaMinDeposit: Decimal
  payoutNetworks: string[]
  postbackSourceIp: string | null
  postbackTypes: PostbackType[]
  /** The promo library's catalogue; null when the deployment has none, and the Promo screen is hidden. */
  promoManifestUrl: string | null
}

export interface Me {
  id: number
  login: string
  role: Role
  balance: Decimal
  totalEarned: Decimal
  pendingPayouts: Decimal
  paidOut: Decimal
  currency: string
  createdAt: Instant
}

export interface Credentials {
  login: string
  password: string
}

export interface CompanyStats {
  registrations: number
  firstDeposits: number
  earned: Decimal
}

export interface Company {
  identity: string
  type: CompanyType
  /** CPA: an amount in the partner currency; RevShare: a percent. */
  amount: Decimal
  status: CompanyStatus
  tag: string
  link: string
  createdAt: Instant
  /** All time. */
  stats: CompanyStats
}

export interface NewCompany {
  identity: string
  type: CompanyType
  amount: Decimal
}

export interface Player {
  playerId: number
  clickId: string | null
  country: string | null
  registeredAt: Instant
  firstDepositAt: Instant | null
  firstDepositAmount: Decimal | null
  depositCount: number
  depositAmount: Decimal
  turnover: Decimal
  earned: Decimal
}

export interface PostbackExample {
  event: PostbackEvent
  method: 'GET'
  url: string
}

export interface Postback {
  id: number
  type: PostbackType
  url: string
  active: boolean
  lastSentAt: Instant | null
  /** HTTP status of the last attempt; null when there was none or it failed before a response. */
  lastStatus: number | null
  /** Why the last attempt failed or was skipped; null after a delivery. */
  lastError: string | null
  createdAt: Instant
  /** The exact requests the server would send, built by the same code that sends them. */
  examples: PostbackExample[]
}

export interface NewPostback {
  type: PostbackType
  url: string
}

export interface PostbackChange {
  active?: boolean
  url?: string
}

export interface Paged<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface PageQuery {
  page: number
  size: number
}

export interface StatsQuery {
  from: Day
  to: Day
  company?: string
}

export interface StatsTotals {
  registrations: number
  firstDeposits: number
  /** FTD / registrations as a percent string. */
  conversion: string
  depositCount: number
  depositAmount: Decimal
  turnover: Decimal
  earned: Decimal
  earnedByType: Record<CompanyType, Decimal>
}

export interface StatsDay {
  day: Day
  registrations: number
  firstDeposits: number
  depositAmount: Decimal
  turnover: Decimal
  earned: Decimal
}

export interface StatsCompany {
  identity: string
  type: CompanyType
  status: CompanyStatus
  registrations: number
  firstDeposits: number
  depositAmount: Decimal
  turnover: Decimal
  earned: Decimal
}

export interface StatsCountry {
  /** ISO 3166-1 alpha-2; null when the player's country is unknown. */
  country: string | null
  registrations: number
  firstDeposits: number
}

export interface Stats {
  from: Day
  to: Day
  totals: StatsTotals
  /** Every day of the range, zeros filled. */
  daily: StatsDay[]
  companies: StatsCompany[]
  /** Top 10. */
  countries: StatsCountry[]
}

export interface Commission {
  id: number
  day: Day
  /** The company identity. */
  company: string
  playerId: number
  type: CompanyType
  /** Signed: a reversal takes back what a cancelled stake added. */
  amount: Decimal
  updatedAt: Instant
}

export interface CommissionQuery extends PageQuery {
  company?: string
}

export interface Payout {
  id: number
  amount: Decimal
  currency: string
  network: string
  address: string
  status: PayoutStatus
  txHash: string | null
  comment: string | null
  createdAt: Instant
  updatedAt: Instant
}

export interface NewPayout {
  amount: Decimal
  network: string
  address: string
}

export interface CompanyOwner {
  id: number
  login: string
  active: boolean
}

export interface AdminCompany extends Company {
  owner: CompanyOwner
}

export interface AdminCompanyQuery extends PageQuery {
  status?: CompanyStatus
  q?: string
}

export interface AdminCompanyChange {
  status?: CompanyStatus
  amount?: Decimal
}

export interface AdminPartner {
  id: number
  login: string
  role: Role
  active: boolean
  balance: Decimal
  companies: number
  createdAt: Instant
}

export interface AdminPartnerQuery extends PageQuery {
  q?: string
}

export type ErrorCode =
  | 'VALIDATION'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'UNAUTHENTICATED'
  | 'INVALID_CREDENTIALS'
  | 'ACCOUNT_BLOCKED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'LOGIN_TAKEN'
  | 'IDENTITY_TAKEN'
  | 'INSUFFICIENT_BALANCE'
  | 'LIMIT_REACHED'
  | 'SELF_BLOCK'
  | 'TOO_MANY_REQUESTS'
  | 'INTERNAL'

/** The error body of every failed request. */
export interface ErrorBody {
  code: ErrorCode | (string & {})
  message: string
  field?: string
  rule?: string
}
