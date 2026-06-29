import request from './request'
import type { PageResult } from '@/types/common'
import { downloadExcel } from '@/utils/download'

export interface AuditLogVO {
  id: number
  tenantId: number
  userId: number
  userName: string
  module: string
  actionType: string
  description: string
  ipAddress: string
  userAgent?: string
  result: number
  errorMsg?: string
  createdAt: string
}

export interface LoginLogVO {
  id: number
  tenantId: number
  userId: number
  username: string
  loginType: string
  ipAddress: string
  result: number
  failReason: string
  createdAt: string
}

export const auditApi = {
  page: (params: {
    page?: number
    size?: number
    module?: string
    startDate?: string
    endDate?: string
  }) => request.get<never, PageResult<AuditLogVO>>('/v1/audit-logs', { params }),

  export: (params: { module?: string; startDate?: string; endDate?: string }) =>
    downloadExcel(
      '/v1/audit-logs/export',
      params as Record<string, unknown>,
      `操作日志_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.xlsx`
    ),
}

export const loginLogApi = {
  page: (params: {
    page?: number
    size?: number
    username?: string
    result?: number
    startDate?: string
    endDate?: string
  }) => request.get<never, PageResult<LoginLogVO>>('/v1/login-logs', { params }),

  export: (params: { username?: string; result?: number; startDate?: string; endDate?: string }) =>
    downloadExcel(
      '/v1/login-logs/export',
      params as Record<string, unknown>,
      `登录日志_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.xlsx`
    ),
}
