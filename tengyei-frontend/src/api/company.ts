import request from './request'
import type { PageResult, IdResult } from '@/types/common'
import type { CompanyVO, CompanyCreateDTO, CompanyUpdateDTO } from '@/types/company'

export const companyApi = {
  page: (params: { page: number; size: number; keyword?: string }) =>
    request.get<never, PageResult<CompanyVO>>('/v1/companies', { params }),
  detail: (id: number) => request.get<never, CompanyVO>(`/v1/companies/${id}`),
  create: (data: CompanyCreateDTO) => request.post<never, IdResult>('/v1/companies', data),
  update: (id: number, data: CompanyUpdateDTO) =>
    request.put<never, void>(`/v1/companies/${id}`, data),
  changeStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/companies/${id}/status`, { status }),
  delete: (id: number) => request.delete<never, void>(`/v1/companies/${id}`),
}
