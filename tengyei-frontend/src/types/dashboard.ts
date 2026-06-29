export interface RecentCompany {
  id: number
  companyNo: string
  fullName: string
  shortName: string
  status: number
  createdAt: string
}

export interface DashboardStats {
  scope: 'super' | 'company'
  companyTotal?: number
  companyActive?: number
  companyTodayNew?: number
  userTotal?: number
  recentCompanies?: RecentCompany[]
  deptCount?: number
  branchCount?: number
  userCount?: number
  todayLoginCount?: number
}

export interface TrendItem {
  date: string
  count: number
}

export interface DistItem {
  name: string
  value: number
}

export interface ChartData {
  userTrend: TrendItem[]
  statusDist: DistItem[]
  companyDist?: Array<{ company: string; count: number }>
}
