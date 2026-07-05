<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Bell, Menu } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useTabStore } from '@/stores/tab'
import { noticeApi, type NoticeVO } from '@/api/notice'

defineProps<{ showMenuButton?: boolean }>()
defineEmits<{ (e: 'toggle-menu'): void }>()

const auth = useAuthStore()
const tabStore = useTabStore()
const route = useRoute()
const router = useRouter()

const currentTitle = computed(() => (route.meta.title as string) || '工作台')
const realName = computed(() => auth.userInfo?.realName ?? '用户')

/* 站内消息:铃铛 + 未读角标,60s 轮询 */
const unread = ref(0)
const notices = ref<NoticeVO[]>([])
let timer: number | undefined

async function refreshUnread() {
  try {
    unread.value = (await noticeApi.unreadCount()).count
  } catch {
    // 静默,下轮再试
  }
}
async function loadNotices() {
  notices.value = await noticeApi.list()
}
async function onNoticeClick(n: NoticeVO) {
  if (!n.isRead) {
    await noticeApi.markRead(n.id)
    n.isRead = 1
    refreshUnread()
  }
  if (n.bizType === 'approval' || n.bizType === 'wf_node') {
    router.push('/company/approval')
  }
}
async function readAll() {
  await noticeApi.markAllRead()
  notices.value.forEach((n) => (n.isRead = 1))
  unread.value = 0
}

onMounted(() => {
  refreshUnread()
  timer = window.setInterval(refreshUnread, 60000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})

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
    <div class="header-left">
      <el-icon v-if="showMenuButton" :size="22" class="menu-btn" @click="$emit('toggle-menu')">
        <Menu />
      </el-icon>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>首页</el-breadcrumb-item>
        <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="header-right">
      <el-popover placement="bottom-end" width="360" trigger="click" @show="loadNotices">
        <template #reference>
          <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell-badge">
            <el-icon :size="20" class="bell-icon"><Bell /></el-icon>
          </el-badge>
        </template>
        <div class="notice-head">
          <span>消息</span>
          <el-button link type="primary" size="small" :disabled="unread === 0" @click="readAll">全部已读</el-button>
        </div>
        <el-empty v-if="!notices.length" description="暂无消息" :image-size="60" />
        <div v-else class="notice-list">
          <div
            v-for="n in notices" :key="n.id"
            :class="['notice-item', { unread: !n.isRead }]"
            @click="onNoticeClick(n)"
          >
            <div class="notice-title">{{ n.title }}</div>
            <div class="notice-content">{{ n.content }}</div>
            <div class="notice-time">{{ n.createdAt }}</div>
          </div>
        </div>
      </el-popover>
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
    </div>
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
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.menu-btn {
  cursor: pointer;
  color: #374151;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 18px;
}
.bell-badge {
  cursor: pointer;
  display: flex;
}
.bell-icon {
  color: #6b7280;
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

<style>
.notice-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.notice-list {
  max-height: 360px;
  overflow-y: auto;
}
.notice-item {
  padding: 8px 6px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
}
.notice-item:hover {
  background: #f9fafb;
}
.notice-item.unread .notice-title {
  font-weight: 600;
}
.notice-item.unread .notice-title::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
  margin-right: 6px;
  vertical-align: middle;
}
.notice-title {
  font-size: 13px;
  color: #1f2937;
}
.notice-content {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}
.notice-time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}
</style>
