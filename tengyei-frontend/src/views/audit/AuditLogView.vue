<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { auditApi, type AuditLogVO } from '@/api/audit'

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

onMounted(fetchList)
</script>

<template>
  <div class="page">
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
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="createdAt" label="时间" width="180" />
      <el-table-column prop="userName" label="操作人" width="120" />
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column prop="actionType" label="操作类型" width="120" />
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column prop="ipAddress" label="IP" width="140" />
      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as AuditLogVO).result === 1 ? 'success' : 'danger'" size="small">
            {{ (row as AuditLogVO).result === 1 ? '成功' : '失败' }}
          </el-tag>
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
