import request from './request'
import type { PageResult } from '@/types/common'

export interface AuditLogVO {
  id: number
  tenantId: number
  userId: number
  userName: string
  module: string
  actionType: string
  description: string
  ipAddress: string
  result: number
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
}
