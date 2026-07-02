export interface DeptTreeVO {
  id: number
  name: string
  code?: string
  parentId: number
  leaderId?: number
  sortOrder: number
  status: number
  children: DeptTreeVO[]
}

export interface DeptSaveDTO {
  name: string
  code?: string
  parentId?: number
  leaderId?: number
  sortOrder?: number
}

export interface BranchVO {
  id: number
  branchNo: string
  name: string
  type: string
  leaderId?: number
  phone?: string
  city?: string
  status: number
  deptId?: number
  deptName?: string
  deptIds: number[]
  deptNames: string[]
}

export interface BranchSaveDTO {
  branchNo: string
  name: string
  type?: string
  province?: string
  city?: string
  district?: string
  address?: string
  leaderId?: number
  deptId?: number
  phone?: string
  maxUsers?: number
}
