<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  contractApi,
  CONTRACT_STATUSES,
  CONTRACT_TYPES,
  type ContractAttachment,
  type ContractSummaryVO,
  type ContractVO,
} from '@/api/contract'
import { commonApi } from '@/api/common'
import { useAuthStore } from '@/stores/auth'
import { useIsMobile } from '@/utils/responsive'

const auth = useAuthStore()
const isMobile = useIsMobile()
const canManage = computed(() => auth.hasPermission('PERM_contract:manage'))
const canDisable = computed(() => auth.hasPermission('PERM_contract:disable'))

const TYPE_LABEL = Object.fromEntries(CONTRACT_TYPES.map((t) => [t.value, t.label]))
const STATUS_META = Object.fromEntries(CONTRACT_STATUSES.map((s) => [s.value, s]))

/* ---------------- 列表 ---------------- */
const loading = ref(false)
const list = ref<ContractVO[]>([])
const total = ref(0)
const summary = ref<ContractSummaryVO>({ total: 0, expiring: 0, expired: 0, remindDays: 30 })
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  type: '',
  status: '',
  expiring: false,
})

async function fetchList() {
  loading.value = true
  try {
    const res = await contractApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      type: query.type || undefined,
      status: query.status || undefined,
      expiring: query.expiring || undefined,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function fetchSummary() {
  summary.value = await contractApi.summary()
}

function search() {
  query.page = 1
  fetchList()
}

function resetQuery() {
  Object.assign(query, { page: 1, keyword: '', type: '', status: '', expiring: false })
  fetchList()
}

/** 只筛临期,点统计卡快速下钻 */
function filterExpiring() {
  query.expiring = true
  query.status = ''
  query.page = 1
  fetchList()
}

/* 到期日的红黄提示:过期红、临期黄 */
function expireTag(row: ContractVO): { text: string; type: 'danger' | 'warning' } | null {
  const d = row.daysToExpire
  if (d === null || d === undefined) return null
  if (d < 0) return { text: `已过期${-d}天`, type: 'danger' }
  if (d <= summary.value.remindDays) return { text: `剩${d}天`, type: 'warning' }
  return null
}

/* ---------------- 选人 ---------------- */
const userOptions = ref<{ id: number; realName: string }[]>([])
async function loadUserOptions() {
  if (userOptions.value.length) return
  userOptions.value = (await commonApi.options()).users
}

/* ---------------- 编辑弹窗 ---------------- */
const dialog = ref(false)
const saving = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  contractNo: '',
  name: '',
  type: 'SALE',
  partyB: '',
  partyBContact: '',
  partyBPhone: '',
  amount: 0 as number,
  signDate: '',
  startDate: '',
  endDate: '',
  ownerId: undefined as number | undefined,
  status: 'DRAFT',
  attachments: [] as ContractAttachment[],
  remark: '',
})

function openCreate() {
  Object.assign(form, {
    id: undefined,
    contractNo: '',
    name: '',
    type: 'SALE',
    partyB: '',
    partyBContact: '',
    partyBPhone: '',
    amount: 0,
    signDate: '',
    startDate: '',
    endDate: '',
    ownerId: auth.userInfo?.userId,
    status: 'DRAFT',
    attachments: [],
    remark: '',
  })
  loadUserOptions()
  dialog.value = true
}

async function openEdit(row: ContractVO) {
  const c = await contractApi.detail(row.id)
  Object.assign(form, {
    id: c.id,
    contractNo: c.contractNo,
    name: c.name,
    type: c.type,
    partyB: c.partyB,
    partyBContact: c.partyBContact ?? '',
    partyBPhone: c.partyBPhone ?? '',
    amount: c.amount ?? 0,
    signDate: c.signDate ?? '',
    startDate: c.startDate ?? '',
    endDate: c.endDate ?? '',
    ownerId: c.ownerId,
    status: c.status,
    attachments: [...(c.attachments ?? [])],
    remark: c.remark ?? '',
  })
  loadUserOptions()
  dialog.value = true
}

