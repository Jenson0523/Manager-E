<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approvalApi } from '@/api/approval'
import { roleApi } from '@/api/rbac'
import { userApi } from '@/api/user'
import { platformRoleApi, platformUserApi } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'
import type { ApprovalInstanceVO, ApprovalFlowVO, ApprovalStatisticsVO, ApprovalDelegateVO } from '@/types/approval'

const auth = useAuthStore()
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
  const map: Record<string, TagType> = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELED: 'info' }
  return map[status] ?? 'info'
}
function statusLabel(status: string) {
  return { PENDING: '审批中', APPROVED: '已通过', REJECTED: '已驳回', CANCELED: '已撤销' }[status] ?? status
}

/* 发起审批 */
const applyDialog = ref(false)
const applyForm = reactive({ formType: 'leave', formDataJson: '{\n  "days": 2,\n  "reason": "回家探亲"\n}' })
function openApply() {
  applyForm.formType = 'leave'
  applyForm.formDataJson = '{\n  "days": 2,\n  "reason": "回家探亲"\n}'
  applyDialog.value = true
}
async function submitApply() {
  let formData: Record<string, unknown>
  try {
    formData = JSON.parse(applyForm.formDataJson)
  } catch {
    ElMessage.error('表单数据不是合法 JSON')
    return
  }
  await approvalApi.apply({ formType: applyForm.formType, formData })
  ElMessage.success('已提交审批')
  applyDialog.value = false
  loadTab('my')
}

/* 审批详情/处理 */
const detailDialog = ref(false)
const detail = ref<ApprovalInstanceVO | null>(null)
const actComment = ref('')
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

/* 流程管理：结构化节点编辑器 */
interface NodeDraft {
  name: string
  approverType: string
  resolveMode: string
  condition: string
  targetUserId?: number
  targetRoleId?: number
  timeoutHours?: number
}
const APPROVER_TYPES = [
  { value: 'LEADER', label: '直属上级' },
  { value: 'DEPT_LEADER', label: '部门负责人' },
  { value: 'SPECIFIC_USER', label: '指定人员' },
  { value: 'ROLE', label: '指定角色' },
  { value: 'SELF_APPROVE', label: '自动通过(备案)' },
]
const flowDialog = ref(false)
const flowForm = reactive({ formType: '', formName: '', processKey: '' })
const flowNodes = ref<NodeDraft[]>([])
const userOptions = ref<{ id: number; name: string }[]>([])
const roleOptions = ref<{ id: number; name: string }[]>([])

