<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { announcementApi, type AnnouncementVO } from '@/api/announcement'
import { companyApi } from '@/api/company'
import { useAuthStore } from '@/stores/auth'
import { useIsMobile } from '@/utils/responsive'

const auth = useAuthStore()
const isMobile = useIsMobile()
const isPlatform = computed(() => auth.userInfo?.tenantId === 0)

const loading = ref(false)
const list = ref<AnnouncementVO[]>([])
async function fetchList() {
  loading.value = true
  try {
    list.value = await announcementApi.list()
  } finally {
    loading.value = false
  }
}

const SCOPE_LABEL: Record<string, string> = {
  SELF: '本单位',
  ALL_COMPANIES: '全部企业',
  COMPANIES: '指定企业',
}
const LEVEL_TAG: Record<string, 'info' | 'warning' | 'danger'> = {
  INFO: 'info',
  WARN: 'warning',
  URGENT: 'danger',
}

/* 编辑弹窗 */
const dialog = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  title: '',
  content: '',
  level: 'INFO',
  linkUrl: '',
  targetScope: 'SELF',
  targetIds: [] as number[],
  startAt: '',
  endAt: '',
  status: 1,
})
const companyOptions = ref<{ id: number; name: string }[]>([])
async function loadCompanies() {
  if (companyOptions.value.length) return
  const page = await companyApi.page({ page: 1, size: 200 })
  companyOptions.value = page.records.map((c) => ({ id: c.id, name: c.shortName || c.fullName }))
}
function openCreate() {
  Object.assign(form, {
    id: undefined, title: '', content: '', level: 'INFO', linkUrl: '',
    targetScope: 'SELF', targetIds: [], startAt: '', endAt: '', status: 1,
  })
  if (isPlatform.value) loadCompanies()
  dialog.value = true
}
function openEdit(row: AnnouncementVO) {
  Object.assign(form, {
    id: row.id,
    title: row.title,
    content: row.content ?? '',
    level: row.level,
    linkUrl: row.linkUrl ?? '',
    targetScope: row.targetScope,
    targetIds: row.targetIds ? row.targetIds.split(',').map(Number) : [],
    startAt: row.startAt ?? '',
    endAt: row.endAt ?? '',
    status: row.status,
  })
  if (isPlatform.value) loadCompanies()
  dialog.value = true
}
async function submit() {
  if (!form.title) {
    ElMessage.error('请填写标题')
    return
  }
  await announcementApi.save({
    id: form.id,
    title: form.title,
    content: form.content || undefined,
    level: form.level,
    linkUrl: form.linkUrl || undefined,
    targetScope: form.targetScope,
    targetIds: form.targetScope === 'COMPANIES' ? form.targetIds : undefined,
    startAt: form.startAt || undefined,
    endAt: form.endAt || undefined,
    status: form.status,
  })
  ElMessage.success('已保存')
  dialog.value = false
  fetchList()
}
async function remove(row: AnnouncementVO) {
  await ElMessageBox.confirm(`确认删除公告「${row.title}」？`, '提示', { type: 'warning' })
  await announcementApi.remove(row.id)
  ElMessage.success('已删除')
  fetchList()
}
async function toggle(row: AnnouncementVO) {
  await announcementApi.save({
    id: row.id,
    title: row.title,
    content: row.content,
    level: row.level,
    linkUrl: row.linkUrl,
    targetScope: row.targetScope,
    targetIds: row.targetIds ? row.targetIds.split(',').map(Number) : undefined,
    startAt: row.startAt,
    endAt: row.endAt,
    status: row.status === 1 ? 0 : 1,
  })
  ElMessage.success('已更新')
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">发布通知</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="级别" width="90">
        <template #default="{ row }">
          <el-tag :type="LEVEL_TAG[(row as AnnouncementVO).level] ?? 'info'" size="small">
            {{ (row as AnnouncementVO).level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="isPlatform" label="发送范围" width="110">
        <template #default="{ row }">
          {{ SCOPE_LABEL[(row as AnnouncementVO).targetScope] }}
        </template>
      </el-table-column>
      <el-table-column prop="startAt" label="开始" width="160" />
      <el-table-column prop="endAt" label="结束" width="160" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as AnnouncementVO).status === 1 ? 'success' : 'info'" size="small">
            {{ (row as AnnouncementVO).status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as AnnouncementVO)">编辑</el-button>
          <el-button link type="primary" @click="toggle(row as AnnouncementVO)">
            {{ (row as AnnouncementVO).status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" @click="remove(row as AnnouncementVO)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑通知' : '发布通知'" width="520px" :fullscreen="isMobile">
      <el-form label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="128" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="3" maxlength="512" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-select v-model="form.level" style="width: 100%">
            <el-option label="普通" value="INFO" />
            <el-option label="重要" value="WARN" />
            <el-option label="紧急" value="URGENT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isPlatform" label="发送范围">
          <el-select v-model="form.targetScope" style="width: 100%">
            <el-option label="仅平台内部" value="SELF" />
            <el-option label="全部企业" value="ALL_COMPANIES" />
            <el-option label="指定企业" value="COMPANIES" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isPlatform && form.targetScope === 'COMPANIES'" label="目标企业">
          <el-select v-model="form.targetIds" multiple filterable style="width: 100%">
            <el-option v-for="c in companyOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="form.linkUrl" placeholder="站内路径,如 /company/approval,可空" />
        </el-form-item>
        <el-form-item label="展示时间">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-date-picker v-model="form.startAt" type="datetime" placeholder="开始(空=立即)" value-format="YYYY-MM-DDTHH:mm:ss" style="flex: 1" />
            <el-date-picker v-model="form.endAt" type="datetime" placeholder="结束(空=长期)" value-format="YYYY-MM-DDTHH:mm:ss" style="flex: 1" />
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
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
</style>
