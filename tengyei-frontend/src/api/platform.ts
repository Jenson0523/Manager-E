import request from './request'
import type {
  PlatformRoleVO,
  PlatformRoleDTO,
  PlatformUserVO,
  PlatformUserDTO,
  PermissionGroupVO,
} from '@/types/platform'

export const platformRoleApi = {
  list: () => request.get<never, PlatformRoleVO[]>('/v1/platform/roles'),
  permissions: () => request.get<never, PermissionGroupVO[]>('/v1/platform/roles/permissions'),
  create: (data: PlatformRoleDTO) => request.post<never, { id: number }>('/v1/platform/roles', data),
  update: (id: number, data: PlatformRoleDTO) =>
    request.put<never, void>(`/v1/platform/roles/${id}`, data),
  remove: (id: number) => request.delete<never, void>(`/v1/platform/roles/${id}`),
  permissionIds: (id: number) => request.get<never, number[]>(`/v1/platform/roles/${id}/permissions`),
  assignPermissions: (id: number, permissionIds: number[]) =>
    request.put<never, void>(`/v1/platform/roles/${id}/permissions`, { permissionIds }),
}

export const platformUserApi = {
  list: (params: { keyword?: string; page?: number; size?: number }) =>
    request.get<never, import('@/types/common').PageResult<PlatformUserVO>>('/v1/platform/users', { params }),
  create: (data: PlatformUserDTO) => request.post<never, { id: number }>('/v1/platform/users', data),
  update: (id: number, data: PlatformUserDTO) =>
    request.put<never, void>(`/v1/platform/users/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/platform/users/${id}/status`, { status }),
  assignRoles: (id: number, roleIds: number[]) =>
    request.put<never, void>(`/v1/platform/users/${id}/roles`, { roleIds }),
  resetPassword: (id: number, password: string) =>
    request.put<never, void>(`/v1/platform/users/${id}/reset-password`, { password }),
  remove: (id: number) => request.delete<never, void>(`/v1/platform/users/${id}`),
}