function blankNode(): NodeDraft {
  return { name: '', approverType: 'LEADER', resolveMode: 'FIRST', condition: '' }
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
function openFlowCreate() {
  flowForm.formType = ''
  flowForm.formName = ''
  flowForm.processKey = ''
  flowNodes.value = [blankNode()]
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
    }))
  } catch {
    flowNodes.value = [blankNode()]
  }
  loadFlowRefs()
  flowDialog.value = true
}
function moveNode(i: number, delta: number) {
  const j = i + delta
  if (j < 0 || j >= flowNodes.value.length) return
  const arr = flowNodes.value
  ;[arr[i], arr[j]] = [arr[j], arr[i]]
}
async function submitFlow() {
  for (const [i, n] of flowNodes.value.entries()) {
    if (!n.name) { ElMessage.error(`第 ${i + 1} 个节点缺少名称`); return }
    if (n.approverType === 'SPECIFIC_USER' && !n.targetUserId) { ElMessage.error(`第 ${i + 1} 个节点需选择指定人员`); return }
    if (n.approverType === 'ROLE' && !n.targetRoleId) { ElMessage.error(`第 ${i + 1} 个节点需选择角色`); return }
  }
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
    })),
  })
  await approvalApi.saveFlow({ ...flowForm, configJson })
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
        <el-table v-loading="loading" :data="todoList" stripe>
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
        <el-table v-loading="loading" :data="myList" stripe>
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
        <el-table v-loading="loading" :data="doneList" stripe>
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
          <el-table-column prop="formType" label="表单类型" width="120" />
          <el-table-column prop="formName" label="名称" width="160" />
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="(row as ApprovalFlowVO).status === 1 ? 'success' : 'info'">
                {{ (row as ApprovalFlowVO).status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
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
    <el-dialog v-model="applyDialog" title="发起审批" width="480px">
      <el-form label-width="90px">
        <el-form-item label="表单类型">
          <el-input v-model="applyForm.formType" placeholder="如 leave / expense / purchase" />
        </el-form-item>
        <el-form-item label="表单数据">
          <el-input v-model="applyForm.formDataJson" type="textarea" :rows="6" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyDialog = false">取消</el-button>
        <el-button type="primary" @click="submitApply">提交</el-button>
      </template>
    </el-dialog>

    <!-- 审批详情/处理 -->
    <el-dialog v-model="detailDialog" :title="`审批详情 ${detail?.instanceNo ?? ''}`" width="560px">
      <template v-if="detail">
        <p>申请人：{{ detail.applicantName }}　申请时间：{{ detail.createdAt }}</p>
        <p style="font-weight: 600; margin-top: 12px">表单数据</p>
        <pre class="form-data">{{ detail.formData }}</pre>
        <p style="font-weight: 600; margin-top: 12px">审批记录</p>
        <ul class="node-list">
          <li v-for="n in detail.nodes" :key="n.id">
            {{ n.nodeName }} — {{ n.approverName ?? '（自动）' }}
            <el-tag size="small" :type="statusTag(n.status)">{{ n.status }}</el-tag>
            <span v-if="n.comment"> ：{{ n.comment }}</span>
          </li>
        </ul>
        <template v-if="detail.status === 'PENDING'">
          <el-input v-model="actComment" type="textarea" :rows="2" placeholder="审批意见（可选）" style="margin-top: 12px" />
        </template>
      </template>
      <template #footer>
        <el-button @click="detailDialog = false">关闭</el-button>
        <template v-if="detail?.status === 'PENDING'">
          <el-button v-if="isMyTurn && canTransfer" @click="openTransfer">转交</el-button>
          <el-button type="danger" @click="act('REJECT')">驳回</el-button>
          <el-button type="primary" @click="act('APPROVE')">通过</el-button>
        </template>
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
    <el-dialog v-model="flowDialog" title="审批流程配置" width="720px">
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

      <div class="node-editor">
        <div v-for="(n, i) in flowNodes" :key="i" class="node-card">
          <div class="node-head">
            <span class="node-index">节点 {{ i + 1 }}</span>
            <span class="node-actions">
              <el-button link :disabled="i === 0" @click="moveNode(i, -1)">↑</el-button>
              <el-button link :disabled="i === flowNodes.length - 1" @click="moveNode(i, 1)">↓</el-button>
              <el-button link type="danger" :disabled="flowNodes.length === 1" @click="flowNodes.splice(i, 1)">删除</el-button>
            </span>
          </div>
          <div class="node-row">
            <el-input v-model="n.name" placeholder="节点名称,如 直属上级审批" style="width: 200px" />
            <el-select v-model="n.approverType" style="width: 150px">
              <el-option v-for="t in APPROVER_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-select
              v-if="n.approverType === 'SPECIFIC_USER'"
              v-model="n.targetUserId" placeholder="选择人员" filterable style="width: 150px"
            >
              <el-option v-for="u in userOptions" :key="u.id" :label="u.name" :value="u.id" />
            </el-select>
            <el-select
              v-if="n.approverType === 'ROLE'"
              v-model="n.targetRoleId" placeholder="选择角色" style="width: 150px"
            >
              <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
            <el-select v-if="n.approverType === 'ROLE'" v-model="n.resolveMode" style="width: 120px">
              <el-option label="单人审批" value="FIRST" />
              <el-option label="会签(全部)" value="ALL" />
              <el-option label="或签(任一)" value="ANYONE" />
            </el-select>
          </div>
          <div class="node-row">
            <el-input v-model="n.condition" placeholder="生效条件(可空),如 form.days >= 3" style="width: 320px" />
            <el-input-number v-model="n.timeoutHours" :min="1" :max="720" placeholder="超时(小时)" style="width: 140px" />
            <span style="color: #909399; font-size: 12px; line-height: 32px">超时小时数,空=不限时</span>
          </div>
        </div>
        <el-button style="margin-top: 8px" @click="flowNodes.push(blankNode())">+ 添加节点</el-button>
      </div>

      <template #footer>
        <el-button @click="flowDialog = false">取消</el-button>
        <el-button type="primary" @click="submitFlow">保存</el-button>
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
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
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
</style>
