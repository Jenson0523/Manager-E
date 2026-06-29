<script setup lang="ts">
import { ref } from 'vue'
import { useTabStore } from '@/stores/tab'
import { Close } from '@element-plus/icons-vue'

const tabStore = useTabStore()

const emit = defineEmits<{
  select: [path: string]
  close: [path: string]
  'close-others': [path: string]
  'close-all': []
}>()

const ctxVisible = ref(false)
const ctxX = ref(0)
const ctxY = ref(0)
const ctxPath = ref('')

function openCtx(e: MouseEvent, path: string) {
  ctxVisible.value = true
  ctxX.value = e.clientX
  ctxY.value = e.clientY
  ctxPath.value = path
}

function closeCtx() {
  ctxVisible.value = false
}
</script>

<template>
  <div class="tabbar" @click="closeCtx">
    <div
      v-for="tab in tabStore.tabs"
      :key="tab.path"
      class="tab"
      :class="{ active: tab.path === tabStore.activePath }"
      @click="emit('select', tab.path)"
      @contextmenu.prevent="openCtx($event, tab.path)"
    >
      <span class="tab-title">{{ tab.title }}</span>
      <el-icon v-if="tab.closable" class="tab-close" @click.stop="emit('close', tab.path)">
        <Close />
      </el-icon>
    </div>

    <ul v-if="ctxVisible" class="tab-context" :style="{ left: ctxX + 'px', top: ctxY + 'px' }">
      <li @click="emit('close', ctxPath)">关闭当前</li>
      <li @click="emit('close-others', ctxPath)">关闭其他</li>
      <li @click="emit('close-all')">关闭全部</li>
    </ul>
  </div>
</template>

<style scoped>
.tabbar {
  height: 40px;
  flex-shrink: 0;
  background: #fff;
  border-bottom: 1px solid #e4e7ec;
  display: flex;
  align-items: flex-end;
  gap: 4px;
  padding: 0 12px;
}
.tab {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid #e4e7ec;
  border-bottom: none;
  border-radius: 6px 6px 0 0;
  background: #f5f7fa;
  font-size: 13px;
  color: #4b5563;
  cursor: pointer;
}
.tab.active {
  background: var(--color-primary, #3b82f6);
  color: #fff;
  border-color: var(--color-primary, #3b82f6);
}
.tab-close {
  font-size: 12px;
  border-radius: 50%;
}
.tab-close:hover {
  background: rgba(0, 0, 0, 0.15);
}
.tab-context {
  position: fixed;
  z-index: 3000;
  background: #fff;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  list-style: none;
  padding: 4px 0;
  margin: 0;
  min-width: 120px;
}
.tab-context li {
  padding: 8px 16px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
}
.tab-context li:hover {
  background: #f3f4f6;
}
</style>
