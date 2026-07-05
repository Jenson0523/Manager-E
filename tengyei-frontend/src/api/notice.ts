import request from './request'

export interface NoticeVO {
  id: number
  type: string
  title: string
  content?: string
  bizType?: string
  bizId?: number
  isRead: number
  createdAt: string
}

export const noticeApi = {
  list: () => request.get<never, NoticeVO[]>('/v1/notices'),
  unreadCount: () => request.get<never, { count: number }>('/v1/notices/unread-count'),
  markRead: (id: number) => request.put<never, void>(`/v1/notices/${id}/read`),
  markAllRead: () => request.put<never, void>('/v1/notices/read-all'),
}
