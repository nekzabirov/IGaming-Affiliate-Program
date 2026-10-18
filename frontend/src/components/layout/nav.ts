import type { IconName } from '@/components/ui/icons'

export interface NavItem {
  label: string
  to: string
  icon: IconName
}

export const PARTNER_NAV: NavItem[] = [
  { label: 'Dashboard', to: '/', icon: 'chart' },
  { label: 'Companies', to: '/companies', icon: 'layers' },
  { label: 'Earnings', to: '/earnings', icon: 'list' },
  { label: 'Payouts', to: '/payouts', icon: 'wallet' },
  { label: 'Promo', to: '/promo', icon: 'image' },
]

export const ADMIN_NAV: NavItem[] = [
  { label: 'Review queue', to: '/admin/companies', icon: 'shield' },
  { label: 'Partners', to: '/admin/partners', icon: 'users' },
]

/** The phone's bottom bar has room for four; Earnings and Promo live in the account menu. */
export const PARTNER_TABS: NavItem[] = PARTNER_NAV.filter((item) => item.to !== '/earnings' && item.to !== '/promo')

export const ADMIN_TAB: NavItem = { label: 'Admin', to: '/admin', icon: 'shield' }

/** A section stays lit on its own sub-pages (a company under Companies); the dashboard only on itself. */
export const isCurrent = (item: NavItem, path: string) => (item.to === '/' ? path === '/' : path === item.to || path.startsWith(`${item.to}/`))
