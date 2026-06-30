export interface PlatformRoleVO {
  id: number
  name: string
  code: string
  description?: string
  isPreset: number
  status: number
}

export interface PlatformRoleDTO {
  name: string
  code: string
  description?: string
}

export interface PlatformUserVO {
  id: number
  username: string
  realName: string
  phone?: string
  email?: string
  status: number
  isSuperAdmin: number
  roleIds: number[]
  roleNames: string[]
}

export interface PlatformUserDTO {
  username: string
  realName: string
  phone?: string
  email?: string
  password?: string
  roleIds?: number[]
}

export interface PermissionGroupVO {
  module: string
  permissions: { id: number; code: string; name: string }[]
}
