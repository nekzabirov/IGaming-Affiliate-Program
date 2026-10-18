import { http } from './client'
import type {
  AdminCompany,
  AdminCompanyChange,
  AdminCompanyQuery,
  AdminPartner,
  AdminPartnerQuery,
  Commission,
  CommissionQuery,
  Company,
  Config,
  Credentials,
  Me,
  NewCompany,
  NewPayout,
  NewPostback,
  PageQuery,
  Paged,
  Payout,
  Player,
  Postback,
  PostbackChange,
  Stats,
  StatsQuery,
} from './types'

const company = (identity: string) => `/companies/${encodeURIComponent(identity)}`

export const api = {
  config: () => http.get<Config>('/config'),

  register: (body: Credentials) => http.post<Me>('/auth/register', body),
  login: (body: Credentials) => http.post<Me>('/auth/login', body),
  logout: () => http.post<void>('/auth/logout'),
  me: () => http.get<Me>('/me'),

  companies: (signal?: AbortSignal) => http.get<Company[]>('/companies', undefined, signal),
  createCompany: (body: NewCompany) => http.post<Company>('/companies', body),
  company: (identity: string, signal?: AbortSignal) => http.get<Company>(company(identity), undefined, signal),
  players: (identity: string, query: PageQuery, signal?: AbortSignal) =>
    http.get<Paged<Player>>(`${company(identity)}/players`, query, signal),

  postbacks: (identity: string, signal?: AbortSignal) => http.get<Postback[]>(`${company(identity)}/postbacks`, undefined, signal),
  createPostback: (identity: string, body: NewPostback) => http.post<Postback>(`${company(identity)}/postbacks`, body),
  updatePostback: (identity: string, id: number, body: PostbackChange) =>
    http.patch<Postback>(`${company(identity)}/postbacks/${id}`, body),
  deletePostback: (identity: string, id: number) => http.delete<void>(`${company(identity)}/postbacks/${id}`),

  stats: (query: StatsQuery, signal?: AbortSignal) => http.get<Stats>('/stats', query, signal),
  commissions: (query: CommissionQuery, signal?: AbortSignal) => http.get<Paged<Commission>>('/commissions', query, signal),

  payouts: (query: PageQuery, signal?: AbortSignal) => http.get<Paged<Payout>>('/payouts', query, signal),
  requestPayout: (body: NewPayout) => http.post<Payout>('/payouts', body),

  admin: {
    companies: (query: AdminCompanyQuery, signal?: AbortSignal) =>
      http.get<Paged<AdminCompany>>('/admin/companies', query, signal),
    updateCompany: (identity: string, body: AdminCompanyChange) =>
      http.patch<AdminCompany>(`/admin/companies/${encodeURIComponent(identity)}`, body),
    partners: (query: AdminPartnerQuery, signal?: AbortSignal) => http.get<Paged<AdminPartner>>('/admin/partners', query, signal),
    updatePartner: (id: number, active: boolean) => http.patch<AdminPartner>(`/admin/partners/${id}`, { active }),
  },
}
