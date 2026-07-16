<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { approvalApi } from '@/api/approval'
import { commonApi } from '@/api/common'
import { useAuthStore } from '@/stores/auth'
import { useIsMobile } from '@/utils/responsive'
import { downloadExcel } from '@/utils/download'
import { refreshPermissionsThrottled } from '@/router'
import type { ApprovalInstanceVO, ApprovalNodeVO, ApprovalFlowVO, ApprovalStatisticsVO, ApprovalDelegateVO, FormField } from '@/types/approval'

const auth = useAuthStore()
const isMobile = useIsMobile()
// 平台层(tenant 0)与公司层权限码并行,任一命中即可
const canManage = computed(
  () => auth.hasPermission('PERM_approval:manage') || auth.hasPermission('PERM_platform:approval:manage')
)
const canApply = computed(
  () => auth.hasPermission('PERM_approval:apply') || auth.hasPermission('PERM_platform:approval:apply')
)
const canTransfer = computed(
  () => auth.hasPermission('PERM_approval:transfer') || auth.hasPermission('PERM_platform:approval:transfer')
)
const canDelegate = computed(
  () => auth.hasPermission('PERM_approval:delegate') || auth.hasPermission('PERM_platform:approval:delegate')
)
const canCancel = computed(
  () => auth.hasPermission('PERM_approval:cancel') || auth.hasPermission('PERM_platform:approval:cancel')
)
const canApprove = computed(
  () => auth.hasPermission('PERM_approval:approve') || auth.hasPermission('PERM_platform:approval:approve')
)
const canReject = computed(
  () => auth.hasPermission('PERM_approval:reject') || auth.hasPermission('PERM_platform:approval:reject')
)
const canView = computed(
  () => auth.hasPermission('PERM_approval:view') || auth.hasPermission('PERM_platform:approval:view')
)
const hasAnyApprovalPerm = computed(() => canView.value || canApply.value || canManage.value)
const isOverdue = (t?: string) => !!t && new Date(t) < new Date()

// 默认落在第一个有权限的 tab,避免权限不含"查看审批"时一进来就请求 /todo 报无权限
function firstPermittedTab(): string {
  if (canView.value) return 'todo'
  if (canApply.value) return 'my'
  if (canManage.value) return 'flows'
  return 'todo'
}
const activeTab = ref(firstPermittedTab())
const loading = ref(false)
const todoList = ref<ApprovalInstanceVO[]>([])
const myList = ref<ApprovalInstanceVO[]>([])
const doneList = ref<ApprovalInstanceVO[]>([])
const ccList = ref<ApprovalInstanceVO[]>([])
const flowList = ref<ApprovalFlowVO[]>([])
const stats = ref<ApprovalStatisticsVO | null>(null)

async function loadTab(tab: string) {
  // 页内切 tab 不经过路由跳转,router 的权限热刷新钩子不会触发,这里补一次(共用同一节流窗口)
  refreshPermissionsThrottled()
  loading.value = true
  try {
    // 每个 tab 先查权限再请求,避免权限不足时发出必 403 的请求(拦截器会全局弹错)
    if (tab === 'todo' && canView.value) todoList.value = await approvalApi.todo()
    else if (tab === 'my' && canApply.value) myList.value = await approvalApi.my()
    else if (tab === 'done' && canView.value) doneList.value = await approvalApi.done()
    else if (tab === 'cc' && canView.value) ccList.value = await approvalApi.cc()
    else if (tab === 'flows' && canManage.value) flowList.value = await approvalApi.flows()
    else if (tab === 'stats' && hasAnyApprovalPerm.value) {
      stats.value = await approvalApi.statistics()
      statsList.value = await approvalApi.listForStats()
    }
  } finally {
    loading.value = false
  }
}

type TagType = 'warning' | 'success' | 'danger' | 'info'
function statusTag(status: string): TagType {
  const map: Record<string, TagType> = {
    PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELED: 'info', RETURNED: 'warning',
  }
  return map[status] ?? 'info'
}
function fmtTime(t?: string) {
  return t ? String(t).replace('T', ' ') : ''
}
function statusLabel(status: string) {
  return {
    PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', CANCELED: '已撤销', RETURNED: '已退回',
  }[status as string] ?? status
}

/* 发起审批：按流程定义的字段动态渲染,无字段定义时降级 JSON 输入 */
const applyDialog = ref(false)
const applyForms = ref<ApprovalFlowVO[]>([])
const applyFormType = ref('')
const applyFields = ref<FormField[]>([])
const applyValues = reactive<Record<string, unknown>>({})
const applyJsonFallback = ref('{}')
/* 多部门员工:发起时选提交部门,决定"部门负责人"审批走谁。单/无部门不显示此项 */
const myDepts = ref<{ id: number; name: string }[]>([])
const applyDeptId = ref<number>()

function parseFields(json?: string): FormField[] {
  try {
    const arr = json ? JSON.parse(json) : []
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}
function onApplyTypeChange() {
  const f = applyForms.value.find((x) => x.formType === applyFormType.value)
  applyFields.value = parseFields(f?.fieldsJson)
  Object.keys(applyValues).forEach((k) => delete applyValues[k])
  applyJsonFallback.value = '{}'
  applyCcUserIds.value = []
}
const applyCcUserIds = ref<number[]>([])
function fileFieldName(key: string): string {
  const v = applyValues[key] as { name?: string } | undefined
  return v && typeof v === 'object' ? (v.name ?? '') : ''
}
function splitLabelUnit(label: string): { label: string; unit: string } {
  const match = label.match(/^(.+?)[(（](?:单位[/]?)?([^)）]+)[)）]$/)
  if (match) return { label: match[1].trim(), unit: match[2].trim() }
  return { label, unit: '' }
}
function fieldLabel(f: FormField): string {
  if (f.type === 'number') return splitLabelUnit(f.label).label
  return f.label
}
function fieldUnit(f: FormField): string {
  if (f.type !== 'number') return ''
  return (f.unit ?? '').trim() || splitLabelUnit(f.label).unit
}
async function onFieldUpload(opt: UploadRequestOptions, key: string) {
  const res = await approvalApi.uploadFile(opt.file as File)
  applyValues[key] = res
  ElMessage.success('附件已上传')
}
async function openApply() {
  applyForms.value = await approvalApi.forms()
  if (!applyForms.value.length) {
    ElMessage.warning('暂无可用审批流程,请联系管理员配置')
    return
  }
  if (!userOptions.value.length) await loadFlowRefs()
  // 多部门员工:拉本人部门,>=2 个时需选提交部门,默认主部门(第一个)
  myDepts.value = await approvalApi.myDepts()
  applyDeptId.value = myDepts.value.length ? myDepts.value[0].id : undefined
  applyCcUserIds.value = []
  resubmitId.value = null
  applyFormType.value = applyForms.value[0].formType
  onApplyTypeChange()
  applyDialog.value = true
}
/* 重新提交(被退回的单):复用发起表单渲染,预填原表单数据 */
const resubmitId = ref<number | null>(null)
async function submitApply() {
  let formData: Record<string, unknown>
  if (applyFields.value.length) {
    for (const f of applyFields.value) {
      const v = applyValues[f.key]
      if (f.required && (v === undefined || v === null || v === ''
          || (f.type === 'file' && !(v as { url?: string })?.url))) {
        ElMessage.error(`请填写「${f.label}」`)
        return
      }
    }
    formData = { ...applyValues }
  } else {
    try {
      formData = JSON.parse(applyJsonFallback.value)
    } catch {
      ElMessage.error('表单数据不是合法 JSON')
      return
    }
  }
  if (resubmitId.value != null) {
    await approvalApi.resubmit(resubmitId.value, formData)
    ElMessage.success('已重新提交')
  } else {
    if (myDepts.value.length >= 2 && !applyDeptId.value) {
      ElMessage.error('请选择提交部门')
      return
    }
    await approvalApi.apply({
      formType: applyFormType.value, formData, ccUserIds: applyCcUserIds.value,
      deptId: myDepts.value.length >= 2 ? applyDeptId.value : undefined,
    })
    ElMessage.success('已提交审批')
  }
  resubmitId.value = null
  applyDialog.value = false
  detailDialog.value = false
  loadTab('my')
}
async function exportApprovals() {
  await downloadExcel('/v1/approval/export', {}, `审批记录_${new Date().toISOString().slice(0, 10)}.xlsx`)
  ElMessage.success('导出成功')
}
async function openResubmit() {
  if (!detail.value) return
  applyForms.value = await approvalApi.forms()
  applyFormType.value = detail.value.formType
  onApplyTypeChange()
  try {
    Object.assign(applyValues, JSON.parse(detail.value.formData || '{}'))
    applyJsonFallback.value = detail.value.formData || '{}'
  } catch { /* 保持空表单 */ }
  resubmitId.value = detail.value.id
  applyDialog.value = true
}

