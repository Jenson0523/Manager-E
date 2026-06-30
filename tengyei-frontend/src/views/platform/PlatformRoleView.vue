<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type CheckboxValueType, type FormInstance, type FormRules } from 'element-plus'
import { platformRoleApi } from '@/api/platform'
import { useAuthStore } from '@/stores/auth'
import type { PermissionGroupVO, PlatformRoleDTO, PlatformRoleVO } from '@/types/platform'

const auth = useAuthStore()

const roles = ref<PlatformRoleVO[]>([])
const activeRole = ref<PlatformRoleVO | null>(null)
const roleLoading = ref(false)

async function fetchRoles() {
  roleLoading.value = true
  try {
    roles.value = await platformRoleApi.list()
    if (!activeRole.value && roles.value.length) selectRole(roles.value[0])
  } finally {
    roleLoading.value = false
  }
}

function selectRole(role: PlatformRoleVO) {
  activeRole.value = role
  loadPermissions(role.id)
}

const permGroups = ref<PermissionGroupVO[]>([])
const checkedIds = ref<number[]>([])
const permLoading = ref(false)
const saving = ref(false)

async function fetchPermGroups() {
  permGroups.value = await platformRoleApi.permissions()
}

async function loadPermissions(roleId: number) {
  permLoading.value = true
  try {
    checkedIds.value = await platformRoleApi.permissionIds(roleId)
  } finally {
    permLoading.value = false
  }
}

function allIdsInGroup(group: PermissionGroupVO) {
  return group.permissions.map((p) => p.id)
}

function groupChecked(group: PermissionGroupVO) {
  const ids = allIdsInGroup(group)
  return ids.length > 0 && ids.every((id) => checkedIds.value.includes(id))
}

function groupIndeterminate(group: PermissionGroupVO) {
  const ids = allIdsInGroup(group)
  const count = ids.filter((id) => checkedIds.value.includes(id)).length
  return count > 0 && count < ids.length
}

function toggleGroup(group: PermissionGroupVO, checked: boolean) {
  const ids = allIdsInGroup(group)
  checkedIds.value = checked
    ? [...new Set([...checkedIds.value, ...ids])]
    : checkedIds.value.filter((id) => !ids.includes(id))
}

async function savePermissions() {
  if (!activeRole.value || activeRole.value.isPreset) return
  saving.value = true
  try {
    await platformRoleApi.assignPermissions(activeRole.value.id, checkedIds.value)
    ElMessage.success('权限已保存')
  } finally {
    saving.value = false
  }
}

const roleDialog = ref(false)
const roleFormRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const roleForm = reactive<PlatformRoleDTO>({
  name: '',
  code: '',
  description: '',
})
const roleRules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  Object.assign(roleForm, { name: '', code: '', description: '' })
  roleDialog.value = true
}

function openEdit(role: PlatformRoleVO) {
  editingId.value = role.id
  Object.assign(roleForm, {
    name: role.name,
    code: role.code,
    description: role.description ?? '',
  })
  roleDialog.value = true
}

async function submitRole() {
  if (!roleFormRef.value) return
  await roleFormRef.value.validate()
  if (editingId.value) {
    await platformRoleApi.update(editingId.value, { ...roleForm })
    ElMessage.success('角色已更新')
  } else {
    await platformRoleApi.create({ ...roleForm })
    ElMessage.success('角色已创建')
  }
  roleDialog.value = false
  await fetchRoles()
}

async function deleteRole(role: PlatformRoleVO) {
  await ElMessageBox.confirm(`确认删除平台角色「${role.name}」？`, '提示', { type: 'warning' })
  await platformRoleApi.remove(role.id)
  ElMessage.success('角色已删除')
  if (activeRole.value?.id === role.id) {
    activeRole.value = null
    checkedIds.value = []
  }
  await fetchRoles()
}

