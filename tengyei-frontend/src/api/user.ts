import request from './request'
import type { PageResult, IdResult } from '@/types/common'
import type { UserVO, UserCreateDTO, UserUpdateDTO } from '@/types/user'
import { downloadExcel } from '@/utils/download'

export const userApi = {
  page: (params: {
    page: number
    size: number
    keyword?: string
    deptId?: number
    roleId?: number
  }) => request.get<never, PageResult<UserVO>>('/v1/users', { params }),
  quota: () => request.get<never, { used: number; max: number | null }>('/v1/users/quota'),
  create: (data: UserCreateDTO) => request.post<never, IdResult>('/v1/users', data),
  update: (id: number, data: UserUpdateDTO) => request.put<never, void>(`/v1/users/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/users/${id}/status`, { status }),
  assignRoles: (id: number, roleIds: number[]) =>
    request.put<never, void>(`/v1/users/${id}/roles`, { roleIds }),
  resetPassword: (id: number, password: string) =>
    request.put<never, void>(`/v1/users/${id}/reset-password`, { password }),

  export: (params: { keyword?: string; deptId?: number }) =>
    downloadExcel(
      '/v1/users/export',
      params as Record<string, unknown>,
      `人员列表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '')}.xlsx`
    ),

  batchStatus: (ids: number[], status: number) =>
    request.put<never, void>('/v1/users/batch/status', { ids, status }),

  batchRoles: (ids: number[], roleIds: number[]) =>
    request.put<never, void>('/v1/users/batch/roles', { ids, roleIds }),
}
