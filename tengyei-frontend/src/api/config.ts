import request from './request'

export interface SystemConfigVO {
  id: number
  configKey: string
  configValue: string
  description: string
}

export const configApi = {
  list: () => request.get<never, SystemConfigVO[]>('/v1/system-config'),
  update: (key: string, value: string) =>
    request.put<never, void>(`/v1/system-config/${key}`, { value }),
}
