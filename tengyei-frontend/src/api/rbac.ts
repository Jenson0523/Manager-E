import request from './request'
import type { IdResult } from '@/types/common'
import type { RoleVO, RoleSaveDTO, PermissionGroupVO } from '@/types/rbac'

export const roleApi = {
  list: () => request.get<never, RoleVO[]>('/v1/roles'),
  create: (data: RoleSaveDTO) => request.post<never, IdResult>('/v1/roles', data),
  update: (id: number, data: RoleSaveDTO) => request.put<never, void>(`/v1/roles/${id}`, data),
  remove: (id: number) => request.delete<never, void>(`/v1/roles/${id}`),
  /** 复制角色(名称加副本,权限一并复制) */
  copy: (id: number) => request.post<never, IdResult>(`/v1/roles/${id}/copy`),
  permissionIds: (id: number) => request.get<never, number[]>(`/v1/roles/${id}/permissions`),
  assignPermissions: (id: number, permissionIds: number[]) =>
    request.put<never, void>(`/v1/roles/${id}/permissions`, { permissionIds }),
}

export const permissionApi = {
  grouped: () => request.get<never, PermissionGroupVO[]>('/v1/permissions'),
}
