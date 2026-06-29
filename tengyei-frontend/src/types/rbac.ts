export interface RoleVO {
  id: number
  name: string
  code: string
  description?: string
  dataScope: string
  isPreset: number
  status: number
}

export interface RoleSaveDTO {
  name: string
  code: string
  description?: string
  dataScope?: string
}

export interface PermissionItem {
  id: number
  code: string
  name: string
}

export interface PermissionGroupVO {
  module: string
  permissions: PermissionItem[]
}
