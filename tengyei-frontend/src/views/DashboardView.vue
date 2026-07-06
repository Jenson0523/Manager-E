<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { announcementApi, type BannerVO } from '@/api/announcement'
import SuperDashboard from './dashboard/SuperDashboard.vue'
import CompanyDashboard from './dashboard/CompanyDashboard.vue'

const auth = useAuthStore()
const router = useRouter()
// Platform tier (tenant_id === 0: owner or platform staff) sees the platform dashboard;
// company-tier users see the company dashboard. Keys on tier, not the is_super_admin flag.
const isPlatform = computed(() => auth.userInfo?.tenantId === 0)

/* 首页横幅:存量公告 + 系统计算(待办审批/到期提醒);会话内可关闭 */
const banners = ref<BannerVO[]>([])
const CLOSED_KEY = 'closed_banners'
function closedSet(): Set<string> {
  try {
    return new Set(JSON.parse(sessionStorage.getItem(CLOSED_KEY) ?? '[]'))
  } catch {
    return new Set()
  }
}
function bannerKey(b: BannerVO) {
  return b.id != null ? `a${b.id}` : `s${b.title}`
}
const visibleBanners = computed(() => {
  const closed = closedSet()
  return banners.value.filter((b) => !closed.has(bannerKey(b)))
})
function closeBanner(b: BannerVO) {
  const closed = closedSet()
  closed.add(bannerKey(b))
  sessionStorage.setItem(CLOSED_KEY, JSON.stringify([...closed]))
  banners.value = [...banners.value]
}
function onBannerClick(b: BannerVO) {
  if (b.linkUrl) router.push(b.linkUrl)
}
function alertType(level: string) {
  return level === 'URGENT' ? 'error' : level === 'WARN' ? 'warning' : 'info'
}

onMounted(async () => {
  try {
    banners.value = await announcementApi.active()
  } catch {
    // 横幅拉取失败不影响首页
  }
})
</script>

<template>
  <div>
    <div v-if="visibleBanners.length" class="banner-strip">
      <el-alert
        v-for="b in visibleBanners"
        :key="bannerKey(b)"
        :type="alertType(b.level)"
        :closable="true"
        :class="{ clickable: !!b.linkUrl }"
        @close="closeBanner(b)"
        @click="onBannerClick(b)"
      >
        <template #title>
          <span>{{ b.title }}</span>
          <span v-if="b.content" class="banner-content">{{ b.content }}</span>
          <span v-if="b.linkUrl" class="banner-go">点击查看 ›</span>
        </template>
      </el-alert>
    </div>
    <SuperDashboard v-if="isPlatform" />
    <CompanyDashboard v-else />
  </div>
</template>

<style scoped>
.banner-strip {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
.banner-strip .clickable {
  cursor: pointer;
}
.banner-content {
  margin-left: 10px;
  font-weight: 400;
  opacity: 0.85;
}
.banner-go {
  margin-left: 10px;
  font-size: 12px;
  text-decoration: underline;
}
</style>
