<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approvalApi } from '@/api/approval'
import { roleApi } from '@/api/rbac'
import { userApi } from '@/api/user'
import { platformRoleApi, platformUserApi } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'
import { useIsMobile } from '@/utils/responsive'
import type { ApprovalInstanceVO, ApprovalFlowVO, ApprovalStatisticsVO, ApprovalDelegateVO, FormField } from '@/types/approval'

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
const isOverdue = (t?: string) => !!t && new Date(t) < new Date()

const activeTab = ref('todo')
const loading = ref(false)
const todoList = ref<ApprovalInstanceVO[]>([])
const myList = ref<ApprovalInstanceVO[]>([])
const doneList = ref<ApprovalInstanceVO[]>([])
const flowList = ref<ApprovalFlowVO[]>([])
const stats = ref<ApprovalStatisticsVO | null>(null)

async function loadTab(tab: string) {
  loading.value = true
  try {
    if (tab === 'todo') todoList.value = await approvalApi.todo()
    else if (tab === 'my') myList.value = await approvalApi.my()
    else if (tab === 'done') doneList.value = await approvalApi.done()
    else if (tab === 'flows') flowList.value = await approvalApi.flows()
    else if (tab === 'stats') stats.value = await approvalApi.statistics()
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
}
async function openApply() {
  applyForms.value = await approvalApi.forms()
  if (!applyForms.value.length) {
    ElMessage.warning('暂无可用审批流程,请联系管理员配置')
    return
  }
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
      if (f.required && (v === undefined || v === null || v === '')) {
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
    await approvalApi.apply({ formType: applyFormType.value, formData })
    ElMessage.success('已提交审批')
  }
  resubmitId.value = null
  applyDialog.value = false
  detailDialog.value = false
  loadTab('my')
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
        return fields.map(f => ({
          label: f.label,
          value: formatFieldValue(data[f.key], f.type),
        })).filter(f => f.value !== '' && f.value !== undefined && f.value !== null)
      }
    } catch { /* fall through to raw */ }
  }
  // 降级:无字段定义时直接按 key->value 展示
  return Object.entries(data).map(([k, v]) => ({ label: k, value: String(v) }))
})
function formatFieldValue(val: unknown, type: string): string {
  if (val === null || val === undefined) return ''
  if (type === 'date') return String(val)
  if (type === 'number') return String(val)
  return String(val)
}

