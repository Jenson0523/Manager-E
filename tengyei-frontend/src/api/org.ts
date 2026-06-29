import request from './request'
import type { PageResult, IdResult } from '@/types/common'
import type { DeptTreeVO, DeptSaveDTO, BranchVO, BranchSaveDTO } from '@/types/org'

export const deptApi = {
  tree: () => request.get<never, DeptTreeVO[]>('/v1/depts/tree'),
  create: (data: DeptSaveDTO) => request.post<never, IdResult>('/v1/depts', data),
  update: (id: number, data: DeptSaveDTO) => request.put<never, void>(`/v1/depts/${id}`, data),
  remove: (id: number) => request.delete<never, void>(`/v1/depts/${id}`),
}

export const branchApi = {
  page: (params: { page: number; size: number }) =>
    request.get<never, PageResult<BranchVO>>('/v1/branches', { params }),
  create: (data: BranchSaveDTO) => request.post<never, IdResult>('/v1/branches', data),
  update: (id: number, data: BranchSaveDTO) => request.put<never, void>(`/v1/branches/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/branches/${id}/status`, { status }),
}
