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
  detail: (id: number) => request.get<never, ApprovalInstanceVO>(`/v1/approval/instances/${id}`),
  apply: (data: ApprovalApplyDTO) =>
    request.post<never, IdResult>('/v1/approval/instances', data),
  act: (id: number, action: 'APPROVE' | 'REJECT', comment?: string) =>
    request.put<never, void>(`/v1/approval/instances/${id}/act`, { action, comment }),
  transfer: (id: number, targetUserId: number) =>
    request.put<never, void>(`/v1/approval/instances/${id}/transfer`, { targetUserId }),
  statistics: () => request.get<never, ApprovalStatisticsVO>('/v1/approval/statistics'),
  delegateGet: () => request.get<never, ApprovalDelegateVO | null>('/v1/approval/delegate'),
  delegateSave: (data: ApprovalDelegateVO) =>
    request.put<never, void>('/v1/approval/delegate', data),

  flows: () => request.get<never, ApprovalFlowVO[]>('/v1/approval/flows'),
  saveFlow: (data: ApprovalFlowSaveDTO) =>
    request.post<never, IdResult>('/v1/approval/flows', data),
  toggleFlowStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/approval/flows/${id}/status`, { status }),
}
