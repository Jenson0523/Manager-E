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
  transfer: (id: number, targetUserId: number) =>
    request.put<never, void>(`/v1/approval/instances/${id}/transfer`, { targetUserId }),
  resubmit: (id: number, formData?: Record<string, unknown>) =>
    request.put<never, void>(`/v1/approval/instances/${id}/resubmit`, { formData }),
  addSign: (id: number, targetUserId: number, position: 'PRE' | 'POST') =>
    request.put<never, void>(`/v1/approval/instances/${id}/addsign`, { targetUserId, position }),
  statistics: () => request.get<never, ApprovalStatisticsVO>('/v1/approval/statistics'),
  listForStats: () => request.get<never, ApprovalInstanceVO[]>('/v1/approval/list'),
  statisticsDetail: (status?: string) =>
    request.get<never, ApprovalInstanceVO[]>('/v1/approval/statistics/detail', { params: { status } }),
  /** 选人/选角色下拉(抄送/转交/加签/流程设计),按审批权限放行 */
  options: () =>
    request.get<never, { users: { id: number; realName: string }[]; roles: { id: number; name: string }[] }>(
      '/v1/approval/options',
    ),
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
