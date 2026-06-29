export interface UserVO {
  id: number
  username: string
  realName: string
  phone: string
  email?: string
  deptId?: number
  branchId?: number
  status: number
  roleIds: number[]
  roleNames: string[]
}

export interface UserCreateDTO {
  username: string
  realName: string
  phone: string
  email?: string
  password: string
  deptId?: number
  branchId?: number
  roleIds?: number[]
}

export interface UserUpdateDTO {
  realName: string
  phone: string
  email?: string
  deptId?: number
  branchId?: number
}
