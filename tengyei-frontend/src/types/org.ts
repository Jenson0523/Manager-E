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
  phone?: string
  maxUsers?: number
}
