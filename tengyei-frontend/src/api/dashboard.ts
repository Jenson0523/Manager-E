import request from './request'
import type { DashboardStats, ChartData } from '@/types/dashboard'

export const dashboardApi = {
  stats: () => request.get<never, DashboardStats>('/v1/dashboard/stats'),
  chartData: () => request.get<never, ChartData>('/v1/dashboard/chart-data'),
}
