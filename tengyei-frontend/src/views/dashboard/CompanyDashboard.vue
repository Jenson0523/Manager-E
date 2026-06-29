<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi } from '@/api/dashboard'
import { useAuthStore } from '@/stores/auth'
import type { DashboardStats } from '@/types/dashboard'

const stats = ref<DashboardStats | null>(null)
const loading = ref(true)
const router = useRouter()
const auth = useAuthStore()

onMounted(async () => {
  try {
    stats.value = await dashboardApi.stats()
  } finally {
    loading.value = false
  }
})

interface Shortcut {
  title: string
  path: string
  perm: string
}
const shortcuts: Shortcut[] = [
  { title: '组织管理', path: '/company/org', perm: 'dept:view' },
  { title: '人员管理', path: '/company/users', perm: 'user:view' },
  { title: '角色与权限', path: '/company/roles', perm: 'role:view' },
]
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <div class="stat-cards">
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">部门数</div>
        <div class="stat-value">{{ stats?.deptCount ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">分支机构</div>
        <div class="stat-value">{{ stats?.branchCount ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">人员总数</div>
        <div class="stat-value">{{ stats?.userCount ?? 0 }}</div>
      </el-card>
      <el-card class="stat-card" shadow="never">
        <div class="stat-label">今日登录</div>
        <div class="stat-value">{{ stats?.todayLoginCount ?? 0 }}</div>
      </el-card>
    </div>

    <el-card class="shortcut-card" shadow="never">
      <template #header><span>快捷操作</span></template>
      <div class="shortcuts">
        <template v-for="s in shortcuts" :key="s.path">
          <div
            v-if="auth.hasPermission(s.perm)"
            class="shortcut"
            @click="router.push(s.path)"
          >
            {{ s.title }}
          </div>
        </template>
      </div>
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
.shortcut-card {
  border-radius: 10px;
}
.shortcuts {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.shortcut {
  padding: 16px 28px;
  background: #f0f5ff;
  color: var(--color-primary, #3b82f6);
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
}
.shortcut:hover {
  background: #e0ecff;
}
</style>
