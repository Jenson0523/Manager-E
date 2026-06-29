export interface CompanyVO {
  id: number
  companyNo: string
  fullName: string
  shortName: string
  creditCode?: string
  adminName: string
  adminPhone: string
  adminEmail?: string
  remark?: string
  status: number
  createdAt: string
}

export interface CompanyCreateDTO {
  fullName: string
  shortName: string
  creditCode?: string
  adminName: string
  adminPhone: string
  adminEmail?: string
  adminUsername: string
  adminPassword: string
}

export interface CompanyUpdateDTO {
  fullName: string
  shortName: string
  creditCode?: string
  adminName?: string
  adminPhone?: string
  adminEmail?: string
  remark?: string
}
