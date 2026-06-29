<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/user'
import { roleApi } from '@/api/rbac'
import type { UserVO, UserCreateDTO } from '@/types/user'
import type { RoleVO } from '@/types/rbac'

const loading = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '', roleId: undefined as number | undefined })

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
  branchId: undefined,
  roleIds: [],
})
const createRules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
}

const roleDialog = ref(false)
const roleTarget = ref<UserVO | null>(null)
const selectedRoleIds = ref<number[]>([])

async function fetchList() {
  loading.value = true
  try {
    const res = await userApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
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
})
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索姓名/账号/手机"
        clearable
        style="width: 220px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select
        v-model="query.roleId"
        placeholder="按角色筛选"
        clearable
        style="width: 160px"
        @change="onSearch"
      >
        <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="realName" label="姓名" width="120" />
      <el-table-column prop="username" label="账号" width="140" />
      <el-table-column prop="phone" label="手机号" width="140" />
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
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRoleAssign(row as UserVO)">分配角色</el-button>
          <el-button link type="primary" @click="resetPassword(row as UserVO)">重置密码</el-button>
          <el-button link type="primary" @click="toggleStatus(row as UserVO)">
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
        <el-form-item label="角色">
          <el-checkbox-group v-model="createForm.roleIds">
            <el-checkbox v-for="r in roles" :key="r.id" :label="r.id">{{ r.name }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
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
</style>