async function submit() {
  if (!form.name.trim()) return ElMessage.error('请填写合同名称')
  if (!form.partyB.trim()) return ElMessage.error('请填写对方单位')
  if (form.startDate && form.endDate && form.endDate < form.startDate) {
    return ElMessage.error('到期日期不能早于生效日期')
  }
  saving.value = true
  try {
    await contractApi.save({
      id: form.id,
      contractNo: form.contractNo || undefined,
      name: form.name.trim(),
      type: form.type,
      partyB: form.partyB.trim(),
      partyBContact: form.partyBContact || undefined,
      partyBPhone: form.partyBPhone || undefined,
      amount: form.amount ?? 0,
      signDate: form.signDate || undefined,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
      ownerId: form.ownerId,
      status: form.status,
      attachments: form.attachments,
      remark: form.remark || undefined,
    })
    ElMessage.success('已保存')
    dialog.value = false
    fetchList()
    fetchSummary()
  } finally {
    saving.value = false
  }
}

/* 附件 */
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
function pickFile() {
  fileInputRef.value?.click()
}
async function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  uploading.value = true
  try {
    form.attachments.push(await contractApi.uploadFile(file))
    ElMessage.success('附件已上传')
  } finally {
    uploading.value = false
  }
}
function removeAttachment(i: number) {
  form.attachments.splice(i, 1)
}

/* ---------------- 详情 ---------------- */
const detailDialog = ref(false)
const detail = ref<ContractVO | null>(null)
async function openDetail(row: ContractVO) {
  detail.value = await contractApi.detail(row.id)
  detailDialog.value = true
}

/* ---------------- 状态流转 / 删除 ---------------- */
async function changeStatus(row: ContractVO, status: string) {
  await contractApi.setStatus(row.id, status)
  ElMessage.success('状态已更新')
  fetchList()
  fetchSummary()
}

async function remove(row: ContractVO) {
  await ElMessageBox.confirm(`确认删除合同「${row.name}」？`, '提示', { type: 'warning' })
  await contractApi.remove(row.id)
  ElMessage.success('已删除')
  fetchList()
  fetchSummary()
}

onMounted(() => {
  fetchList()
  fetchSummary()
})
</script>

