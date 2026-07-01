<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/user'
import { roleApi } from '@/api/rbac'
import { deptApi } from '@/api/org'
import { useAuthStore } from '@/stores/auth'
import type { UserVO, UserCreateDTO, UserUpdateDTO } from '@/types/user'
import type { RoleVO } from '@/types/rbac'
import type { DeptTreeVO } from '@/types/org'

const auth = useAuthStore()

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
  const tree = await deptApi.tree()
  deptOptions.value = flattenDepts(tree)
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
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
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
  batchRoleDialog.value = true
}

async function submitBatchStatus(status: number) {
  if (!selectedIds.value.length) return
  const action = status === 1 ? '启用' : '停用'
  await ElMessageBox.confirm(`确认批量${action} ${selectedIds.value.length} 个用户？`, '提示', { type: 'warning' })
  await userApi.batchStatus(selectedIds.value, status)
  ElMessage.success(`已批量${action}`)
  clearSelection()
  fetchList()
}

async function submitBatchRoles() {
  if (!selectedIds.value.length) return
  await userApi.batchRoles(selectedIds.value, batchRoleIds.value)
  ElMessage.success('角色已批量分配')
  batchRoleDialog.value = false
  clearSelection()
  fetchList()
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
  roles.value = await roleApi.list()
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
  createDialog.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  await userApi.create({ ...createForm })
  ElMessage.success('用户创建成功')
  createDialog.value = false
  fetchList()
}

async function toggleStatus(row: UserVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}用户「${row.realName}」？`, '提示', { type: 'warning' })
  await userApi.changeStatus(row.id, next)
  ElMessage.success(`已${action}`)
  fetchList()
}

async function resetPassword(row: UserVO) {
  try {
    const { value } = await ElMessageBox.prompt(
      `为用户「${row.realName}」设置新密码`,
      '重置密码',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /.{6,}/,
        inputErrorMessage: '密码至少 6 位',
        inputType: 'password',
      }
    )
    await userApi.resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch {
    // cancelled
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
  await editFormRef.value.validate()
  await userApi.update(editingId.value, { ...editForm })
  ElMessage.success('用户信息已更新')
  editDialog.value = false
  fetchList()
}

function openRoleAssign(row: UserVO) {
  roleTarget.value = row
  selectedRoleIds.value = [...(row.roleIds ?? [])]
  roleDialog.value = true
}

async function saveRoles() {
  if (!roleTarget.value) return
  await userApi.assignRoles(roleTarget.value.id, selectedRoleIds.value)
  ElMessage.success('角色已更新')
  roleDialog.value = false
  fetchList()
}

onMounted(() => {
  fetchList()
  fetchRoles()
  fetchDepts()
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
      <el-button :loading="exporting" @click="exportList">导出</el-button>
      <el-button v-if="auth.hasPermission('PERM_user:create')" type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <div v-if="selectedIds.length" class="batch-bar">
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
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="(row as UserVO).status === 1 ? 'success' : 'info'">
            {{ (row as UserVO).status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-if="auth.hasPermission('PERM_user:edit')" link type="primary" @click="openEdit(row as UserVO)">编辑</el-button>
          <el-button v-if="auth.hasPermission('PERM_user:edit')" link type="primary" @click="openRoleAssign(row as UserVO)">分配角色</el-button>
          <el-button v-if="auth.hasPermission('PERM_user:edit')" link type="primary" @click="resetPassword(row as UserVO)">重置密码</el-button>
          <el-button v-if="auth.hasPermission('PERM_user:edit')" link type="primary" @click="toggleStatus(row as UserVO)">
            {{ (row as UserVO).status === 1 ? '停用' : '启用' }}
          </el-button>
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
          <el-checkbox-group v-model="createForm.roleIds">
            <el-checkbox v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}</el-checkbox>
          </el-checkbox-group>
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
      <el-checkbox-group v-model="selectedRoleIds">
        <el-checkbox v-for="r in roles" :key="r.id" :label="r.id" style="display: block; margin: 6px 0">
          {{ r.name }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchRoleDialog" title="批量分配角色" width="420px">
      <p style="margin-bottom: 12px; color: #6b7280">将为已选 {{ selectedIds.length }} 个用户统一分配以下角色：</p>
      <el-checkbox-group v-model="batchRoleIds">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id" style="display: block; margin: 6px 0">
          {{ r.name }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="batchRoleDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBatchRoles">确定</el-button>
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
</style>
