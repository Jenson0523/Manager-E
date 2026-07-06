import request from './request'

export interface BannerVO {
  id: number | null
  title: string
  content?: string
  level: 'INFO' | 'WARN' | 'URGENT'
  linkUrl?: string
}

export interface AnnouncementVO {
  id: number
  title: string
  content?: string
  level: string
  linkUrl?: string
  targetScope: 'SELF' | 'ALL_COMPANIES' | 'COMPANIES'
  targetIds?: string
  startAt?: string
  endAt?: string
  status: number
  createdBy?: string
  createdAt: string
}

export interface AnnouncementSaveDTO {
  id?: number
  title: string
  content?: string
  level?: string
  linkUrl?: string
  targetScope?: string
  targetIds?: number[]
  startAt?: string
  endAt?: string
  status?: number
}

export const announcementApi = {
  active: () => request.get<never, BannerVO[]>('/v1/announcements/active'),
  list: () => request.get<never, AnnouncementVO[]>('/v1/announcements'),
  save: (data: AnnouncementSaveDTO) =>
    request.post<never, { id: number }>('/v1/announcements', data),
  remove: (id: number) => request.delete<never, void>(`/v1/announcements/${id}`),
}
