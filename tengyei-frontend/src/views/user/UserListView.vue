<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import { Edit, ArrowDown } from '@element-plus/icons-vue'
import { userApi } from '@/api/user'
import { roleApi, permissionApi } from '@/api/rbac'
import { deptApi } from '@/api/org'
import { useAuthStore } from '@/stores/auth'
import type { UserVO, UserCreateDTO, UserUpdateDTO } from '@/types/user'
import type { RoleVO } from '@/types/rbac'
import type { DeptTreeVO } from '@/types/org'
import { strongPasswordRule, strongPasswordPattern, PASSWORD_TIP } from '@/utils/password'
import { downloadExcel } from '@/utils/download'
import { moduleLabel } from '@/utils/moduleLabels'

const auth = useAuthStore()
const canCreate = computed(() => auth.hasPermission('PERM_user:create'))
const canEdit = computed(() => auth.hasPermission('PERM_user:edit'))
// user:export 权限不存在(种子未定义),后端导出接口按 user:view 放行,这里与后端对齐
const canExport = computed(() => auth.hasPermission('PERM_user:view'))

const loading = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)
const query = reactive({
  page: 1, size: 20, keyword: '',
  deptId: undefined as number | undefined,
  roleId: undefined as number | undefined,
})

/* 部门扁平列表（用于筛选下拉） */
const deptOptions = ref<{ id: number; label: string }[]>([])

function flattenDepts(nodes: DeptTreeVO[], level = 0): { id: number; label: string }[] {
  const result: { id: number; label: string }[] = []
  for (const n of nodes) {
    result.push({ id: n.id, label: '　'.repeat(level) + n.name })
    if (n.children?.length) result.push(...flattenDepts(n.children, level + 1))
  }
  return result
}

async function fetchDepts() {
  // 部门树只用于筛选下拉/表单选择,单独的 dept:view 权限;只给了"查看人员"的账号
  // 没有这个权限是正常情况,静默留空,不当作错误弹窗打扰用户
  if (!auth.hasPermission('PERM_dept:view')) return
  try {
    const tree = await deptApi.tree()
    deptOptions.value = flattenDepts(tree)
  } catch {
    // 同上,忽略
  }
}

const roles = ref<RoleVO[]>([])

const createDialog = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<UserCreateDTO>({
  username: '',
  realName: '',
  phone: '',
  email: '',
  password: '',
  deptId: undefined,
  deptIds: [],
  branchId: undefined,
  roleIds: [],
})
const createRules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [strongPasswordRule()],
}

/* ---- 编辑 ---- */
const editDialog = ref(false)
const editFormRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const editForm = reactive<UserUpdateDTO>({
  realName: '',
  phone: '',
  email: '',
  deptId: undefined,
  deptIds: [],
  branchId: undefined,
})
const editRules: FormRules = {
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
}

const roleDialog = ref(false)
const roleTarget = ref<UserVO | null>(null)
const selectedRoleIds = ref<number[]>([])

const exporting = ref(false)
async function exportList() {
  exporting.value = true
  try {
    await userApi.export({
      keyword: query.keyword || undefined,
      deptId: query.deptId,
    })
  } finally {
    exporting.value = false
  }
}

/* ---- Excel 批量导入 ---- */
const importDialog = ref(false)
const importing = ref(false)
const importResult = ref<{ total: number; success: number; failed: number; errors: { row: number; username: string; msg: string }[] } | null>(null)

function openImport() {
  importResult.value = null
  importDialog.value = true
}
async function downloadImportTemplate() {
  await downloadExcel('/v1/users/import-template', {}, '人员导入模板.xlsx')
}
async function onImportFile(opt: UploadRequestOptions) {
  importing.value = true
  try {
    importResult.value = await userApi.importUsers(opt.file as File)
    if (importResult.value.failed === 0) {
      ElMessage.success(`导入完成,成功 ${importResult.value.success} 人`)
    } else {
      ElMessage.warning(`导入完成:成功 ${importResult.value.success} 人,失败 ${importResult.value.failed} 行,详见下方明细`)
    }
    fetchList()
  } finally {
    importing.value = false
  }
}

/* ---- 批量操作 ---- */
const selectedIds = ref<number[]>([])

function onSelectionChange(rows: UserVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

function clearSelection() {
  selectedIds.value = []
}

const batchRoleDialog = ref(false)
const batchRoleIds = ref<number[]>([])

function openBatchRoles() {
  batchRoleIds.value = []
  batchRoleMergedPreview.value = []
  batchRoleDialog.value = true
}

async function submitBatchStatus(status: number) {
  if (!selectedIds.value.length) return
  const action = status === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认批量${action} ${selectedIds.value.length} 个用户？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await userApi.batchStatus(selectedIds.value, status)
    ElMessage.success(`已批量${action}`)
    clearSelection()
    fetchList()
  } catch {
    // API error already surfaced by response interceptor
  }
}

