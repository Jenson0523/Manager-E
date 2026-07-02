<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useTabStore } from '@/stores/tab'

const auth = useAuthStore()
const tabStore = useTabStore()
const route = useRoute()
const router = useRouter()

const currentTitle = computed(() => (route.meta.title as string) || '工作台')
const realName = computed(() => auth.userInfo?.realName ?? '用户')

function onCommand(command: string) {
  if (command === 'profile') router.push('/profile')
  else if (command === 'logout') handleLogout()
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await auth.logout()
  tabStore.reset()
  router.replace('/login')
}
</script>

<template>
  <header class="app-header">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item>首页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
    </el-breadcrumb>
    <el-dropdown @command="onCommand">
      <span class="user-trigger">
        <el-avatar :size="28" class="user-avatar">{{ realName.charAt(0) }}</el-avatar>
        <span class="user-name">{{ realName }}</span>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">个人中心</el-dropdown-item>
          <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </header>
</template>

<style scoped>
.app-header {
  height: 56px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #e4e7ec;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  outline: none;
}
.user-avatar {
  background: var(--color-primary, #3b82f6);
  color: #fff;
}
.user-name {
  font-size: 14px;
  color: #1f2937;
}
</style>
