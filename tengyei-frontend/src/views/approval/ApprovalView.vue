<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { approvalApi } from '@/api/approval'
import { useAuthStore } from '@/stores/auth'
import type { ApprovalInstanceVO, ApprovalFlowVO } from '@/types/approval'

const auth = useAuthStore()
// 平台层(tenant 0)与公司层权限码并行,任一命中即可
const canManage = computed(
  () => auth.hasPermission('PERM_approval:manage') || auth.hasPermission('PERM_platform:approval:manage')
)
const canApply = computed(
  () => auth.hasPermission('PERM_approval:apply') || auth.hasPermission('PERM_platform:approval:apply')
)

const activeTab = ref('todo')
const loading = ref(false)
const todoList = ref<ApprovalInstanceVO[]>([])
const myList = ref<ApprovalInstanceVO[]>([])
const doneList = ref<ApprovalInstanceVO[]>([])
const flowList = ref<ApprovalFlowVO[]>([])

async function loadTab(tab: string) {
  loading.value = true
  try {
    if (tab === 'todo') todoList.value = await approvalApi.todo()
    else if (tab === 'my') myList.value = await approvalApi.my()
    else if (tab === 'done') doneList.value = await approvalApi.done()
    else if (tab === 'flows') flowList.value = await approvalApi.flows()
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

/* 流程管理 */
const flowDialog = ref(false)
const flowForm = reactive({
  formType: '',
  formName: '',
  processKey: '',
  configJson: JSON.stringify(
    {
      nodes: [
        { key: 'node_leader', name: '直属上级审批', approverType: 'LEADER', resolveMode: 'FIRST', orderBy: 1, condition: null },
      ],
    },
    null,
    2
  ),
})
function openFlowCreate() {
  flowForm.formType = ''
  flowForm.formName = ''
  flowForm.processKey = ''
  flowDialog.value = true
}
function openFlowEdit(f: ApprovalFlowVO) {
  flowForm.formType = f.formType
  flowForm.formName = f.formName
  flowForm.processKey = f.processKey
  flowForm.configJson = f.configJson
  flowDialog.value = true
}
async function submitFlow() {
  await approvalApi.saveFlow({ ...flowForm })
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
    </div>

    <el-tabs v-model="activeTab" @tab-change="(t) => loadTab(t as string)">
      <el-tab-pane label="我的待办" name="todo">
        <el-table v-loading="loading" :data="todoList" stripe>
          <el-table-column prop="instanceNo" label="单号" width="180" />
          <el-table-column prop="formType" label="表单类型" width="120" />
          <el-table-column prop="applicantName" label="申请人" width="120" />
          <el-table-column prop="createdAt" label="申请时间" width="180" />
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
          <el-button type="danger" @click="act('REJECT')">驳回</el-button>
          <el-button type="primary" @click="act('APPROVE')">通过</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 流程配置 -->
    <el-dialog v-model="flowDialog" title="审批流程配置" width="560px">
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
        <el-form-item label="节点配置">
          <el-input v-model="flowForm.configJson" type="textarea" :rows="12" />
        </el-form-item>
      </el-form>
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
</style>
