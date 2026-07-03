export interface ApprovalNodeVO {
  id: number
  nodeKey: string
  nodeName: string
  approverName?: string
  status: string
  result?: string
  comment?: string
  actionAt?: string
}

export interface ApprovalInstanceVO {
  id: number
  instanceNo: string
  formType: string
  formName?: string
  formData?: string
  applicantId: number
  applicantName: string
  status: string
  currentNode?: string
  priority: number
  createdAt: string
  nodes: ApprovalNodeVO[]
}

export interface ApprovalApplyDTO {
  formType: string
  formData: Record<string, unknown>
}

export interface ApprovalFlowVO {
  id: number
  formType: string
  formName: string
  processKey: string
  configJson: string
  version: number
  status: number
}

export interface ApprovalFlowSaveDTO {
  formType: string
  formName: string
  processKey: string
  configJson: string
}
