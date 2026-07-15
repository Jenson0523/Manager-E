<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type CheckboxValueType } from 'element-plus'
import { roleApi, permissionApi } from '@/api/rbac'
import { useAuthStore } from '@/stores/auth'
import type { RoleVO, RoleSaveDTO, PermissionGroupVO } from '@/types/rbac'
import { moduleLabel } from '@/utils/moduleLabels'

const auth = useAuthStore()

/* ---------- Role list ---------- */
const roles = ref<RoleVO[]>([])
const activeRole = ref<RoleVO | null>(null)

async function fetchRoles() {
  roles.value = await roleApi.list()
}

function selectRole(role: RoleVO) {
  activeRole.value = role
  loadPermissions(role.id)
}

/* ---------- Permission matrix ---------- */
const permGroups = ref<PermissionGroupVO[]>([])
const checkedIds = ref<number[]>([])
const permLoading = ref(false)
const saving = ref(false)

async function fetchPermGroups() {
  permGroups.value = await permissionApi.grouped()
}

async function loadPermissions(roleId: number) {
  permLoading.value = true
  try {
    checkedIds.value = await roleApi.permissionIds(roleId)
  } finally {
    permLoading.value = false
  }
}

function allIdsInGroup(group: PermissionGroupVO) {
  return group.permissions.map((p) => p.id)
}

function groupChecked(group: PermissionGroupVO) {
  const ids = allIdsInGroup(group)
  return ids.every((id) => checkedIds.value.includes(id))
}

function groupIndeterminate(group: PermissionGroupVO) {
  const ids = allIdsInGroup(group)
  const count = ids.filter((id) => checkedIds.value.includes(id)).length
  return count > 0 && count < ids.length
}

function toggleGroup(group: PermissionGroupVO, checked: boolean) {
  const ids = allIdsInGroup(group)
  if (checked) {
    checkedIds.value = [...new Set([...checkedIds.value, ...ids])]
  } else {
    checkedIds.value = checkedIds.value.filter((id) => !ids.includes(id))
  }
}

/* 复用其他角色的权限配置作为起点,可继续增删后再保存 */
const copyFromId = ref<number | null>(null)
const copyOptions = computed(() => roles.value.filter((r) => r.id !== activeRole.value?.id))
async function copyFromRole(roleId: number | null) {
  if (roleId == null || !activeRole.value) return
  permLoading.value = true
  try {
    checkedIds.value = await roleApi.permissionIds(roleId)
    const from = roles.value.find((r) => r.id === roleId)
    ElMessage.success(`已引用「${from?.name}」的权限,可调整后点保存生效`)
  } finally {
    permLoading.value = false
    copyFromId.value = null
  }
}

async function savePermissions() {
  if (!activeRole.value) return
  saving.value = true
  try {
    await roleApi.assignPermissions(activeRole.value.id, checkedIds.value)
    ElMessage.success('权限已保存')
  } catch {
    // API error already surfaced by response interceptor
  } finally {
    saving.value = false
  }
}

/* ---------- Role dialog ---------- */
const roleDialog = ref(false)
const roleFormRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const roleForm = reactive<RoleSaveDTO>({
  name: '',
  code: '',
  description: '',
  dataScope: 'self',
})
const roleRules: FormRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}

function openCreate() {
  editingId.value = null
  Object.assign(roleForm, { name: '', code: '', description: '', dataScope: 'self' })
  roleDialog.value = true
}

function openEdit(role: RoleVO) {
  editingId.value = role.id
  Object.assign(roleForm, {
    name: role.name,
    code: role.code,
    description: role.description,
    dataScope: role.dataScope,
  })
  roleDialog.value = true
}

async function submitRole() {
  if (!roleFormRef.value) return
  try {
    await roleFormRef.value.validate()
  } catch {
    return
  }
  try {
    if (editingId.value) {
      await roleApi.update(editingId.value, { ...roleForm })
      ElMessage.success('角色已更新')
    } else {
      await roleApi.create({ ...roleForm })
      ElMessage.success('角色已创建')
    }
    roleDialog.value = false
    fetchRoles()
  } catch {
    // API error already surfaced by response interceptor
  }
}

