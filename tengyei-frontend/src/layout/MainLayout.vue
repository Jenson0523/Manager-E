<script setup lang="ts">
import { watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTabStore } from '@/stores/tab'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppTabBar from './AppTabBar.vue'

const route = useRoute()
const router = useRouter()
const tabStore = useTabStore()

watch(
  () => route.path,
  (path) => {
    if (path === '/login' || path === '/403') return
    const title = (route.meta.title as string) || '页面'
    const closable = path !== '/dashboard'
    tabStore.openTab({ path, title, closable })
  },
  { immediate: true },
)

function onSelect(path: string) {
  tabStore.setActive(path)
  if (route.path !== path) router.push(path)
}

function onClose(path: string) {
  const next = tabStore.closeTab(path)
  if (route.path !== next) router.push(next)
}

function onCloseOthers(path: string) {
  tabStore.closeOthers(path)
  if (route.path !== path) router.push(path)
}

function onCloseAll() {
  const next = tabStore.closeAll()
  if (route.path !== next) router.push(next)
}
</script>

<template>
  <div class="main-layout">
    <AppSidebar class="layout-sidebar" />
    <div class="layout-body">
      <AppHeader />
      <AppTabBar
        @select="onSelect"
        @close="onClose"
        @close-others="onCloseOthers"
        @close-all="onCloseAll"
      />
      <main class="layout-content">
        <RouterView v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}
.layout-sidebar {
  width: var(--sidebar-width, 220px);
  flex-shrink: 0;
}
.layout-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--content-bg, #f5f7fa);
}
.layout-content {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
</style>
