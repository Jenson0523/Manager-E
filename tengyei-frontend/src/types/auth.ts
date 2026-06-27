export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  pwdResetRequired: boolean
  realName: string
  tenantId: number
}

export interface RouteVO {
  path: string
  name: string
  children?: RouteVO[]
}

export interface UserInfo {
  userId: number
  tenantId: number
  branchId: number | null
  username: string
  realName: string
  avatarUrl: string | null
  isSuperAdmin: boolean
  dataScope: string
  roleCodes: string[]
  permissions: string[]
  routes: RouteVO[]
}

export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}