/* 审批详情/处理 */
const detailDialog = ref(false)
const detail = ref<ApprovalInstanceVO | null>(null)
const actComment = ref('')

/* 表单数据结构化展示:根据 fieldsJson 解析为 {label, value} 数组 */
const detailFormFields = computed(() => {
  if (!detail.value?.formData) return []
  let data: Record<string, unknown> = {}
  try { data = JSON.parse(detail.value.formData) } catch { return [] }
  // 优先用流程定义的字段配置(有 label 映射)
  if (detail.value.fieldsJson) {
    try {
      const fields = JSON.parse(detail.value.fieldsJson) as FormField[]
      if (Array.isArray(fields) && fields.length) {
        return fields.map(f => {
          const raw = data[f.key]
          if (f.type === 'file' && raw && typeof raw === 'object') {
            const o = raw as { url?: string; name?: string }
            return { label: f.label, value: o.name ?? '附件', url: o.url }
          }
          return { label: f.label, value: formatFieldValue(raw, f.type), url: undefined as string | undefined }
        }).filter(f => f.value !== '' && f.value !== undefined && f.value !== null)
      }
    } catch { /* fall through to raw */ }
  }
  // 降级:无字段定义时直接按 key->value 展示
  return Object.entries(data).map(([k, v]) => ({ label: k, value: String(v), url: undefined as string | undefined }))
})
function formatFieldValue(val: unknown, type: string): string {
  if (val === null || val === undefined) return ''
  if (type === 'date') return String(val)
  if (type === 'number') return String(val)
  return String(val)
}

/* 节点状态标签映射 */
function nodeStatusLabel(status: string, approverType?: string): string {
  if (approverType === 'CC') {
    return { APPROVED: '已抄送', REJECTED: '抄送失败', APPROVING: '抄送中', WAITING: '待抄送', CANCELED: '已取消' }[status] ?? status
  }
  return { APPROVED: '已通过', REJECTED: '已驳回', APPROVING: '审批中', WAITING: '等待中', CANCELED: '已取消' }[status] ?? status
}
function isCcNode(n: ApprovalNodeVO): boolean {
  return n.approverType === 'CC'
}

async function openDetail(id: number) {
  detail.value = await approvalApi.detail(id)
  actComment.value = ''
  detailDialog.value = true
}
async function act(action: 'APPROVE' | 'REJECT') {
  if (!detail.value) return
  await approvalApi.act(detail.value.id, action, actComment.value)
  ElMessage.success(action === 'APPROVE' ? '已通过' : '已驳回')
  detailDialog.value = false
  loadTab(activeTab.value)
}

/* 撤回(申请人本人) */
const isMyApply = computed(() => detail.value?.applicantId === auth.userInfo?.userId)
async function cancelInstance() {
  if (!detail.value) return
  await ElMessageBox.confirm('确认撤回该审批？撤回后需重新发起。', '提示', { type: 'warning' })
  await approvalApi.cancel(detail.value.id)
  ElMessage.success('已撤回')
  detailDialog.value = false
  loadTab(activeTab.value)
}

/* 催办:发起人/流程管理员提醒当前审批人,后端每单每小时限一次 */
async function urgeInstance() {
  if (!detail.value) return
  await approvalApi.urge(detail.value.id)
  ElMessage.success('已催办,已通知当前审批人')
}

