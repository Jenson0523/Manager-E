<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { moduleApi, type ModuleVO, type ModuleDTO } from '@/api/module'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canManage = computed(() => auth.hasPermission('PERM_platform:module:manage') || auth.isSuperAdmin)

const loading = ref(false)
const list = ref<ModuleVO[]>([])
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)

// Dialog
const dialogVisible = ref(false)
const dialogTitle = ref('')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = ref<ModuleDTO>({
  moduleCode: '',
  moduleName: '',
  version: '1.0.0',
  entryUrl: '',
  menuConfig: '[]',
  permissions: '[]',
})

const filteredList = computed(() => list.value)

async function fetchList() {
  loading.value = true
  try {
    list.value = await moduleApi.list({
      keyword: keyword.value || undefined,
      status: statusFilter.value,
    })
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  keyword.value = ''
  statusFilter.value = undefined
  fetchList()
}

function openCreate() {
  dialogTitle.value = '注册新模块'
  editingId.value = null
  form.value = {
    moduleCode: '',
    moduleName: '',
    version: '1.0.0',
    entryUrl: '',
    menuConfig: '[]',
    permissions: '[]',
  }
  dialogVisible.value = true
}

function openEdit(row: ModuleVO) {
  dialogTitle.value = '编辑模块'
  editingId.value = row.id
  form.value = {
    moduleCode: row.moduleCode,
    moduleName: row.moduleName,
    version: row.version,
    entryUrl: row.entryUrl,
    menuConfig: row.menuConfig,
    permissions: row.permissions,
  }
  dialogVisible.value = true
}

async function submit() {
  // Basic validation
  if (!form.value.moduleCode.trim()) {
    ElMessage.warning('请输入模块编码')
    return
  }
  if (!form.value.moduleName.trim()) {
    ElMessage.warning('请输入模块名称')
    return
  }
  if (!form.value.entryUrl.trim()) {
    ElMessage.warning('请输入入口地址')
    return
  }
  // Validate JSON
  try {
    JSON.parse(form.value.menuConfig)
    JSON.parse(form.value.permissions)
  } catch {
    ElMessage.warning('菜单配置和权限配置必须是合法的 JSON')
    return
  }

  submitting.value = true
  try {
    if (editingId.value) {
      await moduleApi.update(editingId.value, form.value)
      ElMessage.success('模块已更新')
    } else {
      await moduleApi.create(form.value)
      ElMessage.success('模块已注册')
    }
    dialogVisible.value = false
    fetchList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row: ModuleVO) {
  const action = row.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}模块「${row.moduleName}」吗？`, '提示', {
      type: 'warning',
    })
    await moduleApi.toggleStatus(row.id)
    ElMessage.success(`已${action}`)
    fetchList()
  } catch {
    // cancelled
  }
}

async function deleteModule(row: ModuleVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除模块「${row.moduleName}」吗？此操作不可恢复。`,
      '危险操作',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
    await moduleApi.delete(row.id)
    ElMessage.success('模块已删除')
    fetchList()
  } catch {
    // cancelled
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <!-- Toolbar -->
    <div class="toolbar">
      <div class="left">
        <el-input
          v-model="keyword"
          placeholder="搜索模块编码/名称"
          clearable
          style="width: 220px"
          @keyup.enter="fetchList"
        />
        <el-select
          v-model="statusFilter"
          placeholder="状态"
          clearable
          style="width: 120px"
        >
          <el-option :value="1" label="启用中" />
          <el-option :value="0" label="已停用" />
        </el-select>
        <el-button :icon="Search" type="primary" @click="fetchList">搜索</el-button>
        <el-button :icon="Refresh" @click="resetSearch">重置</el-button>
      </div>
      <div class="right">
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openCreate">注册新模块</el-button>
      </div>
    </div>

    <!-- Table -->
    <el-table v-loading="loading" :data="filteredList" stripe style="width: 100%">
      <el-table-column prop="moduleCode" label="模块编码" width="140" />
      <el-table-column prop="moduleName" label="模块名称" width="160" />
      <el-table-column prop="version" label="版本" width="100" />
      <el-table-column prop="entryUrl" label="入口地址" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="(row as ModuleVO).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as ModuleVO).status === 1 ? '启用中' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170">
        <template #default="{ row }">
          {{ (row as ModuleVO).updatedAt?.substring(0, 19).replace('T', ' ') }}
        </template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as ModuleVO)">编辑</el-button>
          <el-button
            link
            :type="(row as ModuleVO).status === 1 ? 'warning' : 'success'"
            @click="toggleStatus(row as ModuleVO)"
          >
            {{ (row as ModuleVO).status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="deleteModule(row as ModuleVO)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="640px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="110px">
        <el-form-item label="模块编码">
          <el-input
            v-model="form.moduleCode"
            placeholder="如 approval, attendance"
            :disabled="!!editingId"
          />
        </el-form-item>
        <el-form-item label="模块名称">
          <el-input v-model="form.moduleName" placeholder="如 OA审批中心" />
        </el-form-item>
        <el-form-item label="版本号">
          <el-input v-model="form.version" placeholder="如 1.0.0" />
        </el-form-item>
        <el-form-item label="入口地址">
          <el-input v-model="form.entryUrl" placeholder="如 /company/approval" />
        </el-form-item>
        <el-form-item label="菜单配置">
          <el-input
            v-model="form.menuConfig"
            type="textarea"
            :rows="3"
            placeholder='JSON 数组, 如 ["审批中心"]'
          />
        </el-form-item>
        <el-form-item label="权限配置">
          <el-input
            v-model="form.permissions"
            type="textarea"
            :rows="3"
            placeholder='JSON 数组, 如 ["approval:view","approval:apply"]'
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
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
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.toolbar .left {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
