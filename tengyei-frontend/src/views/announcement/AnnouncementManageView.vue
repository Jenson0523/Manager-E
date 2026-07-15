<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { announcementApi, type AnnouncementVO } from '@/api/announcement'
import AnnouncementDetailDialog from '@/components/AnnouncementDetailDialog.vue'
import { commonApi } from '@/api/common'
import type { DeptTreeVO } from '@/types/org'
import { useAuthStore } from '@/stores/auth'
import { useIsMobile } from '@/utils/responsive'

const auth = useAuthStore()
const isMobile = useIsMobile()
const isPlatform = computed(() => auth.userInfo?.tenantId === 0)
const canManage = computed(() =>
  auth.hasPermission(isPlatform.value ? 'PERM_platform:announcement:manage' : 'PERM_announcement:manage'))
const canDisable = computed(() =>
  auth.hasPermission(isPlatform.value ? 'PERM_platform:announcement:disable' : 'PERM_announcement:disable'))

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
const AUDIENCE_LABEL: Record<string, string> = {
  ALL: '全体成员',
  DEPT: '指定部门',
  ROLE: '指定角色',
}

/* 编辑弹窗 */
const dialog = ref(false)
const form = reactive({
  id: undefined as number | undefined,
  title: '',
  content: '',
  level: 'INFO',
  targetScope: 'SELF',
  targetIds: [] as number[],
  audienceType: 'ALL',
  audienceIds: [] as number[],
  startAt: '',
  endAt: '',
  status: 1,
})
const deptTree = ref<DeptTreeVO[]>([])
const roleOptions = ref<{ id: number; name: string }[]>([])
const companyOptions = ref<{ id: number; name: string }[]>([])
// 受众选择(部门/角色/企业)走全站 /common/options 名录接口(登录即可),
// 不借用 dept:view/role:view/platform:company:view 门控的管理接口,避免只配了通知权限的账号误报无权限
async function loadAudienceOptions() {
  if (deptTree.value.length || roleOptions.value.length) return
  const opts = await commonApi.options()
  deptTree.value = opts.depts
  roleOptions.value = opts.roles
  companyOptions.value = opts.companies ?? []
}
function openCreate() {
  Object.assign(form, {
    id: undefined, title: '', content: '', level: 'INFO',
    targetScope: 'SELF', targetIds: [], audienceType: 'ALL', audienceIds: [],
    startAt: '', endAt: '', status: 1,
  })
  loadAudienceOptions()
  dialog.value = true
}
function openEdit(row: AnnouncementVO) {
  Object.assign(form, {
    id: row.id,
    title: row.title,
    content: row.content ?? '',
    level: row.level,
    targetScope: row.targetScope,
    targetIds: row.targetIds ? row.targetIds.split(',').map(Number) : [],
    audienceType: row.audienceType ?? 'ALL',
    audienceIds: row.audienceIds ? row.audienceIds.split(',').map(Number) : [],
    startAt: row.startAt ?? '',
    endAt: row.endAt ?? '',
    status: row.status,
  })
  loadAudienceOptions()
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
    targetScope: form.targetScope,
    targetIds: form.targetScope === 'COMPANIES' ? form.targetIds : undefined,
    audienceType: form.audienceType,
    audienceIds: form.audienceType !== 'ALL' ? form.audienceIds : undefined,
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
  await announcementApi.setStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('已更新')
  fetchList()
}

const detailRef = ref<InstanceType<typeof AnnouncementDetailDialog>>()

/* 已读名单 */
const readsDialog = ref(false)
const readsList = ref<{ userName: string; readAt: string }[]>([])
const readsTitle = ref('')
async function openReads(row: AnnouncementVO) {
  readsTitle.value = row.title
  readsList.value = await announcementApi.reads(row.id)
  readsDialog.value = true
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <div class="toolbar">
      <el-button v-if="canManage" type="primary" @click="openCreate">发布通知</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column label="标题" min-width="180">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="detailRef?.open((row as AnnouncementVO).id)">
            {{ (row as AnnouncementVO).title }}
          </el-link>
        </template>
      </el-table-column>
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
      <el-table-column label="接收范围" width="110">
        <template #default="{ row }">
          {{ (row as AnnouncementVO).targetScope === 'SELF'
            ? AUDIENCE_LABEL[(row as AnnouncementVO).audienceType ?? 'ALL']
            : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="已读" width="80">
        <template #default="{ row }">
          <el-link type="primary" :underline="false" @click="openReads(row as AnnouncementVO)">
            {{ (row as AnnouncementVO).readCount ?? 0 }}人
          </el-link>
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
      <el-table-column v-if="canManage || canDisable" label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="canManage" link type="primary" @click="openEdit(row as AnnouncementVO)">编辑</el-button>
          <el-button v-if="canDisable" link type="primary" @click="toggle(row as AnnouncementVO)">
            {{ (row as AnnouncementVO).status === 1 ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="canDisable" link type="danger" @click="remove(row as AnnouncementVO)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <AnnouncementDetailDialog ref="detailRef" />

    <!-- 已读名单 -->
    <el-dialog v-model="readsDialog" :title="`已读名单 — ${readsTitle}`" width="420px">
      <el-table :data="readsList" size="small" max-height="360">
        <el-table-column prop="userName" label="姓名" width="140" />
        <el-table-column label="阅读时间">
          <template #default="{ row }">{{ String(row.readAt).replace('T', ' ') }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!readsList.length" description="还没有人查看" :image-size="60" />
      <template #footer>
        <el-button type="primary" @click="readsDialog = false">关闭</el-button>
      </template>
    </el-dialog>

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
          <el-select
            v-model="form.targetScope"
            style="width: 100%"
            @change="form.audienceType = 'ALL'; form.audienceIds = []"
          >
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
        <el-form-item v-if="form.targetScope === 'SELF'" label="接收范围">
          <el-select v-model="form.audienceType" style="width: 100%" @change="form.audienceIds = []">
            <el-option label="全体成员" value="ALL" />
            <el-option label="指定部门" value="DEPT" />
            <el-option label="指定角色" value="ROLE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.targetScope === 'SELF' && form.audienceType === 'DEPT'" label="接收部门">
          <el-tree-select
            v-model="form.audienceIds"
            :data="deptTree"
            node-key="id"
            :props="{ label: 'name' }"
            multiple
            show-checkbox
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item v-if="form.targetScope === 'SELF' && form.audienceType === 'ROLE'" label="接收角色">
          <el-select v-model="form.audienceIds" multiple filterable style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
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
