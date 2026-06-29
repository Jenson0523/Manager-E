<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { companyApi } from '@/api/company'
import type { CompanyVO, CompanyCreateDTO } from '@/types/company'

const loading = ref(false)
const list = ref<CompanyVO[]>([])
const total = ref(0)
const query = reactive({ page: 1, size: 20, keyword: '' })

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<CompanyCreateDTO>({
  fullName: '',
  shortName: '',
  adminName: '',
  adminPhone: '',
  adminUsername: '',
  adminPassword: '',
})
const rules: FormRules = {
  fullName: [{ required: true, message: '请输入企业全称', trigger: 'blur' }],
  shortName: [{ required: true, message: '请输入企业简称', trigger: 'blur' }],
  adminName: [{ required: true, message: '请输入管理员姓名', trigger: 'blur' }],
  adminPhone: [{ required: true, message: '请输入管理员电话', trigger: 'blur' }],
  adminUsername: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  adminPassword: [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }],
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
  Object.assign(form, {
    fullName: '',
    shortName: '',
    adminName: '',
    adminPhone: '',
    adminUsername: '',
    adminPassword: '',
  })
  dialogVisible.value = true
}

async function submitCreate() {
  if (!formRef.value) return
  await formRef.value.validate()
  await companyApi.create({ ...form })
  ElMessage.success('企业创建成功')
  dialogVisible.value = false
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

const statusText = (s: number) => (s === 1 ? '启用' : s === 2 ? '停用' : '待激活')
const statusType = (s: number): 'success' | 'info' | 'warning' => (s === 1 ? 'success' : s === 2 ? 'info' : 'warning')

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
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType((row as CompanyVO).status)">{{ statusText((row as CompanyVO).status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggleStatus(row as CompanyVO)">
            {{ (row as CompanyVO).status === 1 ? '停用' : '启用' }}
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

    <el-dialog v-model="dialogVisible" title="新增企业" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="企业全称" prop="fullName">
          <el-input v-model="form.fullName" />
        </el-form-item>
        <el-form-item label="企业简称" prop="shortName">
          <el-input v-model="form.shortName" />
        </el-form-item>
        <el-form-item label="联系人姓名" prop="adminName">
          <el-input v-model="form.adminName" />
        </el-form-item>
        <el-form-item label="联系电话" prop="adminPhone">
          <el-input v-model="form.adminPhone" />
        </el-form-item>
        <el-form-item label="管理员账号" prop="adminUsername">
          <el-input v-model="form.adminUsername" />
        </el-form-item>
        <el-form-item label="管理员密码" prop="adminPassword">
          <el-input v-model="form.adminPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
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
