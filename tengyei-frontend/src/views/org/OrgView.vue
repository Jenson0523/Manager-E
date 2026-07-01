<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Folder, Document } from '@element-plus/icons-vue'
import { deptApi, branchApi } from '@/api/org'
import { useAuthStore } from '@/stores/auth'
import type { DeptTreeVO, DeptSaveDTO, BranchVO, BranchSaveDTO } from '@/types/org'

const auth = useAuthStore()

/* ---------- Department tree ---------- */
const treeLoading = ref(false)
const deptTree = ref<DeptTreeVO[]>([])
const treeProps = { label: 'name', children: 'children' }

/* 负责人选择 - 公司人员列表 */
const companyUsers = ref<{ id: number; label: string }[]>([])

async function fetchCompanyUsers() {
  try {
    // 通过用户API获取本租户下的用户列表用于负责人选择
    const res = await fetch('/api/v1/users?page=1&size=1000', {
      headers: { Authorization: 'Bearer ' + auth.token },
    }).then(r => r.json())
    if (res.records) {
      companyUsers.value = res.records.map((u: any) => ({
        id: u.id,
        label: u.realName + (u.username ? ` (${u.username})` : ''),
      }))
    }
  } catch {
    // silently ignore - 人员列表可能失败
  }
}

const deptDialog = ref(false)
const deptFormRef = ref<FormInstance>()
const deptEditingId = ref<number | null>(null)
const deptForm = reactive<DeptSaveDTO>({ name: '', parentId: 0, sortOrder: 0, leaderId: undefined })
const deptRules: FormRules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
}

async function fetchTree() {
  treeLoading.value = true
  try {
    deptTree.value = await deptApi.tree()
  } finally {
    treeLoading.value = false
  }
}

function openDeptCreate(parent?: DeptTreeVO) {
  deptEditingId.value = null
  Object.assign(deptForm, { name: '', code: '', parentId: parent ? parent.id : 0, sortOrder: 0, leaderId: undefined })
  deptDialog.value = true
}

function openDeptEdit(node: DeptTreeVO) {
  deptEditingId.value = node.id
  Object.assign(deptForm, {
    name: node.name,
    code: node.code,
    parentId: node.parentId,
    sortOrder: node.sortOrder,
    leaderId: node.leaderId,
  })
  deptDialog.value = true
}

async function submitDept() {
  if (!deptFormRef.value) return
  await deptFormRef.value.validate()
  if (deptEditingId.value) {
    await deptApi.update(deptEditingId.value, { ...deptForm })
    ElMessage.success('部门已更新')
  } else {
    await deptApi.create({ ...deptForm })
    ElMessage.success('部门已创建')
  }
  deptDialog.value = false
  fetchTree()
}

async function removeDept(node: DeptTreeVO) {
  await ElMessageBox.confirm(`确认删除部门「${node.name}」？`, '提示', { type: 'warning' })
  await deptApi.remove(node.id)
  ElMessage.success('已删除')
  fetchTree()
}

/* ---------- Branch list ---------- */
const branchLoading = ref(false)
const branches = ref<BranchVO[]>([])
const branchTotal = ref(0)
const branchQuery = reactive({ page: 1, size: 20 })

const branchDialog = ref(false)
const branchFormRef = ref<FormInstance>()
const branchEditingId = ref<number | null>(null)
const branchForm = reactive<BranchSaveDTO>({ branchNo: '', name: '', type: 'independent' })
const branchRules: FormRules = {
  branchNo: [{ required: true, message: '请输入机构编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入机构名称', trigger: 'blur' }],
}

async function fetchBranches() {
  branchLoading.value = true
  try {
    const res = await branchApi.page({ page: branchQuery.page, size: branchQuery.size })
    branches.value = res.records
    branchTotal.value = res.total
  } finally {
    branchLoading.value = false
  }
}

function openBranchCreate() {
  branchEditingId.value = null
  Object.assign(branchForm, {
    branchNo: '',
    name: '',
    type: 'independent',
    city: '',
    phone: '',
  })
  branchDialog.value = true
}

function openBranchEdit(row: BranchVO) {
  branchEditingId.value = row.id
  Object.assign(branchForm, {
    branchNo: row.branchNo,
    name: row.name,
    type: row.type,
    city: row.city,
    phone: row.phone,
  })
  branchDialog.value = true
}

