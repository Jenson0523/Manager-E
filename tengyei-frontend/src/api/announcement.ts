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
  audienceType?: 'ALL' | 'DEPT' | 'ROLE'
  audienceIds?: string
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
  targetScope?: string
  targetIds?: number[]
  audienceType?: string
  audienceIds?: number[]
  startAt?: string
  endAt?: string
  status?: number
}

export interface AnnouncementDetailVO {
  id: number
  title: string
  content?: string
  level: string
  source: string
  publisherName?: string
  publisherTracked?: boolean
  publisherRoles?: string[]
  publisherDepts?: string[]
  createdAt?: string
  startAt?: string
  endAt?: string
}

export const announcementApi = {
  active: () => request.get<never, BannerVO[]>('/v1/announcements/active'),
  list: () => request.get<never, AnnouncementVO[]>('/v1/announcements'),
  detail: (id: number) => request.get<never, AnnouncementDetailVO>(`/v1/announcements/${id}`),
  save: (data: AnnouncementSaveDTO) =>
    request.post<never, { id: number }>('/v1/announcements', data),
  setStatus: (id: number, status: number) =>
    request.put<never, void>(`/v1/announcements/${id}/status`, { status }),
  remove: (id: number) => request.delete<never, void>(`/v1/announcements/${id}`),
}