/* 节点状态标签映射 */
function nodeStatusLabel(status: string): string {
  return { APPROVED: '已通过', REJECTED: '已驳回', APPROVING: '审批中', WAITING: '等待中', CANCELED: '已取消' }[status] ?? status
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

/* 转交 */
const isMyTurn = computed(() =>
  detail.value?.nodes.some(
    (n) => n.status === 'APPROVING' && n.approverId === auth.userInfo?.userId
  ) ?? false
)
const transferDialog = ref(false)
const transferTarget = ref<number>()
async function openTransfer() {
  transferTarget.value = undefined
  if (!userOptions.value.length) await loadFlowRefs()
  transferDialog.value = true
}
async function submitTransfer() {
  if (!detail.value || !transferTarget.value) {
    ElMessage.error('请选择转交对象')
    return
  }
  await approvalApi.transfer(detail.value.id, transferTarget.value)
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
  if (n.approverType === 'SPECIFIC_USER') return userOptions.value.find((u) => u.id === n.targetUserId)?.name ?? ''
  if (n.approverType === 'ROLE') return roleOptions.value.find((r) => r.id === n.targetRoleId)?.name ?? ''
  return ''
}
/* 节点配置弹窗(点流程图卡片打开) */
const nodeConfigDialog = ref(false)
const editingNodeIndex = ref(0)
const editingNode = computed(() => flowNodes.value[editingNodeIndex.value])
function openNodeConfig(i: number) {
  editingNodeIndex.value = i
  nodeConfigDialog.value = true
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
  { value: 'SELF_APPROVE', label: '自动通过', icon: '⚡', color: '#909399' },
]
interface FieldDraft {
  key: string
  label: string
  type: string
  required: boolean
  optionsText: string
}
const FIELD_TYPES = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'select', label: '下拉选择' },
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
  const isPlatform = auth.userInfo?.tenantId === 0
  if (isPlatform) {
    const [roles, users] = await Promise.all([platformRoleApi.list(), platformUserApi.list({})])
    roleOptions.value = roles.map((r) => ({ id: r.id, name: r.name }))
    userOptions.value = users.map((u) => ({ id: u.id, name: u.realName }))
  } else {
    const [roles, page] = await Promise.all([roleApi.list(), userApi.page({ page: 1, size: 200 })])
    roleOptions.value = roles.map((r) => ({ id: r.id, name: r.name }))
    userOptions.value = page.records.map((u) => ({ id: u.id, name: u.realName }))
  }
}
function blankField(): FieldDraft {
  return { key: '', label: '', type: 'text', required: false, optionsText: '' }
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
  }))
  loadFlowRefs()
  flowDialog.value = true
}
async function submitFlow() {
  for (const [i, n] of flowNodes.value.entries()) {
    if (!n.name) { ElMessage.error(`第 ${i + 1} 个节点缺少名称`); return }
    if (n.approverType === 'SPECIFIC_USER' && !n.targetUserId) { ElMessage.error(`第 ${i + 1} 个节点需选择指定人员`); return }
    if (n.approverType === 'ROLE' && !n.targetRoleId) { ElMessage.error(`第 ${i + 1} 个节点需选择角色`); return }
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
      targetRoleId: n.approverType === 'ROLE' ? n.targetRoleId : undefined,
      timeoutHours: n.timeoutHours || undefined,
      rejectPolicy: n.rejectPolicy && n.rejectPolicy !== 'TERMINATE' ? n.rejectPolicy : undefined,
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
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button v-if="canApply" type="primary" @click="openApply">发起审批</el-button>
      <el-button v-if="canDelegate" @click="openDelegate">代理设置</el-button>
    </div>

    <el-tabs v-model="activeTab" @tab-change="(t) => loadTab(t as string)">
      <el-tab-pane label="我的待办" name="todo">
        <div v-if="isMobile" v-loading="loading" class="m-list">
          <div v-for="row in todoList" :key="row.id" class="m-card" @click="openDetail(row.id)">
            <div class="m-card-head">
              <span class="m-card-title">{{ row.formType }}</span>
              <el-tag v-if="isOverdue(row.myDueAt)" type="danger" size="small">已超时</el-tag>
            </div>
            <div class="m-card-line">{{ row.instanceNo }}</div>
            <div class="m-card-line">申请人:{{ row.applicantName }} · {{ row.createdAt }}</div>
          </div>
          <el-empty v-if="!todoList.length" description="暂无待办" :image-size="60" />
        </div>
        <el-table v-else v-loading="loading" :data="todoList" stripe>
          <el-table-column prop="instanceNo" label="单号" width="180" />
          <el-table-column prop="formType" label="表单类型" width="120" />
          <el-table-column prop="applicantName" label="申请人" width="120" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="时限" width="100">
            <template #default="{ row }">
              <el-tag v-if="isOverdue((row as ApprovalInstanceVO).myDueAt)" type="danger" size="small">已超时</el-tag>
              <span v-else-if="(row as ApprovalInstanceVO).myDueAt" style="color: #909399; font-size: 12px">正常</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail((row as ApprovalInstanceVO).id)">审批</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我已发起" name="my">
        <div v-if="isMobile" v-loading="loading" class="m-list">
          <div v-for="row in myList" :key="row.id" class="m-card" @click="openDetail(row.id)">
            <div class="m-card-head">
              <span class="m-card-title">{{ row.formType }}</span>
              <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </div>
            <div class="m-card-line">{{ row.instanceNo }}</div>
            <div class="m-card-line">{{ row.createdAt }}</div>
          </div>
          <el-empty v-if="!myList.length" description="暂无记录" :image-size="60" />
        </div>
        <el-table v-else v-loading="loading" :data="myList" stripe>
          <el-table-column prop="instanceNo" label="单号" width="180" />
          <el-table-column prop="formType" label="表单类型" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTag((row as ApprovalInstanceVO).status)">
                {{ statusLabel((row as ApprovalInstanceVO).status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentNode" label="当前节点" width="140" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail((row as ApprovalInstanceVO).id)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我已审批" name="done">
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
          <el-table-column prop="formType" label="表单类型" width="120" />
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

      <el-tab-pane v-if="canManage" label="统计" name="stats">
        <div v-if="stats" v-loading="loading" class="stats-grid">
          <div class="stat-card"><div class="stat-label">审批总数</div><div class="stat-value">{{ stats.total }}</div></div>
          <div class="stat-card"><div class="stat-label">审批中</div><div class="stat-value">{{ stats.byStatus.PENDING ?? 0 }}</div></div>
          <div class="stat-card"><div class="stat-label">已通过</div><div class="stat-value">{{ stats.byStatus.APPROVED ?? 0 }}</div></div>
          <div class="stat-card"><div class="stat-label">已驳回</div><div class="stat-value">{{ stats.byStatus.REJECTED ?? 0 }}</div></div>
          <div class="stat-card"><div class="stat-label">驳回率</div><div class="stat-value">{{ stats.rejectionRate }}%</div></div>
          <div class="stat-card"><div class="stat-label">平均审批时长(分钟)</div><div class="stat-value">{{ stats.avgDurationMinutes }}</div></div>
        </div>
        <el-table v-if="stats" :data="Object.entries(stats.byFormType).map(([t, c]) => ({ formType: t, count: c }))" stripe style="margin-top: 16px">
          <el-table-column prop="formType" label="表单类型" width="200" />
          <el-table-column prop="count" label="数量" width="120" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canManage" label="流程管理" name="flows">
        <div class="flow-toolbar">
          <el-button type="primary" size="small" @click="openFlowCreate">新建流程</el-button>
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
              <el-button link type="primary" @click="openFlowEdit(row as ApprovalFlowVO)">编辑</el-button>
              <el-button link type="primary" @click="toggleFlow(row as ApprovalFlowVO)">
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
        <template v-if="applyFields.length">
          <el-form-item v-for="f in applyFields" :key="f.key" :label="f.label" :required="f.required">
            <el-input v-if="f.type === 'text'" v-model="applyValues[f.key] as string" />
            <el-input v-else-if="f.type === 'textarea'" v-model="applyValues[f.key] as string" type="textarea" :rows="3" />
            <el-input-number v-else-if="f.type === 'number'" v-model="applyValues[f.key] as number" style="width: 100%" />
            <el-date-picker v-else-if="f.type === 'date'" v-model="applyValues[f.key] as string" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            <el-select v-else-if="f.type === 'select'" v-model="applyValues[f.key] as string" style="width: 100%">
              <el-option v-for="o in f.options ?? []" :key="o" :label="o" :value="o" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item v-else label="表单数据">
          <el-input v-model="applyJsonFallback" type="textarea" :rows="6" placeholder="该流程未定义表单字段,请填写 JSON" />
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
            {{ f.value }}
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
              <el-tag size="small" :type="statusTag(n.status)">{{ nodeStatusLabel(n.status) }}</el-tag>
            </div>
            <div class="timeline-node-info">
              <span v-if="n.approverName">审批人: {{ n.approverName }}</span>
              <span v-else-if="n.result === 'AUTO'">系统自动通过</span>
              <span v-else>待分配审批人</span>
            </div>
            <div v-if="n.comment" class="timeline-node-comment">{{ n.comment }}</div>
          </el-timeline-item>
        </el-timeline>
        <template v-if="detail.status === 'PENDING'">
          <el-input v-model="actComment" type="textarea" :rows="2" placeholder="审批意见（可选）" style="margin-top: 12px" />
        </template>
      </template>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
        <template v-if="detail?.status === 'RETURNED' && isMyApply">
          <el-button v-if="canCancel" @click="cancelInstance">撤回</el-button>
          <el-button type="primary" @click="openResubmit">修改并重新提交</el-button>
        </template>
        <template v-if="detail?.status === 'PENDING'">
          <el-button v-if="isMyApply && canCancel" @click="cancelInstance">撤回</el-button>
          <el-button v-if="isMyTurn && canTransfer" @click="openTransfer">转交</el-button>
          <el-button v-if="isMyTurn" @click="openAddSign">加签</el-button>
          <template v-if="isMyTurn">
            <el-button type="danger" @click="act('REJECT')">驳回</el-button>
            <el-button type="primary" @click="act('APPROVE')">通过</el-button>
          </template>
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
      <p style="color: #909399; font-size: 12px; margin: 0 0 8px">生效期内,分派给你的审批将自动转由代理人处理</p>
      <template #footer>
        <el-button @click="delegateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitDelegate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 转交 -->
    <el-dialog v-model="transferDialog" title="转交审批" width="400px">
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
        <el-divider content-position="left">高级设置</el-divider>
        <el-form-item label="驳回策略">
          <el-select v-model="editingNode.rejectPolicy" style="width: 100%">
            <el-option v-for="p in REJECT_POLICIES" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效条件">
          <el-input v-model="editingNode.condition" placeholder="可空,如 form.days >= 3" />
          <div class="form-tip">满足条件时该节点生效,否则自动跳过</div>
        </el-form-item>
        <el-form-item label="超时提醒">
          <el-input-number v-model="editingNode.timeoutHours" :min="1" :max="720" style="width: 160px" />
          <span class="form-tip-inline">小时，空=不限时</span>
        </el-form-item>
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
  grid-template-columns: repeat(3, 1fr);
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
