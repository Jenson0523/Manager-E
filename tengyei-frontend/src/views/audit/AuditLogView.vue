<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { auditApi, loginLogApi, type AuditLogVO, type LoginLogVO } from '@/api/audit'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canExport = computed(() => auth.hasPermission('PERM_audit:export') || auth.hasPermission('PERM_platform:audit:export'))

const activeTab = ref('audit')

/* ---------- 操作日志 ---------- */
const loading = ref(false)
const list = ref<AuditLogVO[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 20,
  module: '',
  startDate: '',
  endDate: '',
})

async function fetchList() {
  loading.value = true
  try {
    const res = await auditApi.page({
      page: query.page,
      size: query.size,
      module: query.module || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
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

function onReset() {
  Object.assign(query, { page: 1, module: '', startDate: '', endDate: '' })
  fetchList()
}

const exporting = ref(false)
async function exportAudit() {
  exporting.value = true
  try {
    await auditApi.export({
      module: query.module || undefined,
      startDate: query.startDate || undefined,
      endDate: query.endDate || undefined,
    })
  } finally {
    exporting.value = false
  }
}

const loginExporting = ref(false)
async function exportLogin() {
  loginExporting.value = true
  try {
    await loginLogApi.export({
      username: loginQuery.username || undefined,
      result: loginQuery.result,
      startDate: loginQuery.startDate || undefined,
      endDate: loginQuery.endDate || undefined,
    })
  } finally {
    loginExporting.value = false
  }
}

onMounted(fetchList)

/* auto-refresh every 10 seconds */
let refreshTimer: ReturnType<typeof setInterval> | null = null
function startRefresh() {
  stopRefresh()
  refreshTimer = setInterval(() => {
    if (activeTab.value === 'audit') fetchList()
    else fetchLoginList()
  }, 10000)
}
function stopRefresh() {
  if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null }
}
startRefresh()
onUnmounted(stopRefresh)

/* ---------- 登录日志 ---------- */
const loginLoading = ref(false)
const loginList = ref<LoginLogVO[]>([])
const loginTotal = ref(0)

const loginQuery = reactive({
  page: 1,
  size: 20,
  username: '',
  result: undefined as number | undefined,
  startDate: '',
  endDate: '',
})

async function fetchLoginList() {
  loginLoading.value = true
  try {
    const res = await loginLogApi.page({
      page: loginQuery.page,
      size: loginQuery.size,
      username: loginQuery.username || undefined,
      result: loginQuery.result,
      startDate: loginQuery.startDate || undefined,
      endDate: loginQuery.endDate || undefined,
    })
    loginList.value = res.records
    loginTotal.value = res.total
  } finally {
    loginLoading.value = false
  }
}

function onLoginSearch() {
  loginQuery.page = 1
  fetchLoginList()
}

function onLoginReset() {
  Object.assign(loginQuery, { page: 1, username: '', result: undefined, startDate: '', endDate: '' })
  fetchLoginList()
}

function handleTabChange(tab: string | number) {
  const name = String(tab)
  if (name === 'login' && loginList.value.length === 0) {
    fetchLoginList()
  }
}
</script>

<template>
  <div class="page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <!-- 操作日志 -->
      <el-tab-pane label="操作日志" name="audit">
        <div class="toolbar">
          <el-input
            v-model="query.module"
            placeholder="模块"
            clearable
            style="width: 140px"
            @clear="onSearch"
          />
          <el-date-picker
            v-model="query.startDate"
            type="date"
            placeholder="开始日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
          <el-date-picker
            v-model="query.endDate"
            type="date"
            placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
          <el-button :disabled="!canExport" :loading="exporting" @click="exportAudit">导出</el-button>
        </div>

        <el-table v-loading="loading" :data="list" stripe>
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column prop="userName" label="操作人" width="120" />
          <el-table-column prop="module" label="模块" width="120" />
          <el-table-column prop="actionType" label="操作类型" width="120" />
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          <el-table-column prop="ipAddress" label="IP" width="140" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="(row as AuditLogVO).result === 1 ? 'success' : 'danger'" size="small">
                {{ (row as AuditLogVO).result === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="errorMsg" label="错误信息" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="(row as AuditLogVO).errorMsg" style="color: #f56c6c">{{ (row as AuditLogVO).errorMsg }}</span>
              <span v-else style="color: #999">-</span>
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
      </el-tab-pane>

      <!-- 登录日志 -->
      <el-tab-pane label="登录日志" name="login">
        <div class="toolbar">
          <el-input
            v-model="loginQuery.username"
            placeholder="用户名"
            clearable
            style="width: 160px"
            @clear="onLoginSearch"
          />
          <el-select
            v-model="loginQuery.result"
            placeholder="结果"
            clearable
            style="width: 120px"
            @clear="onLoginSearch"
          >
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
          <el-date-picker
            v-model="loginQuery.startDate"
            type="date"
            placeholder="开始日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
          <el-date-picker
            v-model="loginQuery.endDate"
            type="date"
            placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 150px"
          />
          <el-button type="primary" @click="onLoginSearch">查询</el-button>
          <el-button @click="onLoginReset">重置</el-button>
          <el-button :disabled="!canExport" :loading="loginExporting" @click="exportLogin">导出</el-button>
        </div>

        <el-table v-loading="loginLoading" :data="loginList" stripe>
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="loginType" label="类型" width="100" />
          <el-table-column prop="ipAddress" label="IP地址" width="150" />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="(row as LoginLogVO).result === 1 ? 'success' : 'danger'" size="small">
                {{ (row as LoginLogVO).result === 1 ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" label="失败原因" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="(row as LoginLogVO).failReason">{{ (row as LoginLogVO).failReason }}</span>
              <span v-else style="color: #999">-</span>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          class="pager"
          layout="total, prev, pager, next"
          :total="loginTotal"
          :current-page="loginQuery.page"
          :page-size="loginQuery.size"
          @current-change="(p: number) => { loginQuery.page = p; fetchLoginList() }"
        />
      </el-tab-pane>
    </el-tabs>
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
  flex-wrap: wrap;
}
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