async function submitBatchRoles() {
  if (!selectedIds.value.length) return
  try {
    await userApi.batchRoles(selectedIds.value, batchRoleIds.value)
    ElMessage.success('角色已批量分配')
    batchRoleDialog.value = false
    clearSelection()
    fetchList()
  } catch {
    // API error already surfaced by response interceptor
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await userApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      deptId: query.deptId,
      roleId: query.roleId,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function fetchRoles() {
  // 角色列表只用于筛选下拉/分配角色,单独的 role:view 权限,道理同 fetchDepts
  if (!auth.hasPermission('PERM_role:view')) return
  try {
    roles.value = await roleApi.list()
  } catch {
    // 忽略
  }
}

function onSearch() {
  query.page = 1
  fetchList()
}

function openCreate() {
  Object.assign(createForm, {
    username: '',
    realName: '',
    phone: '',
    email: '',
    password: '',
    deptId: undefined,
    deptIds: [],
    branchId: undefined,
    roleIds: [],
  })
  createRoleMergedPreview.value = []
  createDialog.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  try {
    await createFormRef.value.validate()
  } catch {
    return
  }
  try {
    await userApi.create({ ...createForm })
    ElMessage.success('用户创建成功')
    createDialog.value = false
    fetchList()
    fetchQuota()
  } catch {
    // API error already surfaced by response interceptor
  }
}

async function toggleStatus(row: UserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}用户「${row.realName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await userApi.changeStatus(row.id, next)
    ElMessage.success(`已${action}`)
    fetchList()
  } catch {
    // API error already surfaced by response interceptor
  }
}

async function resetPassword(row: UserVO) {
  let value: string
  try {
    const res = await ElMessageBox.prompt(
      `为用户「${row.realName}」设置新密码`,
      '重置密码',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: strongPasswordPattern,
        inputErrorMessage: PASSWORD_TIP,
        inputType: 'password',
      }
    )
    value = res.value
  } catch {
    return // cancelled
  }
  try {
    await userApi.resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch {
    // API error already surfaced by response interceptor
  }
}

function openEdit(row: UserVO) {
  editingId.value = row.id
  Object.assign(editForm, {
    realName: row.realName,
    phone: row.phone,
    email: row.email ?? '',
    deptId: row.deptId,
    deptIds: row.deptIds ?? [],
    branchId: row.branchId,
  })
  editDialog.value = true
}

async function submitEdit() {
  if (!editFormRef.value || editingId.value == null) return
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  try {
    await userApi.update(editingId.value, { ...editForm })
    ElMessage.success('用户信息已更新')
    editDialog.value = false
    fetchList()
  } catch {
    // API error already surfaced by response interceptor
  }
}

function onRowCommand(cmd: string, row: UserVO) {
  if (cmd === 'role') openRoleAssign(row)
  else if (cmd === 'reset') resetPassword(row)
  else if (cmd === 'toggle') toggleStatus(row)
}

function openRoleAssign(row: UserVO) {
  roleTarget.value = row
  selectedRoleIds.value = [...(row.roleIds ?? [])]
  roleDialog.value = true
  refreshRoleMergedPreview()
}

/* 多角色叠加权限预览:超过一个角色时,提示合并后实际拥有的权限(避免误留额外角色导致权限意外扩大——
   这正是排查过一次真实事故的根因) */
const permIdToLabel = ref<Map<number, { module: string; name: string }>>(new Map())
const rolePermCache = new Map<number, number[]>()
async function ensurePermIndex() {
  if (permIdToLabel.value.size) return
  const groups = await permissionApi.grouped()
  const m = new Map<number, { module: string; name: string }>()
  for (const g of groups) for (const p of g.permissions) m.set(p.id, { module: g.module, name: p.name })
  permIdToLabel.value = m
}
async function mergedPreviewFor(roleIds: number[]): Promise<string[]> {
  if (roleIds.length < 2) return []
  // 仅是辅助预览,没有 role:view 权限时静默跳过,不弹错误打断勾选操作
  if (!auth.hasPermission('PERM_role:view')) return []
  try {
    await ensurePermIndex()
  } catch {
    return []
  }
  const missing = roleIds.filter((id) => !rolePermCache.has(id))
  if (missing.length) {
    const results = await Promise.all(missing.map((id) => roleApi.permissionIds(id)))
    missing.forEach((id, i) => rolePermCache.set(id, results[i]))
  }
  const union = new Set<number>()
  roleIds.forEach((id) => rolePermCache.get(id)?.forEach((pid) => union.add(pid)))
  const byModule = new Map<string, string[]>()
  union.forEach((pid) => {
    const info = permIdToLabel.value.get(pid)
    if (!info) return
    const arr = byModule.get(info.module) ?? []
    arr.push(info.name)
    byModule.set(info.module, arr)
  })
  return [...byModule.entries()].map(([mod, names]) => `${moduleLabel(mod)}: ${names.join('、')}`)
}

const roleMergedPreview = ref<string[]>([])
async function refreshRoleMergedPreview() {
  roleMergedPreview.value = await mergedPreviewFor(selectedRoleIds.value)
}
const batchRoleMergedPreview = ref<string[]>([])
async function refreshBatchRoleMergedPreview() {
  batchRoleMergedPreview.value = await mergedPreviewFor(batchRoleIds.value)
}
const createRoleMergedPreview = ref<string[]>([])
async function refreshCreateRoleMergedPreview() {
  createRoleMergedPreview.value = await mergedPreviewFor(createForm.roleIds ?? [])
}

async function saveRoles() {
  if (!roleTarget.value) return
  try {
    await userApi.assignRoles(roleTarget.value.id, selectedRoleIds.value)
    ElMessage.success('角色已更新')
    roleDialog.value = false
    fetchList()
  } catch {
    // API error already surfaced by response interceptor
  }
}

const quota = ref<{ used: number; max: number | null }>({ used: 0, max: null })
async function fetchQuota() {
  quota.value = await userApi.quota()
}

onMounted(() => {
  fetchList()
  fetchRoles()
  fetchDepts()
  fetchQuota()
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索姓名/账号/手机"
        clearable
        style="width: 200px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select
        v-model="query.deptId"
        placeholder="按部门筛选"
        clearable
        style="width: 150px"
        @change="onSearch"
      >
        <el-option v-for="d in deptOptions" :key="d.id" :label="d.label" :value="d.id" />
      </el-select>
      <el-select
        v-model="query.roleId"
        placeholder="按角色筛选"
        clearable
        style="width: 150px"
        @change="onSearch"
      >
        <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button :disabled="!canExport" :loading="exporting" @click="exportList">导出</el-button>
      <el-button v-if="canCreate" @click="openImport">导入</el-button>
      <el-button v-if="canCreate" type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <el-progress
      v-if="quota.max"
      :percentage="Math.min(100, Math.round((quota.used / quota.max) * 100))"
      :status="quota.used >= quota.max ? 'exception' : undefined"
      :format="() => `人员用量 ${quota.used}/${quota.max}`"
      style="margin-bottom: 12px"
    />

    <div v-if="selectedIds.length && canEdit" class="batch-bar">
      <span>已选 {{ selectedIds.length }} 条</span>
      <el-button size="small" type="success" @click="submitBatchStatus(1)">批量启用</el-button>
      <el-button size="small" type="warning" @click="submitBatchStatus(0)">批量停用</el-button>
      <el-button size="small" type="primary" @click="openBatchRoles">批量分配角色</el-button>
      <el-button size="small" @click="clearSelection">取消选择</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe @selection-change="onSelectionChange">
      <el-table-column type="selection" width="50" />
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="username" label="账号" width="140" />
      <el-table-column prop="phone" label="手机号" width="140" />
      <el-table-column label="部门" min-width="160">
        <template #default="{ row }">
          <el-tag
            v-for="name in ((row as UserVO).deptNames ?? [])"
            :key="name"
            size="small"
            type="info"
            style="margin-right: 4px"
          >{{ name }}</el-tag>
          <span v-if="!((row as UserVO).deptNames ?? []).length" style="color: #999">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <el-tag
            v-for="name in ((row as UserVO).roleNames ?? [])"
            :key="name"
            size="small"
            style="margin-right: 4px"
          >{{ name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag
            :type="(row as UserVO).status === 1 ? 'success' : 'info'"
            effect="dark"
            round
            size="small"
            style="font-weight: 500; padding: 2px 12px"
          >
            {{ (row as UserVO).status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <div v-if="auth.hasPermission('PERM_user:edit')" class="action-btns">
            <el-button link type="primary" size="small" @click="openEdit(row as UserVO)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-dropdown trigger="click" @command="(cmd: string) => onRowCommand(cmd, row as UserVO)">
              <el-button link type="primary" size="small" class="more-btn">
                更多<el-icon><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="role">分配角色</el-dropdown-item>
                  <el-dropdown-item command="reset">重置密码</el-dropdown-item>
                  <el-dropdown-item command="toggle" divided>
                    <span :style="{ color: (row as UserVO).status === 1 ? 'var(--el-color-danger)' : 'var(--el-color-success)' }">
                      {{ (row as UserVO).status === 1 ? '停用' : '启用' }}
                    </span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="total"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="(p: number) => { query.page = p; fetchList() }"
    />

    <el-dialog v-model="createDialog" title="新增用户" width="520px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="createForm.realName" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="createForm.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="createForm.deptIds" multiple clearable placeholder="选择部门" style="width: 100%">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.label" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="createForm.roleIds" @change="refreshCreateRoleMergedPreview">
            <el-checkbox v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}</el-checkbox>
          </el-checkbox-group>
          <div v-if="createRoleMergedPreview.length" class="role-merge-preview">
            <div class="role-merge-title">勾选多个角色,合并后将拥有以下权限：</div>
            <div v-for="line in createRoleMergedPreview" :key="line">{{ line }}</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editDialog" title="编辑用户信息" width="480px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="editForm.realName" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="editForm.deptIds" multiple clearable placeholder="选择部门" style="width: 100%">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.label" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="分配角色" width="420px">
      <el-checkbox-group v-model="selectedRoleIds" @change="refreshRoleMergedPreview">
        <el-checkbox v-for="r in roles" :key="r.id" :label="r.id" style="display: block; margin: 6px 0">
          {{ r.name }}
        </el-checkbox>
      </el-checkbox-group>
      <div v-if="roleMergedPreview.length" class="role-merge-preview">
        <div class="role-merge-title">勾选多个角色,合并后将拥有以下权限：</div>
        <div v-for="line in roleMergedPreview" :key="line">{{ line }}</div>
      </div>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchRoleDialog" title="批量分配角色" width="420px">
      <p style="margin-bottom: 12px; color: #6b7280">将为已选 {{ selectedIds.length }} 个用户统一分配以下角色：</p>
      <el-checkbox-group v-model="batchRoleIds" @change="refreshBatchRoleMergedPreview">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" style="display: block; margin: 6px 0">
          {{ r.name }}
        </el-checkbox>
      </el-checkbox-group>
      <div v-if="batchRoleMergedPreview.length" class="role-merge-preview">
        <div class="role-merge-title">勾选多个角色,合并后将拥有以下权限：</div>
        <div v-for="line in batchRoleMergedPreview" :key="line">{{ line }}</div>
      </div>
      <template #footer>
        <el-button @click="batchRoleDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBatchRoles">确定</el-button>
      </template>
    </el-dialog>

    <!-- Excel 批量导入 -->
    <el-dialog v-model="importDialog" title="批量导入人员" width="560px">
      <p style="font-size: 13px; color: #606266; margin: 0 0 12px">
        1. <el-button link type="primary" @click="downloadImportTemplate">下载导入模板</el-button>
        按模板填写(姓名/账号/初始密码/手机必填,角色填角色名称,多个用逗号分隔)<br />
        2. 上传填好的文件,单次最多 500 行;失败行不影响成功行,失败原因见下方明细
      </p>
      <el-upload
        drag
        accept=".xlsx,.xls"
        :show-file-list="false"
        :http-request="onImportFile"
        :disabled="importing"
      >
        <div style="padding: 24px 0">
          {{ importing ? '导入中...' : '点击或拖拽 Excel 文件到此处上传' }}
        </div>
      </el-upload>
      <div v-if="importResult" style="margin-top: 14px">
        <el-alert
          :type="importResult.failed === 0 ? 'success' : 'warning'"
          :title="`共 ${importResult.total} 行:成功 ${importResult.success},失败 ${importResult.failed}`"
          :closable="false"
        />
        <el-table v-if="importResult.errors.length" :data="importResult.errors" size="small" max-height="220" style="margin-top: 8px">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="username" label="账号" width="140" />
          <el-table-column prop="msg" label="失败原因" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="importDialog = false">关闭</el-button>
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
.role-merge-preview {
  margin-top: 10px;
  padding: 8px 12px;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 6px;
  font-size: 12px;
  color: #b88230;
  line-height: 1.8;
}
.role-merge-title {
  font-weight: 600;
  margin-bottom: 2px;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #eff6ff;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #374151;
}
.action-btns {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.action-btns .el-icon {
  font-size: 13px;
  margin-right: 2px;
}
.more-btn .el-icon {
  margin-left: 2px;
  margin-right: 0;
}
</style>
