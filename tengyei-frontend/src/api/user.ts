import request from './request'
import type { PageResult, IdResult } from '@/types/common'
import type { UserVO, UserCreateDTO, UserUpdateDTO } from '@/types/user'

export const userApi = {
  page: (params: {
    page: number
    size: number
    keyword?: string
    deptId?: number
    roleId?: number
  }) => request.get<never, PageResult<UserVO>>('/v1/users', { params }),
  create: (data: UserCreateDTO) => request.post<never, IdResult>('/v1/users', data),
  update: (id: number, data: UserUpdateDTO) => request.put<never, void>(`/v1/users/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/users/${id}/status`, { status }),
  assignRoles: (id: number, roleIds: number[]) =>
    request.put<never, void>(`/v1/users/${id}/roles`, { roleIds }),
  resetPassword: (id: number, password: string) =>
    request.put<never, void>(`/v1/users/${id}/reset-password`, { password }),
}
