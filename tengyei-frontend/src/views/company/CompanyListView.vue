<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { companyApi } from '@/api/company'
import type { CompanyVO, CompanyCreateDTO, CompanyUpdateDTO } from '@/types/company'

const loading = ref(false)
const list = ref<CompanyVO[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

/* ---- 新增 ---- */
const createVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<CompanyCreateDTO>({
  fullName: '',
  shortName: '',
  expireDate: '',
  adminName: '',
  adminPhone: '',
  adminUsername: '',
  adminPassword: '',
})
const createRules: FormRules = {
  fullName: [{ required: true, message: '请输入企业全称', trigger: 'blur' }],
  shortName: [{ required: true, message: '请输入企业简称', trigger: 'blur' }],
  adminName: [{ required: true, message: '请输入管理员姓名', trigger: 'blur' }],
  adminPhone: [{ required: true, message: '请输入管理员电话', trigger: 'blur' }],
  adminUsername: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  adminPassword: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
}

/* ---- 编辑 ---- */
const editVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editingId = ref<number | null>(null)
const editForm = reactive<CompanyUpdateDTO>({
  expireDate: '',
  fullName: '',
  shortName: '',
  creditCode: '',
  adminName: '',
  adminPhone: '',
  adminEmail: '',
  remark: '',
})
const editRules: FormRules = {
  fullName: [{ required: true, message: '请输入企业全称', trigger: 'blur' }],
  shortName: [{ required: true, message: '请输入企业简称', trigger: 'blur' }],
}

async function fetchList() {
  loading.value = true
  try {
    const res = await companyApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  fetchList()
}

function openCreate() {
  Object.assign(createForm, {
    fullName: '', shortName: '', creditCode: '', expireDate: '', adminName: '',
    adminPhone: '', adminUsername: '', adminPassword: '',
  })
  createVisible.value = true
}

async function submitCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate()
  await companyApi.create({ ...createForm })
  ElMessage.success('企业创建成功')
  createVisible.value = false
  fetchList()
}

function openEdit(row: CompanyVO) {
  editingId.value = row.id
  Object.assign(editForm, {
    fullName: row.fullName,
    shortName: row.shortName,
    creditCode: row.creditCode ?? '',
    adminName: row.adminName,
    adminPhone: row.adminPhone,
    adminEmail: row.adminEmail ?? '',
    expireDate: row.expireDate ?? '',
    remark: row.remark ?? '',
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!editFormRef.value || editingId.value == null) return
  await editFormRef.value.validate()
  await companyApi.update(editingId.value, { ...editForm })
  ElMessage.success('企业信息已更新')
  editVisible.value = false
  fetchList()
}

async function toggleStatus(row: CompanyVO) {
  const next = row.status === 1 ? 2 : 1
  const action = next === 2 ? '停用' : '启用'
  await ElMessageBox.confirm(`确认${action}企业「${row.shortName}」？`, '提示', { type: 'warning' })
  await companyApi.changeStatus(row.id, next)
  ElMessage.success(`已${action}`)
  fetchList()
}

async function deleteCompany(row: CompanyVO) {
  if (row.status === 1) {
    ElMessage.warning('请先停用企业再删除')
    return
  }
  await ElMessageBox.confirm(`确认删除企业「${row.shortName}」？此操作不可恢复！`, '危险操作', {
    type: 'error',
    confirmButtonText: '确定删除',
  })
  await companyApi.delete(row.id)
  ElMessage.success('企业已删除')
  fetchList()
}

const statusText = (s: number) => (s === 1 ? '启用' : s === 2 ? '停用' : '待激活')
const statusType = (s: number): 'success' | 'info' | 'warning' => (s === 1 ? 'success' : s === 2 ? 'info' : 'warning')

/* 合作到期预警 */
const expireWarn = (dateStr?: string) => {
  if (!dateStr) return { text: '-', type: 'info' }
  const exp = new Date(dateStr)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const diff = Math.floor((exp.getTime() - today.getTime()) / 86400000)
  if (diff < 0) return { text: '已过期', type: 'danger' }
  if (diff <= 30) return { text: diff + '天后到期', type: 'warning' }
  if (diff <= 90) return { text: diff + '天后到期', type: '' }
  return { text: dateStr, type: 'success' }
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索企业名称"
        clearable
        style="width: 240px"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-button type="primary" @click="onSearch">搜索</el-button>
      <el-button type="primary" @click="openCreate">新增企业</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="companyNo" label="企业编号" width="130" />
      <el-table-column prop="fullName" label="企业全称" min-width="200" />
      <el-table-column prop="adminName" label="联系人" width="120" />
      <el-table-column prop="adminPhone" label="联系电话" width="140" />
      <el-table-column prop="adminUsername" label="管理人员账号" width="130" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType((row as CompanyVO).status)">{{ statusText((row as CompanyVO).status) }}</el-tag>
        </template>
      </el-table-column>
            <el-table-column label="合作到期时间" width="140">
        <template #default="{ row }">
          <el-tag :type="expireWarn((row as CompanyVO).expireDate).type as any" effect="plain">
            {{ expireWarn((row as CompanyVO).expireDate).text }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as CompanyVO)">编辑</el-button>
          <el-button link type="primary" @click="toggleStatus(row as CompanyVO)">
            {{ (row as CompanyVO).status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="deleteCompany(row as CompanyVO)">删除</el-button>
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

    <!-- 新增对话框 -->
    <el-dialog v-model="createVisible" title="新增企业" width="520px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="企业全称" prop="fullName">
          <el-input v-model="createForm.fullName" />
        </el-form-item>
        <el-form-item label="企业简称" prop="shortName">
          <el-input v-model="createForm.shortName" />
        </el-form-item>
        <el-form-item label="统一社会信用代码">
          <el-input v-model="createForm.creditCode" placeholder="统一社会信用代码" />
        </el-form-item>
        <el-form-item label="合作到期时间">
          <el-date-picker v-model="createForm.expireDate" type="date" placeholder="选择到期日期" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="联系人姓名" prop="adminName">
          <el-input v-model="createForm.adminName" />
        </el-form-item>
        <el-form-item label="联系电话" prop="adminPhone">
          <el-input v-model="createForm.adminPhone" />
        </el-form-item>
        <el-form-item label="管理员账号" prop="adminUsername">
          <el-input v-model="createForm.adminUsername" />
        </el-form-item>
        <el-form-item label="管理员密码" prop="adminPassword">
          <el-input v-model="createForm.adminPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" title="编辑企业信息" width="520px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="110px">
        <el-form-item label="企业全称" prop="fullName">
          <el-input v-model="editForm.fullName" />
        </el-form-item>
        <el-form-item label="企业简称" prop="shortName">
          <el-input v-model="editForm.shortName" />
        </el-form-item>
        <el-form-item label="统一社会信用代码">
          <el-input v-model="editForm.creditCode" />
        </el-form-item>
        <el-form-item label="联系人姓名">
          <el-input v-model="editForm.adminName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="editForm.adminPhone" />
        </el-form-item>
        <el-form-item label="联系邮箱">
          <el-input v-model="editForm.adminEmail" />
        </el-form-item>
        <el-form-item label="合作到期时间">
          <el-date-picker v-model="editForm.expireDate" type="date" placeholder="选择日期" style="width: 100%" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
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