const canEditActiveRole = computed(() => {
  return !!activeRole.value && !activeRole.value.isPreset && auth.hasPermission('PERM_platform:role:edit')
})

onMounted(() => {
  fetchPermGroups()
  fetchRoles()
})
</script>

<template>
  <div class="platform-role-view">
    <el-card class="role-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>平台角色</span>
          <el-button
            v-if="auth.hasPermission('PERM_platform:role:create')"
            link
            type="primary"
            @click="openCreate"
          >
            新增角色
          </el-button>
        </div>
      </template>

      <div v-loading="roleLoading" class="role-list">
        <el-empty v-if="!roles.length" description="暂无角色" />
        <div
          v-for="role in roles"
          :key="role.id"
          :class="['role-item', { active: activeRole?.id === role.id }]"
          @click="selectRole(role)"
        >
          <div class="role-main">
            <div>
              <div class="role-name">{{ role.name }}</div>
              <div class="role-meta">{{ role.code }}</div>
            </div>
            <el-tag v-if="role.isPreset" size="small" type="info">内置</el-tag>
          </div>
          <div v-if="role.description" class="role-desc">{{ role.description }}</div>
          <div class="role-actions">
            <el-button
              v-if="!role.isPreset && auth.hasPermission('PERM_platform:role:edit')"
              link
              type="primary"
              size="small"
              @click.stop="openEdit(role)"
            >
              编辑
            </el-button>
            <el-button
              v-if="!role.isPreset && auth.hasPermission('PERM_platform:role:delete')"
              link
              type="danger"
              size="small"
              @click.stop="deleteRole(role)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="perm-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>权限配置{{ activeRole ? ` - ${activeRole.name}` : '' }}</span>
          <el-button
            type="primary"
            size="small"
            :disabled="!canEditActiveRole"
            :loading="saving"
            @click="savePermissions"
          >
            保存
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="activeRole?.isPreset"
        class="preset-alert"
        type="info"
        :closable="false"
        show-icon
        title="内置平台角色受保护，权限不可修改。"
      />
      <el-empty v-if="!activeRole" description="请先选择左侧角色" />

      <div v-else v-loading="permLoading">
        <div v-for="group in permGroups" :key="group.module" class="perm-group">
          <div class="group-header">
            <el-checkbox
              :model-value="groupChecked(group)"
              :indeterminate="groupIndeterminate(group)"
              :disabled="!canEditActiveRole"
              @change="(v: CheckboxValueType) => toggleGroup(group, v as boolean)"
            >
              <strong>{{ group.module }}</strong>
            </el-checkbox>
          </div>
          <el-checkbox-group v-model="checkedIds" class="perm-items" :disabled="!canEditActiveRole">
            <el-checkbox v-for="perm in group.permissions" :key="perm.id" :value="perm.id">
              {{ perm.name }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="roleDialog" :title="editingId ? '编辑平台角色' : '新增平台角色'" width="460px">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="roleForm.name" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="roleForm.code" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" @click="submitRole">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.platform-role-view {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: start;
}
.role-pane,
.perm-pane {
  border-radius: 8px;
}
.pane-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.role-list {
  min-height: 240px;
}
.role-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 6px;
  border: 1px solid transparent;
  transition: background 0.15s, border-color 0.15s;
}
.role-item:hover {
  background: #f5f7fa;
}
.role-item.active {
  background: #ecf5ff;
  border-color: #b3d8ff;
}
.role-main {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.role-name {
  font-weight: 600;
  font-size: 14px;
}
.role-meta,
.role-desc {
  font-size: 12px;
  color: #909399;
}
.role-desc {
  margin-top: 4px;
}
.role-actions {
  margin-top: 4px;
}
.preset-alert {
  margin-bottom: 14px;
}
.perm-group {
  margin-bottom: 20px;
}
.group-header {
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #ebeef5;
}
.perm-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 0;
  padding-left: 16px;
}
.perm-items .el-checkbox {
  width: 220px;
}
</style>