async function submitBranch() {
  if (!branchFormRef.value) return
  await branchFormRef.value.validate()
  if (branchEditingId.value) {
    await branchApi.update(branchEditingId.value, { ...branchForm })
    ElMessage.success('分支机构已更新')
  } else {
    await branchApi.create({ ...branchForm })
    ElMessage.success('分支机构已创建')
  }
  branchDialog.value = false
  fetchBranches()
}

async function toggleBranch(row: BranchVO) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 0 ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}「${row.name}」？`, '提示', { type: 'warning' })
  await branchApi.changeStatus(row.id, next)
  ElMessage.success(`已${action}`)
  fetchBranches()
}

/* ---------- Branch-Dept association ---------- */
const deptLinkDialog = ref(false)
const linkBranchId = ref<number | null>(null)
const linkBranchName = ref('')
const checkedDeptIds = ref<number[]>([])
const deptLinkLoading = ref(false)

/** 扁平化部门树为列表 */
function flattenTree(nodes: DeptTreeVO[]): { id: number; name: string; indent: string }[] {
  const result: { id: number; name: string; indent: string }[] = []
  function walk(list: DeptTreeVO[], level: number) {
    for (const n of list) {
      result.push({ id: n.id, name: n.name, indent: '　'.repeat(level) })
      if (n.children && n.children.length > 0) walk(n.children, level + 1)
    }
  }
  walk(nodes, 0)
  return result
}

const flatDepts = ref<{ id: number; name: string; indent: string }[]>([])

function openDeptLink(row: BranchVO) {
  linkBranchId.value = row.id
  linkBranchName.value = row.name
  checkedDeptIds.value = row.deptIds ? [...row.deptIds] : []
  flatDepts.value = flattenTree(deptTree.value)
  deptLinkDialog.value = true
}

async function submitDeptLink() {
  if (linkBranchId.value == null) return
  deptLinkLoading.value = true
  try {
    await branchApi.linkDepts(linkBranchId.value, checkedDeptIds.value)
    ElMessage.success('部门关联已更新')
    deptLinkDialog.value = false
    fetchBranches()
  } finally {
    deptLinkLoading.value = false
  }
}

async function removeBranch(row: BranchVO) {
  await ElMessageBox.confirm(`确认删除分公司「${row.name}」？关联部门数据将同步清除。`, '提示', { type: 'warning' })
  await branchApi.remove(row.id)
  ElMessage.success('已删除')
  fetchBranches()
}

onMounted(() => {
  fetchTree()
  fetchBranches()
  fetchCompanyUsers()
})
</script>

<template>
  <div class="org">
    <el-card class="dept-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>部门</span>
          <el-button v-if="auth.hasPermission('PERM_dept:create')" link type="primary" @click="openDeptCreate()">新增根部门</el-button>
        </div>
      </template>
      <el-tree
        v-loading="treeLoading"
        :data="deptTree"
        :props="treeProps"
        node-key="id"
        default-expand-all
        :expand-on-click-node="false"
        class="dept-tree"
      >
        <template #default="{ data, node }">
          <span class="tree-node" :class="{ 'is-leaf': !data.children?.length }">
            <span class="tree-icon">
              <el-icon v-if="data.children?.length"><Folder /></el-icon>
              <el-icon v-else><Document /></el-icon>
            </span>
            <span class="tree-label">{{ data.name }}</span>
            <span v-if="data.leaderId && data.leaderName" class="tree-leader">{{ data.leaderName }}</span>
            <span class="tree-actions">
              <el-button v-if="auth.hasPermission('PERM_dept:create')" link type="primary" size="small" @click.stop="openDeptCreate(data)">
                <el-icon><Plus /></el-icon>
              </el-button>
              <el-button v-if="auth.hasPermission('PERM_dept:edit')" link type="primary" size="small" @click.stop="openDeptEdit(data)">
                <el-icon><Edit /></el-icon>
              </el-button>
              <el-button v-if="auth.hasPermission('PERM_dept:delete')" link type="danger" size="small" @click.stop="removeDept(data)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </span>
          </span>
        </template>
      </el-tree>
    </el-card>

    <el-card class="branch-pane" shadow="never">
      <template #header>
        <div class="pane-head">
          <span>分支机构</span>
          <el-button v-if="auth.hasPermission('PERM_branch:create')" type="primary" size="small" @click="openBranchCreate">新增分支机构</el-button>
        </div>
      </template>
      <el-table v-loading="branchLoading" :data="branches" stripe>
        <el-table-column prop="branchNo" label="机构编号" width="120" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column label="关联部门" min-width="160">
          <template #default="{ row }">
            <el-tag
              v-for="(name, idx) in (row as BranchVO).deptNames || []"
              :key="idx"
              size="small"
              style="margin-right: 4px; margin-bottom: 2px"
            >
              {{ name }}
            </el-tag>
            <span v-if="!(row as BranchVO).deptNames?.length" style="color: #999">未关联</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(row as BranchVO).status === 1 ? 'success' : 'info'">
              {{ (row as BranchVO).status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="auth.hasPermission('PERM_branch:edit')" link type="primary" @click="openDeptLink(row as BranchVO)">关联部门</el-button>
            <el-button v-if="auth.hasPermission('PERM_branch:edit')" link type="primary" @click="openBranchEdit(row as BranchVO)">编辑</el-button>
            <el-button v-if="auth.hasPermission('PERM_branch:edit')" link type="primary" @click="toggleBranch(row as BranchVO)">
              {{ (row as BranchVO).status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-if="auth.hasPermission('PERM_branch:delete')" link type="danger" @click="removeBranch(row as BranchVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="branchTotal"
        :current-page="branchQuery.page"
        :page-size="branchQuery.size"
        @current-change="(p: number) => { branchQuery.page = p; fetchBranches() }"
      />
    </el-card>

    <!-- 部门新增/编辑弹窗 -->
    <el-dialog v-model="deptDialog" :title="deptEditingId ? '编辑部门' : '新增部门'" width="420px">
      <el-form ref="deptFormRef" :model="deptForm" :rules="deptRules" label-width="90px">
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="deptForm.name" />
        </el-form-item>
        <el-form-item label="部门编码">
          <el-input v-model="deptForm.code" />
        </el-form-item>
        <el-form-item label="部门负责人">
          <el-select
            v-model="deptForm.leaderId"
            clearable
            filterable
            remote
            :remote-method="fetchCompanyUsers"
            placeholder="搜索选择部门负责人（非必填）"
            style="width: 100%"
          >
            <el-option
              v-for="u in companyUsers"
              :key="u.id"
              :label="u.label"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="deptForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deptDialog = false">取消</el-button>
        <el-button type="primary" @click="submitDept">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分支机构新增/编辑弹窗 -->
    <el-dialog
      v-model="branchDialog"
      :title="branchEditingId ? '编辑分支机构' : '新增分支机构'"
      width="460px"
    >
      <el-form ref="branchFormRef" :model="branchForm" :rules="branchRules" label-width="90px">
        <el-form-item label="机构编号" prop="branchNo">
          <el-input v-model="branchForm.branchNo" />
        </el-form-item>
        <el-form-item label="机构名称" prop="name">
          <el-input v-model="branchForm.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="branchForm.type">
            <el-option label="独立机构" value="independent" />
            <el-option label="附属机构" value="affiliated" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在城市">
          <el-input v-model="branchForm.city" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="branchForm.phone" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="branchDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBranch">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分公司关联部门弹窗 -->
    <el-dialog
      v-model="deptLinkDialog"
      :title="`关联部门 — ${linkBranchName}`"
      width="480px"
    >
      <el-checkbox-group v-model="checkedDeptIds">
        <div v-for="d in flatDepts" :key="d.id" style="margin-bottom: 6px">
          <el-checkbox :value="d.id">{{ d.indent }}{{ d.name }}</el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="deptLinkDialog = false">取消</el-button>
        <el-button type="primary" :loading="deptLinkLoading" @click="submitDeptLink">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.org {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  align-items: start;
}
.dept-pane,
.branch-pane {
  border-radius: 10px;
}
.pane-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.dept-tree :deep(.el-tree-node__content) {
  height: 40px;
  border-radius: 6px;
  margin-bottom: 2px;
  padding-right: 8px !important;
}
.dept-tree :deep(.el-tree-node__content:hover) {
  background-color: #f0f5ff;
}
.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}
.tree-icon {
  display: flex;
  align-items: center;
  color: #606266;
}
.tree-icon .el-icon {
  font-size: 16px;
}
.tree-node.is-leaf .tree-icon {
  color: #909399;
}
.tree-label {
  flex: 1;
  color: #303133;
}
.tree-leader {
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 1px 6px;
  border-radius: 4px;
  white-space: nowrap;
}
.tree-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}
.tree-node:hover .tree-actions {
  opacity: 1;
}
.tree-actions .el-button {
  padding: 4px;
  height: 24px;
  width: 24px;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