/* 打印:新窗口渲染审批单(基本信息+表单+流转记录)后调浏览器打印 */
function printDetail() {
  const d = detail.value
  if (!d) return
  const esc = (s: unknown) =>
    String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const fields = detailFormFields.value
    .map((f) => `<tr><td class="k">${esc(f.label)}</td><td>${esc(f.value)}</td></tr>`)
    .join('')
  const nodeStatus = (s: string) =>
    ({ APPROVING: '审批中', WAITING: '待流转', APPROVED: '已通过', REJECTED: '已驳回', CANCELED: '已撤销' }[s] ?? s)
  const nodes = (d.nodes ?? [])
    .filter((n) => n.approverType !== 'CC')
    .map((n) => `<tr><td>${esc(n.nodeName)}</td><td>${esc(n.approverName ?? '')}</td>` +
      `<td>${esc(nodeStatus(n.status))}</td>` +
      `<td>${esc(fmtTime(n.actionAt))}</td><td>${esc(n.comment ?? '')}</td></tr>`)
    .join('')
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>审批单 ${esc(d.instanceNo)}</title>
    <style>
      body{font-family:"Microsoft YaHei",sans-serif;padding:24px;color:#333}
      h2{text-align:center;margin-bottom:4px} .sub{text-align:center;color:#888;margin-bottom:20px;font-size:13px}
      table{width:100%;border-collapse:collapse;margin-bottom:18px}
      td,th{border:1px solid #ccc;padding:6px 10px;font-size:13px;text-align:left}
      td.k{width:130px;background:#f7f7f7} h4{margin:14px 0 6px}
    </style></head><body>
    <h2>${esc(d.formName || d.formType)}</h2>
    <div class="sub">单号:${esc(d.instanceNo)}　状态:${esc(statusLabel(d.status))}</div>
    <table>
      <tr><td class="k">申请人</td><td>${esc(d.applicantName)}</td></tr>
      <tr><td class="k">申请时间</td><td>${esc(fmtTime(d.createdAt))}</td></tr>
      ${fields}
    </table>
    <h4>流转记录</h4>
    <table><tr><th>节点</th><th>审批人</th><th>状态</th><th>处理时间</th><th>意见</th></tr>${nodes}</table>
    </body></html>`
  const win = window.open('', '_blank', 'width=800,height=900')
  if (!win) { ElMessage.error('浏览器拦截了打印窗口,请允许弹窗后重试'); return }
  win.document.write(html)
  win.document.close()
  win.focus()
  win.print()
}

/* 转交 */
const isMyTurn = computed(() =>
  detail.value?.nodes.some(
    (n) => n.status === 'APPROVING' && n.approverType !== 'CC' && n.approverId === auth.userInfo?.userId
  ) ?? false
)
const transferDialog = ref(false)
const transferTarget = ref<number>()
/* 管理员代为转交:审批人离职/停用导致单据卡死时的兜底。需指明转交谁的审批 */
const isManageTransfer = computed(() => !isMyTurn.value && canManage.value)
const transferFrom = ref<number>()
const currentApprovers = computed(() =>
  (detail.value?.nodes ?? [])
    .filter((n) => n.status === 'APPROVING' && n.approverType !== 'CC' && n.approverId)
    .map((n) => ({ id: n.approverId as number, name: n.approverName || String(n.approverId) }))
)
async function openTransfer() {
  transferTarget.value = undefined
  transferFrom.value = currentApprovers.value.length === 1 ? currentApprovers.value[0].id : undefined
  if (!userOptions.value.length) await loadFlowRefs()
  transferDialog.value = true
}
async function submitTransfer() {
  if (!detail.value || !transferTarget.value) {
    ElMessage.error('请选择转交对象')
    return
  }
  if (isManageTransfer.value && !transferFrom.value) {
    ElMessage.error('请选择要转交谁的审批')
    return
  }
  await approvalApi.transfer(detail.value.id, transferTarget.value,
    isManageTransfer.value ? transferFrom.value : undefined)
  ElMessage.success('已转交')
  transferDialog.value = false
  detailDialog.value = false
  loadTab(activeTab.value)
}

/* 加签:当前审批人插入一位审批人(前=其先审再回自己;后=己审后其再审) */
const addSignDialog = ref(false)
const addSignTarget = ref<number>()
const addSignPosition = ref<'PRE' | 'POST'>('POST')
async function openAddSign() {
  addSignTarget.value = undefined
  addSignPosition.value = 'POST'
  if (!userOptions.value.length) await loadFlowRefs()
  addSignDialog.value = true
}
async function submitAddSign() {
  if (!detail.value || !addSignTarget.value) {
    ElMessage.error('请选择加签人')
    return
  }
  await approvalApi.addSign(detail.value.id, addSignTarget.value, addSignPosition.value)
  ElMessage.success('已加签')
  addSignDialog.value = false
  detailDialog.value = false
  loadTab(activeTab.value)
}

/* 代理设置(一人一条,休假期间由代理人审批) */
const delegateDialog = ref(false)
const delegateForm = reactive<ApprovalDelegateVO>({ delegateId: 0, startAt: '', endAt: '', status: 1 })
async function openDelegate() {
  if (!userOptions.value.length) await loadFlowRefs()
  const cur = await approvalApi.delegateGet()
  if (cur) Object.assign(delegateForm, {
    delegateId: cur.delegateId, startAt: cur.startAt, endAt: cur.endAt, status: cur.status,
  })
  else Object.assign(delegateForm, { delegateId: 0, startAt: '', endAt: '', status: 1 })
  delegateDialog.value = true
}
async function submitDelegate() {
  if (!delegateForm.delegateId || !delegateForm.startAt || !delegateForm.endAt) {
    ElMessage.error('请选择代理人和起止时间')
    return
  }
  await approvalApi.delegateSave({ ...delegateForm })
  ElMessage.success('代理设置已保存')
  delegateDialog.value = false
}

/* 流程管理：纵向流程图式设计器 */
interface NodeDraft {
  name: string
  approverType: string
  resolveMode: string
  condition: string
  targetUserId?: number
  targetRoleId?: number
  timeoutHours?: number
  rejectPolicy?: string
  ccUserIds?: number[]
}
const REJECT_POLICIES = [
  { value: 'TERMINATE', label: '驳回即终结' },
  { value: 'TO_INITIATOR', label: '退回发起人(可重新提交)' },
  { value: 'TO_PREV', label: '退回上一节点重审' },
]
const REJECT_POLICY_SHORT: Record<string, string> = {
  TO_INITIATOR: '驳回退发起人',
  TO_PREV: '驳回退上一节点',
}
function approverTypeLabel(t: string) {
  return APPROVER_TYPES.find((x) => x.value === t)?.label ?? t
}
function approverTypeIcon(t: string) {
  return APPROVER_TYPES.find((x) => x.value === t)?.icon ?? '📋'
}
function approverTypeColor(t: string) {
  return APPROVER_TYPES.find((x) => x.value === t)?.color ?? '#909399'
}
function nodeTargetName(n: NodeDraft) {
  if (n.approverType === 'CC') {
    if (n.ccUserIds && n.ccUserIds.length) {
      return n.ccUserIds.map((id) => userOptions.value.find((u) => u.id === id)?.name ?? '').filter(Boolean).join(', ')
    }
    if (n.targetRoleId) return roleOptions.value.find((r) => r.id === n.targetRoleId)?.name ?? ''
    return ''
  }
  if (n.approverType === 'SPECIFIC_USER') return userOptions.value.find((u) => u.id === n.targetUserId)?.name ?? ''
  if (n.approverType === 'ROLE') return roleOptions.value.find((r) => r.id === n.targetRoleId)?.name ?? ''
  return ''
}
/* 节点配置弹窗(点流程图卡片打开) */
const nodeConfigDialog = ref(false)
const editingNodeIndex = ref(0)
const editingNode = computed(() => flowNodes.value[editingNodeIndex.value])

// Condition editing state — use raw ref instead of computed-to-condition-string,
// so partial edits (field selected but value not typed yet) are never wiped.
const _condField = ref('')
const _condOp = ref('>=')
const _condValue = ref('')
const editingNodeConditionField = computed({
  get: () => _condField.value,
  set: (v) => { _condField.value = v; syncCondition() },
})
const editingNodeConditionOp = computed({
  get: () => _condOp.value,
  set: (v) => { _condOp.value = v || '>='; syncCondition() },
})
const editingNodeConditionValue = computed({
  get: () => _condValue.value,
  set: (v) => { _condValue.value = v; syncCondition() },
})
function syncCondition() {
  const f = _condField.value, o = _condOp.value || '>=', v = _condValue.value
  editingNode.value.condition = (f && o && (v != null && v !== ''))
    ? `{${f}} ${o} ${v}` : ''
}
function loadConditionIntoEditors(cond?: string) {
  const p = parseCondition(cond)
  _condField.value = p.field
  _condOp.value = p.op
  _condValue.value = p.value
}
function parseCondition(cond?: string): { field: string; op: string; value: string } {
  if (!cond) return { field: '', op: '>=', value: '' }
  const s = cond.trim()
  // Match {field} op value or field op value or form.field op value
  const m = s.match(/^\{?(?:form\.)?([^\s{}<>=!]+)\}?\s*(>=|<=|==|!=|>|<)\s*(.+)$/)
  if (m) return { field: m[1], op: m[2], value: m[3] }
  return { field: '', op: '>=', value: '' }
}
function openNodeConfig(i: number) {
  editingNodeIndex.value = i
  // Ensure CC nodes have proper defaults
  const n = flowNodes.value[i]
  if (n.approverType === 'CC' && !n.resolveMode) {
    n.resolveMode = 'FIRST'
  }
  loadConditionIntoEditors(n.condition)
  nodeConfigDialog.value = true
}
function onCcModeChange() {
  // Clear fields when switching CC mode
  if (editingNode.value.resolveMode === 'ALL') {
    editingNode.value.ccUserIds = undefined
  } else {
    editingNode.value.targetRoleId = undefined
  }
}
function insertNodeAt(i: number) {
  flowNodes.value.splice(i, 0, blankNode())
  openNodeConfig(i)
}
function removeNodeAt(i: number) {
  if (flowNodes.value.length === 1) {
    ElMessage.warning('至少保留一个审批节点')
    return
  }
  flowNodes.value.splice(i, 1)
}
const APPROVER_TYPES = [
  { value: 'LEADER', label: '直属上级', icon: '👑', color: '#409eff' },
  { value: 'DEPT_LEADER', label: '部门负责人', icon: '🏷️', color: '#67c23a' },
  { value: 'SPECIFIC_USER', label: '指定人员', icon: '👤', color: '#e6a23c' },
  { value: 'ROLE', label: '指定角色', icon: '🔑', color: '#f56c6c' },
  { value: 'CC', label: '抄送', icon: '📧', color: '#9b59b6' },
  { value: 'SELF_APPROVE', label: '自动通过', icon: '⚡', color: '#909399' },
]
interface FieldDraft {
  key: string
  label: string
  type: string
  required: boolean
  optionsText: string
  unit: string
}
const FIELD_TYPES = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'select', label: '下拉选择' },
  { value: 'file', label: '附件' },
]
const flowDialog = ref(false)
const flowForm = reactive({ formType: '', formName: '', processKey: '' })
const flowNodes = ref<NodeDraft[]>([])
const flowFields = ref<FieldDraft[]>([])
const userOptions = ref<{ id: number; name: string }[]>([])
const roleOptions = ref<{ id: number; name: string }[]>([])

function blankNode(): NodeDraft {
  return { name: '', approverType: 'LEADER', resolveMode: 'FIRST', condition: '', rejectPolicy: 'TERMINATE' }
}
function flowNodeSummary(f: ApprovalFlowVO): string {
  try {
    const parsed = JSON.parse(f.configJson) as { nodes: { name: string }[] }
    return (parsed.nodes ?? []).map(n => n.name).join(' → ')
  } catch {
    return ''
  }
}
async function loadFlowRefs() {
  // 走全站 /common/options 名录接口(登录即可),不依赖 user:view/role:view 管理权限,
  // 否则只有审批权限的普通员工点"发起审批"会连弹无权限访问且对话框打不开
  const opts = await commonApi.options()
  roleOptions.value = opts.roles.map((r) => ({ id: r.id, name: r.name }))
  userOptions.value = opts.users.map((u) => ({ id: u.id, name: u.realName }))
}
function blankField(): FieldDraft {
  return { key: '', label: '', type: 'text', required: false, optionsText: '', unit: '' }
}
function openFlowCreate() {
  flowForm.formType = ''
  flowForm.formName = ''
  flowForm.processKey = ''
  flowNodes.value = [blankNode()]
  flowFields.value = []
  loadFlowRefs()
  flowDialog.value = true
}
function openFlowEdit(f: ApprovalFlowVO) {
  flowForm.formType = f.formType
  flowForm.formName = f.formName
  flowForm.processKey = f.processKey
  try {
    const parsed = JSON.parse(f.configJson) as { nodes: Record<string, unknown>[] }
    flowNodes.value = (parsed.nodes ?? []).map((n) => ({
      name: (n.name as string) ?? '',
      approverType: (n.approverType as string) ?? 'LEADER',
      resolveMode: (n.resolveMode as string) ?? 'FIRST',
      condition: (n.condition as string) ?? '',
      targetUserId: n.targetUserId as number | undefined,
      targetRoleId: n.targetRoleId as number | undefined,
      timeoutHours: n.timeoutHours as number | undefined,
      rejectPolicy: (n.rejectPolicy as string) ?? 'TERMINATE',
      ccUserIds: (n.ccUserIds as number[]) ?? undefined,
    }))
  } catch {
    flowNodes.value = [blankNode()]
  }
  flowFields.value = parseFields(f.fieldsJson).map((x) => ({
    key: x.key,
    label: x.label,
    type: x.type,
    required: !!x.required,
    optionsText: (x.options ?? []).join(','),
    unit: x.unit ?? '',
  }))
  loadFlowRefs()
  flowDialog.value = true
}
async function submitFlow() {
  for (const [i, n] of flowNodes.value.entries()) {
    if (!n.name) { ElMessage.error(`第 ${i + 1} 个节点缺少名称`); return }
    if (n.approverType === 'SPECIFIC_USER' && !n.targetUserId) { ElMessage.error(`第 ${i + 1} 个节点需选择指定人员`); return }
    if (n.approverType === 'ROLE' && !n.targetRoleId) { ElMessage.error(`第 ${i + 1} 个节点需选择角色`); return }
    if (n.approverType === 'CC' && !(n.ccUserIds && n.ccUserIds.length) && !n.targetRoleId) { ElMessage.error(`第 ${i + 1} 个节点(抄送)需选择抄送人或角色`); return }
  }
  for (const [i, f] of flowFields.value.entries()) {
    if (!f.key || !f.label) { ElMessage.error(`第 ${i + 1} 个字段需填写标识和名称`); return }
    if (f.type === 'select' && !f.optionsText.trim()) { ElMessage.error(`字段「${f.label}」需填写选项`); return }
  }
  const fieldsJson = flowFields.value.length
    ? JSON.stringify(flowFields.value.map((f) => ({
        key: f.key,
        label: f.label,
        type: f.type,
        required: f.required || undefined,
        options: f.type === 'select' ? f.optionsText.split(',').map((s) => s.trim()).filter(Boolean) : undefined,
        unit: f.type === 'number' && f.unit?.trim() ? f.unit.trim() : undefined,
      })))
    : undefined
  const configJson = JSON.stringify({
    nodes: flowNodes.value.map((n, i) => ({
      key: `node_${i + 1}`,
      name: n.name,
      approverType: n.approverType,
      resolveMode: n.approverType === 'ROLE' ? n.resolveMode : 'FIRST',
      orderBy: i + 1,
      condition: n.condition || null,
      targetUserId: n.approverType === 'SPECIFIC_USER' ? n.targetUserId : undefined,
      targetRoleId: (n.approverType === 'ROLE' || n.approverType === 'CC') ? n.targetRoleId : undefined,
      timeoutHours: n.timeoutHours || undefined,
      rejectPolicy: n.rejectPolicy && n.rejectPolicy !== 'TERMINATE' ? n.rejectPolicy : undefined,
      ccUserIds: n.approverType === 'CC' && n.ccUserIds && n.ccUserIds.length ? n.ccUserIds : undefined,
    })),
  })
  await approvalApi.saveFlow({ ...flowForm, configJson, fieldsJson })
  ElMessage.success('已保存')
  flowDialog.value = false
  loadTab('flows')
}
async function toggleFlow(f: ApprovalFlowVO) {
  const next = f.status === 1 ? 0 : 1
  await ElMessageBox.confirm(`确认${next === 1 ? '启用' : '停用'}「${f.formName}」流程？`, '提示', { type: 'warning' })
  await approvalApi.toggleFlowStatus(f.id, next)
  ElMessage.success('已更新')
  loadTab('flows')
}

onMounted(() => loadTab('todo'))

/* Statistics helpers */
const statsList = ref<ApprovalInstanceVO[]>([])
const statsFilter = ref('')

function currentNodeName(row: ApprovalInstanceVO): string {
  if (!row.nodes || row.nodes.length === 0) return ''
  if (row.currentNode) {
    const node = row.nodes.find((n) => n.nodeKey === row.currentNode)
    if (node) return node.nodeName
  }
  return ''
}
const filteredStatsList = computed(() => {
  if (!statsFilter.value) return statsList.value
  const f = statsFilter.value.toLowerCase()
  return statsList.value.filter((r) =>
    (r.instanceNo ?? '').toLowerCase().includes(f) ||
    (r.formName ?? r.formType ?? '').toLowerCase().includes(f) ||
    (r.applicantName ?? '').toLowerCase().includes(f) ||
    (r.status ?? '').toLowerCase().includes(f)
  )
})
async function refreshStats() {
  loading.value = true
  try {
    stats.value = await approvalApi.statistics()
    statsList.value = await approvalApi.listForStats()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-tooltip :disabled="canApply" content="无发起审批权限,请联系管理员分配" placement="top">
        <el-button type="primary" :disabled="!canApply" @click="openApply">发起审批</el-button>
      </el-tooltip>
      <el-tooltip :disabled="canDelegate" content="无审批代理权限,请联系管理员分配" placement="top">
        <el-button :disabled="!canDelegate" @click="openDelegate">代理设置</el-button>
      </el-tooltip>
    </div>

    <el-tabs v-model="activeTab" @tab-change="(t) => loadTab(t as string)">
      <el-tab-pane :disabled="!canView" label="我的待办" name="todo">
        <div v-if="isMobile" v-loading="loading" class="m-list">
          <div v-for="row in todoList" :key="row.id" class="m-card" @click="openDetail(row.id)">
            <div class="m-card-head">
              <span class="m-card-title">{{ row.formType }}</span>
              <el-tag v-if="isOverdue(row.myDueAt)" type="danger" size="small">已超时</el-tag>
            </div>
            <div class="m-card-line">{{ row.instanceNo }}</div>
            <div class="m-card-line">申请人:{{ row.applicantName }} · {{ fmtTime(row.createdAt) }}</div>
          </div>
          <el-empty v-if="!todoList.length" description="暂无待办" :image-size="60" />
        </div>
        <el-table v-else v-loading="loading" :data="todoList" stripe>
          <el-table-column prop="instanceNo" label="单号" width="180" />
          <el-table-column label="表单类型" width="140">
            <template #default="{ row }">
              {{ (row as ApprovalInstanceVO).formName || (row as ApprovalInstanceVO).formType }}
            </template>
          </el-table-column>
          <el-table-column prop="applicantName" label="申请人" width="120" />
          <el-table-column label="申请时间" width="170">
            <template #default="{ row }">{{ fmtTime((row as ApprovalInstanceVO).createdAt) }}</template>
          </el-table-column>
          <el-table-column label="时限" width="100">
            <template #default="{ row }">
              <el-tag v-if="isOverdue((row as ApprovalInstanceVO).myDueAt)" type="danger" size="small">已超时</el-tag>
              <span v-else-if="(row as ApprovalInstanceVO).myDueAt" style="color: #909399; font-size: 12px">正常</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail((row as ApprovalInstanceVO).id)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :disabled="!canApply" label="我已发起" name="my">
        <div v-if="isMobile" v-loading="loading" class="m-list">
          <div v-for="row in myList" :key="row.id" class="m-card" @click="openDetail(row.id)">
            <div class="m-card-head">
              <span class="m-card-title">{{ row.formType }}</span>
              <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </div>
            <div class="m-card-line">{{ row.instanceNo }}</div>
            <div class="m-card-line">{{ fmtTime(row.createdAt) }}</div>
          </div>
          <el-empty v-if="!myList.length" description="暂无记录" :image-size="60" />
        </div>
        <el-table v-else v-loading="loading" :data="myList" stripe>
          <el-table-column prop="instanceNo" label="单号" width="180" />
          <el-table-column label="表单类型" width="140">
            <template #default="{ row }">
              {{ (row as ApprovalInstanceVO).formName || (row as ApprovalInstanceVO).formType }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag((row as ApprovalInstanceVO).status)">
                {{ statusLabel((row as ApprovalInstanceVO).status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentNode" label="当前节点" width="140" />
          <el-table-column label="申请时间" width="170">
            <template #default="{ row }">{{ fmtTime((row as ApprovalInstanceVO).createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail((row as ApprovalInstanceVO).id)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :disabled="!canView" label="我已审批" name="done">
        <div v-if="isMobile" v-loading="loading" class="m-list">
          <div v-for="row in doneList" :key="row.id" class="m-card" @click="openDetail(row.id)">
            <div class="m-card-head">
              <span class="m-card-title">{{ row.formType }}</span>
              <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </div>
            <div class="m-card-line">{{ row.instanceNo }}</div>
            <div class="m-card-line">申请人:{{ row.applicantName }}</div>
          </div>
          <el-empty v-if="!doneList.length" description="暂无记录" :image-size="60" />
        </div>
        <el-table v-else v-loading="loading" :data="doneList" stripe>
          <el-table-column prop="instanceNo" label="单号" width="180" />
          <el-table-column label="表单类型" width="140">
            <template #default="{ row }">
              {{ (row as ApprovalInstanceVO).formName || (row as ApprovalInstanceVO).formType }}
            </template>
          </el-table-column>
          <el-table-column prop="applicantName" label="申请人" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag((row as ApprovalInstanceVO).status)">
                {{ statusLabel((row as ApprovalInstanceVO).status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail((row as ApprovalInstanceVO).id)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :disabled="!canView" label="抄送我" name="cc">
        <div v-if="isMobile" v-loading="loading" class="m-list">
          <div v-for="row in ccList" :key="row.id" class="m-card" @click="openDetail(row.id)">
            <div class="m-card-head">
              <span class="m-card-title">{{ row.formName || row.formType }}</span>
              <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </div>
            <div class="m-card-line">{{ row.instanceNo }}</div>
            <div class="m-card-line">申请人:{{ row.applicantName }} · {{ fmtTime(row.createdAt) }}</div>
          </div>
          <el-empty v-if="!ccList.length" description="暂无抄送" :image-size="60" />
        </div>
        <el-table v-else v-loading="loading" :data="ccList" stripe>
          <el-table-column prop="instanceNo" label="单号" width="180" />
          <el-table-column label="表单类型" width="140">
            <template #default="{ row }">
              {{ (row as ApprovalInstanceVO).formName || (row as ApprovalInstanceVO).formType }}
            </template>
          </el-table-column>
          <el-table-column prop="applicantName" label="申请人" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag((row as ApprovalInstanceVO).status)">
                {{ statusLabel((row as ApprovalInstanceVO).status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="申请时间" width="170">
            <template #default="{ row }">{{ fmtTime((row as ApprovalInstanceVO).createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail((row as ApprovalInstanceVO).id)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="hasAnyApprovalPerm" label="统计" name="stats">
        <template v-if="stats">
          <!-- Compact summary bar -->
          <div class="stats-summary-bar">
            <el-tag size="small" :type="stats.scope === 'all' ? 'success' : 'info'" style="margin-right: 4px">
              {{ stats.scope === 'all' ? '全公司数据' : '与我相关' }}
            </el-tag>
            <span class="summary-item">总数 <b>{{ stats.total }}</b></span>
            <span class="summary-item" style="color:#e6a23c">审批中 <b>{{ stats.byStatus.PENDING ?? 0 }}</b></span>
            <span class="summary-item" style="color:#67c23a">已通过 <b>{{ stats.byStatus.APPROVED ?? 0 }}</b></span>
            <span class="summary-item" style="color:#f56c6c">已驳回 <b>{{ stats.byStatus.REJECTED ?? 0 }}</b></span>
            <span class="summary-item" style="color:#909399">已撤销 <b>{{ stats.byStatus.CANCELED ?? 0 }}</b></span>
            <span class="summary-item">通过率 <b>{{ stats.total > 0 ? Math.round((stats.byStatus.APPROVED ?? 0) * 1000.0 / stats.total) / 10.0 : 0 }}%</b></span>
          </div>

          <!-- Toolbar -->
          <div class="stats-toolbar">
            <el-input v-model="statsFilter" placeholder="搜索单号/表单/申请人/状态" clearable size="small" style="width: 260px" />
            <div>
              <el-button size="small" @click="refreshStats">刷新</el-button>
              <el-tooltip :disabled="canManage" content="无审批管理权限,请联系管理员分配" placement="top">
                <el-button size="small" :disabled="!canManage" @click="exportApprovals">导出</el-button>
              </el-tooltip>
            </div>
          </div>

          <!-- Instance list table -->
          <el-table v-loading="loading" :data="filteredStatsList" stripe size="small" max-height="600">
            <el-table-column prop="instanceNo" label="单号" width="160" />
            <el-table-column label="表单类型" min-width="120">
              <template #default="{ row }">
                {{ (row as ApprovalInstanceVO).formName || (row as ApprovalInstanceVO).formType }}
              </template>
            </el-table-column>
            <el-table-column prop="applicantName" label="申请人" width="90" />
            <el-table-column label="当前节点" width="120">
              <template #default="{ row }">
                <span v-if="(row as ApprovalInstanceVO).status === 'PENDING'" style="color:#e6a23c">{{ currentNodeName(row as ApprovalInstanceVO) || '-' }}</span>
                <span v-else style="color:#909399">-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="statusTag((row as ApprovalInstanceVO).status)" size="small">
                  {{ statusLabel((row as ApprovalInstanceVO).status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="申请时间" width="160" />
            <el-table-column label="操作" width="70" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openDetail((row as ApprovalInstanceVO).id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!loading && filteredStatsList.length === 0" class="empty-tip">暂无数据</div>
        </template>
      </el-tab-pane>

      <el-tab-pane :disabled="!canManage" label="流程管理" name="flows">
        <div class="flow-toolbar">
          <el-button type="primary" size="small" :disabled="!canManage" @click="openFlowCreate">新建流程</el-button>
        </div>
        <el-table v-loading="loading" :data="flowList" stripe>
          <el-table-column prop="formType" label="表单类型" width="100" />
          <el-table-column prop="formName" label="名称" width="120" />
          <el-table-column label="审批节点" min-width="200">
            <template #default="{ row }">
              <span class="flow-summary">{{ flowNodeSummary(row as ApprovalFlowVO) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="70" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="(row as ApprovalFlowVO).status === 1 ? 'success' : 'info'" size="small">
                {{ (row as ApprovalFlowVO).status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="!canManage" @click="openFlowEdit(row as ApprovalFlowVO)">编辑</el-button>
              <el-button link type="primary" :disabled="!canManage" @click="toggleFlow(row as ApprovalFlowVO)">
                {{ (row as ApprovalFlowVO).status === 1 ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 发起审批 -->
    <el-dialog v-model="applyDialog" title="发起审批" width="520px" :fullscreen="isMobile">
      <el-form label-width="100px">
        <el-form-item label="审批类型">
          <el-select v-model="applyFormType" style="width: 100%" @change="onApplyTypeChange">
            <el-option v-for="f in applyForms" :key="f.formType" :label="f.formName" :value="f.formType" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="myDepts.length >= 2" label="提交部门" required>
          <el-select v-model="applyDeptId" style="width: 100%" placeholder="以哪个部门身份提交(决定部门负责人)">
            <el-option v-for="d in myDepts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <template v-if="applyFields.length">
          <el-form-item v-for="f in applyFields" :key="f.key" :label="fieldLabel(f)" :required="f.required">
            <el-input v-if="f.type === 'text'" v-model="applyValues[f.key] as string" />
            <el-input v-else-if="f.type === 'textarea'" v-model="applyValues[f.key] as string" type="textarea" :rows="3" />
            <div v-else-if="f.type === 'number'" class="number-with-unit">
              <el-input-number v-model="applyValues[f.key] as number" style="flex: 1" :controls-position="'right'" />
              <span v-if="fieldUnit(f)" class="field-unit">{{ fieldUnit(f) }}</span>
            </div>
            <el-date-picker v-else-if="f.type === 'date'" v-model="applyValues[f.key] as string" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            <el-select v-else-if="f.type === 'select'" v-model="applyValues[f.key] as string" style="width: 100%">
              <el-option v-for="o in f.options ?? []" :key="o" :label="o" :value="o" />
            </el-select>
            <template v-else-if="f.type === 'file'">
              <el-upload :show-file-list="false" :http-request="(opt: UploadRequestOptions) => onFieldUpload(opt, f.key)"
                accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar">
                <el-button size="small">{{ fileFieldName(f.key) ? '重新上传' : '上传附件' }}</el-button>
              </el-upload>
              <span v-if="fileFieldName(f.key)" class="upload-file-name">
                {{ fileFieldName(f.key) }}
                <el-button link type="danger" size="small" @click="delete applyValues[f.key]">移除</el-button>
              </span>
            </template>
          </el-form-item>
        </template>
        <el-form-item v-else label="表单数据">
          <el-input v-model="applyJsonFallback" type="textarea" :rows="6" placeholder="该流程未定义表单字段,请填写 JSON" />
        </el-form-item>
        <el-form-item v-if="resubmitId == null" label="抄送人">
          <el-select v-model="applyCcUserIds" multiple filterable clearable placeholder="可选,知会相关人(不占审批环节)" style="width: 100%">
            <el-option
              v-for="u in userOptions.filter((o) => o.id !== auth.userInfo?.userId)"
              :key="u.id" :label="u.name" :value="u.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" @click="submitApply">提交</el-button>
      </template>
    </el-dialog>

    <!-- 审批详情/处理 -->
    <el-dialog v-model="detailDialog" :title="`审批详情 ${detail?.instanceNo ?? ''}`" width="620px" :fullscreen="isMobile">
      <template v-if="detail">
        <!-- 警告提示(如节点无可用审批人) -->
        <el-alert v-if="detail.warning" :title="detail.warning" type="error" show-icon :closable="false" style="margin-bottom: 12px" />
        <!-- 基本信息 -->
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请人">{{ detail.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="表单类型">{{ detail.formName || detail.formType }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detail.status)" size="small">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <!-- 表单数据(结构化展示) -->
        <p style="font-weight: 600; margin-top: 16px; margin-bottom: 8px">表单数据</p>
        <el-descriptions v-if="detailFormFields.length" :column="1" border size="small">
          <el-descriptions-item v-for="f in detailFormFields" :key="f.label" :label="f.label">
            <el-link v-if="f.url" :href="f.url" target="_blank" type="primary">{{ f.value }}</el-link>
            <template v-else>{{ f.value }}</template>
          </el-descriptions-item>
        </el-descriptions>
        <pre v-else class="form-data">{{ detail.formData }}</pre>
        <!-- 审批进度时间线 -->
        <p style="font-weight: 600; margin-top: 16px; margin-bottom: 8px">审批进度</p>
        <el-timeline>
          <el-timeline-item
            v-for="n in detail.nodes" :key="n.id"
            :type="n.status === 'APPROVED' ? 'success' : n.status === 'REJECTED' ? 'danger' : n.status === 'APPROVING' ? 'warning' : 'info'"
            :timestamp="n.actionAt || ''"
            placement="top"
          >
            <div class="timeline-node">
              <span class="timeline-node-name">{{ n.nodeName }}</span>
              <el-tag size="small" :type="statusTag(n.status)">{{ nodeStatusLabel(n.status, n.approverType) }}</el-tag>
            </div>
            <div class="timeline-node-info">
              <span v-if="isCcNode(n)">抄送对象: {{ n.approverName || '待流程到达后通知' }}</span>
              <span v-else-if="n.approverName">审批人: {{ n.approverName }}</span>
              <span v-else-if="n.result === 'AUTO'">系统自动通过</span>
              <span v-else>待分配审批人</span>
            </div>
            <div v-if="n.comment" class="timeline-node-comment">{{ n.comment }}</div>
          </el-timeline-item>
        </el-timeline>
        <template v-if="detail.status === 'PENDING' && isMyTurn">
          <el-input v-model="actComment" type="textarea" :rows="2" placeholder="审批意见（可选）" style="margin-top: 12px" />
        </template>
      </template>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
        <el-button @click="printDetail">打印</el-button>
        <el-button
          v-if="detail?.status === 'PENDING' && (isMyApply || canManage)"
          @click="urgeInstance"
        >
          催办
        </el-button>
        <template v-if="detail?.status === 'RETURNED' && isMyApply">
          <el-tooltip :disabled="canCancel" content="无撤回权限,请联系管理员分配" placement="top">
            <el-button :disabled="!canCancel" @click="cancelInstance">撤回</el-button>
          </el-tooltip>
          <el-button type="primary" @click="openResubmit">修改并重新提交</el-button>
        </template>
        <template v-if="detail?.status === 'PENDING'">
          <el-tooltip v-if="isMyApply" :disabled="canCancel" content="无撤回权限,请联系管理员分配" placement="top">
            <el-button :disabled="!canCancel" @click="cancelInstance">撤回</el-button>
          </el-tooltip>
          <el-tooltip v-if="isMyTurn" :disabled="canTransfer" content="无转交权限,请联系管理员分配" placement="top">
            <el-button :disabled="!canTransfer" @click="openTransfer">转交</el-button>
          </el-tooltip>
          <!-- 管理兜底:审批人离职/停用导致卡单时,流程管理员可代为转交 -->
          <el-button
            v-if="!isMyTurn && canManage && detail?.status === 'PENDING' && currentApprovers.length"
            @click="openTransfer"
          >
            代为转交
          </el-button>
          <el-tooltip v-if="isMyTurn" :disabled="canApprove" content="无审批通过权限,加签也不可用" placement="top">
            <el-button :disabled="!canApprove" @click="openAddSign">加签</el-button>
          </el-tooltip>
          <el-tooltip v-if="isMyTurn" :disabled="canReject" content="无驳回权限,请联系管理员分配" placement="top">
            <el-button type="danger" :disabled="!canReject" @click="act('REJECT')">驳回</el-button>
          </el-tooltip>
          <el-tooltip v-if="isMyTurn" :disabled="canApprove" content="无通过权限,请联系管理员分配" placement="top">
            <el-button type="primary" :disabled="!canApprove" @click="act('APPROVE')">通过</el-button>
          </el-tooltip>
        </template>
      </template>
    </el-dialog>

    <!-- 加签 -->
    <el-dialog v-model="addSignDialog" title="审批加签" width="420px">
      <el-form label-width="90px">
        <el-form-item label="加签人">
          <el-select v-model="addSignTarget" placeholder="选择加签人" filterable style="width: 100%">
            <el-option
              v-for="u in userOptions.filter((o) => o.id !== auth.userInfo?.userId)"
              :key="u.id" :label="u.name" :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="加签方式">
          <el-radio-group v-model="addSignPosition">
            <el-radio value="PRE">前加签(其先审,再回到我)</el-radio>
            <el-radio value="POST">后加签(我审完后其再审)</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addSignDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAddSign">确定加签</el-button>
      </template>
    </el-dialog>

    <!-- 代理设置 -->
    <el-dialog v-model="delegateDialog" title="审批代理设置" width="440px">
      <el-form label-width="90px">
        <el-form-item label="代理人">
          <el-select v-model="delegateForm.delegateId" placeholder="选择代理人" filterable style="width: 100%">
            <el-option
              v-for="u in userOptions.filter((o) => o.id !== auth.userInfo?.userId)"
              :key="u.id" :label="u.name" :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="delegateForm.startAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="delegateForm.endAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="delegateForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <p style="color: #909399; font-size: 12px; margin: 0 0 8px">
        生效期内,新流转到你的审批将自动转由代理人处理;
        <b>设置前已在你待办中的审批不会自动转出</b>,如需处理请使用「转交」
      </p>
      <template #footer>
        <el-button @click="delegateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitDelegate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 转交 -->
    <el-dialog v-model="transferDialog" :title="isManageTransfer ? '代为转交审批' : '转交审批'" width="400px">
      <el-select
        v-if="isManageTransfer"
        v-model="transferFrom"
        placeholder="选择要转交谁的审批(原审批人)"
        style="width: 100%; margin-bottom: 12px"
      >
        <el-option v-for="a in currentApprovers" :key="a.id" :label="a.name" :value="a.id" />
      </el-select>
      <el-select v-model="transferTarget" placeholder="选择转交对象" filterable style="width: 100%">
        <el-option
          v-for="u in userOptions.filter((o) => o.id !== auth.userInfo?.userId)"
          :key="u.id" :label="u.name" :value="u.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="transferDialog = false">取消</el-button>
        <el-button type="primary" @click="submitTransfer">确定转交</el-button>
      </template>
    </el-dialog>

    <!-- 流程配置：可视化节点编辑 -->
    <el-dialog v-model="flowDialog" title="审批流程配置" width="720px" :fullscreen="isMobile">
      <el-form label-width="90px">
        <el-form-item label="表单类型">
          <el-input v-model="flowForm.formType" placeholder="如 leave" />
        </el-form-item>
        <el-form-item label="表单名称">
          <el-input v-model="flowForm.formName" placeholder="如 请假申请" />
        </el-form-item>
        <el-form-item label="流程标识">
          <el-input v-model="flowForm.processKey" placeholder="如 LEAVE_APPROVAL" />
        </el-form-item>
      </el-form>

      <!-- 纵向流程图设计器:点卡片配置,点 + 插入节点 -->
      <div class="flow-canvas">
        <div class="flow-node flow-terminal">
          <div class="flow-terminal-label">发起人提交</div>
        </div>
        <template v-for="(n, i) in flowNodes" :key="i">
          <div class="flow-connector">
            <button class="flow-add" type="button" title="在此插入节点" @click="insertNodeAt(i)">+</button>
          </div>
          <div class="flow-node flow-card" @click="openNodeConfig(i)">
            <div class="flow-card-icon" :style="{ background: approverTypeColor(n.approverType) }">
              {{ approverTypeIcon(n.approverType) }}
            </div>
            <div class="flow-card-content">
              <div class="flow-card-head">
                <span class="flow-card-seq">{{ i + 1 }}</span>
                <span class="flow-card-title">{{ n.name || `节点 ${i + 1}(未命名)` }}</span>
                <button class="flow-del" type="button" title="删除节点" @click.stop="removeNodeAt(i)">✕</button>
              </div>
              <div class="flow-card-body">
                <span>{{ approverTypeLabel(n.approverType) }}</span>
                <span v-if="nodeTargetName(n)" class="flow-card-target">{{ nodeTargetName(n) }}</span>
              </div>
              <div class="flow-card-tags">
                <el-tag v-if="n.approverType === 'ROLE' && n.resolveMode === 'ALL'" size="small">会签</el-tag>
                <el-tag v-if="n.approverType === 'ROLE' && n.resolveMode === 'ANYONE'" size="small">或签</el-tag>
                <el-tag v-if="n.condition" size="small" type="warning">条件</el-tag>
                <el-tag v-if="n.timeoutHours" size="small" type="info">{{ n.timeoutHours }}h超时</el-tag>
                <el-tag v-if="n.rejectPolicy && n.rejectPolicy !== 'TERMINATE'" size="small" type="danger">
                  {{ REJECT_POLICY_SHORT[n.rejectPolicy] }}
                </el-tag>
              </div>
            </div>
          </div>
        </template>
        <div class="flow-connector">
          <button class="flow-add" type="button" title="在末尾添加节点" @click="insertNodeAt(flowNodes.length)">+</button>
        </div>
        <div class="flow-node flow-terminal flow-end">
          <div class="flow-terminal-label">流程结束</div>
        </div>
      </div>

      <el-collapse style="margin-top: 12px">
        <el-collapse-item title="表单字段配置（发起时按此渲染，空=JSON输入）" name="fields">
          <div v-for="(f, i) in flowFields" :key="i" class="node-row">
            <el-input v-model="f.key" placeholder="字段标识,如 days" style="width: 110px" />
            <el-input v-model="f.label" placeholder="显示名,如 请假天数" style="width: 130px" />
            <el-select v-model="f.type" style="width: 110px">
              <el-option v-for="t in FIELD_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-input v-if="f.type === 'number'" v-model="f.unit" placeholder="单位,如 天" style="width: 90px" />
            <el-checkbox v-model="f.required">必填</el-checkbox>
            <el-input v-if="f.type === 'select'" v-model="f.optionsText" placeholder="选项,逗号分隔" style="width: 150px" />
            <el-button link type="danger" @click="flowFields.splice(i, 1)">删除</el-button>
          </div>
          <el-button style="margin-top: 4px" @click="flowFields.push(blankField())">+ 添加字段</el-button>
        </el-collapse-item>
      </el-collapse>

      <template #footer>
        <el-button @click="flowDialog = false">取消</el-button>
        <el-button type="primary" @click="submitFlow">保存</el-button>
      </template>
    </el-dialog>

    <!-- 节点配置(点流程图卡片) -->
    <el-dialog v-model="nodeConfigDialog" title="节点配置" width="500px" append-to-body>
      <el-form v-if="editingNode" label-width="100px">
        <el-form-item label="节点名称">
          <el-input v-model="editingNode.name" placeholder="如 直属上级审批" />
        </el-form-item>
        <el-divider content-position="left">审批人设置</el-divider>
        <el-form-item label="审批人类型">
          <el-select v-model="editingNode.approverType" style="width: 100%">
            <el-option v-for="t in APPROVER_TYPES" :key="t.value" :label="t.icon + ' ' + t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingNode.approverType === 'SPECIFIC_USER'" label="指定人员">
          <el-select v-model="editingNode.targetUserId" placeholder="选择人员" filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.name" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingNode.approverType === 'ROLE'" label="指定角色">
          <el-select v-model="editingNode.targetRoleId" placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingNode.approverType === 'ROLE'" label="多人审批">
          <el-radio-group v-model="editingNode.resolveMode">
            <el-radio value="FIRST">单人审批</el-radio>
            <el-radio value="ALL">会签(全部通过)</el-radio>
            <el-radio value="ANYONE">或签(任一通过)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editingNode.approverType === 'CC'" label="抄送方式">
          <el-radio-group v-model="editingNode.resolveMode" @change="onCcModeChange">
            <el-radio value="FIRST">指定人员</el-radio>
            <el-radio value="ALL">按角色</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editingNode.approverType === 'CC' && editingNode.resolveMode !== 'ALL'" label="抄送人员">
          <el-select v-model="editingNode.ccUserIds" multiple filterable clearable placeholder="选择抄送人(可多选)" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.name" :value="u.id" />
          </el-select>
          <div class="form-tip">流程到达此节点时自动抄送给这些人(不占审批环节)</div>
        </el-form-item>
        <el-form-item v-if="editingNode.approverType === 'CC' && editingNode.resolveMode === 'ALL'" label="抄送角色">
          <el-select v-model="editingNode.targetRoleId" placeholder="选择角色(抄送给该角色下所有人)" style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <template v-if="editingNode.approverType !== 'CC'">
        <el-divider content-position="left">高级设置</el-divider>
        <el-form-item label="驳回策略">
          <el-select v-model="editingNode.rejectPolicy" style="width: 100%">
            <el-option v-for="p in REJECT_POLICIES" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效条件">
          <div class="condition-row">
            <el-select v-model="editingNodeConditionField" placeholder="选择字段" clearable style="width: 120px">
              <el-option v-for="f in flowFields.filter((x) => x.key)" :key="f.key" :label="f.label || f.key" :value="f.key" />
            </el-select>
            <el-select v-model="editingNodeConditionOp" placeholder="运算符" style="width: 90px">
              <el-option label=">" value=">" />
              <el-option label=">=" value=">=" />
              <el-option label="=" value="==" />
              <el-option label="!=" value="!=" />
              <el-option label="<" value="<" />
              <el-option label="<=" value="<=" />
            </el-select>
            <el-input v-model="editingNodeConditionValue" placeholder="值,如 2" style="width: 120px" />
          </div>
          <div class="form-tip">满足条件时该节点生效,否则自动跳过;可选字段来自本流程「表单字段」区,新增条件字段(如 金额)请先在流程配置弹窗下方添加</div>
        </el-form-item>
        <el-form-item label="超时提醒">
          <el-input-number v-model="editingNode.timeoutHours" :min="1" :max="720" style="width: 160px" />
          <span class="form-tip-inline">小时，空=不限时</span>
        </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="nodeConfigDialog = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.flow-toolbar {
  margin-bottom: 12px;
}
.form-data {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px;
  font-size: 13px;
  white-space: pre-wrap;
}
.node-list {
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.8;
}
.upload-file-name {
  margin-left: 10px;
  font-size: 13px;
  color: #409eff;
}
.number-with-unit {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.field-unit {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}
.condition-row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.timeline-node {
  display: flex;
  align-items: center;
  gap: 8px;
}
.timeline-node-name {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}
.timeline-node-info {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.timeline-node-comment {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  background: #f5f7fa;
  border-radius: 4px;
  padding: 4px 8px;
}
.m-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.m-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px 14px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.m-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.m-card-title {
  font-weight: 600;
  font-size: 14px;
}
.m-card-line {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr 1fr;
  }
}
.stat-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
  background: #fafbfc;
  cursor: default;
}
.stat-card.clickable {
  cursor: pointer;
  transition: all 0.2s;
}
.stat-card.clickable:hover {
  border-color: #409eff;
  background: #ecf5ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}
.stat-label {
  font-size: 13px;
  color: #6b7280;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  margin-top: 4px;
}
.stats-section {
  margin-top: 20px;
}
.stats-section-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  margin-bottom: 10px;
}
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 80px;
  padding: 8px 0;
}
.trend-bar-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}
.trend-bar {
  width: 32px;
  background: linear-gradient(180deg, #409eff, #79bbff);
  border-radius: 4px 4px 0 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  min-height: 4px;
  transition: height 0.3s;
}
.trend-bar-num {
  font-size: 11px;
  color: #fff;
  margin-top: 2px;
}
.trend-bar-label {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}
.empty-tip {
  text-align: center;
  color: #909399;
  padding: 24px 0;
}
.stats-summary-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 10px 16px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #606266;
}
.stats-summary-bar .summary-item b {
  font-size: 15px;
  color: #111827;
  margin-left: 2px;
}
.stats-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.node-editor {
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}
.node-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fafbfc;
}
.node-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.node-index {
  font-weight: 600;
  font-size: 13px;
  color: #374151;
}
.node-row {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.form-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
  margin-top: 4px;
}
.form-tip-inline {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}
.flow-summary {
  font-size: 12px;
  color: #606266;
  line-height: 1.5;
}
/* 纵向流程图设计器 */
.flow-canvas {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 0 8px;
  border-top: 1px solid #ebeef5;
  background:
    radial-gradient(circle, #e5e9f0 1px, transparent 1px) 0 0 / 16px 16px;
  border-radius: 8px;
}
.flow-node {
  width: 260px;
}
.flow-terminal {
  text-align: center;
}
.flow-terminal-label {
  display: inline-block;
  padding: 6px 22px;
  border-radius: 16px;
  background: #303133;
  color: #fff;
  font-size: 13px;
}
.flow-end .flow-terminal-label {
  background: #909399;
}
.flow-connector {
  position: relative;
  width: 2px;
  height: 40px;
  background: #c0c4cc;
  display: flex;
  align-items: center;
  justify-content: center;
}
.flow-add {
  position: absolute;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: #409eff;
  color: #fff;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(64, 158, 255, 0.4);
}
.flow-add:hover {
  transform: scale(1.15);
}
.flow-card {
  display: flex;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.15s, border-color 0.15s;
  overflow: hidden;
}
.flow-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 10px rgba(64, 158, 255, 0.18);
}
.flow-card-icon {
  width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #fff;
  flex-shrink: 0;
}
.flow-card-content {
  flex: 1;
  padding: 8px 12px;
  min-width: 0;
}
.flow-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.flow-card-seq {
  display: inline-block;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #909399;
  font-size: 11px;
  text-align: center;
  line-height: 18px;
  margin-right: 6px;
  flex-shrink: 0;
}
.flow-card-title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.flow-del {
  border: none;
  background: transparent;
  color: #c0c4cc;
  cursor: pointer;
  font-size: 12px;
  flex-shrink: 0;
  margin-left: 4px;
}
.flow-del:hover {
  color: #f56c6c;
}
.flow-card-body {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}
.flow-card-target {
  color: #409eff;
  font-weight: 500;
  margin-left: 6px;
}
.flow-card-tags {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.flow-card-tags:empty {
  display: none;
}
</style>
