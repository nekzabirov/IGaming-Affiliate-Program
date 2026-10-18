import { createRouter, createWebHistory } from 'vue-router'
import { BRAND } from '@/brand'
import AppShell from '@/components/layout/AppShell.vue'
import { hasPromo } from '@/lib/promo'
import { isAdmin, session } from '@/lib/session'
import LoginView from '@/views/LoginView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** Open without a session: the sign-in screens. */
    guest?: boolean
    admin?: boolean
  }
}

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { title: 'Sign in', guest: true } },
    { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { title: 'Create account', guest: true } },
    {
      path: '/',
      component: AppShell,
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: 'Dashboard' } },
        { path: 'companies', name: 'companies', component: () => import('@/views/CompaniesView.vue'), meta: { title: 'Companies' } },
        {
          path: 'companies/:identity',
          name: 'company',
          component: () => import('@/views/CompanyView.vue'),
          props: true,
          meta: { title: 'Company' },
        },
        { path: 'earnings', name: 'earnings', component: () => import('@/views/EarningsView.vue'), meta: { title: 'Earnings' } },
        { path: 'payouts', name: 'payouts', component: () => import('@/views/PayoutsView.vue'), meta: { title: 'Payouts' } },
        {
          path: 'promo',
          name: 'promo',
          component: () => import('@/views/PromoView.vue'),
          meta: { title: 'Promo materials' },
          // A deployment without a promo library has no such screen.
          beforeEnter: () => (hasPromo() ? true : { name: 'dashboard' }),
        },
        { path: 'admin', redirect: '/admin/companies' },
        {
          path: 'admin/companies',
          name: 'admin-companies',
          component: () => import('@/views/AdminCompaniesView.vue'),
          meta: { title: 'Review queue', admin: true },
        },
        {
          path: 'admin/partners',
          name: 'admin-partners',
          component: () => import('@/views/AdminPartnersView.vue'),
          meta: { title: 'Partners', admin: true },
        },
        { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { title: 'Not found' } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (to.meta.guest) return session.me ? { name: 'dashboard' } : true
  if (!session.me) return { name: 'login', query: to.fullPath === '/' ? {} : { next: to.fullPath } }
  if (to.meta.admin && !isAdmin()) return { name: 'dashboard' }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · ${BRAND.name}` : BRAND.name
})

/** Where to land after signing in: the page that asked for it, never an address outside the cabinet. */
export function afterSignIn(next: unknown): string {
  return typeof next === 'string' && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}
