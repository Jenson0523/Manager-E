import request from './request'
import type { DashboardStats } from '@/types/dashboard'

export const dashboardApi = {
  stats: () => request.get<never, DashboardStats>('/v1/dashboard/stats'),
}
