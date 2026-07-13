export interface ApprovalNodeVO {
  id: number
  nodeKey: string
  nodeName: string
  approverId?: number
  approverName?: string
  approverType?: string
  status: string
  result?: string
  comment?: string
  actionAt?: string
  dueAt?: string
  rejectPolicy?: string
}

export interface ApprovalInstanceVO {
  id: number
  instanceNo: string
  formType: string
  formName?: string
  formData?: string
  fieldsJson?: string
  applicantId: number
  applicantName: string
  status: string
  currentNode?: string
  priority: number
  createdAt: string
  myDueAt?: string
  warning?: string
  nodes: ApprovalNodeVO[]
}

export interface ApprovalApplyDTO {
  formType: string
  formData: Record<string, unknown>
  ccUserIds?: number[]
}

export interface FormField {
  key: string
  label: string
  type: 'text' | 'number' | 'date' | 'textarea' | 'select' | 'file'
  required?: boolean
  options?: string[]
  unit?: string
}

export interface ApprovalFlowVO {
  id: number
  formType: string
  formName: string
  processKey: string
  configJson: string
  fieldsJson?: string
  version: number
  status: number
}

export interface ApprovalFlowSaveDTO {
  formType: string
  formName: string
  processKey: string
  configJson: string
  fieldsJson?: string
}

export interface ApprovalDelegateVO {
  id?: number
  delegateId: number
  delegateName?: string
  startAt: string
  endAt: string
  status: number
}

export interface ApprovalFormTypeStat {
  formType: string
  formName: string
  total: number
  pending: number
  approved: number
  rejected: number
  canceled: number
  returned: number
  approvalRate: number
  avgDurationMinutes: number
}

export interface ApprovalApplicantStat {
  applicantId: number
  applicantName: string
  total: number
  pending: number
  approved: number
  rejected: number
  canceled: number
  returned: number
}

export interface ApprovalStatisticsVO {
  scope?: 'all' | 'self'
  total: number
  byStatus: Record<string, number>
  byFormType: Record<string, number>
  rejectionRate: number
  avgDurationMinutes: number
  todayCount: number
  weekCount: number
  overdueCount: number
  formTypeDetail: ApprovalFormTypeStat[]
  applicantDetail: ApprovalApplicantStat[]
  dailyTrend: Record<string, number>
}
