import request from './request'

export const meApi = {
  changePassword: (oldPassword: string, newPassword: string) =>
    request.put<never, void>('/v1/me/password', { oldPassword, newPassword }),
}