<template>
  <div class="page">
    <!-- 统计 -->
    <div class="stat-row">
      <div class="stat-card">
        <div class="stat-label">合同总数</div>
        <div class="stat-value">{{ summary.total }}</div>
      </div>
      <div class="stat-card clickable" @click="filterExpiring">
        <div class="stat-label">{{ summary.remindDays }}天内到期</div>
        <div class="stat-value warn">{{ summary.expiring }}</div>
      </div>
      <div class="stat-card clickable" @click="filterExpiring">
        <div class="stat-label">已过期未处理</div>
        <div class="stat-value danger">{{ summary.expired }}</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="合同名称 / 编号 / 对方单位"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.type" placeholder="类型" clearable style="width: 130px" @change="search">
        <el-option v-for="t in CONTRACT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="search">
        <el-option v-for="s in CONTRACT_STATUSES" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-checkbox v-model="query.expiring" @change="search">只看临期/过期</el-checkbox>
      <el-button @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div class="spacer" />
      <el-button v-if="canManage" type="primary" @click="openCreate">新增合同</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="合同编号" width="140">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openDetail(row as ContractVO)">
            {{ (row as ContractVO).contractNo }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="合同名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ TYPE_LABEL[(row as ContractVO).type] ?? (row as ContractVO).type }}</template>
      </el-table-column>
      <el-table-column prop="partyB" label="对方单位" min-width="160" show-overflow-tooltip />
      <el-table-column label="金额(元)" width="140" align="right">
        <template #default="{ row }">{{ Number((row as ContractVO).amount ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</template>
      </el-table-column>
      <el-table-column label="负责人" width="100">
        <template #default="{ row }">{{ (row as ContractVO).ownerName ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="到期日期" width="160">
        <template #default="{ row }">
          <span v-if="!(row as ContractVO).endDate">长期</span>
          <template v-else>
            {{ (row as ContractVO).endDate }}
            <el-tag
              v-if="expireTag(row as ContractVO)"
              :type="expireTag(row as ContractVO)!.type"
              size="small"
              style="margin-left: 4px"
            >
              {{ expireTag(row as ContractVO)!.text }}
            </el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="STATUS_META[(row as ContractVO).status]?.tag ?? 'info'" size="small">
            {{ STATUS_META[(row as ContractVO).status]?.label ?? (row as ContractVO).status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="canManage || canDisable" label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canManage" link type="primary" @click="openEdit(row as ContractVO)">编辑</el-button>
          <el-button
            v-if="canManage && (row as ContractVO).status === 'DRAFT'"
            link
            type="primary"
            @click="changeStatus(row as ContractVO, 'EFFECTIVE')"
          >
            生效
          </el-button>
          <el-button
            v-if="canManage && ((row as ContractVO).status === 'EFFECTIVE' || (row as ContractVO).status === 'PERFORMING')"
            link
            type="primary"
            @click="changeStatus(row as ContractVO, 'COMPLETED')"
          >
            完成
          </el-button>
          <el-button
            v-if="canDisable && (row as ContractVO).status !== 'TERMINATED'"
            link
            type="warning"
            @click="changeStatus(row as ContractVO, 'TERMINATED')"
          >
            终止
          </el-button>
          <el-button v-if="canDisable" link type="danger" @click="remove(row as ContractVO)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="fetchList"
      @size-change="search"
    />

    <!-- 新增/编辑 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑合同' : '新增合同'" width="640px" :fullscreen="isMobile">
      <el-form label-width="96px">
        <el-form-item label="合同编号">
          <el-input v-model="form.contractNo" maxlength="64" placeholder="留空自动生成，如 HT202608001" />
        </el-form-item>
        <el-form-item label="合同名称" required>
          <el-input v-model="form.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="合同类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in CONTRACT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对方单位" required>
          <el-input v-model="form.partyB" maxlength="128" />
        </el-form-item>
        <el-form-item label="对方联系人">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="form.partyBContact" maxlength="64" placeholder="姓名" style="flex: 1" />
            <el-input v-model="form.partyBPhone" maxlength="32" placeholder="联系电话" style="flex: 1" />
          </div>
        </el-form-item>
        <el-form-item label="合同金额">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签订日期">
          <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="起止日期">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-date-picker
              v-model="form.startDate"
              type="date"
              placeholder="生效日期"
              value-format="YYYY-MM-DD"
              style="flex: 1"
            />
            <el-date-picker
              v-model="form.endDate"
              type="date"
              placeholder="到期日期(空=长期)"
              value-format="YYYY-MM-DD"
              style="flex: 1"
            />
          </div>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.ownerId" filterable clearable placeholder="默认为本人" style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="s in CONTRACT_STATUSES" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="附件">
          <div style="width: 100%">
            <el-button :loading="uploading" @click="pickFile">上传附件</el-button>
            <input
              ref="fileInputRef"
              type="file"
              style="display: none"
              accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar"
              @change="onFilePicked"
            />
            <div v-for="(a, i) in form.attachments" :key="a.url" class="attach-row">
              <el-link :href="a.url" target="_blank" type="primary" :underline="false">{{ a.name }}</el-link>
              <el-button link type="danger" @click="removeAttachment(i)">移除</el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="512" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailDialog" title="合同详情" width="600px" :fullscreen="isMobile">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="合同编号">{{ detail.contractNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="STATUS_META[detail.status]?.tag ?? 'info'" size="small">
            {{ STATUS_META[detail.status]?.label ?? detail.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="合同名称" :span="2">{{ detail.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ TYPE_LABEL[detail.type] ?? detail.type }}</el-descriptions-item>
        <el-descriptions-item label="金额(元)">
          {{ Number(detail.amount ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}
        </el-descriptions-item>
        <el-descriptions-item label="对方单位" :span="2">{{ detail.partyB }}</el-descriptions-item>
        <el-descriptions-item label="对方联系人">{{ detail.partyBContact || '—' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.partyBPhone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="签订日期">{{ detail.signDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ detail.startDate || '—' }}</el-descriptions-item>
        <el-descriptions-item label="到期日期">{{ detail.endDate || '长期' }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detail.ownerName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="归属部门">{{ detail.ownerDeptName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createdBy || '—' }}</el-descriptions-item>
        <el-descriptions-item label="附件" :span="2">
          <span v-if="!detail.attachments?.length">—</span>
          <div v-for="a in detail.attachments" :key="a.url">
            <el-link :href="a.url" target="_blank" type="primary" :underline="false">{{ a.name }}</el-link>
          </div>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="detailDialog = false">关闭</el-button>
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
.stat-row {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.stat-card {
  flex: 1;
  min-width: 140px;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 16px;
}
.stat-card.clickable {
  cursor: pointer;
}
.stat-card.clickable:hover {
  background: #eef2f7;
}
.stat-label {
  font-size: 13px;
  color: #6b7280;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  margin-top: 4px;
}
.stat-value.warn {
  color: #d97706;
}
.stat-value.danger {
  color: #dc2626;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
}
.spacer {
  flex: 1;
}
.attach-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}
</style>
