<script setup lang="ts">
import { onMounted, ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '@/api/dashboard'
import type { DashboardStats, ChartData } from '@/types/dashboard'
import * as echarts from 'echarts/core'
import { LineChart, PieChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, PieChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const stats = ref<DashboardStats | null>(null)
const loading = ref(true)
const router = useRouter()

const chartData = ref<ChartData | null>(null)
const trendRef = ref<HTMLDivElement>()
const pieRef = ref<HTMLDivElement>()
const compRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
let compChart: echarts.ECharts | null = null

async function fetchCharts() {
  chartData.value = await dashboardApi.chartData()
  await nextTick()
  renderTrend()
  renderPie()
  renderComp()
}

function renderTrend() {
  if (!trendRef.value || !chartData.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  const dates = chartData.value.userTrend.map(d => d.date)
  const counts = chartData.value.userTrend.map(d => d.count)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '新增用户', type: 'line', smooth: true, data: counts, areaStyle: {} }],
  })
}

function renderPie() {
  if (!pieRef.value || !chartData.value) return
  if (!pieChart) pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      name: '用户状态',
      type: 'pie',
      radius: ['40%', '70%'],
      data: chartData.value.statusDist,
    }],
  })
}

function renderComp() {
  if (!compRef.value || !chartData.value?.companyDist?.length) return
  if (!compChart) compChart = echarts.init(compRef.value)
  const names = chartData.value.companyDist.map(d => d.company)
  const vals = chartData.value.companyDist.map(d => d.count)
  compChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: '用户数', type: 'bar', data: vals }],
  })
}

onMounted(async () => {
  try {
    stats.value = await dashboardApi.stats()
  } finally {
    loading.value = false
  }
  fetchCharts()
})

const statusText = (s: number) => (s === 1 ? '启用' : s === 2 ? '停用' : '待激活')
const statusType = (s: number): 'success' | 'info' | 'warning' => (s === 1 ? 'success' : s === 2 ? 'info' : 'warning')
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="stat-cards">
      <el-card class="stat-card clickable" shadow="never" @click="router.push('/admin/companies')">
        <div class="stat-label">企业总数</div>
        <div class="stat-value">{{ stats?.companyTotal ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card clickable" shadow="never" @click="router.push('/admin/companies')">
        <div class="stat-label">活跃企业</div>
        <div class="stat-value">{{ stats?.companyActive ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card clickable" shadow="never" @click="router.push('/admin/companies')">
        <div class="stat-label">今日新增企业</div>
        <div class="stat-value">{{ stats?.companyTodayNew ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card clickable" shadow="never" @click="router.push('/admin/companies')">
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
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
      </el-table>
    </el-card>

    <div class="charts-row">
      <div class="chart-card">
        <div class="chart-title">近7天用户增长</div>
        <div ref="trendRef" style="height: 240px" />
      </div>
      <div class="chart-card">
        <div class="chart-title">用户状态分布</div>
        <div ref="pieRef" style="height: 240px" />
      </div>
    </div>
    <div v-if="chartData?.companyDist?.length" class="chart-card" style="margin-top: 16px">
      <div class="chart-title">各企业用户数（TOP 10）</div>
      <div ref="compRef" style="height: 260px" />
    </div>
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
.stat-card.clickable {
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}
.stat-card.clickable:hover {
  box-shadow: 0 2px 12px rgba(59, 130, 246, 0.2);
  transform: translateY(-1px);
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
.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 16px;
}
.chart-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
}
</style>
