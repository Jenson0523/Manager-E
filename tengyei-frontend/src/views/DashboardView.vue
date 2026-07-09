<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { announcementApi, type BannerVO } from '@/api/announcement'
import AnnouncementDetailDialog from '@/components/AnnouncementDetailDialog.vue'
import SuperDashboard from './dashboard/SuperDashboard.vue'
import CompanyDashboard from './dashboard/CompanyDashboard.vue'

const auth = useAuthStore()
const router = useRouter()
// Platform tier (tenant_id === 0: owner or platform staff) sees the platform dashboard;
// company-tier users see the company dashboard. Keys on tier, not the is_super_admin flag.
const isPlatform = computed(() => auth.userInfo?.tenantId === 0)

/* 首页横幅轮播:存量公告 + 系统计算(待办审批/到期提醒);会话内可关闭;紧急额外弹窗 */
const banners = ref<BannerVO[]>([])
const CLOSED_KEY = 'closed_banners'
const POPPED_KEY = 'popped_urgent'
function storedSet(key: string): Set<string> {
  try {
    return new Set(JSON.parse(sessionStorage.getItem(key) ?? '[]'))
  } catch {
    return new Set()
  }
}
function bannerKey(b: BannerVO) {
  return b.id != null ? `a${b.id}` : `s${b.title}`
}
const visibleBanners = computed(() => {
  const closed = storedSet(CLOSED_KEY)
  return banners.value.filter((b) => !closed.has(bannerKey(b)))
})
function closeBanner(b: BannerVO) {
  const closed = storedSet(CLOSED_KEY)
  closed.add(bannerKey(b))
  sessionStorage.setItem(CLOSED_KEY, JSON.stringify([...closed]))
  banners.value = [...banners.value]
}
const detailRef = ref<InstanceType<typeof AnnouncementDetailDialog>>()
function onBannerClick(b: BannerVO) {
  // 发布的通知(有id)弹详情;系统计算横幅走各自跳转
  if (b.id != null) detailRef.value?.open(b.id)
  else if (b.linkUrl) router.push(b.linkUrl)
}
function alertType(level: string) {
  return level === 'URGENT' ? 'error' : level === 'WARN' ? 'warning' : 'info'
}

/* 紧急通知弹窗:每条会话内只弹一次 */
const urgentDialog = ref(false)
const urgentList = ref<BannerVO[]>([])
function popUrgent() {
  const popped = storedSet(POPPED_KEY)
  urgentList.value = visibleBanners.value.filter(
    (b) => b.level === 'URGENT' && !popped.has(bannerKey(b)),
  )
  if (!urgentList.value.length) return
  urgentList.value.forEach((b) => popped.add(bannerKey(b)))
  sessionStorage.setItem(POPPED_KEY, JSON.stringify([...popped]))
  urgentDialog.value = true
}
function goUrgent(b: BannerVO) {
  urgentDialog.value = false
  if (b.id != null) detailRef.value?.open(b.id)
  else if (b.linkUrl) router.push(b.linkUrl)
}

onMounted(async () => {
  try {
    banners.value = await announcementApi.active()
    popUrgent()
  } catch {
    // 横幅拉取失败不影响首页
  }
})
</script>

<template>
  <div>
    <el-carousel
      v-if="visibleBanners.length"
      class="banner-carousel"
      height="44px"
      direction="vertical"
      :autoplay="visibleBanners.length > 1"
      :interval="4000"
      indicator-position="none"
    >
      <el-carousel-item v-for="b in visibleBanners" :key="bannerKey(b)">
        <el-alert
          :type="alertType(b.level)"
          :closable="true"
          :class="{ clickable: b.id != null || !!b.linkUrl }"
          @close="closeBanner(b)"
          @click="onBannerClick(b)"
        >
          <template #title>
            <span>{{ b.title }}</span>
            <span v-if="b.content" class="banner-content">{{ b.content }}</span>
            <span v-if="b.id != null || b.linkUrl" class="banner-go">点击查看 ›</span>
          </template>
        </el-alert>
      </el-carousel-item>
    </el-carousel>

    <el-dialog v-model="urgentDialog" title="⚠️ 紧急通知" width="440px" append-to-body>
      <div
        v-for="b in urgentList"
        :key="bannerKey(b)"
        class="urgent-item"
        :class="{ clickable: b.id != null || !!b.linkUrl }"
        @click="goUrgent(b)"
      >
        <div class="urgent-title">{{ b.title }}</div>
        <div v-if="b.content" class="urgent-content">{{ b.content }}</div>
        <div v-if="b.id != null || b.linkUrl" class="banner-go">点击查看 ›</div>
      </div>
      <template #footer>
        <el-button type="primary" @click="urgentDialog = false">知道了</el-button>
      </template>
    </el-dialog>

    <AnnouncementDetailDialog ref="detailRef" />

    <SuperDashboard v-if="isPlatform" />
    <CompanyDashboard v-else />
  </div>
</template>

<style scoped>
.banner-carousel {
  margin-bottom: 12px;
  border-radius: 6px;
}
.banner-carousel .clickable {
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
.urgent-item {
  padding: 10px 12px;
  border: 1px solid var(--el-color-danger-light-5);
  background: var(--el-color-danger-light-9);
  border-radius: 6px;
}
.urgent-item + .urgent-item {
  margin-top: 8px;
}
.urgent-item.clickable {
  cursor: pointer;
}
.urgent-title {
  font-weight: 600;
  color: var(--el-color-danger);
}
.urgent-content {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
</style>
