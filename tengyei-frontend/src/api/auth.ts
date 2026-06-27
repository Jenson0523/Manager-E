import request from './request'
import type { LoginRequest, LoginResponse, UserInfo } from '@/types/auth'

export const authApi = {
  login: (data: LoginRequest) =>
    request.post<never, LoginResponse>('/v1/auth/login', data),

  logout: () =>
    request.post<never, void>('/v1/auth/logout'),

  refresh: () =>
    request.post<never, string>('/v1/auth/refresh'),

  userinfo: () =>
    request.get<never, UserInfo>('/v1/auth/userinfo'),
}