async function deleteRole(role: RoleVO) {
  try {
    await ElMessageBox.confirm(`确认删除角色「${role.name}」？此操作不可撤销。`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await roleApi.remove(role.id)
    ElMessage.success('已删除')
    if (activeRole.value?.id === role.id) {
      activeRole.value = null
      checkedIds.value = []
    }
    fetchRoles()
  } catch {
    // API error already surfaced by response interceptor
  }
}

/* 复制角色:名称加"副本"、权限一并复制,建相似角色免重配权限 */
async function copyRole(role: RoleVO) {
  try {
    await roleApi.copy(role.id)
    ElMessage.success(`已复制角色「${role.name}」,可编辑副本的名称与权限`)
    fetchRoles()
  } catch {
    // API error already surfaced by response interceptor
  }
}

const noActiveRole = computed(() => !activeRole.value)

onMounted(() => {
  fetchRoles()
  fetchPermGroups()
})
</script>

<template>
  <div class="role-view">
    <el-card class="role-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>角色列表</span>
          <el-button v-if="auth.hasPermission('PERM_role:create')" link type="primary" @click="openCreate">新增角色</el-button>
        </div>
      </template>
      <div
        v-for="role in roles"
        :key="role.id"
        :class="['role-card', { active: activeRole?.id === role.id }]"
        @click="selectRole(role)"
      >
        <div class="role-name">{{ role.name }}</div>
        <div class="role-meta">{{ role.code }}</div>
        <div class="role-actions">
          <el-button v-if="auth.hasPermission('PERM_role:edit')" link type="primary" size="small" @click.stop="openEdit(role)">编辑</el-button>
          <el-button v-if="auth.hasPermission('PERM_role:create')" link type="primary" size="small" @click.stop="copyRole(role)">复制</el-button>
          <el-button
            v-if="!role.isPreset && auth.hasPermission('PERM_role:delete')"
            link
            type="danger"
            size="small"
            @click.stop="deleteRole(role)"
          >删除</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="perm-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>权限配置{{ activeRole ? ` — ${activeRole.name}` : '' }}</span>
          <div class="pane-head-actions">
            <el-select
              v-if="auth.hasPermission('PERM_role:edit')"
              v-model="copyFromId"
              placeholder="复用角色权限"
              size="small"
              clearable
              :disabled="noActiveRole"
              style="width: 150px"
              @change="copyFromRole"
            >
              <el-option v-for="r in copyOptions" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
            <el-button
              v-if="auth.hasPermission('PERM_role:edit')"
              type="primary"
              size="small"
              :disabled="noActiveRole"
              :loading="saving"
              @click="savePermissions"
            >保存</el-button>
          </div>
        </div>
      </template>

      <el-empty v-if="noActiveRole" description="请先选择左侧角色" />

      <div v-else v-loading="permLoading">
        <div v-for="group in permGroups" :key="group.module" class="perm-group">
          <div class="group-header">
            <el-checkbox
              :model-value="groupChecked(group)"
              :indeterminate="groupIndeterminate(group)"
              @change="(v: CheckboxValueType) => toggleGroup(group, v as boolean)"
            >
              <strong>{{ moduleLabel(group.module) }}</strong>
            </el-checkbox>
          </div>
          <el-checkbox-group v-model="checkedIds" class="perm-items">
            <el-checkbox
              v-for="perm in group.permissions"
              :key="perm.id"
              :value="perm.id"
            >{{ perm.name }}</el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="roleDialog" :title="editingId ? '编辑角色' : '新增角色'" width="460px">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="90px">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="roleForm.name" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="roleForm.code" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="roleForm.dataScope">
            <el-option label="全部数据" value="all" />
            <el-option label="本机构数据" value="branch" />
            <el-option label="本部门数据" value="dept" />
            <el-option label="仅本人数据" value="self" />
          </el-select>
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
.role-view {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  align-items: start;
}
@media (max-width: 768px) {
  .role-view {
    grid-template-columns: 1fr;
  }
}
.role-pane,
.perm-pane {
  border-radius: 10px;
}
.pane-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pane-head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.role-card {
  padding: 14px 16px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04);
  transition: all 0.2s ease;
}
.role-card:hover {
  background: #f8faff;
  border-color: #b3d8ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.08);
}
.role-card.active {
  background: #ecf5ff;
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}
.role-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.role-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.role-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}
.role-actions .el-button {
  padding: 4px 8px;
  height: 24px;
}
.perm-group {
  margin-bottom: 24px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 8px;
}
.group-header {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}
.perm-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
}
.perm-items .el-checkbox {
  width: 180px;
  margin-right: 0;
}
.perm-items .el-checkbox__label {
  font-size: 13px;
}
</style>
