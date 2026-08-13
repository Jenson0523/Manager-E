import request from './request'
import type { PageResult } from '@/types/common'

export interface ContractAttachment {
  name: string
  url: string
}

export interface ContractVO {
  id: number
  contractNo: string
  name: string
  type: string
  partyB: string
  partyBContact?: string
  partyBPhone?: string
  amount: number
  signDate?: string
  startDate?: string
  endDate?: string
  ownerId?: number
  ownerName?: string
  ownerDeptId?: number
  ownerDeptName?: string
  status: string
  attachments: ContractAttachment[]
  remark?: string
  createdBy?: string
  createdAt?: string
  /** 距到期天数:负数=已过期,null=无固定期限或已完成/终止 */
  daysToExpire?: number | null
}

export interface ContractSaveDTO {
  id?: number
  contractNo?: string
  name: string
  type?: string
  partyB: string
  partyBContact?: string
  partyBPhone?: string
  amount?: number
  signDate?: string
  startDate?: string
  endDate?: string
  ownerId?: number
  status?: string
  attachments?: ContractAttachment[]
  remark?: string
}

export interface ContractSummaryVO {
  total: number
  expiring: number
  expired: number
  remindDays: number
}

export interface ContractQuery {
  page?: number
  size?: number
  keyword?: string
  type?: string
  status?: string
  expiring?: boolean
}

export const CONTRACT_TYPES = [
  { value: 'SALE', label: '销售合同' },
  { value: 'PURCHASE', label: '采购合同' },
  { value: 'SERVICE', label: '劳务合同' },
  { value: 'LEASE', label: '租赁合同' },
  { value: 'OTHER', label: '其他' },
]

export const CONTRACT_STATUSES = [
  { value: 'DRAFT', label: '草稿', tag: 'info' as const },
  { value: 'EFFECTIVE', label: '已生效', tag: 'success' as const },
  { value: 'PERFORMING', label: '履约中', tag: 'primary' as const },
  { value: 'COMPLETED', label: '已完成', tag: 'info' as const },
  { value: 'TERMINATED', label: '已终止', tag: 'danger' as const },
]

export const contractApi = {
  /** 合同扫描件/附件,复用全站业务附件接口(后端已放行 contract:manage) */
  uploadFile: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post<never, ContractAttachment>('/v1/upload/file', fd)
  },
  page: (params: ContractQuery) =>
    request.get<never, PageResult<ContractVO>>('/v1/contracts', { params }),
  summary: () => request.get<never, ContractSummaryVO>('/v1/contracts/summary'),
  detail: (id: number) => request.get<never, ContractVO>(`/v1/contracts/${id}`),
  save: (data: ContractSaveDTO) => request.post<never, { id: number }>('/v1/contracts', data),
  setStatus: (id: number, status: string) =>
    request.put<never, void>(`/v1/contracts/${id}/status`, { status }),
  remove: (id: number) => request.delete<never, void>(`/v1/contracts/${id}`),
}
