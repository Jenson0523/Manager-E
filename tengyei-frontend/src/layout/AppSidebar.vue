<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { HomeFilled, OfficeBuilding, Share, User, Lock, Document, Setting, Loading, Grid } from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const iconMap: Record<string, Component> = {
  '/dashboard': HomeFilled,
  '/admin/companies': OfficeBuilding,
  '/admin/audit-logs': Document,
  '/admin/system-config': Setting,
  '/admin/modules': Grid,
  '/admin/users': User,
  '/admin/roles': Lock,
  '/company/org': Share,
  '/company/users': User,
  '/company/roles': Lock,
  '/company/approval': Document,
}

const titleMap: Record<string, string> = {
  '/dashboard': '工作台',
  '/admin/companies': '企业管理',
  '/admin/audit-logs': '操作日志',
  '/admin/system-config': '系统设置',
  '/admin/modules': '模块管理',
  '/admin/users': '平台人员',
  '/admin/roles': '平台角色',
  '/company/org': '组织管理',
  '/company/users': '人员管理',
  '/company/roles': '角色与权限',
  '/company/approval': '审批中心',
}

const menuRoutes = computed(() => auth.routes)
const activePath = computed(() => route.path)

// Brand: company tenant uses company name + logo, platform uses default
const isCompanyTenant = computed(() => {
  const tid = auth.userInfo?.tenantId
  return tid != null && tid > 0
})
// Only admin (super admin or company admin) can upload logo
const canUploadLogo = computed(() => {
  return isCompanyTenant.value && (auth.userInfo?.isSuperAdmin === true)
})
const brandName = computed(() => {
  return auth.userInfo?.companyName || '腾飞企业管理'
})
const brandLogo = computed(() => {
  return auth.userInfo?.companyLogo || null
})
const brandInitial = computed(() => {
  const name = brandName.value
  return name ? name.charAt(0) : '腾'
})

const logoInputRef = ref<HTMLInputElement | null>(null)
const uploading = ref(false)

function triggerLogoUpload() {
  if (!canUploadLogo.value) return
  logoInputRef.value?.click()
}

async function onLogoSelected(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files || files.length === 0) return
  const file = files[0]

  // Validate
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('Logo 大小不能超过 2MB')
    return
  }
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!ext || !['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext)) {
    ElMessage.error('仅支持 jpg/png/gif/webp/svg 格式')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    // Do NOT set Content-Type manually - axios will auto-set with correct boundary
    const logoUrl = await request.post<string, string>('/v1/upload/logo', formData)
    if (logoUrl) {
      // Update company logo via company edit API
      const tenantId = auth.userInfo?.tenantId
      if (tenantId) {
        await request.put<string, unknown>(`/v1/companies/${tenantId}`, { logoUrl })
        // Refresh user info to get new logo
        await auth.fetchUserInfo()
        ElMessage.success('Logo 上传成功')
      }
    } else {
      ElMessage.error('上传失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  } finally {
    uploading.value = false
    if (logoInputRef.value) logoInputRef.value.value = ''
  }
}

function go(path: string) {
  if (route.path !== path) router.push(path)
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand" :class="{ 'is-company': isCompanyTenant }">
      <div class="brand-logo" @click="triggerLogoUpload" :class="{ 'uploadable': canUploadLogo }">
        <img v-if="brandLogo" :src="brandLogo" class="logo-img" alt="logo" />
        <template v-else>{{ brandInitial }}</template>
        <div v-if="uploading" class="upload-spinner">
          <el-icon class="is-loading"><Loading /></el-icon>
        </div>
      </div>
      <span class="brand-text">{{ brandName }}</span>
      <input
        ref="logoInputRef"
        type="file"
        accept="image/*"
        style="display: none"
        @change="onLogoSelected"
      />
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
        <span>{{ titleMap[r.path] || r.name }}</span>
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
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
  transition: box-shadow 0.2s;
}
.brand-logo.uploadable {
  cursor: pointer;
}
.brand-logo.uploadable:hover {
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.4);
}
.logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
}
.upload-spinner {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 8px;
}
.brand-text {
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
