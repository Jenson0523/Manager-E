<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { platformRoleApi, platformUserApi } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'
import type { PlatformRoleVO, PlatformUserDTO, PlatformUserVO } from '@/types/platform'
import { isStrongPassword, strongPasswordPattern, PASSWORD_TIP } from '@/utils/password'

const auth = useAuthStore()

const loading = ref(false)
const list = ref<PlatformUserVO[]>([])
const query = reactive({
  keyword: '',
  page: 1,
  size: 10,
})

const pagedList = computed(() => {
  const start = (query.page - 1) * query.size
  return list.value.slice(start, start + query.size)
})

const roles = ref<PlatformRoleVO[]>([])

async function fetchRoles() {
  roles.value = await platformRoleApi.list()
}

async function fetchList() {
  loading.value = true
  try {
    list.value = await platformUserApi.list({ keyword: query.keyword || undefined })
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  fetchList()
}

const userDialog = ref(false)
const userFormRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const userForm = reactive<PlatformUserDTO>({
  username: '',
  realName: '',
  phone: '',
  email: '',
  password: '',
  roleIds: [],
})

const userRules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: [
    {
      validator: (_rule, value, callback) => {
        if (!editingId.value && !value) callback(new Error('请输入初始密码'))
        else if (value && !isStrongPassword(value)) callback(new Error(PASSWORD_TIP))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function resetForm() {
  Object.assign(userForm, {
    username: '',
    realName: '',
    phone: '',
    email: '',
    password: '',
    roleIds: [],
  })
}

function openCreate() {
  editingId.value = null
  resetForm()
  userDialog.value = true
}

function openEdit(row: PlatformUserVO) {
  editingId.value = row.id
  Object.assign(userForm, {
    username: row.username,
    realName: row.realName,
    phone: row.phone ?? '',
    email: row.email ?? '',
    password: '',
    roleIds: [...(row.roleIds ?? [])],
  })
  userDialog.value = true
}

async function submitUser() {
  if (!userFormRef.value) return
  try {
    await userFormRef.value.validate()
  } catch {
    return
  }
  try {
    if (editingId.value) {
      await platformUserApi.update(editingId.value, {
        username: userForm.username,
        realName: userForm.realName,
        phone: userForm.phone,
        email: userForm.email,
        roleIds: userForm.roleIds,
      })
      ElMessage.success('平台账号已更新')
    } else {
      await platformUserApi.create({ ...userForm })
      ElMessage.success('平台账号已创建')
    }
    userDialog.value = false
    await fetchList()
  } catch {
    // API error already surfaced by response interceptor
  }
}

async function toggleStatus(row: PlatformUserVO) {
  if (row.isSuperAdmin === 1) return
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${action}平台账号「${row.realName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await platformUserApi.changeStatus(row.id, next)
    ElMessage.success(`已${action}`)
    await fetchList()
  } catch {
    // API error already surfaced by response interceptor
  }
}

async function resetPassword(row: PlatformUserVO) {
  let value: string
  try {
    const res = await ElMessageBox.prompt(`为平台账号「${row.realName}」设置新密码`, '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: strongPasswordPattern,
      inputErrorMessage: PASSWORD_TIP,
      inputType: 'password',
    })
    value = res.value
  } catch {
    return // cancelled
  }
  try {
    await platformUserApi.resetPassword(row.id, value)
    ElMessage.success('密码已重置')
  } catch {
    // API error already surfaced by response interceptor
  }
}

async function deleteUser(row: PlatformUserVO) {
  if (row.isSuperAdmin === 1) return
  try {
    await ElMessageBox.confirm(`确认删除平台账号「${row.realName}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await platformUserApi.remove(row.id)
    ElMessage.success('平台账号已删除')
    await fetchList()
  } catch {
    // API error already surfaced by response interceptor
  }
}

onMounted(() => {
  fetchRoles()
  fetchList()
})
</script>

<template>
  <div class="platform-user-page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索姓名/账号/手机"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button
        v-if="auth.hasPermission('PERM_platform:user:create')"
        type="primary"
        @click="openCreate"
      >
        新增平台账号
      </el-button>
    </div>

    <el-table v-loading="loading" :data="pagedList" stripe>
      <el-table-column prop="realName" label="姓名" width="130" />
      <el-table-column prop="username" label="账号" width="160" />
      <el-table-column prop="phone" label="手机" width="140" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column label="角色" min-width="180">
        <template #default="{ row }">
          <el-tag
            v-for="name in ((row as PlatformUserVO).roleNames ?? [])"
            :key="name"
            size="small"
            style="margin-right: 4px"
          >
            {{ name }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(row as PlatformUserVO).status === 1 ? 'success' : 'info'">
            {{ (row as PlatformUserVO).status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag v-if="(row as PlatformUserVO).isSuperAdmin === 1" type="warning">内置</el-tag>
          <span v-else>平台账号</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="auth.hasPermission('PERM_platform:user:edit')"
            link
            type="primary"
            @click="openEdit(row as PlatformUserVO)"
          >
            编辑
          </el-button>
          <el-button
            v-if="auth.hasPermission('PERM_platform:user:reset_pwd')"
            link
            type="primary"
            @click="resetPassword(row as PlatformUserVO)"
          >
            重置密码
          </el-button>
          <el-button
            v-if="!((row as PlatformUserVO).isSuperAdmin === 1) && auth.hasPermission('PERM_platform:user:edit')"
            link
            type="primary"
            @click="toggleStatus(row as PlatformUserVO)"
          >
            {{ (row as PlatformUserVO).status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button
            v-if="!((row as PlatformUserVO).isSuperAdmin === 1) && auth.hasPermission('PERM_platform:user:delete')"
            link
            type="danger"
            @click="deleteUser(row as PlatformUserVO)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next"
      :total="list.length"
      :current-page="query.page"
      :page-size="query.size"
      @current-change="(p: number) => { query.page = p }"
    />

    <el-dialog v-model="userDialog" :title="editingId ? '编辑平台账号' : '新增平台账号'" width="520px">
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="96px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="userForm.username" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="userForm.realName" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="userForm.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" prop="password">
          <el-input v-model="userForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-checkbox-group v-model="userForm.roleIds">
            <el-checkbox v-for="role in roles" :key="role.id" :value="role.id">
              {{ role.name }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" @click="submitUser">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.platform-user-page {
  background: #fff;
  border-radius: 8px;
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
