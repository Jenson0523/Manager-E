import request from './request'

export interface ModuleVO {
  id: number
  moduleCode: string
  moduleName: string
  version: string
  entryUrl: string
  menuConfig: string
  permissions: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface ModuleDTO {
  moduleCode: string
  moduleName: string
  version: string
  entryUrl: string
  menuConfig: string
  permissions: string
}

export const moduleApi = {
  list: (params?: { keyword?: string; status?: number }) =>
    request.get<never, ModuleVO[]>('/v1/modules', { params }),
  create: (data: ModuleDTO) =>
    request.post<never, void>('/v1/modules', data),
  update: (id: number, data: ModuleDTO) =>
    request.put<never, void>(`/v1/modules/${id}`, data),
  toggleStatus: (id: number) =>
    request.put<never, void>(`/v1/modules/${id}/status`),
  delete: (id: number) =>
    request.delete<never, void>(`/v1/modules/${id}`),
}
