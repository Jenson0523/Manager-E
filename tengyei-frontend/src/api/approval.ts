import request from './request'
import type { IdResult } from '@/types/common'
import type {
  ApprovalInstanceVO,
  ApprovalApplyDTO,
  ApprovalFlowVO,
  ApprovalFlowSaveDTO,
  ApprovalStatisticsVO,
  ApprovalDelegateVO,
} from '@/types/approval'

export const approvalApi = {
  todo: () => request.get<never, ApprovalInstanceVO[]>('/v1/approval/todo'),
  my: () => request.get<never, ApprovalInstanceVO[]>('/v1/approval/my'),
  done: () => request.get<never, ApprovalInstanceVO[]>('/v1/approval/done'),
  cc: () => request.get<never, ApprovalInstanceVO[]>('/v1/approval/cc'),
  uploadFile: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post<never, { url: string; name: string }>('/v1/upload/file', fd)
  },
  detail: (id: number) => request.get<never, ApprovalInstanceVO>(`/v1/approval/instances/${id}`),
  apply: (data: ApprovalApplyDTO) =>
    request.post<never, IdResult>('/v1/approval/instances', data),
  act: (id: number, action: 'APPROVE' | 'REJECT', comment?: string) =>
    request.put<never, void>(`/v1/approval/instances/${id}/act`, { action, comment }),
  cancel: (id: number) => request.put<never, void>(`/v1/approval/instances/${id}/cancel`),
  /** fromUserId:管理员代为转交时指明原审批人(审批人本人转交无需传) */
  transfer: (id: number, targetUserId: number, fromUserId?: number) =>
    request.put<never, void>(`/v1/approval/instances/${id}/transfer`, { targetUserId, fromUserId }),
  /** 催办:发起人/流程管理员提醒当前审批人,每单每小时限一次 */
  urge: (id: number) => request.put<never, void>(`/v1/approval/instances/${id}/urge`),
  resubmit: (id: number, formData?: Record<string, unknown>) =>
    request.put<never, void>(`/v1/approval/instances/${id}/resubmit`, { formData }),
  addSign: (id: number, targetUserId: number, position: 'PRE' | 'POST') =>
    request.put<never, void>(`/v1/approval/instances/${id}/addsign`, { targetUserId, position }),
  statistics: () => request.get<never, ApprovalStatisticsVO>('/v1/approval/statistics'),
  listForStats: () => request.get<never, ApprovalInstanceVO[]>('/v1/approval/list'),
  statisticsDetail: (status?: string) =>
    request.get<never, ApprovalInstanceVO[]>('/v1/approval/statistics/detail', { params: { status } }),
  delegateGet: () => request.get<never, ApprovalDelegateVO | null>('/v1/approval/delegate'),
  delegateSave: (data: ApprovalDelegateVO) =>
    request.put<never, void>('/v1/approval/delegate', data),

  flows: () => request.get<never, ApprovalFlowVO[]>('/v1/approval/flows'),
  forms: () => request.get<never, ApprovalFlowVO[]>('/v1/approval/forms'),
  saveFlow: (data: ApprovalFlowSaveDTO) =>
    request.post<never, IdResult>('/v1/approval/flows', data),
  toggleFlowStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/approval/flows/${id}/status`, { status }),
}
