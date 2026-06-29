<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { dashboardApi } from '@/api/dashboard'
import type { DashboardStats } from '@/types/dashboard'

const stats = ref<DashboardStats | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    stats.value = await dashboardApi.stats()
  } finally {
    loading.value = false
  }
})

const statusText = (s: number) => (s === 1 ? '启用' : s === 2 ? '停用' : '待激活')
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="stat-cards">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">企业总数</div>
        <div class="stat-value">{{ stats?.companyTotal ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">活跃企业</div>
        <div class="stat-value">{{ stats?.companyActive ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">今日新增企业</div>
        <div class="stat-value">{{ stats?.companyTodayNew ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">总用户数</div>
        <div class="stat-value">{{ stats?.userTotal ?? 0 }}</div>
      </el-card>
    </div>

    <el-card class="recent-card" shadow="never">
      <template #header><span>最近注册企业</span></template>
      <el-table :data="stats?.recentCompanies ?? []" stripe>
        <el-table-column prop="companyNo" label="企业编号" width="140" />
        <el-table-column prop="fullName" label="企业全称" />
        <el-table-column prop="shortName" label="简称" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ statusText(row.status) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  border-radius: 10px;
}
.stat-label {
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
}
.recent-card {
  border-radius: 10px;
}
</style>
