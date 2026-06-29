<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { HomeFilled, OfficeBuilding, Share, User, Lock } from '@element-plus/icons-vue'
import type { Component } from 'vue'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const iconMap: Record<string, Component> = {
  '/dashboard': HomeFilled,
  '/admin/companies': OfficeBuilding,
  '/company/org': Share,
  '/company/users': User,
  '/company/roles': Lock,
}

const menuRoutes = computed(() => auth.routes)
const activePath = computed(() => route.path)

function go(path: string) {
  if (route.path !== path) router.push(path)
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-logo">腾</div>
      <span class="brand-text">腾飞企业管理</span>
    </div>
    <el-menu
      class="sidebar-menu"
      :default-active="activePath"
      background-color="transparent"
      text-color="#c9ced6"
      active-text-color="#ffffff"
      @select="go"
    >
      <el-menu-item v-for="r in menuRoutes" :key="r.path" :index="r.path">
        <el-icon v-if="iconMap[r.path]"><component :is="iconMap[r.path]" /></el-icon>
        <span>{{ r.name }}</span>
      </el-menu-item>
    </el-menu>
    <div class="sidebar-footer">v2.0</div>
  </aside>
</template>

<style scoped>
.sidebar {
  height: 100vh;
  background: var(--sidebar-bg, #0f1117);
  display: flex;
  flex-direction: column;
  border-right: 1px solid #1e2130;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 16px;
  border-bottom: 1px solid #1e2130;
}
.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--color-primary, #3b82f6);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.brand-text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
}
.sidebar-menu {
  flex: 1;
  border-right: none;
  padding-top: 8px;
}
.sidebar-footer {
  padding: 12px 16px;
  color: #5b6472;
  font-size: 12px;
  border-top: 1px solid #1e2130;
}
:deep(.el-menu-item.is-active) {
  background: rgba(59, 130, 246, 0.15) !important;
  border-left: 2px solid var(--color-primary, #3b82f6);
}
</style>
